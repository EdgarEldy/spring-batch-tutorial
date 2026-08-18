package com.edgareldy.springbatchtutorial.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.edgareldy.springbatchtutorial.batch.partition.EmployeePartitioner;
import com.edgareldy.springbatchtutorial.e2e.PostgresTestcontainerConfiguration;
import com.edgareldy.springbatchtutorial.entity.Employee;
import com.edgareldy.springbatchtutorial.entity.PayrollRun;
import com.edgareldy.springbatchtutorial.entity.PayrollRunStatus;
import com.edgareldy.springbatchtutorial.entity.TimesheetEntry;
import com.edgareldy.springbatchtutorial.repository.EmployeeRepository;
import com.edgareldy.springbatchtutorial.repository.PayrollRunRepository;
import com.edgareldy.springbatchtutorial.repository.PayslipRepository;
import com.edgareldy.springbatchtutorial.repository.TimesheetEntryRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.ActiveProfiles;

/**
 * One-off, manually-run benchmark comparing a single-partition (effectively
 * sequential) execution of the {@code calculatePayslips} master/worker
 * {@code Step} against a genuinely fanned-out multi-partition execution, at
 * roughly 10,000 employees. Not part of {@code mvn verify}: the class name
 * deliberately doesn't match Surefire's default {@code Test*}/{@code *Test}/
 * {@code *Tests}/{@code *TestCase} include patterns (verified against this
 * repo's {@code pom.xml}, which carries no custom Surefire include/exclude
 * override), so it never runs in CI or a plain {@code mvn test}; wall-clock
 * timing assertions would be flaky there anyway. Rerun on demand with
 * {@code mvn test -Dtest=CalculatePayslipsPartitioningBenchmark}.
 * <p>
 * Builds two ad hoc {@code Job}s around two variants of the same
 * {@code calculatePayslipsWorker} {@code Step} bean the real
 * {@code calculatePayslips} bean in {@code PayrollJobConfig} wraps: one
 * partitioned with {@code gridSize(1)} on a {@link SyncTaskExecutor} (runs
 * on the calling thread, no thread pool overhead at all - the honest
 * "sequential equivalent" baseline, not a rewrite of the pre-partitioning
 * {@code calculatePayslips} Step, since that Step no longer exists and
 * reconstructing it would benchmark different code than what actually
 * ships), and one partitioned with {@link #PARALLEL_GRID_SIZE} (double the
 * branch's default {@code payroll.calculation.partition.pool-size} of 4) on
 * a dedicated {@link ThreadPoolTaskExecutor} of matching size, giving real
 * parallelism a fair shot at 10,000 employees. Each variant runs against its
 * own {@link PayrollRun} row (same dedicated period, 2099-01, unused by any
 * other test in this suite) so their {@code Payslip} rows never mix.
 * <p>
 * Generates ~10,000 {@link Employee} rows and 4 {@link TimesheetEntry} rows
 * per employee (40,000 total, all below the overtime threshold to keep the
 * calculation itself cheap and the comparison about partitioning, not
 * overtime math) directly via {@code saveAll} batches of
 * {@link #SAVE_BATCH_SIZE}, never one row at a time.
 * <p>
 * Elapsed time per variant is read from the actual
 * {@link StepExecution#getStartTime()}/{@link StepExecution#getEndTime()} of
 * every {@code StepExecution} the run produced (the master partition
 * {@code StepExecution} plus every {@code calculatePayslipsWorker:partitionN}
 * worker), not just a {@code System.currentTimeMillis()} wrapper around the
 * launch call, though the wall-clock figure is logged too as a cross-check.
 * <p>
 * Created by Edgar Muhamyangabo on 8/18/26
 * Author : Edgar Muhamyangabo
 * Date : 8/18/26
 * Project : spring-batch-tutorial
 */
@ActiveProfiles("test")
@Import(PostgresTestcontainerConfiguration.class)
@SpringBatchTest
@SpringBootTest
class CalculatePayslipsPartitioningBenchmark {

    private static final Logger log = LoggerFactory.getLogger(CalculatePayslipsPartitioningBenchmark.class);

    private static final int PERIOD_YEAR = 2099;
    private static final int PERIOD_MONTH = 1;
    private static final int EMPLOYEE_COUNT = 10_000;
    private static final int ENTRIES_PER_EMPLOYEE = 4;
    private static final int SAVE_BATCH_SIZE = 500;
    private static final int PARALLEL_GRID_SIZE = 8;

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private EmployeePartitioner employeePartitioner;

