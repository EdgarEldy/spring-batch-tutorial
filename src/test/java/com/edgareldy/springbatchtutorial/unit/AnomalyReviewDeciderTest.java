package com.edgareldy.springbatchtutorial.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.edgareldy.springbatchtutorial.batch.aggregationstep.AggregateHoursTasklet;
import com.edgareldy.springbatchtutorial.batch.decision.AnomalyReviewDecider;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.flow.FlowExecutionStatus;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.test.MetaDataInstanceFactory;

/**
 * Exercises {@link AnomalyReviewDecider#decide} directly against a
 * {@link StepExecution} built with {@link MetaDataInstanceFactory} (the same
 * helper {@code spring-batch-test} itself relies on): no {@code JobLauncher},
 * no Spring context, since the decider is a plain, stateless
 * {@code JobExecutionDecider} that only reads the {@code hasAnomalies} key
 * off the {@code ExecutionContext} it is handed.
 * <p>
 * Covers an anomalous {@code ExecutionContext} ({@code hasAnomalies=true},
 * routed to {@code REVIEW_REQUIRED}), a clean one
 * ({@code hasAnomalies=false}, routed to {@code PROCEED}), and the case
 * where {@code AggregateHoursTasklet} never even wrote the key (the
 * decider's own {@code false} default, also {@code PROCEED}).
 * <p>
 * Created by Edgar Muhamyangabo on 8/17/26
 * Author : Edgar Muhamyangabo
 * Date : 8/17/26
 * Project : spring-batch-tutorial
 */
class AnomalyReviewDeciderTest {

    private final AnomalyReviewDecider decider = new AnomalyReviewDecider();

    @Test
    void anomalousExecutionContext_routesToReviewRequired() {
        StepExecution stepExecution = stepExecutionWithHasAnomalies(true);

        FlowExecutionStatus status = decider.decide(stepExecution.getJobExecution(), stepExecution);

        assertThat(status).isEqualTo(AnomalyReviewDecider.REVIEW_REQUIRED);
    }

    @Test
    void cleanExecutionContext_routesToProceed() {
        StepExecution stepExecution = stepExecutionWithHasAnomalies(false);

        FlowExecutionStatus status = decider.decide(stepExecution.getJobExecution(), stepExecution);

        assertThat(status).isEqualTo(AnomalyReviewDecider.PROCEED);
    }

    @Test
    void missingHasAnomaliesKey_defaultsToProceed() {
        StepExecution stepExecution = MetaDataInstanceFactory.createStepExecution(
                jobParameters(), new ExecutionContext());

        FlowExecutionStatus status = decider.decide(stepExecution.getJobExecution(), stepExecution);

        assertThat(status).isEqualTo(AnomalyReviewDecider.PROCEED);
    }

    private StepExecution stepExecutionWithHasAnomalies(boolean hasAnomalies) {
        ExecutionContext executionContext = new ExecutionContext();
        executionContext.put(AggregateHoursTasklet.HAS_ANOMALIES_KEY, hasAnomalies);
        return MetaDataInstanceFactory.createStepExecution(jobParameters(), executionContext);
    }

    private JobParameters jobParameters() {
        return new JobParametersBuilder()
                .addLong("payrollRunId", 42L)
                .addString("period", "2026-08")
                .toJobParameters();
    }
}
