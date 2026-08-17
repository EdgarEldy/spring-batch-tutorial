package com.edgareldy.springbatchtutorial.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.edgareldy.springbatchtutorial.batch.aggregationstep.AggregateHoursTasklet;
import com.edgareldy.springbatchtutorial.dto.csv.EmployeeHoursAggregate;
import com.edgareldy.springbatchtutorial.e2e.PostgresTestcontainerConfiguration;
import com.edgareldy.springbatchtutorial.entity.Employee;
import com.edgareldy.springbatchtutorial.entity.PayrollRun;
import com.edgareldy.springbatchtutorial.entity.PayrollRunStatus;
import com.edgareldy.springbatchtutorial.entity.TimesheetEntry;
import com.edgareldy.springbatchtutorial.repository.EmployeeRepository;
import com.edgareldy.springbatchtutorial.repository.PayrollRunRepository;
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
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Runs the {@code aggregateHoursPerEmployee} step in isolation via
 * {@code JobLauncherTestUtils.launchStep} against a real PostgreSQL instance
 * (Testcontainers), seeding real {@link TimesheetEntry} rows directly (rather
 * than replaying {@code importTimesheets} against the shared sample CSV)
 * under a dedicated period (2031-03) that no other test in this suite ever
 * writes to. This project's PostgreSQL Testcontainer is a single Spring
 * context-cached bean reused across every {@code @SpringBootTest} class in
 * the same Maven Surefire fork (same {@code @Import}/{@code @ActiveProfiles}
 * signature everywhere), and {@code timesheet_entries} carries no
 * {@code payroll_run_id} (see {@code TimesheetEntryRepository}'s Javadoc):
 * two tests that both import the real CSV would both land in the CSV's own
 * 2026-08 period and their totals would silently add up. A dedicated period
 * per test sidesteps that entirely instead of relying on execution order.
 * <p>
 * Mirrors the sample CSV's real per-employee hour distribution one-for-one
 * (Alice 40h, Bob 37.5h, Carla 32h, Emma 18h, David Chen 312h) so the
 * anomaly scenario matches the one described in the README: David Chen's
 * 312h is above the default 300h/month anomaly threshold, every other
 * employee is below it.
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
class AggregateHoursPerEmployeeStepIntegrationTest {

    private static final int PERIOD_YEAR = 2031;
    private static final int PERIOD_MONTH = 3;

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private PayrollRunRepository payrollRunRepository;

    @Autowired
    private TimesheetEntryRepository timesheetEntryRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Test
    void producesCorrectPerEmployeeTotalsAndFlagsTheOneEmployeeAboveTheAnomalyThreshold() throws Exception {
        PayrollRun payrollRun = createStartedPayrollRun();
        seedTimesheetData(payrollRun);

        JobExecution execution = jobLauncherTestUtils.launchStep(
                "aggregateHoursPerEmployee", jobParametersFor(payrollRun));

        assertThat(execution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);

        List<EmployeeHoursAggregate> totals = timesheetEntryRepository.aggregateHoursByEmployee(payrollRun.getId());
        assertThat(totals).hasSize(5);
        assertThat(totalHoursFor(totals, "alice.martin@example.com")).isEqualByComparingTo("40.00");
        assertThat(totalHoursFor(totals, "bob.dupont@example.com")).isEqualByComparingTo("37.50");
        assertThat(totalHoursFor(totals, "carla.silva@example.com")).isEqualByComparingTo("32.00");
        assertThat(totalHoursFor(totals, "emma.rossi@example.com")).isEqualByComparingTo("18.00");
        assertThat(totalHoursFor(totals, "david.chen@example.com")).isEqualByComparingTo("312.00");

        assertThat(execution.getStepExecutions()).hasSize(1);
        StepExecution stepExecution = execution.getStepExecutions().iterator().next();
        assertThat(stepExecution.getStatus().isUnsuccessful()).isFalse();
        ExecutionContext executionContext = stepExecution.getExecutionContext();
        assertThat(executionContext.get(AggregateHoursTasklet.HAS_ANOMALIES_KEY, Boolean.class)).isTrue();
        assertThat(executionContext.getInt(AggregateHoursTasklet.ANOMALY_EMPLOYEE_COUNT_KEY)).isEqualTo(1);
    }

    private BigDecimal totalHoursFor(List<EmployeeHoursAggregate> totals, String email) {
        Long employeeId = employeeRepository.findByEmail(email).orElseThrow().getId();
        return totals.stream()
                .filter(aggregate -> aggregate.employeeId().equals(employeeId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No aggregated hours for " + email))
                .totalHours();
    }

    /**
     * Persists real {@link TimesheetEntry} rows for all five seeded
     * employees, spread over distinct days of {@code PERIOD_YEAR}/
     * {@code PERIOD_MONTH}, reproducing the sample CSV's valid rows exactly:
     * 5 x 8h for Alice, 5 x 7.5h for Bob, 4 x 8h for Carla, 3 x 6h for Emma,
     * 13 x 24h for David Chen (the anomaly case).
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

    private JobParameters jobParametersFor(PayrollRun payrollRun) {
        return new JobParametersBuilder()
                .addLong("payrollRunId", payrollRun.getId())
                .addString("period", payrollRun.getPeriodYear() + "-" + String.format("%02d", payrollRun.getPeriodMonth()))
                .toJobParameters();
    }
}
