package com.edgareldy.springbatchtutorial.batch.importstep;

import com.edgareldy.springbatchtutorial.dto.csv.TimesheetCsvRow;
import com.edgareldy.springbatchtutorial.entity.Employee;
import com.edgareldy.springbatchtutorial.entity.TimesheetEntry;
import com.edgareldy.springbatchtutorial.exception.InvalidTimesheetRowException;
import com.edgareldy.springbatchtutorial.repository.EmployeeRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Validates and transforms a raw {@link TimesheetCsvRow} into a persistable
 * {@link TimesheetEntry}: {@code hours_worked} must be a number in
 * {@code (0, 24]}, {@code work_date} must be a valid ISO date, and the
 * row's email must resolve to a known {@link Employee} via
 * {@link EmployeeRepository}. Any failure throws
 * {@link InvalidTimesheetRowException}, which the {@code importTimesheets}
 * step is configured to skip rather than fail on.
 * <p>
 * Created by Edgar Muhamyangabo on 8/17/26
 * Author : Edgar Muhamyangabo
 * Date : 8/17/26
 * Project : spring-batch-tutorial
 */
@Component
public class TimesheetItemProcessor implements ItemProcessor<TimesheetCsvRow, TimesheetEntry> {

    private static final BigDecimal MIN_HOURS_EXCLUSIVE = BigDecimal.ZERO;
    private static final BigDecimal MAX_HOURS_INCLUSIVE = new BigDecimal("24");

    private final EmployeeRepository employeeRepository;
    private final String sourceFileName;

    public TimesheetItemProcessor(
            EmployeeRepository employeeRepository,
            @Value("${payroll.import.csv-path}") Resource csvResource) {
        this.employeeRepository = employeeRepository;
        this.sourceFileName = csvResource.getFilename();
    }

    @Override
    public TimesheetEntry process(TimesheetCsvRow item) {
        BigDecimal hoursWorked = parseHours(item.hoursWorked());
        LocalDate workDate = parseWorkDate(item.workDate());
        Employee employee = employeeRepository.findByEmail(item.email())
                .orElseThrow(() -> new InvalidTimesheetRowException(
                        "Unknown employee email: " + item.email()));

        TimesheetEntry entry = new TimesheetEntry();
        entry.setEmployee(employee);
        entry.setWorkDate(workDate);
        entry.setHoursWorked(hoursWorked);
        entry.setSourceFile(sourceFileName);
        entry.setImportedAt(LocalDateTime.now());
        return entry;
    }

    private BigDecimal parseHours(String rawHours) {
        BigDecimal hours;
        try {
            hours = new BigDecimal(rawHours.trim());
        } catch (NumberFormatException | NullPointerException ex) {
            throw new InvalidTimesheetRowException("hours_worked is not a valid number: " + rawHours, ex);
        }
        if (hours.compareTo(MIN_HOURS_EXCLUSIVE) <= 0 || hours.compareTo(MAX_HOURS_INCLUSIVE) > 0) {
            throw new InvalidTimesheetRowException(
                    "hours_worked must be > 0 and <= 24, was: " + hours);
        }
        return hours;
    }

    private LocalDate parseWorkDate(String rawWorkDate) {
        try {
            return LocalDate.parse(rawWorkDate.trim());
        } catch (DateTimeParseException | NullPointerException ex) {
            throw new InvalidTimesheetRowException("work_date is not a valid ISO date: " + rawWorkDate, ex);
        }
    }
}
