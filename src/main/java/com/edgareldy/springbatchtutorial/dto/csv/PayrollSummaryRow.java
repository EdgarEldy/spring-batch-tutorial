package com.edgareldy.springbatchtutorial.dto.csv;

import java.math.BigDecimal;

/**
 * One row of the {@code payroll-summary-<year>-<month>.csv} file written by
 * {@code ExportPayrollSummaryTasklet}: a single {@code Payslip} joined with
 * its {@code Employee}, produced by
 * {@code PayslipRepository.findSummaryByPayrollRunId} (one grouped join
 * query, not an item-by-item read - see {@code ExportPayrollSummaryTasklet}'s
 * Javadoc) for the current {@code PayrollRun}.
 * <p>
 * Created by Edgar Muhamyangabo on 8/17/26
 * Author : Edgar Muhamyangabo
 * Date : 8/17/26
 * Project : spring-batch-tutorial
 */
public record PayrollSummaryRow(
        Long employeeId,
        String firstName,
        String lastName,
        String email,
        String department,
        BigDecimal totalHours,
        BigDecimal grossPay,
        BigDecimal deductions,
        BigDecimal netPay
) {
}
