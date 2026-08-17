package com.edgareldy.springbatchtutorial.dto.csv;

/**
 * Raw row read from {@code sample-data/timesheets-import-sample.csv} by
 * {@code TimesheetCsvItemReaderConfig}, before any validation: every field stays
 * plain text so {@code TimesheetItemProcessor} is the single place that
 * parses and validates {@code work_date}/{@code hours_worked} and resolves
 * the employee, rather than splitting validation between reader and
 * processor.
 * <p>
 * Created by Edgar Muhamyangabo on 8/17/26
 * Author : Edgar Muhamyangabo
 * Date : 8/17/26
 * Project : spring-batch-tutorial
 */
public record TimesheetCsvRow(
        String email,
        String workDate,
        String hoursWorked
) {
}
