package com.edgareldy.springbatchtutorial.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.edgareldy.springbatchtutorial.e2e.PostgresTestcontainerConfiguration;
import com.edgareldy.springbatchtutorial.entity.PayrollRun;
import com.edgareldy.springbatchtutorial.entity.PayrollRunStatus;
import com.edgareldy.springbatchtutorial.entity.RejectedTimesheetEntry;
import com.edgareldy.springbatchtutorial.repository.PayrollRunRepository;
import com.edgareldy.springbatchtutorial.repository.RejectedTimesheetEntryRepository;
import com.edgareldy.springbatchtutorial.repository.TimesheetEntryRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
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
 * Runs the {@code importTimesheets} step in isolation via
 * {@code JobLauncherTestUtils.launchStep} against a real PostgreSQL instance
 * (Testcontainers) and the real sample CSV
 * (`sample-data/timesheets-import-sample.csv`), seeded with a real
 * {@link PayrollRun} row up front to satisfy
 * {@code rejected_timesheet_entries.payroll_run_id}'s NOT NULL foreign key.
 * Verifies that every valid row lands in {@code timesheet_entries} and that
 * the three known-invalid rows (hours_worked = 0.00, hours_worked = 25.00,
 * an unknown employee email) land in {@code rejected_timesheet_entries} with
 * the right reason, linked to the run that produced them.
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
class ImportTimesheetsStepIntegrationTest {

    // 30 of the 33 data rows in the sample CSV are valid (Alice x5, Bob x5,
    // Carla x4, Emma x3, David x13); the remaining 3 are the known-invalid
    // rows this test also verifies.
    private static final int EXPECTED_VALID_ROWS = 30;
    private static final int EXPECTED_REJECTED_ROWS = 3;
    private static final int EXPECTED_READ_COUNT = 33;

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private PayrollRunRepository payrollRunRepository;

    @Autowired
    private TimesheetEntryRepository timesheetEntryRepository;

    @Autowired
    private RejectedTimesheetEntryRepository rejectedTimesheetEntryRepository;

    @Test
    void importsAllValidRowsFromSampleCsv() throws Exception {
        PayrollRun payrollRun = createStartedPayrollRun();
        long validRowsBefore = timesheetEntryRepository.count();

        JobExecution execution = jobLauncherTestUtils.launchStep(
                "importTimesheets", jobParametersFor(payrollRun));

        assertThat(execution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);
        assertThat(timesheetEntryRepository.count() - validRowsBefore).isEqualTo(EXPECTED_VALID_ROWS);

        assertThat(execution.getStepExecutions()).hasSize(1);
        StepExecution stepExecution = execution.getStepExecutions().iterator().next();
        assertThat(stepExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(stepExecution.getReadCount()).isEqualTo(EXPECTED_READ_COUNT);
        assertThat(stepExecution.getWriteCount()).isEqualTo(EXPECTED_VALID_ROWS);
        assertThat(stepExecution.getSkipCount()).isEqualTo(EXPECTED_REJECTED_ROWS);
        assertThat(stepExecution.getProcessSkipCount()).isEqualTo(EXPECTED_REJECTED_ROWS);
        assertThat(stepExecution.getReadSkipCount()).isZero();
        assertThat(stepExecution.getWriteSkipCount()).isZero();
    }

    @Test
    void rejectsKnownInvalidRowsWithReasonLinkedToTheirPayrollRun() throws Exception {
        PayrollRun payrollRun = createStartedPayrollRun();

        JobExecution execution = jobLauncherTestUtils.launchStep(
                "importTimesheets", jobParametersFor(payrollRun));

        assertThat(execution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);

        List<RejectedTimesheetEntry> rejectedForThisRun = rejectedTimesheetEntryRepository.findAll().stream()
                .filter(rejected -> rejected.getPayrollRun().getId().equals(payrollRun.getId()))
                .toList();

        assertThat(rejectedForThisRun).hasSize(EXPECTED_REJECTED_ROWS);
        assertThat(rejectedForThisRun)
                .anySatisfy(rejected -> {
                    assertThat(rejected.getRawLine()).contains("david.chen@example.com,2026-08-20,0.00");
                    assertThat(rejected.getReason()).contains("hours_worked must be > 0 and <= 24");
                })
                .anySatisfy(rejected -> {
                    assertThat(rejected.getRawLine()).contains("david.chen@example.com,2026-08-21,25.00");
                    assertThat(rejected.getReason()).contains("hours_worked must be > 0 and <= 24");
                })
                .anySatisfy(rejected -> {
                    assertThat(rejected.getRawLine()).contains("unknown.employee@example.com");
                    assertThat(rejected.getReason()).contains("Unknown employee email");
                });
    }

    private PayrollRun createStartedPayrollRun() {
        PayrollRun payrollRun = new PayrollRun();
        payrollRun.setPeriodMonth(8);
        payrollRun.setPeriodYear(2026);
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
