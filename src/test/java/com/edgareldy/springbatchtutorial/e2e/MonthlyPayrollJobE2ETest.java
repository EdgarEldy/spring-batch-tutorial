package com.edgareldy.springbatchtutorial.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.edgareldy.springbatchtutorial.entity.PayrollRun;
import com.edgareldy.springbatchtutorial.entity.PayrollRunStatus;
import com.edgareldy.springbatchtutorial.repository.PayrollRunRepository;
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
 * (Testcontainers). This branch wires only {@code importTimesheets} into the
 * job, so the expectation here is narrow: the job completes cleanly right
 * after that single step, rather than failing or hanging for the steps
 * later branches (aggregate/calculate/export) will add.
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

    @Test
    void launchingTheJobStopsCleanlyAfterImportTimesheets() throws Exception {
        PayrollRun payrollRun = createStartedPayrollRun();
        JobParameters jobParameters = new JobParametersBuilder()
                .addLong("payrollRunId", payrollRun.getId())
                .addString("period", payrollRun.getPeriodYear() + "-" + String.format("%02d", payrollRun.getPeriodMonth()))
                .toJobParameters();

        JobExecution execution = jobLauncherTestUtils.launchJob(jobParameters);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(execution.getExitStatus().getExitCode()).isEqualTo("COMPLETED");

        // Only importTimesheets is wired into monthlyPayrollJob at this stage:
        // the job must stop right there, not fail or attempt to run steps
        // later branches (aggregate/calculate/export) will add.
        assertThat(execution.getStepExecutions()).hasSize(1);
        StepExecution stepExecution = execution.getStepExecutions().iterator().next();
        assertThat(stepExecution.getStepName()).isEqualTo("importTimesheets");
        assertThat(stepExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    }

    private PayrollRun createStartedPayrollRun() {
        PayrollRun payrollRun = new PayrollRun();
        payrollRun.setPeriodMonth(8);
        payrollRun.setPeriodYear(2026);
        payrollRun.setStatus(PayrollRunStatus.STARTED);
        payrollRun.setStartedAt(LocalDateTime.now());
        return payrollRunRepository.save(payrollRun);
    }
}
