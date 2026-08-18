package com.edgareldy.springbatchtutorial.batch.exportstep;

import com.edgareldy.springbatchtutorial.dto.csv.PayrollSummaryRow;
import com.edgareldy.springbatchtutorial.entity.PayrollRun;
import com.edgareldy.springbatchtutorial.entity.PayrollRunStatus;
import com.edgareldy.springbatchtutorial.repository.PayrollRunRepository;
import com.edgareldy.springbatchtutorial.repository.PayslipRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.file.FlatFileItemWriter;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemWriterBuilder;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;

/**
 * Final step of {@code monthlyPayrollJob}/{@code payrollFinalizeJob}: one
 * query joining {@code Payslip}/{@code Employee} for the current run
 * ({@link PayslipRepository#findSummaryByPayrollRunId(Long)}), one
 * {@code FlatFileItemWriter}-backed CSV write of the whole result in a
 * single pass. Neither is naturally item-by-item (the join is a single
 * grouped query, the file is written as one block once the query returns),
 * so - like {@code AggregateHoursTasklet} - this is a {@code Tasklet} rather
 * than a chunk-oriented step (see the README's "Step design: chunk-oriented
 * vs. tasklet" section).
 * <p>
 * On success, this is also the single place that sets the business-level
 * {@link PayrollRun#setStatus(PayrollRunStatus) PayrollRun.status} to
 * {@code COMPLETED} and stamps {@code completedAt} - the flow's terminal,
 * happy-path state, mirroring how {@code FlagForReviewTasklet} owns the
 * {@code AWAITING_REVIEW} transition on the anomaly branch. This never
 * touches {@code JobExecution.status}, kept strictly separate per the
 * project's conventions.
 * <p>
 * Declared {@code @StepScope} with both
 * {@code @Value("#{jobParameters['payrollRunId']}")} (to query and update the
 * right {@code PayrollRun}) and {@code @Value("#{jobParameters['period']}")}
 * (to name the output file {@code payroll-summary-<period>.csv}, where
 * {@code period} is already formatted {@code <year>-<month>} by
 * {@code PayrollJobLauncherServiceImpl}) - both are only known at execution
 * time, Spring Batch's standard late-binding mechanism for
 * {@code JobParameters}, exactly as documented in the README for this class.
 * <p>
 * Created by Edgar Muhamyangabo on 8/17/26
 * Author : Edgar Muhamyangabo
 * Date : 8/17/26
 * Project : spring-batch-tutorial
 */
@Component
@StepScope
public class ExportPayrollSummaryTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(ExportPayrollSummaryTasklet.class);

    private final PayslipRepository payslipRepository;
    private final PayrollRunRepository payrollRunRepository;
    private final Long payrollRunId;
    private final String period;
    private final String outputDir;

    public ExportPayrollSummaryTasklet(
            PayslipRepository payslipRepository,
            PayrollRunRepository payrollRunRepository,
            @Value("#{jobParameters['payrollRunId']}") Long payrollRunId,
            @Value("#{jobParameters['period']}") String period,
            @Value("${payroll.export.output-dir}") String outputDir) {
        this.payslipRepository = payslipRepository;
        this.payrollRunRepository = payrollRunRepository;
        this.payrollRunId = payrollRunId;
        this.period = period;
        this.outputDir = outputDir;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        List<PayrollSummaryRow> summaryRows = payslipRepository.findSummaryByPayrollRunId(payrollRunId);

        Path summaryFile = writeSummaryFile(summaryRows);

        PayrollRun payrollRun = payrollRunRepository.getReferenceById(payrollRunId);
        payrollRun.setStatus(PayrollRunStatus.COMPLETED);
        payrollRun.setCompletedAt(LocalDateTime.now());
        payrollRunRepository.save(payrollRun);

        log.info("exportPayrollSummary summary: payrollRunId={}, rows={}, file={}",
                payrollRunId, summaryRows.size(), summaryFile);

        return RepeatStatus.FINISHED;
    }

    /**
     * Builds and drives a {@code FlatFileItemWriter} by hand ({@code open}/
     * {@code write}/{@code close}) rather than wiring it as the step's
     * {@code ItemWriter}: the whole result set is written in a single call,
     * as one unit of work, consistent with this step being a {@code Tasklet}
     * rather than a chunk-oriented step.
     */
    private Path writeSummaryFile(List<PayrollSummaryRow> summaryRows) throws Exception {
        Path directory = Path.of(outputDir);
        Files.createDirectories(directory);
        Path summaryFile = directory.resolve("payroll-summary-" + period + ".csv");

        FlatFileItemWriter<PayrollSummaryRow> writer = new FlatFileItemWriterBuilder<PayrollSummaryRow>()
                .name("payrollSummaryItemWriter")
                .resource(new FileSystemResource(summaryFile))
                // Overwrites any file left behind by a previous attempt for the same period
                // (e.g. a retried/restarted execution), keeping the export idempotent.
                .shouldDeleteIfExists(true)
                .headerCallback(out -> out.write(
                        "employee_id,first_name,last_name,email,department,total_hours,gross_pay,deductions,net_pay"))
                .delimited()
                .delimiter(",")
                .fieldExtractor(row -> new Object[] {
                        row.employeeId(), row.firstName(), row.lastName(), row.email(), row.department(),
                        row.totalHours(), row.grossPay(), row.deductions(), row.netPay()
                })
                .build();

        writer.open(new ExecutionContext());
        try {
            writer.write(new Chunk<>(summaryRows));
        } finally {
            writer.close();
        }

        return summaryFile;
    }
}
