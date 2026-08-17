package com.edgareldy.springbatchtutorial.batch.importstep;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.stereotype.Component;

/**
 * Logs a read/written/skipped summary at the end of the
 * {@code importTimesheets} step, breaking down the skip count by phase
 * (read/process/write) so an operator can tell at a glance whether the CSV
 * itself was malformed or individual rows failed validation.
 * <p>
 * Created by Edgar Muhamyangabo on 8/17/26
 * Author : Edgar Muhamyangabo
 * Date : 8/17/26
 * Project : spring-batch-tutorial
 */
@Component
public class ImportStepExecutionListener implements StepExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(ImportStepExecutionListener.class);

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        log.info("importTimesheets summary: read={}, written={}, skipped={} "
                        + "(readSkip={}, processSkip={}, writeSkip={})",
                stepExecution.getReadCount(),
                stepExecution.getWriteCount(),
                stepExecution.getSkipCount(),
                stepExecution.getReadSkipCount(),
                stepExecution.getProcessSkipCount(),
                stepExecution.getWriteSkipCount());
        return stepExecution.getExitStatus();
    }
}
