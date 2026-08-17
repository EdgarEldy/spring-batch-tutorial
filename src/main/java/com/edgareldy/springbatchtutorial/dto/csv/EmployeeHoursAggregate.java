package com.edgareldy.springbatchtutorial.dto.csv;

import java.math.BigDecimal;

/**
 * One employee's total clocked hours for a {@code PayrollRun}'s period,
 * produced by {@code TimesheetEntryRepository.aggregateHoursByEmployee}
 * (one grouped {@code SUM(hours_worked) GROUP BY employee_id} query) and
 * consumed both by {@code AggregateHoursTasklet} (anomaly detection) and by
 * {@code EmployeeHoursItemReader}/{@code PayslipItemProcessor} (payslip
 * computation). Carries {@code hourlyRate} alongside the aggregated hours so
 * {@code PayslipItemProcessor} can compute {@code gross_pay} without a
 * second round trip to {@code EmployeeRepository}.
 * <p>
 * Created by Edgar Muhamyangabo on 8/17/26
 * Author : Edgar Muhamyangabo
 * Date : 8/17/26
 * Project : spring-batch-tutorial
 */
public record EmployeeHoursAggregate(
        Long employeeId,
        BigDecimal hourlyRate,
        BigDecimal totalHours
) {
}
