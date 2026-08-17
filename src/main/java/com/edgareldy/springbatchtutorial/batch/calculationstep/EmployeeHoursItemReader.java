package com.edgareldy.springbatchtutorial.batch.calculationstep;

import com.edgareldy.springbatchtutorial.dto.csv.EmployeeHoursAggregate;
import com.edgareldy.springbatchtutorial.repository.TimesheetEntryRepository;
import java.util.List;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.batch.infrastructure.item.support.ListItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Re-runs {@code AggregateHoursTasklet}'s exact aggregation
 * ({@link TimesheetEntryRepository#aggregateHoursByEmployee}) for
 * {@code calculatePayslips}, rather than reading the aggregated rows back
 * from the {@code ExecutionContext} (which never holds bulk data - see
 * {@code AggregateHoursTasklet}). Both steps call the same repository
 * method, so the aggregation logic itself is defined once.
 * <p>
 * This deliberately isn't a {@code JpaPagingItemReader} re-issuing a
 * {@code LIMIT}/{@code OFFSET} query per page: the grouped result set is
 * already bounded by the employee count (not by the number of raw
 * {@code timesheet_entries} rows), so it is fetched once, in the same
 * single grouped SQL query the tasklet uses, and served one item per
 * {@code read()} call from an in-memory {@link ListItemReader} - the chunk
 * boundaries of {@code calculatePayslips} still apply on top of that, so
 * items are still processed/written in configurable-size chunks.
 * <p>
 * Declared {@code @StepScope} with {@code @Value("#{jobParameters['payrollRunId']}")}
 * because it needs the current run's id at execution time (late binding),
 * the same pattern as {@code AggregateHoursTasklet} and
 * {@code TimesheetSkipListener}.
 * <p>
 * Created by Edgar Muhamyangabo on 8/17/26
 * Author : Edgar Muhamyangabo
 * Date : 8/17/26
 * Project : spring-batch-tutorial
 */
@Component
@StepScope
public class EmployeeHoursItemReader implements ItemReader<EmployeeHoursAggregate> {

    private final ItemReader<EmployeeHoursAggregate> delegate;

    public EmployeeHoursItemReader(
            TimesheetEntryRepository timesheetEntryRepository,
            @Value("#{jobParameters['payrollRunId']}") Long payrollRunId) {
        List<EmployeeHoursAggregate> hoursPerEmployee = timesheetEntryRepository.aggregateHoursByEmployee(payrollRunId);
        this.delegate = new ListItemReader<>(hoursPerEmployee);
    }

    @Override
    public EmployeeHoursAggregate read() throws Exception {
        return delegate.read();
    }
}
