package com.edgareldy.springbatchtutorial.batch.reviewstep;

import com.edgareldy.springbatchtutorial.entity.PayrollRun;
import com.edgareldy.springbatchtutorial.entity.PayrollRunStatus;
import com.edgareldy.springbatchtutorial.repository.PayrollRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Single unit of work executed on the {@code REVIEW_REQUIRED} branch of
 * {@code monthlyPayrollJob}: sets the business-level
 * {@link PayrollRun#setStatus(PayrollRunStatus) PayrollRun.status} to
 * {@code AWAITING_REVIEW} so a human can review the flagged anomaly
 * out-of-band. This never touches {@code JobExecution.status} - the
 * {@code JobExecution} still completes normally on this path, since routing
 * to review is the job doing exactly what it is supposed to do, not a
 * technical failure (see the README's "Why these library choices" section).
 * A single grouped status update is not item-by-item work, hence a
 * {@code Tasklet} rather than a chunk-oriented step.
 * <p>
 * Declared {@code @StepScope} with {@code @Value("#{jobParameters['payrollRunId']}")}
 * because it needs the current run's id at execution time (late binding),
 * the same pattern as {@code TimesheetSkipListener} and
 * {@code AggregateHoursTasklet}.
 * <p>
 * Created by Edgar Muhamyangabo on 8/17/26
 * Author : Edgar Muhamyangabo
 * Date : 8/17/26
 * Project : spring-batch-tutorial
 */
@Component
@StepScope
public class FlagForReviewTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(FlagForReviewTasklet.class);

    private final PayrollRunRepository payrollRunRepository;
    private final Long payrollRunId;

    public FlagForReviewTasklet(
            PayrollRunRepository payrollRunRepository,
            @Value("#{jobParameters['payrollRunId']}") Long payrollRunId) {
        this.payrollRunRepository = payrollRunRepository;
        this.payrollRunId = payrollRunId;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        PayrollRun payrollRun = payrollRunRepository.getReferenceById(payrollRunId);
        payrollRun.setStatus(PayrollRunStatus.AWAITING_REVIEW);
        payrollRunRepository.save(payrollRun);

        log.info("flagForReview: payrollRunId={} set to AWAITING_REVIEW, awaiting resume", payrollRunId);

        return RepeatStatus.FINISHED;
    }
}
