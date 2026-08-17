package com.edgareldy.springbatchtutorial.batch;

import com.edgareldy.springbatchtutorial.batch.aggregationstep.AggregateHoursTasklet;
import com.edgareldy.springbatchtutorial.batch.calculationstep.CalculationStepExecutionListener;
import com.edgareldy.springbatchtutorial.batch.calculationstep.EmployeeHoursItemReader;
import com.edgareldy.springbatchtutorial.batch.calculationstep.PayslipItemProcessor;
import com.edgareldy.springbatchtutorial.batch.calculationstep.PayslipItemWriter;
import com.edgareldy.springbatchtutorial.batch.importstep.ImportStepExecutionListener;
import com.edgareldy.springbatchtutorial.batch.importstep.TimesheetItemProcessor;
import com.edgareldy.springbatchtutorial.batch.importstep.TimesheetItemWriter;
import com.edgareldy.springbatchtutorial.batch.importstep.TimesheetSkipListener;
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
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Assembles every {@code Step} into {@code monthlyPayrollJob}: the
 * chunk-oriented {@code importTimesheets} (CSV import with skip support),
 * followed by the {@code Tasklet} {@code aggregateHoursPerEmployee} (single
 * grouped hour aggregation with anomaly detection), followed by the
 * chunk-oriented {@code calculatePayslips} (per-employee payslip
 * computation). Later branches (the anomaly-review flow,
 * {@code exportPayrollSummary}) extend the same {@code Job} bean and add
 * {@code payrollFinalizeJob}.
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

    @Bean
    public Job monthlyPayrollJob(
            JobRepository jobRepository,
            Step importTimesheets,
            Step aggregateHoursPerEmployee,
            Step calculatePayslips) {
        return new JobBuilder("monthlyPayrollJob", jobRepository)
                .start(importTimesheets)
                .next(aggregateHoursPerEmployee)
                .next(calculatePayslips)
                .build();
    }
}
