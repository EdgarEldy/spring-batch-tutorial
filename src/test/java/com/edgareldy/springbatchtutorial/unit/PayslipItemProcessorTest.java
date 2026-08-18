package com.edgareldy.springbatchtutorial.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.edgareldy.springbatchtutorial.batch.calculationstep.PayslipItemProcessor;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link PayslipItemProcessor}'s pure gross/net pay arithmetic
 * ({@code computeGrossPay}/{@code computeDeductions}/{@code computeNetPay}):
 * no {@code JobLauncher}, no Spring context, no mock, since these methods
 * take only {@link BigDecimal} arguments and return a {@link BigDecimal}.
 * <p>
 * Covers the overtime multiplier boundary exactly at the threshold (160h, no
 * uplift) versus one hundredth of an hour past it (160.01h, uplift on that
 * fraction only), a case well above the threshold (200h), a case below it
 * (40h, no overtime at all), the flat-percentage deductions computation, and
 * {@code net_pay = gross_pay - deductions}.
 * <p>
 * Created by Edgar Muhamyangabo on 8/17/26
 * Author : Edgar Muhamyangabo
 * Date : 8/17/26
 * Project : spring-batch-tutorial
 */
class PayslipItemProcessorTest {

    private static final BigDecimal HOURLY_RATE = new BigDecimal("40.00");
    private static final BigDecimal OVERTIME_THRESHOLD_HOURS = new BigDecimal("160");
    private static final BigDecimal OVERTIME_MULTIPLIER = new BigDecimal("1.5");
    private static final BigDecimal DEDUCTION_RATE = new BigDecimal("0.15");

    @Test
    void hoursBelowThreshold_noOvertimeApplied() {
        BigDecimal grossPay = PayslipItemProcessor.computeGrossPay(
                new BigDecimal("40"), HOURLY_RATE, OVERTIME_THRESHOLD_HOURS, OVERTIME_MULTIPLIER);

        assertThat(grossPay).isEqualByComparingTo("1600.00");
    }

    @Test
    void hoursExactly160_noOvertimeApplied() {
        BigDecimal grossPay = PayslipItemProcessor.computeGrossPay(
                new BigDecimal("160"), HOURLY_RATE, OVERTIME_THRESHOLD_HOURS, OVERTIME_MULTIPLIER);

        // Exactly at the threshold: every hour is still paid at the regular
        // rate, the multiplier only applies to hours strictly beyond it.
        assertThat(grossPay).isEqualByComparingTo("6400.00");
    }

    @Test
    void hoursJustAboveThreshold_overtimeAppliedOnlyToTheFraction() {
        BigDecimal grossPay = PayslipItemProcessor.computeGrossPay(
                new BigDecimal("160.01"), HOURLY_RATE, OVERTIME_THRESHOLD_HOURS, OVERTIME_MULTIPLIER);

        // 160h at the regular rate (6400.00) + 0.01h at 1.5x (0.60).
        assertThat(grossPay).isEqualByComparingTo("6400.60");
    }

    @Test
    void hoursWellAboveThreshold_overtimeAppliedToTheFullExcess() {
        BigDecimal grossPay = PayslipItemProcessor.computeGrossPay(
                new BigDecimal("200"), HOURLY_RATE, OVERTIME_THRESHOLD_HOURS, OVERTIME_MULTIPLIER);

        // 160h regular (6400.00) + 40h at 1.5x (2400.00).
        assertThat(grossPay).isEqualByComparingTo("8800.00");
    }

    @Test
    void deductions_areAFlatPercentageOfGrossPay() {
        BigDecimal deductions = PayslipItemProcessor.computeDeductions(new BigDecimal("1000.00"), DEDUCTION_RATE);

        assertThat(deductions).isEqualByComparingTo("150.00");
    }

    @Test
    void deductions_onTheOvertimeGrossPayFigure() {
        BigDecimal deductions = PayslipItemProcessor.computeDeductions(new BigDecimal("8800.00"), DEDUCTION_RATE);

        assertThat(deductions).isEqualByComparingTo("1320.00");
    }

    @Test
    void netPay_isGrossPayMinusDeductions() {
        BigDecimal netPay = PayslipItemProcessor.computeNetPay(new BigDecimal("1000.00"), new BigDecimal("150.00"));

        assertThat(netPay).isEqualByComparingTo("850.00");
    }

    @Test
    void netPay_endToEndFromRawHoursIncludingOvertime() {
        // David Chen's real scenario from the sample CSV: 312h, i.e. 160
        // regular hours plus 152 overtime hours, hourly rate 38.00.
        BigDecimal grossPay = PayslipItemProcessor.computeGrossPay(
                new BigDecimal("312"), new BigDecimal("38.00"), OVERTIME_THRESHOLD_HOURS, OVERTIME_MULTIPLIER);
        BigDecimal deductions = PayslipItemProcessor.computeDeductions(grossPay, DEDUCTION_RATE);
        BigDecimal netPay = PayslipItemProcessor.computeNetPay(grossPay, deductions);

        assertThat(grossPay).isEqualByComparingTo("14744.00");
        assertThat(deductions).isEqualByComparingTo("2211.60");
        assertThat(netPay).isEqualByComparingTo("12532.40");
    }
}