    @Autowired
    private Step calculatePayslipsWorker;

    @Autowired
    private PayrollRunRepository payrollRunRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private TimesheetEntryRepository timesheetEntryRepository;

    @Autowired
    private PayslipRepository payslipRepository;

    @Test
    void comparesSequentialVsPartitionedExecutionTimeAt10kEmployees() throws Exception {
        List<Employee> employees = generateEmployees();
        generateTimesheetEntries(employees);
        log.info("Benchmark dataset ready: {} employees, {} timesheet entries",
                employees.size(), (long) employees.size() * ENTRIES_PER_EMPLOYEE);

        PayrollRun sequentialRun = createStartedPayrollRun();
        PayrollRun parallelRun = createStartedPayrollRun();

        Step sequentialStep = buildPartitionedStep(
                "calculatePayslipsBenchmarkSequential", 1, new SyncTaskExecutor());
        Step parallelStep = buildPartitionedStep(
                "calculatePayslipsBenchmarkParallel", PARALLEL_GRID_SIZE, parallelTaskExecutor());

        Job sequentialJob = new JobBuilder("calculatePayslipsBenchmarkSequentialJob", jobRepository)
                .start(sequentialStep)
                .build();
        Job parallelJob = new JobBuilder("calculatePayslipsBenchmarkParallelJob", jobRepository)
                .start(parallelStep)
                .build();

        jobLauncherTestUtils.setJob(sequentialJob);
        long sequentialWallClockStart = System.currentTimeMillis();
        JobExecution sequentialExecution = jobLauncherTestUtils.launchJob(jobParametersFor(sequentialRun));
        long sequentialWallClockMillis = System.currentTimeMillis() - sequentialWallClockStart;
        assertThat(sequentialExecution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);

        jobLauncherTestUtils.setJob(parallelJob);
        long parallelWallClockStart = System.currentTimeMillis();
        JobExecution parallelExecution = jobLauncherTestUtils.launchJob(jobParametersFor(parallelRun));
        long parallelWallClockMillis = System.currentTimeMillis() - parallelWallClockStart;
        assertThat(parallelExecution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);

        long sequentialStepMillis = stepExecutionElapsedMillis(sequentialExecution);
        long parallelStepMillis = stepExecutionElapsedMillis(parallelExecution);

        long sequentialPayslipCount = countPayslips(sequentialRun);
        long parallelPayslipCount = countPayslips(parallelRun);

        double speedup = parallelStepMillis == 0 ? 0 : sequentialStepMillis / (double) parallelStepMillis;

        log.info("=== calculatePayslips partitioning benchmark: {} employees, {} entries each ===",
                EMPLOYEE_COUNT, ENTRIES_PER_EMPLOYEE);
        log.info("Sequential (gridSize=1, SyncTaskExecutor): stepExecution elapsed={} ms, "
                        + "wall-clock launch={} ms, payslips written={}",
                sequentialStepMillis, sequentialWallClockMillis, sequentialPayslipCount);
        log.info("Parallel (gridSize={}, poolSize={}): stepExecution elapsed={} ms, "
                        + "wall-clock launch={} ms, payslips written={}",
                PARALLEL_GRID_SIZE, PARALLEL_GRID_SIZE, parallelStepMillis, parallelWallClockMillis, parallelPayslipCount);
        log.info("Speedup (sequential / parallel): {}x", String.format("%.2f", speedup));

        assertThat(sequentialPayslipCount).isEqualTo(EMPLOYEE_COUNT);
        assertThat(parallelPayslipCount).isEqualTo(EMPLOYEE_COUNT);
    }

    private long countPayslips(PayrollRun payrollRun) {
        return payslipRepository.findAll().stream()
                .filter(payslip -> payslip.getPayrollRun().getId().equals(payrollRun.getId()))
                .count();
    }

