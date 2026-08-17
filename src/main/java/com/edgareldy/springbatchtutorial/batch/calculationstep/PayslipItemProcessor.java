package com.edgareldy.springbatchtutorial.batch.calculationstep;

import com.edgareldy.springbatchtutorial.dto.csv.EmployeeHoursAggregate;
import com.edgareldy.springbatchtutorial.entity.Payslip;
import com.edgareldy.springbatchtutorial.repository.EmployeeRepository;
import com.edgareldy.springbatchtutorial.repository.PayrollRunRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Computes a {@link Payslip} from one employee's aggregated hours for the
 * current run: {@code gross_pay = total_hours * hourly_rate}, with a
 * configurable multiplier (default 1.5x) applied to hours beyond a
 * configurable monthly threshold (default 160h), {@code deductions} as a
 * flat configurable percentage of {@code gross_pay}, and
 * {@code net_pay = gross_pay - deductions}. The arithmetic itself lives in
 * {@link #computeGrossPay}/{@link #computeDeductions}/{@link #computeNetPay},
 * static methods taking only {@link BigDecimal} arguments so they are
 * directly unit-testable without a Spring context or any mock, including at
 * the overtime boundary (exactly 160h vs. 160.01h).
 * <p>
 * Declared {@code @StepScope} with {@code @Value("#{jobParameters['payrollRunId']}")}
 * because it needs the current run's id at execution time (late binding) to
 * attach the {@code payroll_run_id} foreign key on each {@link Payslip} -
 * resolved via {@code getReferenceById} rather than a full load, since only
 * the id is needed to populate the FK column.
 * <p>
 * Created by Edgar Muhamyangabo on 8/17/26
 * Author : Edgar Muhamyangabo
 * Date : 8/17/26
 * Project : spring-batch-tutorial
 */
@Component
@StepScope
public class PayslipItemProcessor implements ItemProcessor<EmployeeHoursAggregate, Payslip> {

    private static final int MONEY_SCALE = 2;

    private final EmployeeRepository employeeRepository;
    private final PayrollRunRepository payrollRunRepository;
    private final Long payrollRunId;
    private final BigDecimal overtimeThresholdHours;
    private final BigDecimal overtimeMultiplier;
    private final BigDecimal deductionRate;

    public PayslipItemProcessor(
            EmployeeRepository employeeRepository,
            PayrollRunRepository payrollRunRepository,
            @Value("#{jobParameters['payrollRunId']}") Long payrollRunId,
            @Value("${payroll.calculation.overtime-threshold-hours}") BigDecimal overtimeThresholdHours,
            @Value("${payroll.calculation.overtime-multiplier}") BigDecimal overtimeMultiplier,
            @Value("${payroll.calculation.deduction-rate}") BigDecimal deductionRate) {
        this.employeeRepository = employeeRepository;
        this.payrollRunRepository = payrollRunRepository;
        this.payrollRunId = payrollRunId;
        this.overtimeThresholdHours = overtimeThresholdHours;
        this.overtimeMultiplier = overtimeMultiplier;
        this.deductionRate = deductionRate;
    }

    @Override
    public Payslip process(EmployeeHoursAggregate item) {
        BigDecimal grossPay = computeGrossPay(item.totalHours(), item.hourlyRate(), overtimeThresholdHours, overtimeMultiplier);
        BigDecimal deductions = computeDeductions(grossPay, deductionRate);
        BigDecimal netPay = computeNetPay(grossPay, deductions);

        Payslip payslip = new Payslip();
        payslip.setPayrollRun(payrollRunRepository.getReferenceById(payrollRunId));
        payslip.setEmployee(employeeRepository.getReferenceById(item.employeeId()));
        payslip.setTotalHours(item.totalHours());
        payslip.setGrossPay(grossPay);
        payslip.setDeductions(deductions);
        payslip.setNetPay(netPay);
        payslip.setGeneratedAt(LocalDateTime.now());
        return payslip;
    }

    /**
     * Pure gross pay computation: hours up to {@code overtimeThresholdHours}
     * are paid at {@code hourlyRate}, any hours beyond it are paid at
     * {@code hourlyRate * overtimeMultiplier}.
     */
    static BigDecimal computeGrossPay(
            BigDecimal totalHours,
            BigDecimal hourlyRate,
            BigDecimal overtimeThresholdHours,
            BigDecimal overtimeMultiplier) {
        if (totalHours.compareTo(overtimeThresholdHours) <= 0) {
            return totalHours.multiply(hourlyRate).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }
        BigDecimal regularHours = overtimeThresholdHours;
        BigDecimal overtimeHours = totalHours.subtract(overtimeThresholdHours);
        BigDecimal regularPay = regularHours.multiply(hourlyRate);
        BigDecimal overtimePay = overtimeHours.multiply(hourlyRate).multiply(overtimeMultiplier);
        return regularPay.add(overtimePay).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /** Pure deductions computation: a flat percentage of gross pay. */
    static BigDecimal computeDeductions(BigDecimal grossPay, BigDecimal deductionRate) {
        return grossPay.multiply(deductionRate).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /** Pure net pay computation: gross pay minus deductions. */
    static BigDecimal computeNetPay(BigDecimal grossPay, BigDecimal deductions) {
        return grossPay.subtract(deductions).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
