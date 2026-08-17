package com.edgareldy.springbatchtutorial.dto.batch;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Request body of {@code POST /api/v1/payroll/runs}: the period a new
 * {@code PayrollRun} is created for and {@code monthlyPayrollJob} is
 * launched against.
 * <p>
 * Created by Edgar Muhamyangabo on 8/17/26
 * Author : Edgar Muhamyangabo
 * Date : 8/17/26
 * Project : spring-batch-tutorial
 */
public record CreatePayrollRunRequest(
        @Min(1) @Max(12) int periodMonth,
        @Min(2000) int periodYear
) {
}
