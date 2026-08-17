package com.edgareldy.springbatchtutorial.batch.decision;

import com.edgareldy.springbatchtutorial.batch.aggregationstep.AggregateHoursTasklet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.flow.FlowExecutionStatus;
import org.springframework.batch.core.job.flow.JobExecutionDecider;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.stereotype.Component;

/**
 * Routes {@code monthlyPayrollJob} between the anomaly-review branch and the
 * normal calculation branch, reading the {@code hasAnomalies} flag
 * {@link AggregateHoursTasklet} writes into {@code aggregateHoursPerEmployee}'s
 * {@code StepExecution} {@code ExecutionContext} rather than failing the
 * step - an anomalous employee is a business exception, not a technical one
 * (see the README's "Why these library choices" section).
 * <p>
 * The modern {@link JobExecutionDecider#decide(JobExecution, StepExecution)}
 * signature is passed the {@code StepExecution} of the most recently
 * executed step in the flow directly as its second argument: confirmed by
 * inspecting {@code spring-batch-core 6.0.4}'s {@code DecisionState}/
 * {@code JobFlowExecutor} bytecode, where {@code executeStep} stores the
 * {@code StepExecution} returned by the step handler in a {@code ThreadLocal}
 * that {@code getStepExecution()} then hands to this decider. No
 * {@code ExecutionContextPromotionListener} is needed to promote the flag up
 * to job scope - this decider reads {@code aggregateHoursPerEmployee}'s own
 * {@code ExecutionContext} straight off the parameter.
 * <p>
 * Created by Edgar Muhamyangabo on 8/17/26
 * Author : Edgar Muhamyangabo
 * Date : 8/17/26
 * Project : spring-batch-tutorial
 */
@Component
public class AnomalyReviewDecider implements JobExecutionDecider {

    private static final Logger log = LoggerFactory.getLogger(AnomalyReviewDecider.class);

    public static final FlowExecutionStatus REVIEW_REQUIRED = new FlowExecutionStatus("REVIEW_REQUIRED");
    public static final FlowExecutionStatus PROCEED = new FlowExecutionStatus("PROCEED");

    @Override
    public FlowExecutionStatus decide(JobExecution jobExecution, StepExecution stepExecution) {
        boolean hasAnomalies = stepExecution.getExecutionContext()
                .get(AggregateHoursTasklet.HAS_ANOMALIES_KEY, Boolean.class, false);

        log.info("anomalyReviewDecider: payrollRunId={}, hasAnomalies={}",
                jobExecution.getJobParameters().getLong("payrollRunId"), hasAnomalies);

        return hasAnomalies ? REVIEW_REQUIRED : PROCEED;
    }
}
