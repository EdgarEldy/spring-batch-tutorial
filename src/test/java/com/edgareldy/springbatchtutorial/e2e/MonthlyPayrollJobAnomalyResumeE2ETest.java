package com.edgareldy.springbatchtutorial.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.edgareldy.springbatchtutorial.batch.calculationstep.PayslipItemProcessor;
import com.edgareldy.springbatchtutorial.dto.csv.EmployeeHoursAggregate;
import com.edgareldy.springbatchtutorial.entity.Employee;
import com.edgareldy.springbatchtutorial.entity.PayrollRun;
import com.edgareldy.springbatchtutorial.entity.PayrollRunStatus;
import com.edgareldy.springbatchtutorial.entity.Payslip;
import com.edgareldy.springbatchtutorial.repository.EmployeeRepository;
import com.edgareldy.springbatchtutorial.repository.PayrollRunRepository;
import com.edgareldy.springbatchtutorial.repository.PayslipRepository;
import com.edgareldy.springbatchtutorial.repository.TimesheetEntryRepository;
import com.edgareldy.springbatchtutorial.service.PayrollJobLauncherService;
import com.edgareldy.springbatchtutorial.service.PayrollRunService;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
 * Runs the full anomaly path end to end against a real PostgreSQL instance
 * (Testcontainers) and the real sample CSV: {@code monthlyPayrollJob}
 * (import -&gt; aggregate -&gt; flagged for review, since David Chen's real 312h
 * in the sample data is above the default 300h/month anomaly threshold),
 * then the resume half of the flow - {@link PayrollRunService#resumeRun}
 * followed by {@link PayrollJobLauncherService#launchPayrollFinalizeJob} -
 * the exact same two production calls {@code PayrollController#resumeRun}
 * makes, rather than reaching for {@code JobOperator}/{@code JobLauncher}
 * directly. Confirms {@code payrollFinalizeJob} then runs
 * {@code calculatePayslips}, producing the payslips {@code monthlyPayrollJob}
 * never got to write for every one of the sample CSV's five valid employees,
 * followed by {@code exportPayrollSummary} - chained onto
 * {@code calculatePayslips} since {@code feature/export-and-scheduling} -
 * writing the resulting summary CSV to disk.
 * <p>
 * {@code MonthlyPayrollJobE2ETest} stops at asserting the
 * {@code AWAITING_REVIEW} outcome of the first half of this same scenario;
 * this class is where the full round trip - including the resume and the
 * resulting {@link Payslip} rows - is verified, since a single test method
 * covering both would conflate two rather different concerns (a routing
 * decision, and a calculation once resumed).
 * <p>
 * Shares this suite's real 2026-08 period and single cached Testcontainer
 * with every other {@code @SpringBootTest} class here (see
 * {@code MonthlyPayrollJobE2ETest}'s Javadoc): David Chen's hours only ever
 * grow across repeated imports of the same CSV, never shrink, so he stays
 * above the anomaly threshold regardless of run order, while every other
 * employee stays well below it even accumulated across this suite's few
 * real-CSV imports - the expected gross/deductions/net figures below are
 * computed from whatever total hours are actually on record right before
 * {@code calculatePayslips} runs, never hardcoded, for the same reason.
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
@TestPropertySource(properties = "payroll.export.output-dir=target/test-output/monthly-payroll-job-anomaly-resume-e2e")
class MonthlyPayrollJobAnomalyResumeE2ETest {

    private static final List<String> VALID_EMPLOYEE_EMAILS = List.of(
            "alice.martin@example.com",
            "bob.dupont@example.com",
            "carla.silva@example.com",
            "emma.rossi@example.com",
            "david.chen@example.com");

    private static final BigDecimal OVERTIME_THRESHOLD_HOURS = new BigDecimal("160");
    private static final BigDecimal OVERTIME_MULTIPLIER = new BigDecimal("1.5");
    private static final BigDecimal DEDUCTION_RATE = new BigDecimal("0.15");
    private static final String PERIOD = "2026-08";
    private static final Path OUTPUT_DIR = Path.of("target", "test-output", "monthly-payroll-job-anomaly-resume-e2e");

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

    @Autowired
    private PayrollRunService payrollRunService;

    @Autowired
    private PayrollJobLauncherService payrollJobLauncherService;

    @AfterEach
    void deleteGeneratedFile() throws IOException {
        Files.deleteIfExists(OUTPUT_DIR.resolve("payroll-summary-" + PERIOD + ".csv"));
    }

    @Test
    void fullAnomalyPath_importAggregateFlagResumeCalculatePayslips() throws Exception {
        PayrollRun payrollRun = createStartedPayrollRun();

        JobExecution monthlyExecution = jobLauncherTestUtils.launchJob(jobParametersFor(payrollRun));

        assertThat(monthlyExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(monthlyExecution.getStepExecutions())
                .extracting(StepExecution::getStepName)
                .containsExactlyInAnyOrder("importTimesheets", "aggregateHoursPerEmployee", "flagForReview");

        PayrollRun flaggedRun = payrollRunRepository.findById(payrollRun.getId()).orElseThrow();
        assertThat(flaggedRun.getStatus()).isEqualTo(PayrollRunStatus.AWAITING_REVIEW);
        assertThat(payslipsFor(payrollRun)).isEmpty();

        // A human has reviewed David Chen's flagged hours out-of-band and
        // decided to proceed; resumeRun moves the run back to STARTED and
        // payrollFinalizeJob picks up right where monthlyPayrollJob left off,
        // reusing the calculatePayslips step bean as-is, then chains straight
        // into exportPayrollSummary.
        PayrollRun resumedRun = payrollRunService.resumeRun(payrollRun.getId());
        assertThat(resumedRun.getStatus()).isEqualTo(PayrollRunStatus.STARTED);

        JobExecution finalizeExecution = payrollJobLauncherService.launchPayrollFinalizeJob(resumedRun);

        assertThat(finalizeExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        // calculatePayslips is now a partitioned master/worker Step: besides
        // the master itself and exportPayrollSummary, one
        // calculatePayslipsWorker:partitionN StepExecution per non-empty
        // EmployeePartitioner range also shows up here (workers run
        // concurrently, so their relative order among themselves is not
        // guaranteed - only that both non-worker steps ran, in order, and
        // that at least one worker partition executed).
        List<String> nonWorkerStepNames = finalizeExecution.getStepExecutions().stream()
                .map(StepExecution::getStepName)
                .filter(name -> !name.startsWith("calculatePayslipsWorker:partition"))
                .toList();
        assertThat(nonWorkerStepNames).containsExactly("calculatePayslips", "exportPayrollSummary");
        assertThat(finalizeExecution.getStepExecutions())
                .filteredOn(stepExecution -> stepExecution.getStepName().startsWith("calculatePayslipsWorker:partition"))
                .isNotEmpty();
        assertThat(finalizeExecution.getStepExecutions())
                .extracting(StepExecution::getStatus)
                .containsOnly(BatchStatus.COMPLETED);

        Path summaryFile = OUTPUT_DIR.resolve("payroll-summary-" + PERIOD + ".csv");
        assertThat(summaryFile).exists();

        List<Payslip> payslips = payslipsFor(payrollRun);
        assertThat(payslips).hasSize(VALID_EMPLOYEE_EMAILS.size());

        Map<Long, BigDecimal> totalHoursByEmployeeId = timesheetEntryRepository.aggregateHoursByEmployee(payrollRun.getId())
                .stream()
                .collect(Collectors.toMap(EmployeeHoursAggregate::employeeId, EmployeeHoursAggregate::totalHours));

        for (String email : VALID_EMPLOYEE_EMAILS) {
            Employee employee = employeeRepository.findByEmail(email).orElseThrow();
            Payslip payslip = payslips.stream()
                    .filter(candidate -> candidate.getEmployee().getId().equals(employee.getId()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No payslip generated for " + email));

            BigDecimal totalHours = totalHoursByEmployeeId.get(employee.getId());
            assertThat(totalHours).as("aggregated total hours for %s", email).isNotNull();
            assertThat(payslip.getTotalHours()).isEqualByComparingTo(totalHours);

            BigDecimal expectedGrossPay = PayslipItemProcessor.computeGrossPay(
                    totalHours, employee.getHourlyRate(), OVERTIME_THRESHOLD_HOURS, OVERTIME_MULTIPLIER);
            BigDecimal expectedDeductions = PayslipItemProcessor.computeDeductions(expectedGrossPay, DEDUCTION_RATE);
            BigDecimal expectedNetPay = PayslipItemProcessor.computeNetPay(expectedGrossPay, expectedDeductions);

            assertThat(payslip.getGrossPay()).isEqualByComparingTo(expectedGrossPay);
            assertThat(payslip.getDeductions()).isEqualByComparingTo(expectedDeductions);
            assertThat(payslip.getNetPay()).isEqualByComparingTo(expectedNetPay);
        }
    }

    private List<Payslip> payslipsFor(PayrollRun payrollRun) {
        return payslipRepository.findAll().stream()
                .filter(payslip -> payslip.getPayrollRun().getId().equals(payrollRun.getId()))
                .toList();
    }

    private PayrollRun createStartedPayrollRun() {
        PayrollRun payrollRun = new PayrollRun();
        payrollRun.setPeriodMonth(8);
        payrollRun.setPeriodYear(2026);
        payrollRun.setStatus(PayrollRunStatus.STARTED);
        payrollRun.setStartedAt(LocalDateTime.now());
        return payrollRunRepository.save(payrollRun);
    }

    private JobParameters jobParametersFor(PayrollRun payrollRun) {
        return new JobParametersBuilder()
                .addLong("payrollRunId", payrollRun.getId())
                .addString("period", payrollRun.getPeriodYear() + "-" + String.format("%02d", payrollRun.getPeriodMonth()))
                .toJobParameters();
    }
}
