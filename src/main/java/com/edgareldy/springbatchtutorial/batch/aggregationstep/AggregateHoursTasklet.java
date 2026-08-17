package com.edgareldy.springbatchtutorial.batch.aggregationstep;

import com.edgareldy.springbatchtutorial.dto.csv.EmployeeHoursAggregate;
import com.edgareldy.springbatchtutorial.repository.TimesheetEntryRepository;
import java.math.BigDecimal;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Aggregates the current run's timesheet hours per employee in a single
 * grouped SQL query ({@link TimesheetEntryRepository#aggregateHoursByEmployee}),
 * not item by item - the whole point of this being a {@link Tasklet} rather
 * than a chunk-oriented step (see the README's "Step design: chunk-oriented
 * vs. tasklet" section). Flags any employee whose aggregated hours exceed a
 * configurable threshold (default 300h/month) as an anomaly.
 * <p>
 * Writes only {@code hasAnomalies} (boolean) and the offending employee
 * count into the {@code StepExecution}'s {@link ExecutionContext} - never
 * the aggregated rows themselves, since {@code ExecutionContext} is for
 * small metadata, not bulk data. {@code EmployeeHoursItemReader} recomputes
 * the same aggregation independently via the same repository method rather
 * than reading it back from here.
 * <p>
 * Declared {@code @StepScope} with {@code @Value("#{jobParameters['payrollRunId']}")}
 * because it needs the current run's id at execution time to resolve its
 * period (late binding), the same pattern as {@code TimesheetSkipListener}.
 * <p>
 * Created by Edgar Muhamyangabo on 8/17/26
 * Author : Edgar Muhamyangabo
 * Date : 8/17/26
 * Project : spring-batch-tutorial
 */
@Component
@StepScope
public class AggregateHoursTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(AggregateHoursTasklet.class);

    public static final String HAS_ANOMALIES_KEY = "hasAnomalies";
    public static final String ANOMALY_EMPLOYEE_COUNT_KEY = "anomalyEmployeeCount";

    private final TimesheetEntryRepository timesheetEntryRepository;
    private final Long payrollRunId;
    private final BigDecimal anomalyThresholdHours;

    public AggregateHoursTasklet(
            TimesheetEntryRepository timesheetEntryRepository,
            @Value("#{jobParameters['payrollRunId']}") Long payrollRunId,
            @Value("${payroll.aggregation.anomaly-threshold-hours}") BigDecimal anomalyThresholdHours) {
        this.timesheetEntryRepository = timesheetEntryRepository;
        this.payrollRunId = payrollRunId;
        this.anomalyThresholdHours = anomalyThresholdHours;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        List<EmployeeHoursAggregate> hoursPerEmployee = timesheetEntryRepository.aggregateHoursByEmployee(payrollRunId);

        long anomalyCount = hoursPerEmployee.stream()
                .filter(aggregate -> aggregate.totalHours().compareTo(anomalyThresholdHours) > 0)
                .count();
        boolean hasAnomalies = anomalyCount > 0;

        ExecutionContext executionContext = chunkContext.getStepContext().getStepExecution().getExecutionContext();
        executionContext.put(HAS_ANOMALIES_KEY, hasAnomalies);
        executionContext.putInt(ANOMALY_EMPLOYEE_COUNT_KEY, (int) anomalyCount);

        log.info("aggregateHoursPerEmployee summary: payrollRunId={}, employees={}, anomalies={}, thresholdHours={}",
                payrollRunId, hoursPerEmployee.size(), anomalyCount, anomalyThresholdHours);

        return RepeatStatus.FINISHED;
    }
}
