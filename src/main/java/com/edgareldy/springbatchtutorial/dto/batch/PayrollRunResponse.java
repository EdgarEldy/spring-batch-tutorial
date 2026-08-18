package com.edgareldy.springbatchtutorial.dto.batch;

import com.edgareldy.springbatchtutorial.entity.PayrollRunStatus;
import java.time.LocalDateTime;

/**
 * Business-level view of a {@code PayrollRun}, returned as the {@code data}
 * payload of {@code ApiResponse} by every payroll run endpoint
 * ({@code POST /api/v1/payroll/runs}, {@code GET .../{id}},
 * {@code GET /api/v1/payroll/runs} (paginated), and
 * {@code POST .../{id}/resume}) so a client always sees the same shape
 * whether creating, polling, listing, or resuming a run.
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
