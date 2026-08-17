package com.edgareldy.springbatchtutorial.batch.importstep;

import com.edgareldy.springbatchtutorial.dto.csv.TimesheetCsvRow;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

/**
 * Builds the {@code FlatFileItemReader<TimesheetCsvRow>} bean that reads
 * {@code sample-data/timesheets-import-sample.csv} (header
 * {@code email,work_date,hours_worked}) line by line for the
 * {@code importTimesheets} step, mapping each line to a raw,
 * not-yet-validated {@link TimesheetCsvRow}.
 * <p>
 * Declared {@code @StepScope} not because it depends on {@code JobParameters}
 * (it doesn't - the CSV path is a fixed application property) but because a
 * {@code FlatFileItemReader} is inherently stateful (open file handle,
 * current line position): a singleton instance shared across job executions
 * would not be safe to reuse, so a fresh reader per step execution is
 * required regardless of late-binding. Named
 * {@code TimesheetCsvItemReaderConfig} rather than {@code TimesheetCsvItemReader}
 * (matching the {@code BatchConfig}/{@code OpenApiConfig}/{@code PayrollJobConfig}
 * naming convention for {@code @Configuration} classes): naming it after the
 * bean it declares used to collide with the {@code @Bean} method's own
 * default name (both resolved to {@code timesheetCsvItemReader}), which
 * Spring rejects as a duplicate bean definition at context startup.
 * <p>
 * Created by Edgar Muhamyangabo on 8/17/26
 * Author : Edgar Muhamyangabo
 * Date : 8/17/26
 * Project : spring-batch-tutorial
 */
@Configuration
public class TimesheetCsvItemReaderConfig {

    @Bean
    @StepScope
    public FlatFileItemReader<TimesheetCsvRow> timesheetCsvItemReader(
            @Value("${payroll.import.csv-path}") Resource csvResource) {
        return new FlatFileItemReaderBuilder<TimesheetCsvRow>()
                .name("timesheetCsvItemReader")
                .resource(csvResource)
                .linesToSkip(1)
                .delimited()
                .names("email", "work_date", "hours_worked")
                .fieldSetMapper(fieldSet -> new TimesheetCsvRow(
                        fieldSet.readString("email"),
                        fieldSet.readString("work_date"),
                        fieldSet.readString("hours_worked")))
                .strict(true)
                .build();
    }
}
