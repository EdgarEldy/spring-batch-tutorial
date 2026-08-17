package com.edgareldy.springbatchtutorial.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.edgareldy.springbatchtutorial.batch.calculationstep.PayslipItemProcessor;
import com.edgareldy.springbatchtutorial.dto.csv.EmployeeHoursAggregate;
import com.edgareldy.springbatchtutorial.entity.Employee;
import com.edgareldy.springbatchtutorial.entity.PayrollRun;
import com.edgareldy.springbatchtutorial.entity.PayrollRunStatus;
import com.edgareldy.springbatchtutorial.entity.Payslip;
import com.edgareldy.springbatchtutorial.repository.EmployeeRepository;
import com.edgareldy.springbatchtutorial.repository.PayrollRunRepository;
import com.edgareldy.springbatchtutorial.repository.PayslipRepository;
import com.edgareldy.springbatchtutorial.repository.TimesheetEntryRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Runs {@code monthlyPayrollJob} end to end via
 * {@code JobLauncherTestUtils.launchJob} against a real PostgreSQL instance
 * (Testcontainers) and the real sample CSV. As of this branch the job chains
 * all three steps ({@code importTimesheets} -&gt; {@code aggregateHoursPerEmployee}
 * -&gt; {@code calculatePayslips}), so the nominal path verified here is: the
 * job completes with all three steps {@code COMPLETED}, and every one of the
 * sample CSV's five valid employees (alice.martin, bob.dupont, carla.silva,
 * emma.rossi, david.chen - every email except the three known-invalid rows)
 * ends up with exactly one {@link Payslip} for this run. The anomaly path
 * (flagged-for-review, resume) and restart semantics are exercised once
 * {@code feature/conditional-flow} adds the review step/decision this job
 * does not have yet.
 * <p>
 * This project's PostgreSQL Testcontainer is a single Spring context-cached
 * bean reused across every {@code @SpringBootTest} class in the same Maven
 * Surefire fork, and {@code timesheet_entries} carries no
 * {@code payroll_run_id} (aggregation is scoped by period, see
 * {@code TimesheetEntryRepository}'s Javadoc): other tests in this suite
 * (e.g. {@code ImportTimesheetsStepIntegrationTest}) also import the same
 * sample CSV into the same real 2026-08 period, so the total hours this
 * run's {@code aggregateHoursPerEmployee} actually sees can be a multiple of
 * a single import's numbers depending on execution order. Hardcoding
 * "Alice = 40h" here would be fragile against that reuse, so the expected
 * gross/deductions/net figures below are instead recomputed from whatever
 * total hours {@link TimesheetEntryRepository#aggregateHoursByEmployee}
 * actually reports for this run right after it completes, via
 * {@code PayslipItemProcessor}'s own public static compute methods rather
 * than a hand-copied formula, so the expectation can never silently drift
 * from the real calculation.
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
class MonthlyPayrollJobE2ETest {

    private static final List<String> VALID_EMPLOYEE_EMAILS = List.of(
            "alice.martin@example.com",
            "bob.dupont@example.com",
            "carla.silva@example.com",
            "emma.rossi@example.com",
            "david.chen@example.com");

    private static final BigDecimal OVERTIME_THRESHOLD_HOURS = new BigDecimal("160");
    private static final BigDecimal OVERTIME_MULTIPLIER = new BigDecimal("1.5");
    private static final BigDecimal DEDUCTION_RATE = new BigDecimal("0.15");

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
    void runningTheThreeChainedStepsProducesExpectedPayslipsForEveryValidEmployee() throws Exception {
        PayrollRun payrollRun = createStartedPayrollRun();
        JobParameters jobParameters = new JobParametersBuilder()
                .addLong("payrollRunId", payrollRun.getId())
                .addString("period", payrollRun.getPeriodYear() + "-" + String.format("%02d", payrollRun.getPeriodMonth()))
                .toJobParameters();

        JobExecution execution = jobLauncherTestUtils.launchJob(jobParameters);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(execution.getExitStatus().getExitCode()).isEqualTo("COMPLETED");

        assertThat(execution.getStepExecutions()).hasSize(3);
        assertThat(execution.getStepExecutions())
                .extracting(StepExecution::getStepName)
                .containsExactlyInAnyOrder("importTimesheets", "aggregateHoursPerEmployee", "calculatePayslips");
        assertThat(execution.getStepExecutions())
                .extracting(StepExecution::getStatus)
                .containsOnly(BatchStatus.COMPLETED);

        List<Payslip> payslips = payslipRepository.findAll().stream()
                .filter(payslip -> payslip.getPayrollRun().getId().equals(payrollRun.getId()))
                .toList();
        assertThat(payslips).hasSize(VALID_EMPLOYEE_EMAILS.size());

        Map<Long, BigDecimal> totalHoursByEmployeeId = timesheetEntryRepository.aggregateHoursByEmployee(payrollRun.getId())
                .stream()
                .collect(Collectors.toMap(EmployeeHoursAggregate::employeeId, EmployeeHoursAggregate::totalHours));

        for (String email : VALID_EMPLOYEE_EMAILS) {
            Employee employee = employeeRepository.findByEmail(email).orElseThrow();
            Payslip payslip = payslips.stream()
                    .filter(candidate -> candidate.getEmployee().getId().equals(employee.getId()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No payslip generated for " + email));

            BigDecimal totalHours = totalHoursByEmployeeId.get(employee.getId());
            assertThat(totalHours).as("aggregated total hours for %s", email).isNotNull();
            assertThat(payslip.getTotalHours()).isEqualByComparingTo(totalHours);

            BigDecimal expectedGrossPay = PayslipItemProcessor.computeGrossPay(
                    totalHours, employee.getHourlyRate(), OVERTIME_THRESHOLD_HOURS, OVERTIME_MULTIPLIER);
            BigDecimal expectedDeductions = PayslipItemProcessor.computeDeductions(expectedGrossPay, DEDUCTION_RATE);
            BigDecimal expectedNetPay = PayslipItemProcessor.computeNetPay(expectedGrossPay, expectedDeductions);

            assertThat(payslip.getGrossPay()).isEqualByComparingTo(expectedGrossPay);
            assertThat(payslip.getDeductions()).isEqualByComparingTo(expectedDeductions);
            assertThat(payslip.getNetPay()).isEqualByComparingTo(expectedNetPay);
        }
    }

    private PayrollRun createStartedPayrollRun() {
        PayrollRun payrollRun = new PayrollRun();
        payrollRun.setPeriodMonth(8);
        payrollRun.setPeriodYear(2026);
        payrollRun.setStatus(PayrollRunStatus.STARTED);
        payrollRun.setStartedAt(LocalDateTime.now());
        return payrollRunRepository.save(payrollRun);
    }
}
