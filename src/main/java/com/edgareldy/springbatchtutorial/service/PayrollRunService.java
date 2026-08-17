package com.edgareldy.springbatchtutorial.service;

import com.edgareldy.springbatchtutorial.entity.PayrollRun;

/**
 * Contract for creating and looking up {@link PayrollRun} rows, the
 * business-level counterpart of a {@code monthlyPayrollJob} execution.
 * Implemented by {@code PayrollRunServiceImpl}.
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
}
