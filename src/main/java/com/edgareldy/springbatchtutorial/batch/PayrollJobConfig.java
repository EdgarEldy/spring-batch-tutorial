package com.edgareldy.springbatchtutorial.batch;

import com.edgareldy.springbatchtutorial.batch.aggregationstep.AggregateHoursTasklet;
import com.edgareldy.springbatchtutorial.batch.calculationstep.CalculationStepExecutionListener;
import com.edgareldy.springbatchtutorial.batch.calculationstep.EmployeeHoursItemReader;
import com.edgareldy.springbatchtutorial.batch.calculationstep.PayslipItemProcessor;
import com.edgareldy.springbatchtutorial.batch.calculationstep.PayslipItemWriter;
import com.edgareldy.springbatchtutorial.batch.decision.AnomalyReviewDecider;
import com.edgareldy.springbatchtutorial.batch.importstep.ImportStepExecutionListener;
import com.edgareldy.springbatchtutorial.batch.importstep.TimesheetItemProcessor;
import com.edgareldy.springbatchtutorial.batch.importstep.TimesheetItemWriter;
import com.edgareldy.springbatchtutorial.batch.importstep.TimesheetSkipListener;
import com.edgareldy.springbatchtutorial.batch.reviewstep.FlagForReviewTasklet;
import com.edgareldy.springbatchtutorial.dto.csv.EmployeeHoursAggregate;
import com.edgareldy.springbatchtutorial.dto.csv.TimesheetCsvRow;
import com.edgareldy.springbatchtutorial.entity.Payslip;
import com.edgareldy.springbatchtutorial.entity.TimesheetEntry;
import com.edgareldy.springbatchtutorial.exception.InvalidTimesheetRowException;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Assembles every {@code Step} into {@code monthlyPayrollJob}: the
 * chunk-oriented {@code importTimesheets} (CSV import with skip support),
 * followed by the {@code Tasklet} {@code aggregateHoursPerEmployee} (single
 * grouped hour aggregation with anomaly detection), followed by
 * {@code anomalyReviewDecider} (a {@code JobExecutionDecider} routing to
 * either the {@code flagForReview} {@code Tasklet} step or straight on to
 * the chunk-oriented {@code calculatePayslips}). Also assembles
 * {@code payrollFinalizeJob}, a second, distinctly named {@code Job} that
 * reuses the same {@code calculatePayslips} {@code Step} bean to resume a
 * run once a human has cleared its anomaly out-of-band. A later branch adds
 * {@code exportPayrollSummary} to both.
 * <p>
 * Created by Edgar Muhamyangabo on 8/17/26
 * Author : Edgar Muhamyangabo
 * Date : 8/17/26
 * Project : spring-batch-tutorial
 */
@Configuration
public class PayrollJobConfig {

    @Value("${payroll.import.chunk-size}")
    private int importChunkSize;

    @Value("${payroll.import.skip-limit}")
    private int importSkipLimit;

    @Value("${payroll.calculation.chunk-size}")
    private int calculationChunkSize;

    @Bean
    public Step importTimesheets(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            FlatFileItemReader<TimesheetCsvRow> timesheetCsvItemReader,
            TimesheetItemProcessor timesheetItemProcessor,
            TimesheetItemWriter timesheetItemWriter,
            TimesheetSkipListener timesheetSkipListener,
            ImportStepExecutionListener importStepExecutionListener) {
        return new StepBuilder("importTimesheets", jobRepository)
                .<TimesheetCsvRow, TimesheetEntry>chunk(importChunkSize)
                .reader(timesheetCsvItemReader)
                .processor(timesheetItemProcessor)
                .writer(timesheetItemWriter)
                .transactionManager(transactionManager)
                .faultTolerant()
                .skipLimit(importSkipLimit)
                .skip(InvalidTimesheetRowException.class)
                .skipListener(timesheetSkipListener)
                .listener(importStepExecutionListener)
                .build();
    }

    @Bean
    public Step aggregateHoursPerEmployee(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            AggregateHoursTasklet aggregateHoursTasklet) {
        return new StepBuilder("aggregateHoursPerEmployee", jobRepository)
                .tasklet(aggregateHoursTasklet, transactionManager)
                .build();
    }

    @Bean
    public Step flagForReview(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            FlagForReviewTasklet flagForReviewTasklet) {
        return new StepBuilder("flagForReview", jobRepository)
                .tasklet(flagForReviewTasklet, transactionManager)
                .build();
    }

    @Bean
    public Step calculatePayslips(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            EmployeeHoursItemReader employeeHoursItemReader,
            PayslipItemProcessor payslipItemProcessor,
            PayslipItemWriter payslipItemWriter,
            CalculationStepExecutionListener calculationStepExecutionListener) {
        return new StepBuilder("calculatePayslips", jobRepository)
                .<EmployeeHoursAggregate, Payslip>chunk(calculationChunkSize)
                .reader(employeeHoursItemReader)
                .processor(payslipItemProcessor)
                .writer(payslipItemWriter)
                .transactionManager(transactionManager)
                .listener(calculationStepExecutionListener)
                .build();
    }

    /**
     * {@code @Primary} because two {@code Job} beans now exist in this
     * context: anything that resolves a {@code Job} by type alone via an
     * {@code ObjectProvider} ({@code JobLauncherTestUtils}/
     * {@code JobOperatorTestUtils} among them, via {@code ifUnique()}) would
     * otherwise stay unwired once resolution is no longer unambiguous.
     * {@code monthlyPayrollJob} is the default entry point of the two
     * ({@code payrollFinalizeJob} only ever gets launched explicitly by name
     * from {@code PayrollJobLauncherServiceImpl}), so marking it
     * {@code @Primary} keeps that kind of by-type resolution working without
     * forcing every caller to look the {@code Job} bean up by name.
     */
    @Bean
    @Primary
    public Job monthlyPayrollJob(
            JobRepository jobRepository,
            Step importTimesheets,
            Step aggregateHoursPerEmployee,
            AnomalyReviewDecider anomalyReviewDecider,
            Step flagForReview,
            Step calculatePayslips) {
        return new JobBuilder("monthlyPayrollJob", jobRepository)
                .start(importTimesheets)
                .next(aggregateHoursPerEmployee)
                .next(anomalyReviewDecider)
                    .on(AnomalyReviewDecider.REVIEW_REQUIRED.getName()).to(flagForReview)
                .from(anomalyReviewDecider)
                    .on(AnomalyReviewDecider.PROCEED.getName()).to(calculatePayslips)
                .end()
                .build();
    }

    /**
     * Resumes a {@code PayrollRun} that a human has cleared out of
     * {@code AWAITING_REVIEW}: reuses the exact same {@code calculatePayslips}
     * {@code Step} bean {@code monthlyPayrollJob} uses (not a redefinition),
     * so the calculation logic is defined once. A distinct {@code Job} name
     * means a resume launch never collides with the original run's
     * {@code monthlyPayrollJob} instance in the {@code JobRepository}, even
     * when both share the same {@code payrollRunId}/{@code period}
     * {@code JobParameters} values.
     */
    @Bean
    public Job payrollFinalizeJob(
            JobRepository jobRepository,
            Step calculatePayslips) {
        return new JobBuilder("payrollFinalizeJob", jobRepository)
                .start(calculatePayslips)
                .build();
    }
}
