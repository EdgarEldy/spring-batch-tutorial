package com.edgareldy.springbatchtutorial.repository;

import com.edgareldy.springbatchtutorial.dto.csv.EmployeeHoursAggregate;
import com.edgareldy.springbatchtutorial.entity.TimesheetEntry;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JPA repository for {@link TimesheetEntry}, used by
 * {@code TimesheetItemWriter} to persist validated rows during
 * {@code importTimesheets}, and by {@code AggregateHoursTasklet}/
 * {@code EmployeeHoursItemReader} to compute per-employee hour totals via
 * {@link #aggregateHoursByEmployee(Long)}.
 * <p>
 * {@code timesheet_entries} deliberately has no {@code payroll_run_id}
 * column (see {@link TimesheetEntry}'s Javadoc): a run is not identified by
 * tagging entries at import time, it is identified by its period
 * ({@code period_year}/{@code period_month} on {@code payroll_runs}). So
 * "the timesheet entries for this run" means "the entries whose
 * {@code work_date} falls within this run's period", not "the entries
 * imported since some payroll_run_id was created". This query joins
 * {@code PayrollRun} purely to resolve that period from {@code payrollRunId},
 * then filters {@code TimesheetEntry} by {@code extract(year/month from
 * work_date)} - this is the one place the payroll_run-to-timesheet_entries
 * link is resolved, reused as-is by both {@code AggregateHoursTasklet}
 * (single grouped call) and {@code EmployeeHoursItemReader} (same call,
 * read back one item at a time), so the aggregation logic itself is defined
 * exactly once.
 * <p>
 * Created by Edgar Muhamyangabo on 8/17/26
 * Author : Edgar Muhamyangabo
 * Date : 8/17/26
 * Project : spring-batch-tutorial
 */
public interface TimesheetEntryRepository extends JpaRepository<TimesheetEntry, Long> {

    @Query("""
            select new com.edgareldy.springbatchtutorial.dto.csv.EmployeeHoursAggregate(
                te.employee.id,
                te.employee.hourlyRate,
                sum(te.hoursWorked))
            from PayrollRun pr, TimesheetEntry te
            where pr.id = :payrollRunId
              and extract(year from te.workDate) = pr.periodYear
              and extract(month from te.workDate) = pr.periodMonth
            group by te.employee.id, te.employee.hourlyRate
            order by te.employee.id
            """)
    List<EmployeeHoursAggregate> aggregateHoursByEmployee(@Param("payrollRunId") Long payrollRunId);
}
