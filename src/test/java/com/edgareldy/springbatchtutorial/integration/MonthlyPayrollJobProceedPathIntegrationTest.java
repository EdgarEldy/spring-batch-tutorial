package com.edgareldy.springbatchtutorial.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.edgareldy.springbatchtutorial.e2e.PostgresTestcontainerConfiguration;
import com.edgareldy.springbatchtutorial.entity.Employee;
import com.edgareldy.springbatchtutorial.entity.PayrollRun;
import com.edgareldy.springbatchtutorial.entity.PayrollRunStatus;
import com.edgareldy.springbatchtutorial.entity.Payslip;
import com.edgareldy.springbatchtutorial.entity.TimesheetEntry;
import com.edgareldy.springbatchtutorial.repository.EmployeeRepository;
import com.edgareldy.springbatchtutorial.repository.PayrollRunRepository;
import com.edgareldy.springbatchtutorial.repository.PayslipRepository;
import com.edgareldy.springbatchtutorial.repository.TimesheetEntryRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Runs the complete {@code monthlyPayrollJob} via
 * {@code JobLauncherTestUtils.launchJob} against a real PostgreSQL instance
 * (Testcontainers), seeding every employee well below the default
 * 300h/month anomaly threshold, to confirm the {@code PROCEED} branch of
 * {@code anomalyReviewDecider} actually reaches {@code calculatePayslips}
 * then {@code exportPayrollSummary} inside {@code monthlyPayrollJob} itself
 * and lands on {@code PayrollRunStatus.COMPLETED} with a real summary CSV on
 * disk. {@code monthlyPayrollJob} wires this chain through
 * {@code JobBuilder}'s flow/transition API
 * ({@code .from(anomalyReviewDecider).on(PROCEED).to(calculatePayslips).next(exportPayrollSummary)}),
 * a different construction path than the plain
 * {@code .start(calculatePayslips).next(exportPayrollSummary)} used by
 * {@code payrollFinalizeJob} - every other test exercising this chain
 * ({@code MonthlyPayrollJobAnomalyResumeE2ETest}, {@code PayrollRunHappyPathE2ETest})
 * only ever does so via {@code payrollFinalizeJob}'s resume path, never via
 * {@code monthlyPayrollJob} directly, which left this transition itself
 * unverified.
 * <p>
 * Seeds real {@link TimesheetEntry} rows directly under a dedicated period
 * (2031-06, distinct from every other test in this suite, see
 * {@code AggregateHoursPerEmployeeStepIntegrationTest}'s Javadoc for why a
 * dedicated period matters against this project's single shared
 * Testcontainer) rather than replaying {@code importTimesheets} against the
 * real sample CSV, since the real CSV's David Chen sits above the anomaly
 * threshold and would route to {@code flagForReview} instead.
 * <p>
 * {@code payroll.export.output-dir} is overridden via
 * {@code @TestPropertySource} to a dedicated {@code target/} directory so
 * this class never reads/writes the application's default {@code exports/}
 * directory; the generated file is deleted in {@code @AfterEach} regardless
 * of test outcome.
 * <p>
 * Created by Edgar Muhamyangabo on 8/18/26
 * Author : Edgar Muhamyangabo
 * Date : 8/18/26
 * Project : spring-batch-tutorial
 */
@ActiveProfiles("test")
@Import(PostgresTestcontainerConfiguration.class)
@SpringBatchTest
@SpringBootTest
@TestPropertySource(properties = "payroll.export.output-dir=target/test-output/monthly-payroll-job-proceed-path-it")
class MonthlyPayrollJobProceedPathIntegrationTest {

    private static final int PERIOD_YEAR = 2031;
    private static final int PERIOD_MONTH = 6;
    private static final String PERIOD = PERIOD_YEAR + "-" + String.format("%02d", PERIOD_MONTH);
    private static final Path OUTPUT_DIR = Path.of("target", "test-output", "monthly-payroll-job-proceed-path-it");

