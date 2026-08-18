package com.edgareldy.springbatchtutorial.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.edgareldy.springbatchtutorial.e2e.PostgresTestcontainerConfiguration;
import com.edgareldy.springbatchtutorial.entity.Employee;
import com.edgareldy.springbatchtutorial.entity.PayrollRun;
import com.edgareldy.springbatchtutorial.entity.PayrollRunStatus;
import com.edgareldy.springbatchtutorial.entity.Payslip;
import com.edgareldy.springbatchtutorial.repository.EmployeeRepository;
import com.edgareldy.springbatchtutorial.repository.PayrollRunRepository;
import com.edgareldy.springbatchtutorial.repository.PayslipRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Runs the {@code exportPayrollSummary} step in isolation via
 * {@code JobLauncherTestUtils.launchStep} against a real PostgreSQL instance
 * (Testcontainers), seeding a real {@link PayrollRun} and two real
 * {@link Payslip} rows directly, then reading the CSV file
 * {@code ExportPayrollSummaryTasklet} actually wrote from disk and asserting
 * its exact header and rows. {@code ExportPayrollSummaryTasklet} is
 * {@code @StepScope} and reads {@code payrollRunId}/{@code period} off
 * {@code JobParameters} plus {@code payroll.export.output-dir} off
 * configuration - exercised here through the
 * {@code StepScopeTestExecutionListener} that {@code @SpringBatchTest}
 * registers automatically, the same mechanism the README's testing-strategy
 * section calls out for this exact class.
 * <p>
 * {@code payroll.export.output-dir} is overridden via
 * {@code @TestPropertySource} to a dedicated {@code target/} directory
 * instead of the application's default {@code exports/} directory, so this
 * test never writes into (or races with) whatever the rest of the app/test
 * suite puts there, and its own file is deleted in {@code @AfterEach}
 * regardless of test outcome. Overriding a property this way gives this
 * class its own Spring context (and its own Testcontainers PostgreSQL
 * instance, distinct from the one the rest of the {@code integration}/
 * {@code e2e} suites share) rather than the usual context-cached one - an
 * acceptable one-off cost for a deterministic, isolated file assertion.
 * <p>
 * Created by Edgar Muhamyangabo on 8/17/26
 * Author : Edgar Muhamyangabo
 * Date : 8/17/26
 * Project : spring-batch-tutorial
 */
@ActiveProfiles("test")
@Import(PostgresTestcontainerConfiguration.class)
@SpringBatchTest
@SpringBootTest
@TestPropertySource(properties = "payroll.export.output-dir=target/test-output/export-payroll-summary-step-it")
class ExportPayrollSummaryStepIntegrationTest {

    private static final int PERIOD_YEAR = 2031;
    private static final int PERIOD_MONTH = 5;
    private static final String PERIOD = PERIOD_YEAR + "-" + String.format("%02d", PERIOD_MONTH);
    private static final Path OUTPUT_DIR = Path.of("target", "test-output", "export-payroll-summary-step-it");

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private PayrollRunRepository payrollRunRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private PayslipRepository payslipRepository;

    @AfterEach
    void deleteGeneratedFile() throws IOException {
        Path summaryFile = OUTPUT_DIR.resolve("payroll-summary-" + PERIOD + ".csv");
        Files.deleteIfExists(summaryFile);
    }

    @Test
    void producesCsvWithExactExpectedRowsForAKnownSetOfPayslips() throws Exception {
        PayrollRun payrollRun = createStartedPayrollRun();
        Payslip alicePayslip = seedPayslip(
                payrollRun, "alice.martin@example.com",
                "40.00", "1800.00", "270.00", "1530.00");
        Payslip carlaPayslip = seedPayslip(
                payrollRun, "carla.silva@example.com",
                "32.00", "1120.00", "168.00", "952.00");

        JobExecution execution = jobLauncherTestUtils.launchStep("exportPayrollSummary", jobParametersFor(payrollRun));

        assertThat(execution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);

        Path summaryFile = OUTPUT_DIR.resolve("payroll-summary-" + PERIOD + ".csv");
        assertThat(summaryFile).exists();

        List<String> lines = Files.readAllLines(summaryFile);
        assertThat(lines).containsExactly(
                "employee_id,first_name,last_name,email,department,total_hours,gross_pay,deductions,net_pay",
                expectedRow(alicePayslip),
                expectedRow(carlaPayslip));

        PayrollRun completed = payrollRunRepository.findById(payrollRun.getId()).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(PayrollRunStatus.COMPLETED);
        assertThat(completed.getCompletedAt()).isNotNull();
    }

    @Test
    void producesOnlyTheHeaderRowWhenTheRunHasNoPayslips() throws Exception {
        PayrollRun payrollRun = createStartedPayrollRun();

        JobExecution execution = jobLauncherTestUtils.launchStep("exportPayrollSummary", jobParametersFor(payrollRun));

        assertThat(execution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);

        Path summaryFile = OUTPUT_DIR.resolve("payroll-summary-" + PERIOD + ".csv");
        List<String> lines = Files.readAllLines(summaryFile);
        assertThat(lines).containsExactly(
                "employee_id,first_name,last_name,email,department,total_hours,gross_pay,deductions,net_pay");
    }

    /**
     * Rows come back from {@code PayslipRepository.findSummaryByPayrollRunId}
     * ordered by {@code employee.id}, and Alice Martin/Carla Silva are seeded
     * with ascending ids in {@code V1__init_schema.sql}, so the expected CSV
     * row order below (Alice, then Carla) matches that ordering rather than
     * insertion order into this test.
     */
    private String expectedRow(Payslip payslip) {
        Employee employee = payslip.getEmployee();
        return String.join(",",
                employee.getId().toString(),
                employee.getFirstName(),
                employee.getLastName(),
                employee.getEmail(),
                employee.getDepartment(),
                payslip.getTotalHours().toString(),
                payslip.getGrossPay().toString(),
                payslip.getDeductions().toString(),
                payslip.getNetPay().toString());
    }

    private Payslip seedPayslip(
            PayrollRun payrollRun, String employeeEmail,
            String totalHours, String grossPay, String deductions, String netPay) {
        Employee employee = employeeRepository.findByEmail(employeeEmail).orElseThrow();
        Payslip payslip = new Payslip();
        payslip.setPayrollRun(payrollRun);
        payslip.setEmployee(employee);
        payslip.setTotalHours(new BigDecimal(totalHours));
        payslip.setGrossPay(new BigDecimal(grossPay));
        payslip.setDeductions(new BigDecimal(deductions));
        payslip.setNetPay(new BigDecimal(netPay));
        payslip.setGeneratedAt(LocalDateTime.now());
        return payslipRepository.save(payslip);
    }

    private PayrollRun createStartedPayrollRun() {
        PayrollRun payrollRun = new PayrollRun();
        payrollRun.setPeriodMonth(PERIOD_MONTH);
        payrollRun.setPeriodYear(PERIOD_YEAR);
        payrollRun.setStatus(PayrollRunStatus.STARTED);
        payrollRun.setStartedAt(LocalDateTime.now());
        return payrollRunRepository.save(payrollRun);
    }

    private JobParameters jobParametersFor(PayrollRun payrollRun) {
        return new JobParametersBuilder()
                .addLong("payrollRunId", payrollRun.getId())
                .addString("period", PERIOD)
                .toJobParameters();
    }
}
