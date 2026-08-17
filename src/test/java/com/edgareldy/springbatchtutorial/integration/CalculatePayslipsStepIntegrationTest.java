package com.edgareldy.springbatchtutorial.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.edgareldy.springbatchtutorial.e2e.PostgresTestcontainerConfiguration;
import com.edgareldy.springbatchtutorial.entity.Employee;
import com.edgareldy.springbatchtutorial.entity.PayrollRun;
import com.edgareldy.springbatchtutorial.entity.PayrollRunStatus;
import com.edgareldy.springbatchtutorial.entity.Payslip;
import com.edgareldy.springbatchtutorial.entity.TimesheetEntry;
import com.edgareldy.springbatchtutorial.repository.EmployeeRepository;
import com.edgareldy.springbatchtutorial.repository.PayrollRunRepository;
import com.edgareldy.springbatchtutorial.repository.PayslipRepository;
import com.edgareldy.springbatchtutorial.repository.TimesheetEntryRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Runs the {@code calculatePayslips} step in isolation via
 * {@code JobLauncherTestUtils.launchStep}, after first running
 * {@code aggregateHoursPerEmployee} on the same {@link PayrollRun} to mirror
 * the real {@code monthlyPayrollJob} ordering, against a real PostgreSQL
 * instance (Testcontainers). Seeds real {@link TimesheetEntry} rows directly
 * under a dedicated period (2031-04, distinct from every other test in this
 * suite, see {@code AggregateHoursPerEmployeeStepIntegrationTest}'s Javadoc
 * for why a dedicated period matters here) reproducing the sample CSV's
 * real per-employee hour distribution, so both a normal case (Alice, 40h,
 * no overtime) and the overtime case (David Chen, 312h, above the 160h
 * threshold) are exercised.
 * <p>
 * Since {@code JobLauncherTestUtils.launchStep} always wraps the requested
 * step in an ad hoc job named {@code TestJob} (see {@code StepRunner} in
 * spring-batch-test), launching two different steps against the very same
 * {@link JobParameters} back to back would collide on the same
 * {@code TestJob} job instance and fail with
 * {@code JobInstanceAlreadyCompleteException}; an extra {@code stepUnderTest}
 * string parameter keeps {@code payrollRunId}/{@code period} identical
 * (both steps resolve {@code payrollRunId} from {@code JobParameters}) while
 * making each launch's parameter set unique.
 * <p>
 * Created by Edgar Muhamyangabo on 8/17/26
 * Author : Edgar Muhamyangabo
 * Date : 8/17/26
 * Project : spring-batch-tutorial
 */
@ActiveProfiles("test")
@Import(PostgresTestcontainerConfiguration.class)
@SpringBatchTest
@SpringBootTest
class CalculatePayslipsStepIntegrationTest {

    private static final int PERIOD_YEAR = 2031;
    private static final int PERIOD_MONTH = 4;

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private PayrollRunRepository payrollRunRepository;

    @Autowired
    private TimesheetEntryRepository timesheetEntryRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private PayslipRepository payslipRepository;

