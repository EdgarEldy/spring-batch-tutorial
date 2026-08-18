package com.edgareldy.springbatchtutorial.repository;

import com.edgareldy.springbatchtutorial.dto.csv.PayrollSummaryRow;
import com.edgareldy.springbatchtutorial.entity.Payslip;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JPA repository for {@link Payslip}, used by
 * {@code PayslipItemWriter} to persist the payslips computed by
 * {@code calculatePayslips}, and by {@code ExportPayrollSummaryTasklet} to
 * read back the {@code Payslip}/{@code Employee} join for the current run's
 * CSV summary via {@link #findSummaryByPayrollRunId(Long)}.
 * <p>
 * Created by Edgar Muhamyangabo on 8/17/26
 * Author : Edgar Muhamyangabo
 * Date : 8/17/26
 * Project : spring-batch-tutorial
 */
public interface PayslipRepository extends JpaRepository<Payslip, Long> {

    @Query("""
            select new com.edgareldy.springbatchtutorial.dto.csv.PayrollSummaryRow(
                p.employee.id,
                p.employee.firstName,
                p.employee.lastName,
                p.employee.email,
                p.employee.department,
                p.totalHours,
                p.grossPay,
                p.deductions,
                p.netPay)
            from Payslip p
            where p.payrollRun.id = :payrollRunId
            order by p.employee.id
            """)
    List<PayrollSummaryRow> findSummaryByPayrollRunId(@Param("payrollRunId") Long payrollRunId);
}
