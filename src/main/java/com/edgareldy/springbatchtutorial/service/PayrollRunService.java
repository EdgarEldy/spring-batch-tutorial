package com.edgareldy.springbatchtutorial.service;

import com.edgareldy.springbatchtutorial.entity.PayrollRun;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Contract for creating, looking up, listing, and resuming {@link PayrollRun}
 * rows, the business-level counterpart of a {@code monthlyPayrollJob}/
 * {@code payrollFinalizeJob} execution. Implemented by
 * {@code PayrollRunServiceImpl}.
 * <p>
 * Created by Edgar Muhamyangabo on 8/17/26
 * Author : Edgar Muhamyangabo
 * Date : 8/17/26
 * Project : spring-batch-tutorial
 */
public interface PayrollRunService {

    /**
     * Creates a new {@link PayrollRun} for the given period with
     * {@code status = STARTED} and {@code startedAt = now}.
     */
    PayrollRun createRun(int periodMonth, int periodYear);

    /**
     * Looks up a {@link PayrollRun} by id, throwing
     * {@code ResourceNotFoundException} if none exists.
     */
    PayrollRun getRun(Long id);

    /**
     * Paginated run history, so an admin can see past/in-progress runs
     * without querying {@code BATCH_JOB_EXECUTION} directly.
     */
    Page<PayrollRun> listRuns(Pageable pageable);

    /**
     * Validates that the {@link PayrollRun} identified by {@code id} is
     * currently {@code AWAITING_REVIEW} (throwing
     * {@code BusinessRuleException} otherwise) and, on success, moves it
     * back to {@code STARTED} ahead of {@code payrollFinalizeJob} being
     * launched for it.
     */
    PayrollRun resumeRun(Long id);
}
