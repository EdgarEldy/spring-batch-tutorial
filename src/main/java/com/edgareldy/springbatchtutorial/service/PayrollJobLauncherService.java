package com.edgareldy.springbatchtutorial.service;

import com.edgareldy.springbatchtutorial.entity.PayrollRun;
import org.springframework.batch.core.job.JobExecution;

/**
 * Contract for launching {@code monthlyPayrollJob} for a given
 * {@link PayrollRun}. Implemented by {@code PayrollJobLauncherServiceImpl}.
 * <p>
 * Created by Edgar Muhamyangabo on 8/17/26
 * Author : Edgar Muhamyangabo
 * Date : 8/17/26
 * Project : spring-batch-tutorial
 */
public interface PayrollJobLauncherService {

    /**
     * Launches {@code monthlyPayrollJob} with {@code payrollRunId} and
     * {@code period} as identifying {@code JobParameters}, so every run gets
     * its own, non-colliding {@code JobInstance}.
     */
    JobExecution launchMonthlyPayrollJob(PayrollRun payrollRun);
}
