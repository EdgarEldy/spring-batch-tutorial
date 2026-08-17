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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Default {@link PayrollJobLauncherService} implementation, launching
 * {@code monthlyPayrollJob} and {@code payrollFinalizeJob} through Spring
 * Batch's {@link JobOperator} (the {@code JobLauncher} successor as of
 * Spring Batch 6). Each call builds {@code JobParameters} from the
 * {@link PayrollRun}'s own id (already unique per row) and its period; the
 * two {@code Job} beans are distinctly named, so a {@code payrollRunId}/
 * {@code period} pair used to launch both never collides in the
 * {@code JobRepository} even though the parameter values are identical.
 * <p>
 * Both {@code Job} constructor parameters are {@code @Qualifier}-annotated
 * by bean name: {@code monthlyPayrollJob} is {@code @Primary} (see
 * {@code PayrollJobConfig}, needed so Spring Batch's own
 * {@code ObjectProvider<Job>.ifUnique()}-based test utilities keep resolving
 * it unambiguously), and Spring's autowiring resolves a {@code @Primary}
 * candidate before falling back to parameter-name matching - so without an
 * explicit qualifier here, the {@code payrollFinalizeJob} parameter would
 * silently receive the {@code monthlyPayrollJob} bean instead.
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
    private final Job payrollFinalizeJob;

    public PayrollJobLauncherServiceImpl(
            JobOperator jobOperator,
            @Qualifier("monthlyPayrollJob") Job monthlyPayrollJob,
            @Qualifier("payrollFinalizeJob") Job payrollFinalizeJob) {
        this.jobOperator = jobOperator;
        this.monthlyPayrollJob = monthlyPayrollJob;
        this.payrollFinalizeJob = payrollFinalizeJob;
    }

    @Override
    public JobExecution launchMonthlyPayrollJob(PayrollRun payrollRun) {
        try {
            return jobOperator.start(monthlyPayrollJob, toJobParameters(payrollRun));
        } catch (JobExecutionAlreadyRunningException | JobRestartException
                 | JobInstanceAlreadyCompleteException | InvalidJobParametersException ex) {
            throw new IllegalStateException(
                    "Unable to launch monthlyPayrollJob for payrollRunId=" + payrollRun.getId(), ex);
        }
    }

    @Override
    public JobExecution launchPayrollFinalizeJob(PayrollRun payrollRun) {
        try {
            return jobOperator.start(payrollFinalizeJob, toJobParameters(payrollRun));
        } catch (JobExecutionAlreadyRunningException | JobRestartException
                 | JobInstanceAlreadyCompleteException | InvalidJobParametersException ex) {
            throw new IllegalStateException(
                    "Unable to launch payrollFinalizeJob for payrollRunId=" + payrollRun.getId(), ex);
        }
    }

    private JobParameters toJobParameters(PayrollRun payrollRun) {
        return new JobParametersBuilder()
                .addLong("payrollRunId", payrollRun.getId())
                .addString("period", toPeriod(payrollRun))
                .toJobParameters();
    }

    private String toPeriod(PayrollRun payrollRun) {
        return String.format("%04d-%02d", payrollRun.getPeriodYear(), payrollRun.getPeriodMonth());
    }
}
