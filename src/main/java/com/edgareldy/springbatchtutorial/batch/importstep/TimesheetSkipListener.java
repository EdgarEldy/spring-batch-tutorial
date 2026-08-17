package com.edgareldy.springbatchtutorial.batch.importstep;

import com.edgareldy.springbatchtutorial.dto.csv.TimesheetCsvRow;
import com.edgareldy.springbatchtutorial.entity.PayrollRun;
import com.edgareldy.springbatchtutorial.entity.RejectedTimesheetEntry;
import com.edgareldy.springbatchtutorial.entity.TimesheetEntry;
import com.edgareldy.springbatchtutorial.repository.PayrollRunRepository;
import com.edgareldy.springbatchtutorial.repository.RejectedTimesheetEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.listener.SkipListener;
import org.springframework.batch.infrastructure.item.file.FlatFileParseException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Persists every row skipped by the {@code importTimesheets} step into
 * {@code rejected_timesheet_entries} - a malformed line skipped at read time
 * ({@link #onSkipInRead}) or a row that failed validation/employee
 * resolution in {@code TimesheetItemProcessor}
 * ({@link #onSkipInProcess}) - so a rejection is always traced, never
 * silently dropped.
 * <p>
 * Declared {@code @StepScope} with {@code @Value("#{jobParameters['payrollRunId']}")}
 * because it genuinely needs the current run's id at execution time (late
 * binding), to satisfy {@code rejected_timesheet_entries.payroll_run_id}'s
 * NOT NULL FK constraint - the reference case for this project's
 * late-binding convention.
 * <p>
 * Created by Edgar Muhamyangabo on 8/17/26
 * Author : Edgar Muhamyangabo
 * Date : 8/17/26
 * Project : spring-batch-tutorial
 */
@Component
@StepScope
public class TimesheetSkipListener implements SkipListener<TimesheetCsvRow, TimesheetEntry> {

    private static final Logger log = LoggerFactory.getLogger(TimesheetSkipListener.class);
    private static final int RAW_LINE_MAX_LENGTH = 500;
    private static final int REASON_MAX_LENGTH = 255;

    private final RejectedTimesheetEntryRepository rejectedTimesheetEntryRepository;
    private final PayrollRunRepository payrollRunRepository;
    private final Long payrollRunId;

    public TimesheetSkipListener(
            RejectedTimesheetEntryRepository rejectedTimesheetEntryRepository,
            PayrollRunRepository payrollRunRepository,
            @Value("#{jobParameters['payrollRunId']}") Long payrollRunId) {
        this.rejectedTimesheetEntryRepository = rejectedTimesheetEntryRepository;
        this.payrollRunRepository = payrollRunRepository;
        this.payrollRunId = payrollRunId;
    }

    @Override
    public void onSkipInRead(Throwable t) {
        persist(extractRawLine(t), t);
    }

    @Override
    public void onSkipInProcess(TimesheetCsvRow item, Throwable t) {
        persist(toRawLine(item), t);
    }

    @Override
    public void onSkipInWrite(TimesheetEntry item, Throwable t) {
        // The writer only persists rows the processor already validated, so a
        // skip here would signal a repository/constraint problem rather than
        // a bad input row - logged for visibility instead of silently
        // swallowed, but not written to rejected_timesheet_entries since
        // there is no well-formed raw line to attach to it at this stage.
        log.warn("Skip during write phase for payrollRunId={}, entry employeeId={}: {}",
                payrollRunId, item.getEmployee() != null ? item.getEmployee().getId() : null, t.getMessage(), t);
    }

    private String extractRawLine(Throwable t) {
        if (t instanceof FlatFileParseException flatFileParseException) {
            return flatFileParseException.getInput();
        }
        return t.getMessage();
    }

    private String toRawLine(TimesheetCsvRow item) {
        return item.email() + "," + item.workDate() + "," + item.hoursWorked();
    }

    private void persist(String rawLine, Throwable t) {
        PayrollRun payrollRun = payrollRunRepository.getReferenceById(payrollRunId);
        String reason = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();

        RejectedTimesheetEntry rejected = new RejectedTimesheetEntry();
        rejected.setRawLine(truncate(rawLine, RAW_LINE_MAX_LENGTH));
        rejected.setReason(truncate(reason, REASON_MAX_LENGTH));
        rejected.setPayrollRun(payrollRun);
        rejectedTimesheetEntryRepository.save(rejected);

        log.warn("Skipped timesheet row for payrollRunId={}: {}", payrollRunId, rejected.getReason());
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
