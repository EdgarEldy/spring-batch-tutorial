package com.edgareldy.springbatchtutorial.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.edgareldy.springbatchtutorial.entity.PayrollRun;
import com.edgareldy.springbatchtutorial.entity.PayrollRunStatus;
import com.edgareldy.springbatchtutorial.entity.Payslip;
import com.edgareldy.springbatchtutorial.repository.PayrollRunRepository;
import com.edgareldy.springbatchtutorial.repository.PayslipRepository;
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
 * Runs {@code monthlyPayrollJob} end to end via
 * {@code JobLauncherTestUtils.launchJob} against a real PostgreSQL instance
 * (Testcontainers) and the real sample CSV. Before
 * {@code feature/conditional-flow}, this class asserted that all three
 * chained steps completed and produced a {@link Payslip} for every valid
 * employee - that assumption no longer holds now that
 * {@code anomalyReviewDecider} actually routes the job: the sample CSV's own
 * David Chen genuinely logs 312h, above the default 300h/month anomaly
 * threshold, so running the real CSV through this job is now correctly
 * expected to stop at {@code flagForReview} with
 * {@code PayrollRun.status = AWAITING_REVIEW}, not to reach
 * {@code calculatePayslips}. This is the routing decision being verified,
 * not a regression - see {@code AnomalyReviewDeciderTest} (unit) and
 * {@code MonthlyPayrollJobAnomalyPathIntegrationTest} (integration) for the
 * same decision covered against synthetic data. The rest of this exact
 * scenario - a human clearing the review and {@code payrollFinalizeJob}
 * producing the missing payslips - is covered separately by
 * {@link MonthlyPayrollJobAnomalyResumeE2ETest}, which is where the
 * "complete happy path with payslips" assertion this class used to make now
 * lives, one step further down the same flow.
 * <p>
 * This project's PostgreSQL Testcontainer is a single Spring context-cached
 * bean reused across every {@code @SpringBootTest} class in the same Maven
 * Surefire fork, and {@code timesheet_entries} carries no
 * {@code payroll_run_id} (aggregation is scoped by period, see
 * {@code TimesheetEntryRepository}'s Javadoc): other tests in this suite
 * also import the same sample CSV into the same real 2026-08 period, but
 * that only ever pushes David Chen's total further above the threshold, so
 * this assertion never depends on execution order.
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
class MonthlyPayrollJobE2ETest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private PayrollRunRepository payrollRunRepository;

    @Autowired
    private PayslipRepository payslipRepository;

    @Test
    void runningTheRealSampleCsvStopsAtAwaitingReviewBecauseDavidChenExceedsTheAnomalyThreshold() throws Exception {
        PayrollRun payrollRun = createStartedPayrollRun();
        JobParameters jobParameters = jobParametersFor(payrollRun);

        JobExecution execution = jobLauncherTestUtils.launchJob(jobParameters);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(execution.getExitStatus().getExitCode()).isEqualTo("COMPLETED");

        assertThat(execution.getStepExecutions()).hasSize(3);
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
