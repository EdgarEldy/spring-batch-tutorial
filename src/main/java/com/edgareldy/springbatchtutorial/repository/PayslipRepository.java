package com.edgareldy.springbatchtutorial.repository;

import com.edgareldy.springbatchtutorial.entity.Payslip;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link Payslip}, used by
 * {@code PayslipItemWriter} to persist the payslips computed by
 * {@code calculatePayslips}. The summary query joining {@code Payslip}/
 * {@code Employee} for {@code exportPayrollSummary} is added here once
 * {@code feature/export-and-scheduling} needs it.
 * <p>
 * Created by Edgar Muhamyangabo on 8/17/26
 * Author : Edgar Muhamyangabo
 * Date : 8/17/26
 * Project : spring-batch-tutorial
 */
public interface PayslipRepository extends JpaRepository<Payslip, Long> {
}