    /**
     * Earliest {@code startTime}/latest {@code endTime} across every
     * {@code StepExecution} the job produced (the master partition step
     * plus every worker partition), so the figure reflects the whole
     * fan-out, not just one worker or the master's own bookkeeping.
     */
    private long stepExecutionElapsedMillis(JobExecution jobExecution) {
        LocalDateTime earliestStart = null;
        LocalDateTime latestEnd = null;
        for (StepExecution stepExecution : jobExecution.getStepExecutions()) {
            LocalDateTime start = stepExecution.getStartTime();
            LocalDateTime end = stepExecution.getEndTime();
            if (start != null && (earliestStart == null || start.isBefore(earliestStart))) {
                earliestStart = start;
            }
            if (end != null && (latestEnd == null || end.isAfter(latestEnd))) {
                latestEnd = end;
            }
        }
        if (earliestStart == null || latestEnd == null) {
            return 0;
        }
        return Duration.between(earliestStart, latestEnd).toMillis();
    }

    private Step buildPartitionedStep(String stepName, int gridSize, TaskExecutor taskExecutor) {
        return new StepBuilder(stepName, jobRepository)
                .partitioner("calculatePayslipsWorker", employeePartitioner)
                .step(calculatePayslipsWorker)
                .taskExecutor(taskExecutor)
                .gridSize(gridSize)
                .build();
    }

    private TaskExecutor parallelTaskExecutor() {
        ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
        taskExecutor.setCorePoolSize(PARALLEL_GRID_SIZE);
        taskExecutor.setMaxPoolSize(PARALLEL_GRID_SIZE);
        taskExecutor.setThreadNamePrefix("payslip-benchmark-partition-");
        taskExecutor.initialize();
        return taskExecutor;
    }

    /**
     * ~10,000 employees, saved in {@link #SAVE_BATCH_SIZE}-sized
     * {@code saveAll} batches rather than one row at a time. Department
     * cycles through a fixed small set, hourly rate stays a small positive
     * value satisfying the {@code hourly_rate > 0} check constraint, and
     * bank account is a synthetic, always-34-character IBAN-shaped string
     * satisfying the {@code VARCHAR(34)} column. Email is unique per row,
     * satisfying the {@code employees.email} unique constraint.
     */
    private List<Employee> generateEmployees() {
        String[] departments = {"Engineering", "Sales", "Finance", "HR", "Operations"};
        List<Employee> allEmployees = new ArrayList<>(EMPLOYEE_COUNT);
        List<Employee> batch = new ArrayList<>(SAVE_BATCH_SIZE);
        for (int i = 1; i <= EMPLOYEE_COUNT; i++) {
            Employee employee = new Employee();
            employee.setFirstName("Bench");
            employee.setLastName("Employee" + i);
            employee.setEmail("bench.employee." + i + "@example.com");
            employee.setDepartment(departments[i % departments.length]);
            employee.setHourlyRate(BigDecimal.valueOf(20 + (i % 60)).setScale(2, java.math.RoundingMode.UNNECESSARY));
            employee.setBankAccount(String.format("FR76%030d", i));
            batch.add(employee);
            if (batch.size() == SAVE_BATCH_SIZE) {
                allEmployees.addAll(employeeRepository.saveAll(batch));
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            allEmployees.addAll(employeeRepository.saveAll(batch));
        }
        return allEmployees;
    }

    /**
     * {@link #ENTRIES_PER_EMPLOYEE} entries per employee, 8h each on
     * distinct days of the benchmark period, well under the 160h overtime
     * threshold so the calculation itself stays cheap and the benchmark
     * measures partitioning, not overtime math. Saved in
     * {@link #SAVE_BATCH_SIZE}-sized {@code saveAll} batches.
     */
    private void generateTimesheetEntries(List<Employee> employees) {
        LocalDate base = LocalDate.of(PERIOD_YEAR, PERIOD_MONTH, 1);
        LocalDateTime importedAt = LocalDateTime.now();
        List<TimesheetEntry> batch = new ArrayList<>(SAVE_BATCH_SIZE);
        for (Employee employee : employees) {
            for (int day = 0; day < ENTRIES_PER_EMPLOYEE; day++) {
                TimesheetEntry entry = new TimesheetEntry();
                entry.setEmployee(employee);
                entry.setWorkDate(base.plusDays(day));
                entry.setHoursWorked(new BigDecimal("8.00"));
                entry.setSourceFile("benchmark-seed");
                entry.setImportedAt(importedAt);
                batch.add(entry);
                if (batch.size() == SAVE_BATCH_SIZE) {
                    timesheetEntryRepository.saveAll(batch);
                    batch.clear();
                }
            }
        }
        if (!batch.isEmpty()) {
            timesheetEntryRepository.saveAll(batch);
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
