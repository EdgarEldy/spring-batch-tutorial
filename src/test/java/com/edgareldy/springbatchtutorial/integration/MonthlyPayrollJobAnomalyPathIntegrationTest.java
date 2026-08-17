package com.edgareldy.springbatchtutorial.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.edgareldy.springbatchtutorial.e2e.PostgresTestcontainerConfiguration;
import com.edgareldy.springbatchtutorial.entity.Employee;
import com.edgareldy.springbatchtutorial.entity.PayrollRun;
import com.edgareldy.springbatchtutorial.entity.PayrollRunStatus;
import com.edgareldy.springbatchtutorial.entity.TimesheetEntry;
import com.edgareldy.springbatchtutorial.repository.EmployeeRepository;
import com.edgareldy.springbatchtutorial.repository.PayrollRunRepository;
import com.edgareldy.springbatchtutorial.repository.PayslipRepository;
import com.edgareldy.springbatchtutorial.repository.TimesheetEntryRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

/**
 * Runs the complete {@code monthlyPayrollJob} (not a single isolated step,
 * since it is the routing behaviour of the whole flow being verified here)
 * via {@code JobLauncherTestUtils.launchJob} against a real PostgreSQL
 * instance (Testcontainers), seeding one employee (David Chen, 312h) above
 * the default 300h/month anomaly threshold alongside four employees below
 * it. Confirms the job actually stops the {@code calculatePayslips} branch:
 * {@code flagForReview} runs instead, {@code PayrollRun.status} ends up
 * {@code AWAITING_REVIEW}, and no {@link com.edgareldy.springbatchtutorial.entity.Payslip}
 * row exists yet for this run - {@code calculatePayslips} only ever runs
 * later, from {@code payrollFinalizeJob}, once a human resumes the run.
 * <p>
 * Seeds real {@link TimesheetEntry} rows directly under a dedicated period
 * (2031-05, distinct from every other test in this suite - see
 * {@code AggregateHoursPerEmployeeStepIntegrationTest}'s Javadoc for why a
 * dedicated period matters against this project's single shared
 * Testcontainer) rather than replaying {@code importTimesheets} against the
 * shared sample CSV, since only the post-aggregation routing behaviour is
 * under test here.
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
class MonthlyPayrollJobAnomalyPathIntegrationTest {

    private static final int PERIOD_YEAR = 2031;
    private static final int PERIOD_MONTH = 5;

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

    @Test
    void runSeededWithAnAnomalousEmployeeStopsAtAwaitingReviewWithNoPayslipsWritten() throws Exception {
        PayrollRun payrollRun = createStartedPayrollRun();
        seedTimesheetData(payrollRun);

        JobExecution execution = jobLauncherTestUtils.launchJob(jobParametersFor(payrollRun));

        // The technical JobExecution still completes: routing to review is
        // the job doing what it is supposed to do, not a failure (see the
        // README's "Why these library choices" section). It is the
        // business-level PayrollRun.status, not JobExecution.status, that
        // carries the anomaly outcome.
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        assertThat(execution.getStepExecutions())
                .extracting(StepExecution::getStepName)
                .containsExactlyInAnyOrder("importTimesheets", "aggregateHoursPerEmployee", "flagForReview");
        assertThat(execution.getStepExecutions())
                .extracting(StepExecution::getStatus)
                .containsOnly(BatchStatus.COMPLETED);

        PayrollRun reloaded = payrollRunRepository.findById(payrollRun.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(PayrollRunStatus.AWAITING_REVIEW);

        long payslipsForThisRun = payslipRepository.findAll().stream()
                .filter(payslip -> payslip.getPayrollRun().getId().equals(payrollRun.getId()))
                .count();
        assertThat(payslipsForThisRun).isZero();
    }

    /**
     * Same real per-employee hour distribution as the sample CSV: 5 x 8h for
     * Alice, 5 x 7.5h for Bob, 4 x 8h for Carla, 3 x 6h for Emma (all below
     * the 300h/month anomaly threshold), 13 x 24h for David Chen (312h,
     * above it).
     */
    private void seedTimesheetData(PayrollRun payrollRun) {
        LocalDate base = LocalDate.of(payrollRun.getPeriodYear(), payrollRun.getPeriodMonth(), 1);
        saveDailyEntries("alice.martin@example.com", base, new BigDecimal("8.00"), 5);
        saveDailyEntries("bob.dupont@example.com", base, new BigDecimal("7.50"), 5);
        saveDailyEntries("carla.silva@example.com", base, new BigDecimal("8.00"), 4);
        saveDailyEntries("emma.rossi@example.com", base, new BigDecimal("6.00"), 3);
        saveDailyEntries("david.chen@example.com", base, new BigDecimal("24.00"), 13);
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
                .addString("period", payrollRun.getPeriodYear() + "-" + String.format("%02d", payrollRun.getPeriodMonth()))
                .toJobParameters();
    }
}