    @Test
    void persistsExpectedPayslipRowsForNormalAndOvertimeEmployees() throws Exception {
        PayrollRun payrollRun = createStartedPayrollRun();
        seedTimesheetData(payrollRun);

        jobLauncherTestUtils.launchStep("aggregateHoursPerEmployee", jobParametersFor(payrollRun, "aggregate"));
        JobExecution execution = jobLauncherTestUtils.launchStep(
                "calculatePayslips", jobParametersFor(payrollRun, "calculate"));

        assertThat(execution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);

        List<Payslip> payslips = payslipsFor(payrollRun);
        assertThat(payslips).hasSize(5);

        // Alice Martin: 40h, entirely below the 160h overtime threshold.
        Payslip alicePayslip = payslipFor(payslips, "alice.martin@example.com");
        assertThat(alicePayslip.getTotalHours()).isEqualByComparingTo("40.00");
        assertThat(alicePayslip.getGrossPay()).isEqualByComparingTo("1800.00");
        assertThat(alicePayslip.getDeductions()).isEqualByComparingTo("270.00");
        assertThat(alicePayslip.getNetPay()).isEqualByComparingTo("1530.00");

        // David Chen: 312h, 160h regular + 152h overtime at 1.5x, rate 38.00.
        Payslip davidPayslip = payslipFor(payslips, "david.chen@example.com");
        assertThat(davidPayslip.getTotalHours()).isEqualByComparingTo("312.00");
        assertThat(davidPayslip.getGrossPay()).isEqualByComparingTo("14744.00");
        assertThat(davidPayslip.getDeductions()).isEqualByComparingTo("2211.60");
        assertThat(davidPayslip.getNetPay()).isEqualByComparingTo("12532.40");

        for (Payslip payslip : payslips) {
            assertThat(payslip.getPayrollRun().getId()).isEqualTo(payrollRun.getId());
            assertThat(payslip.getNetPay()).isEqualByComparingTo(payslip.getGrossPay().subtract(payslip.getDeductions()));
            assertThat(payslip.getGeneratedAt()).isNotNull();
        }
    }

    private List<Payslip> payslipsFor(PayrollRun payrollRun) {
        return payslipRepository.findAll().stream()
                .filter(payslip -> payslip.getPayrollRun().getId().equals(payrollRun.getId()))
                .toList();
    }

    private Payslip payslipFor(List<Payslip> payslips, String email) {
        Long employeeId = employeeRepository.findByEmail(email).orElseThrow().getId();
        return payslips.stream()
                .filter(payslip -> payslip.getEmployee().getId().equals(employeeId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No payslip generated for " + email));
    }

    /**
     * Same real per-employee hour distribution as the sample CSV: 5 x 8h for
     * Alice, 5 x 7.5h for Bob, 4 x 8h for Carla, 3 x 6h for Emma, 13 x 24h
     * for David Chen.
     */
    private void seedTimesheetData(PayrollRun payrollRun) {
        LocalDate base = LocalDate.of(payrollRun.getPeriodYear(), payrollRun.getPeriodMonth(), 1);
        saveDailyEntries("alice.martin@example.com", base, new BigDecimal("8.00"), 5);
        saveDailyEntries("bob.dupont@example.com", base, new BigDecimal("7.50"), 5);
        saveDailyEntries("carla.silva@example.com", base, new BigDecimal("8.00"), 4);
        saveDailyEntries("emma.rossi@example.com", base, new BigDecimal("6.00"), 3);
        saveDailyEntries("david.chen@example.com", base, new BigDecimal("24.00"), 13);
    }

    private void saveDailyEntries(String email, LocalDate base, BigDecimal hoursPerDay, int days) {
        Employee employee = employeeRepository.findByEmail(email).orElseThrow();
        for (int day = 0; day < days; day++) {
            TimesheetEntry entry = new TimesheetEntry();
            entry.setEmployee(employee);
            entry.setWorkDate(base.plusDays(day));
            entry.setHoursWorked(hoursPerDay);
            entry.setSourceFile("integration-test-seed");
            entry.setImportedAt(LocalDateTime.now());
            timesheetEntryRepository.save(entry);
        }
    }

    private PayrollRun createStartedPayrollRun() {
        PayrollRun payrollRun = new PayrollRun();
        payrollRun.setPeriodMonth(PERIOD_MONTH);
        payrollRun.setPeriodYear(PERIOD_YEAR);
        payrollRun.setStatus(PayrollRunStatus.STARTED);
        payrollRun.setStartedAt(LocalDateTime.now());
        return payrollRunRepository.save(payrollRun);
    }

    private JobParameters jobParametersFor(PayrollRun payrollRun, String stepUnderTest) {
        return new JobParametersBuilder()
                .addLong("payrollRunId", payrollRun.getId())
                .addString("period", payrollRun.getPeriodYear() + "-" + String.format("%02d", payrollRun.getPeriodMonth()))
                .addString("stepUnderTest", stepUnderTest)
                .toJobParameters();
    }
}
