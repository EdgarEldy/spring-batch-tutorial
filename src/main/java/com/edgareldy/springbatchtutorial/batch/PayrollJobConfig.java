package com.edgareldy.springbatchtutorial.batch;

import com.edgareldy.springbatchtutorial.batch.importstep.ImportStepExecutionListener;
import com.edgareldy.springbatchtutorial.batch.importstep.TimesheetItemProcessor;
import com.edgareldy.springbatchtutorial.batch.importstep.TimesheetItemWriter;
import com.edgareldy.springbatchtutorial.batch.importstep.TimesheetSkipListener;
import com.edgareldy.springbatchtutorial.dto.csv.TimesheetCsvRow;
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
 * Assembles every {@code Step} into {@code monthlyPayrollJob}. This branch
 * wires only the first step, {@code importTimesheets} (chunk-oriented CSV
 * import with skip support); later branches ({@code aggregateHoursPerEmployee},
 * {@code calculatePayslips}, the anomaly-review flow, {@code exportPayrollSummary})
 * extend the same {@code Job} bean and add {@code payrollFinalizeJob}.
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
    public Job monthlyPayrollJob(JobRepository jobRepository, Step importTimesheets) {
        return new JobBuilder("monthlyPayrollJob", jobRepository)
                .start(importTimesheets)
                .build();
    }
}
