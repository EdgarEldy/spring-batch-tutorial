package com.edgareldy.springbatchtutorial.dto.batch;

import com.edgareldy.springbatchtutorial.entity.PayrollRunStatus;
import java.time.LocalDateTime;

/**
 * Business-level view of a {@code PayrollRun}, returned as the {@code data}
 * payload of {@code ApiResponse} by both
 * {@code POST /api/v1/payroll/runs} and {@code GET /api/v1/payroll/runs/{id}}
 * so a client can create a run and immediately poll its status with the
 * same shape.
 * <p>
 * Created by Edgar Muhamyangabo on 8/17/26
 * Author : Edgar Muhamyangabo
 * Date : 8/17/26
 * Project : spring-batch-tutorial
 */
public record PayrollRunResponse(
        Long id,
        Integer periodMonth,
        Integer periodYear,
        PayrollRunStatus status,
        LocalDateTime startedAt,
        LocalDateTime completedAt
) {
}