    private static final List<String> VALID_EMPLOYEE_EMAILS = List.of(
            "alice.martin@example.com",
            "bob.dupont@example.com",
            "carla.silva@example.com",
            "emma.rossi@example.com",
            "david.chen@example.com");

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private PayrollRunRepository payrollRunRepository;

    @Autowired
    private TimesheetEntryRepository timesheetEntryRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private PayslipRepository payslipRepository;

    @AfterEach
    void deleteGeneratedFile() throws IOException {
        Files.deleteIfExists(OUTPUT_DIR.resolve("payroll-summary-" + PERIOD + ".csv"));
    }

    @Test
    void runSeededBelowTheAnomalyThresholdCompletesThroughCalculationAndExport() throws Exception {
        PayrollRun payrollRun = createStartedPayrollRun();
        seedTimesheetData(payrollRun);

        JobExecution execution = jobLauncherTestUtils.launchJob(jobParametersFor(payrollRun));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(execution.getStepExecutions())
                .extracting(StepExecution::getStepName)
                .containsExactly("importTimesheets", "aggregateHoursPerEmployee", "calculatePayslips", "exportPayrollSummary");
        assertThat(execution.getStepExecutions())
                .extracting(StepExecution::getStatus)
                .containsOnly(BatchStatus.COMPLETED);

        PayrollRun completed = payrollRunRepository.findById(payrollRun.getId()).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(PayrollRunStatus.COMPLETED);
        assertThat(completed.getCompletedAt()).isNotNull();

        List<Payslip> payslips = payslipsFor(payrollRun);
        assertThat(payslips).hasSize(VALID_EMPLOYEE_EMAILS.size());

        Path summaryFile = OUTPUT_DIR.resolve("payroll-summary-" + PERIOD + ".csv");
        List<String> lines = Files.readAllLines(summaryFile);
        assertThat(lines.get(0)).isEqualTo(
                "employee_id,first_name,last_name,email,department,total_hours,gross_pay,deductions,net_pay");
        assertThat(lines).hasSize(1 + VALID_EMPLOYEE_EMAILS.size());
        for (String email : VALID_EMPLOYEE_EMAILS) {
            assertThat(lines).as("a row for %s", email).anyMatch(line -> line.contains(email));
        }
    }

    /**
     * Same real per-employee hour distribution as the sample CSV, except
     * David Chen is capped at 8h/day x 5 days (40h) instead of the sample
     * CSV's 312h, so every employee stays below the 300h/month anomaly
     * threshold and the job takes the {@code PROCEED} branch.
     */
    private void seedTimesheetData(PayrollRun payrollRun) {
        LocalDate base = LocalDate.of(payrollRun.getPeriodYear(), payrollRun.getPeriodMonth(), 1);
        saveDailyEntries("alice.martin@example.com", base, new BigDecimal("8.00"), 5);
        saveDailyEntries("bob.dupont@example.com", base, new BigDecimal("7.50"), 5);
        saveDailyEntries("carla.silva@example.com", base, new BigDecimal("8.00"), 4);
        saveDailyEntries("emma.rossi@example.com", base, new BigDecimal("6.00"), 3);
        saveDailyEntries("david.chen@example.com", base, new BigDecimal("8.00"), 5);
    }

    private void saveDailyEntries(String email, LocalDate base, BigDecimal hoursPerDay, int days) {
        Employee employee = employeeRepository.findByEmail(email).orElseThrow();
        for (int day = 0; day < days; day++) {
            TimesheetEntry entry = new TimesheetEntry();
            entry.setEmployee(employee);
            entry.setWorkDate(base.plusDays(day));
            entry.setHoursWorked(hoursPerDay);
            entry.setSourceFile("integration-test-seed");
            entry.setImportedAt(LocalDateTime.now());
            timesheetEntryRepository.save(entry);
        }
    }

    private List<Payslip> payslipsFor(PayrollRun payrollRun) {
        return payslipRepository.findAll().stream()
                .filter(payslip -> payslip.getPayrollRun().getId().equals(payrollRun.getId()))
                .toList();
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
