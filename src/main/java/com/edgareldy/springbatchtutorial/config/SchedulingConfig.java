package com.edgareldy.springbatchtutorial.config;

import com.edgareldy.springbatchtutorial.entity.PayrollRun;
import com.edgareldy.springbatchtutorial.service.PayrollJobLauncherService;
import com.edgareldy.springbatchtutorial.service.PayrollRunService;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Triggers {@code monthlyPayrollJob} automatically once a month, on the
 * cron schedule configured by {@code payroll.scheduling.cron} (defaults to
 * {@code 0 0 3 1 * *}: the first day of the month at 03:00), for the period
 * that just ended - the month before the one the trigger fires in. Reuses
 * {@code PayrollRunService.createRun}/{@code PayrollJobLauncherService.launchMonthlyPayrollJob}
 * as-is, the same run-creation and job-launch path
 * {@code POST /api/v1/payroll/runs} uses, so a scheduled run is
 * indistinguishable in the database from a manually triggered one.
 * <p>
 * Created by Edgar Muhamyangabo on 8/17/26
 * Author : Edgar Muhamyangabo
 * Date : 8/17/26
 * Project : spring-batch-tutorial
 */
@Component
@EnableScheduling
public class SchedulingConfig {

    private static final Logger log = LoggerFactory.getLogger(SchedulingConfig.class);

    private final PayrollRunService payrollRunService;
    private final PayrollJobLauncherService payrollJobLauncherService;

    public SchedulingConfig(
            PayrollRunService payrollRunService,
            PayrollJobLauncherService payrollJobLauncherService) {
        this.payrollRunService = payrollRunService;
        this.payrollJobLauncherService = payrollJobLauncherService;
    }

    @Scheduled(cron = "${payroll.scheduling.cron:0 0 3 1 * *}")
    public void launchScheduledMonthlyPayrollRun() {
        LocalDate previousPeriod = LocalDate.now().minusMonths(1);
        int periodMonth = previousPeriod.getMonthValue();
        int periodYear = previousPeriod.getYear();

        log.info("Scheduled trigger: creating PayrollRun for period {}-{} and launching monthlyPayrollJob",
                periodYear, periodMonth);

        PayrollRun payrollRun = payrollRunService.createRun(periodMonth, periodYear);
        payrollJobLauncherService.launchMonthlyPayrollJob(payrollRun);
    }
}
