package com.edgareldy.springbatchtutorial.batch.calculationstep;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.stereotype.Component;

/**
 * Logs a read/written summary at the end of each {@code calculatePayslips}
 * chunk-processing execution, the chunk-oriented equivalent of
 * {@code ImportStepExecutionListener} for the payslip calculation step.
 * <p>
 * {@code calculatePayslips} is now a partitioned master/worker {@code Step}:
 * this listener is attached to the worker step
 * ({@code calculatePayslipsWorker}), not to the master {@code PartitionStep}
 * itself, so it fires once per partition rather than once for the step as a
 * whole - the master step never reads/writes items itself (its worker
 * {@code StepExecution}s do), so a listener on it would only ever log zero
 * counts. {@link StepExecution#getStepName()} disambiguates each partition's
 * log line (e.g. {@code calculatePayslipsWorker:partition0}).
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
        log.info("calculatePayslips worker summary [{}]: read={}, written={}",
                stepExecution.getStepName(),
                stepExecution.getReadCount(),
                stepExecution.getWriteCount());
        return stepExecution.getExitStatus();
    }
}
