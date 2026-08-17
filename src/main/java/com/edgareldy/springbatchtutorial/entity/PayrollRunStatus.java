package com.edgareldy.springbatchtutorial.entity;

/**
 * Business-level lifecycle of a {@link PayrollRun}, deliberately kept
 * distinct from Spring Batch's own {@code JobExecution.status}: this enum
 * answers "where is this payroll run in the business process", the batch
 * status answers "did the technical execution succeed".
 * <p>
 * Created by Edgar Muhamyangabo on 8/17/26
 * Author : Edgar Muhamyangabo
 * Date : 8/17/26
 * Project : spring-batch-tutorial
 */
public enum PayrollRunStatus {
    STARTED,
    AWAITING_REVIEW,
    COMPLETED,
    FAILED
}
