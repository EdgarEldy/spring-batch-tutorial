package com.edgareldy.springbatchtutorial.dto.batch;

/**
 * Technical, Spring-Batch-level view of a just-launched {@code JobExecution}
 * (its id and initial {@code JobExecution.status}), distinct from the
 * business-level {@link PayrollRunResponse}. Not returned by
 * {@code POST /api/v1/payroll/runs} in this branch (see
 * {@code PayrollController} for that choice), kept available for endpoints
 * that need to surface the raw execution id/status directly (e.g. the
 * {@code resume} endpoint added in feature/conditional-flow).
 * <p>
 * Created by Edgar Muhamyangabo on 8/17/26
 * Author : Edgar Muhamyangabo
 * Date : 8/17/26
 * Project : spring-batch-tutorial
 */
public record JobLaunchResponse(
        Long jobExecutionId,
        String status
) {
}
