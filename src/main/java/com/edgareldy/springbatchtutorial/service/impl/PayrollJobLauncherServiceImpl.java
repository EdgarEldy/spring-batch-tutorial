package com.edgareldy.springbatchtutorial.service.impl;

import com.edgareldy.springbatchtutorial.entity.PayrollRun;
import com.edgareldy.springbatchtutorial.service.PayrollJobLauncherService;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.launch.JobRestartException;
import org.springframework.stereotype.Service;

/**
 * Default {@link PayrollJobLauncherService} implementation, launching
 * {@code monthlyPayrollJob} through Spring Batch's {@link JobOperator}
 * (the {@code JobLauncher} successor as of Spring Batch 6). Each call
 * builds {@code JobParameters} from the {@link PayrollRun}'s own id
 * (already unique per row) and its period, so no two runs ever collide in
 * the {@code JobRepository}.
 * <p>
 * Created by Edgar Muhamyangabo on 8/17/26
 * Author : Edgar Muhamyangabo
 * Date : 8/17/26
 * Project : spring-batch-tutorial
 */
@Service
public class PayrollJobLauncherServiceImpl implements PayrollJobLauncherService {

    private final JobOperator jobOperator;
    private final Job monthlyPayrollJob;

    public PayrollJobLauncherServiceImpl(JobOperator jobOperator, Job monthlyPayrollJob) {
        this.jobOperator = jobOperator;
        this.monthlyPayrollJob = monthlyPayrollJob;
    }

    @Override
    public JobExecution launchMonthlyPayrollJob(PayrollRun payrollRun) {
        JobParameters jobParameters = new JobParametersBuilder()
                .addLong("payrollRunId", payrollRun.getId())
                .addString("period", toPeriod(payrollRun))
                .toJobParameters();
        try {
            return jobOperator.start(monthlyPayrollJob, jobParameters);
        } catch (JobExecutionAlreadyRunningException | JobRestartException
                 | JobInstanceAlreadyCompleteException | InvalidJobParametersException ex) {
            throw new IllegalStateException(
                    "Unable to launch monthlyPayrollJob for payrollRunId=" + payrollRun.getId(), ex);
        }
    }

    private String toPeriod(PayrollRun payrollRun) {
        return String.format("%04d-%02d", payrollRun.getPeriodYear(), payrollRun.getPeriodMonth());
    }
}
