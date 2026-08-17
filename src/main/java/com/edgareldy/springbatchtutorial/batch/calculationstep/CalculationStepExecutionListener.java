package com.edgareldy.springbatchtutorial.batch.calculationstep;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.stereotype.Component;

/**
 * Logs a read/written summary at the end of the {@code calculatePayslips}
 * step, the chunk-oriented equivalent of {@code ImportStepExecutionListener}
 * for the payslip calculation step.
 * <p>
 * Created by Edgar Muhamyangabo on 8/17/26
 * Author : Edgar Muhamyangabo
 * Date : 8/17/26
 * Project : spring-batch-tutorial
 */
@Component
public class CalculationStepExecutionListener implements StepExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(CalculationStepExecutionListener.class);

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        log.info("calculatePayslips summary: read={}, written={}",
                stepExecution.getReadCount(),
                stepExecution.getWriteCount());
        return stepExecution.getExitStatus();
    }
}
