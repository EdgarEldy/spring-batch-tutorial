# spring-batch-tutorial

A complete, hands-on tutorial for building a **payroll processing system** with **Spring Batch**, on top of **Spring Boot 4.1.x** (Spring Framework 7, Java 17). Organized into Git branches - following **git-flow** - that progressively cover most of the key concepts of the Spring Batch ecosystem.

The domain is a monthly payroll run: `employees` clock hours (`timesheet_entries`), which are imported in bulk, aggregated, and turned into `payslips` for a given `payroll_run`, with anomalies routed to a manual review step rather than silently processed (see [feature/conditional-flow](#featureconditional-flow)).

This document is the **complete specification** of the project: it is meant to be followed step by step to implement each branch.

## Table of contents

- [Why these library choices](#why-these-library-choices)
- [Tech stack](#tech-stack)
- [Data model](#data-model)
- [Step design: chunk-oriented vs. tasklet](#step-design-chunk-oriented-vs-tasklet)
- [Git workflow (git-flow)](#git-workflow-git-flow)
- [Branching strategy](#branching-strategy)
- [Project structure](#project-structure)
- [Standard response format](#standard-response-format)
- [Testing strategy](#testing-strategy)
- [Git commit convention](#git-commit-convention)
- [feature/core-architecture](#featurecore-architecture)
- [feature/timesheet-import](#featuretimesheet-import)
- [feature/payroll-calculation](#featurepayroll-calculation)
- [feature/conditional-flow](#featureconditional-flow)
- [feature/export-and-scheduling](#featureexport-and-scheduling)
- [feature/parallel-processing (bonus)](#featureparallel-processing-bonus)
- [Order of work](#order-of-work)
- [Code conventions](#code-conventions)
- [Concepts covered](#concepts-covered)
- [How to follow this tutorial](#how-to-follow-this-tutorial)

## Why these library choices

- **`spring-boot-starter-batch` instead of a bare Spring Batch dependency**: the starter wires the `JobRepository`/`JobLauncher` infrastructure through Spring Boot auto-configuration, while still letting `BatchConfig` override the datasource and transaction manager explicitly - the tutorial keeps auto-configuration for wiring but never lets a job run implicitly on startup (`spring.batch.job.enabled=false`), since an accidental payroll run is a much worse default than an accidental REST call.
- **PostgreSQL for both the business schema and the Batch metadata schema, instead of an in-memory H2 metadata store**: Spring Boot defaults to an in-memory `JobRepository` when no datasource is configured, which silently loses all job history on restart. A real payroll system needs `BATCH_JOB_EXECUTION`/`BATCH_STEP_EXECUTION` to survive restarts and be queryable - so the same PostgreSQL instance hosts both schemas from `feature/core-architecture` onward.
- **A `Tasklet` for aggregation and export, instead of forcing everything into chunk-oriented steps**: chunk-oriented processing exists for item-by-item transformation; a single grouped SQL aggregation or a single summary file write is not naturally "item-by-item" and gains nothing from `ItemReader`/`ItemProcessor`/`ItemWriter` indirection. Using `Tasklet` where it fits, and chunk-oriented steps where they fit, is the actual idiomatic split in Spring Batch - not "chunk-oriented everywhere."
- **A `JobExecutionDecider` for the anomaly-review branch, instead of failing the job**: an employee with an abnormal number of hours is a business exception, not a technical failure. Failing the `Job` would conflate the two and make restart semantics confusing; a decider that routes to a distinct review flow keeps `JobExecution.status = COMPLETED` meaningful (the job did what it was supposed to do - flag the run for review) while the business-level `PayrollRun.status` carries the actual state.
- **Testcontainers over an embedded database for integration/E2E tests**: Spring Batch's metadata schema and locking behavior are PostgreSQL-specific enough (sequence usage, column types) that testing against H2 would validate a different database's semantics - the same reasoning already applied throughout this tutorial series.

## Tech stack

| Component | Choice |
|---|---|
| Framework | Spring Boot 4.1.x (Spring Framework 7) |
| Language | Java 17 (LTS) |
| Build | Maven - `groupId com.edgareldy`, `artifactId spring-batch-tutorial` |
| Database | PostgreSQL 16 (via Docker Compose) - hosts both the business schema and the Spring Batch metadata schema |
| Batch processing | Spring Batch (`spring-boot-starter-batch`) |
| Migrations | Flyway |
| ORM | Spring Data JPA / Hibernate |
| File reading/writing | `FlatFileItemReader`/`FlatFileItemWriter` (CSV) |
| API documentation | springdoc-openapi (Swagger UI) |
| Monitoring | Spring Boot Actuator |
| Tests | JUnit 5, Mockito, `spring-batch-test`, Testcontainers |
| CI/CD | GitHub Actions |
| Containerization | Docker, docker-compose |

## Data model

```
employees (id, first_name, last_name, email, department, hourly_rate, bank_account)
    │ 1
    │
    │ N
timesheet_entries (id, employee_id, work_date, hours_worked, source_file, imported_at)

payroll_runs (id, period_month, period_year, status, started_at, completed_at)
    │ 1
    │
    │ N
payslips (id, payroll_run_id, employee_id, total_hours, gross_pay, deductions, net_pay, generated_at)

rejected_timesheet_entries (id, raw_line, reason, payroll_run_id)
```

`payslips.employee_id` and `rejected_timesheet_entries.payroll_run_id` are foreign keys to `employees`/`payroll_runs` respectively, omitted from the diagram above for readability.

### Column details

**employees**
| Column | Type | Constraints |
|---|---|---|
| id | BIGINT | PK, auto-increment |
| first_name | VARCHAR(100) | NOT NULL |
| last_name | VARCHAR(100) | NOT NULL |
| email | VARCHAR(255) | NOT NULL, UNIQUE |
| department | VARCHAR(100) | NOT NULL |
| hourly_rate | NUMERIC(10,2) | NOT NULL, > 0 |
| bank_account | VARCHAR(34) | NOT NULL (IBAN-length) |

**timesheet_entries**
| Column | Type | Constraints |
|---|---|---|
| id | BIGINT | PK, auto-increment |
| employee_id | BIGINT | FK → employees.id, NOT NULL |
| work_date | DATE | NOT NULL |
| hours_worked | NUMERIC(4,2) | NOT NULL, > 0 and ≤ 24 |
| source_file | VARCHAR(255) | NOT NULL, name of the imported CSV |
| imported_at | TIMESTAMP | NOT NULL |

**payroll_runs**
| Column | Type | Constraints |
|---|---|---|
| id | BIGINT | PK, auto-increment |
| period_month | INT | NOT NULL, 1–12 |
| period_year | INT | NOT NULL |
| status | VARCHAR(30) | NOT NULL (`STARTED`, `AWAITING_REVIEW`, `COMPLETED`, `FAILED`) |
| started_at | TIMESTAMP | NOT NULL |
| completed_at | TIMESTAMP | nullable |

**payslips**
| Column | Type | Constraints |
|---|---|---|
| id | BIGINT | PK, auto-increment |
| payroll_run_id | BIGINT | FK → payroll_runs.id, NOT NULL |
| employee_id | BIGINT | FK → employees.id, NOT NULL |
| total_hours | NUMERIC(6,2) | NOT NULL |
| gross_pay | NUMERIC(10,2) | NOT NULL, computed |
| deductions | NUMERIC(10,2) | NOT NULL, computed |
| net_pay | NUMERIC(10,2) | NOT NULL, computed = gross_pay − deductions |
| generated_at | TIMESTAMP | NOT NULL |

**rejected_timesheet_entries**
| Column | Type | Constraints |
|---|---|---|
| id | BIGINT | PK, auto-increment |
| raw_line | VARCHAR(500) | NOT NULL, the untouched source CSV line |
| reason | VARCHAR(255) | NOT NULL |
| payroll_run_id | BIGINT | FK → payroll_runs.id, NOT NULL |

## Step design: chunk-oriented vs. tasklet

Spring Batch supports two equally idiomatic ways of implementing a `Step`, and `monthlyPayrollJob` deliberately uses both, evenly split:

- **Chunk-oriented steps** (`ItemReader` → `ItemProcessor` → `ItemWriter`, processed in configurable-size chunks) for **`importTimesheets`** (CSV rows → validated `TimesheetEntry` rows) and **`calculatePayslips`** (paginated read of per-employee hour aggregates → payslip computation → persisted `Payslip`): both are genuinely item-by-item transformations over a dataset whose size isn't known up front.
- **Tasklet steps** (`Tasklet.execute(...)`, a single unit of work) for **`aggregateHoursPerEmployee`** (one grouped SQL query, not naturally item-by-item) and **`exportPayrollSummary`** (one query, one file write): forcing either into `ItemReader`/`ItemProcessor`/`ItemWriter` would add indirection with no benefit.

All four steps share the same `Job` (`monthlyPayrollJob`) and the same underlying `PayrollRun`/`Employee`/`Payslip` JPA repositories - only the step implementation style changes based on what each step actually does. `ExportPayrollSummaryTasklet` also needs the run's `period` at execution time (not at bean-definition time): it's declared `@StepScope` and reads it via `@Value("#{jobParameters['period']}")`, Spring Batch's standard late-binding mechanism for injecting job parameters into a step-scoped bean.

## Git workflow (git-flow)

This repository follows **git-flow** (Vincent Driessen's branching model), using the `git-flow` CLI extension. No commit is ever made directly on `master` or `develop`.

| Branch type | Purpose | Branched from | Merged into |
|---|---|---|---|
| `master` | Production-ready history. Every commit on `master` is a tagged release. | - | - |
| `develop` | Integration branch; always reflects the latest delivered features. | `master` (once, at init) | `master` (via `release/*`) |
| `feature/*` | One unit of work from this specification. | `develop` | `develop` |
| `release/*` | Stabilizes a set of merged features before tagging (version bump, changelog, final regression pass). | `develop` | `master` and `develop` |
| `hotfix/*` | Urgent fix against a released version, without pulling in unreleased `develop` work. | `master` | `master` and `develop` |

Typical commands used throughout this tutorial:

```
git flow init                              # once, at repository creation
git flow feature start core-architecture   # creates feature/core-architecture from develop
git flow feature finish core-architecture  # merges back into develop, deletes the branch
git flow release start 0.1.0               # after all features for a milestone are in develop
git flow release finish 0.1.0              # merges into master and develop, tags v0.1.0
```

## Branching strategy

The `feature/*` branches implemented in this tutorial, in the order they build on one another:

| Branch | Role |
|---|---|
| `feature/core-architecture` | Technical foundation: project structure, Spring Batch/PostgreSQL configuration, Docker, CI. |
| `feature/timesheet-import` | `importTimesheets` chunk-oriented step: CSV → validated `TimesheetEntry` rows, with skip/retry. |
| `feature/payroll-calculation` | `aggregateHoursPerEmployee` (Tasklet) and `calculatePayslips` (chunk-oriented), chained after import. |
| `feature/conditional-flow` | `JobExecutionDecider` routing anomalous runs to a manual-review step instead of finalizing. |
| `feature/export-and-scheduling` | `exportPayrollSummary` (Tasklet), REST trigger endpoints, and `@Scheduled` monthly run. |
| `feature/parallel-processing` | *Bonus*: partitioning `calculatePayslips` across threads for a large employee count. |

## Project structure

```
spring-batch-tutorial/
├── .github/
│   ├── workflows/
│   │   ├── ci.yml                              # mvn verify (unit + integration + e2e)
│   │   └── pr-checks.yml                       # commit message lint on the PR range
│   └── PULL_REQUEST_TEMPLATE.md
├── src/
│   ├── main/
│   │   ├── java/com/edgareldy/springbatchtutorial/
│   │   │   ├── SpringBatchTutorialApplication.java
│   │   │   ├── config/
│   │   │   │   ├── OpenApiConfig.java
│   │   │   │   ├── BatchConfig.java              (JobRepository, PlatformTransactionManager for Batch)
│   │   │   │   └── SchedulingConfig.java
│   │   │   ├── entity/
│   │   │   │   ├── Employee.java
│   │   │   │   ├── TimesheetEntry.java
│   │   │   │   ├── PayrollRun.java
│   │   │   │   ├── PayrollRunStatus.java         (enum)
│   │   │   │   ├── Payslip.java
│   │   │   │   └── RejectedTimesheetEntry.java
│   │   │   ├── repository/
│   │   │   │   ├── EmployeeRepository.java
│   │   │   │   ├── TimesheetEntryRepository.java
│   │   │   │   ├── PayrollRunRepository.java
│   │   │   │   ├── PayslipRepository.java
│   │   │   │   └── RejectedTimesheetEntryRepository.java
│   │   │   ├── dto/
│   │   │   │   ├── common/
│   │   │   │   │   ├── ApiResponse.java
│   │   │   │   │   └── PageResponse.java
│   │   │   │   ├── batch/
│   │   │   │   │   ├── JobLaunchResponse.java     (jobExecutionId, status)
│   │   │   │   │   └── PayrollRunResponse.java
│   │   │   │   └── csv/
│   │   │   │       ├── TimesheetCsvRow.java        (raw row read from the import CSV)
│   │   │   │       ├── EmployeeHoursAggregate.java  (per-employee aggregated hours for a period)
│   │   │   │       └── PayrollSummaryRow.java        (row written to the export CSV)
│   │   │   ├── service/
│   │   │   │   ├── PayrollRunService.java            (contract/implementation)
│   │   │   │   ├── PayrollJobLauncherService.java
│   │   │   │   └── impl/
│   │   │   │       ├── PayrollRunServiceImpl.java
│   │   │   │       └── PayrollJobLauncherServiceImpl.java
│   │   │   ├── controller/
│   │   │   │   └── PayrollController.java             (trigger, status, resume, list)
│   │   │   ├── batch/
│   │   │   │   ├── PayrollJobConfig.java               (assembles all Steps into monthlyPayrollJob + payrollFinalizeJob)
│   │   │   │   ├── importstep/
│   │   │   │   │   ├── TimesheetCsvItemReader.java
│   │   │   │   │   ├── TimesheetItemProcessor.java      (validation, employee resolution)
│   │   │   │   │   ├── TimesheetItemWriter.java
│   │   │   │   │   ├── TimesheetSkipListener.java        (records rejected rows)
│   │   │   │   │   └── ImportStepExecutionListener.java
│   │   │   │   ├── aggregationstep/
│   │   │   │   │   └── AggregateHoursTasklet.java        (Tasklet, flags anomalies only - no bulk data in ExecutionContext)
│   │   │   │   ├── decision/
│   │   │   │   │   └── AnomalyReviewDecider.java          (JobExecutionDecider)
│   │   │   │   ├── reviewstep/
│   │   │   │   │   └── FlagForReviewTasklet.java          (Tasklet, sets PayrollRun.status = AWAITING_REVIEW)
│   │   │   │   ├── calculationstep/
│   │   │   │   │   ├── EmployeeHoursItemReader.java       (JpaPagingItemReader)
│   │   │   │   │   ├── PayslipItemProcessor.java          (gross/net pay computation)
│   │   │   │   │   └── PayslipItemWriter.java
│   │   │   │   ├── exportstep/
│   │   │   │   │   └── ExportPayrollSummaryTasklet.java
│   │   │   │   └── partition/
│   │   │   │       └── EmployeePartitioner.java           (bonus: splits employees across worker threads)
│   │   │   └── exception/
│   │   │       ├── ResourceNotFoundException.java
│   │   │       ├── BusinessRuleException.java
│   │   │       ├── ErrorResponse.java
│   │   │       └── GlobalExceptionHandler.java
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-dev.yml
│   │       ├── application-test.yml
│   │       ├── application-prod.yml
│   │       ├── db/migration/
│   │       │   ├── V1__init_schema.sql
│   │       │   └── V2__init_spring_batch_metadata.sql
│   │       └── sample-data/
│   │           └── timesheets-import-sample.csv
│   └── test/
│       └── java/com/edgareldy/springbatchtutorial/
│           ├── unit/                                     (Mockito, pure computations)
│           ├── integration/                              (Testcontainers PostgreSQL, real repositories/steps)
│           └── e2e/                                      (spring-batch-test JobLauncherTestUtils, full job runs)
├── docker-compose.yml
├── Dockerfile
├── pom.xml
└── README.md
```

## Standard response format

Every response (success and error alike) is wrapped in a generic `ApiResponse<T>`.

```java
public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        Instant timestamp
) {
    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, message, data, Instant.now());
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null, Instant.now());
    }
}
```

- List endpoints wrap their content in `ApiResponse<PageResponse<T>>` (`PageResponse` carries `content`, `page`, `size`, `totalElements`, `totalPages`).
- For job-triggering endpoints, `data` holds a `JobLaunchResponse` (execution id, initial status) or a `PayrollRunResponse` (business-level `PayrollRun` state).
- `GlobalExceptionHandler` (`@RestControllerAdvice`) always returns an `ApiResponse<ErrorResponse>` with `success = false` for `ResourceNotFoundException` (404), validation errors (400, field-level detail in `ErrorResponse.fieldErrors`), `BusinessRuleException` (422), and any other exception (500). `ErrorResponse` carries `timestamp`, `status`, `error`, `message`, `path`, and an optional `fieldErrors` list.

## Testing strategy

Every branch from `feature/timesheet-import` onward is expected to ship all three test layers before its Pull Request is opened: no branch is "done" with only unit tests.

| Layer | Tool | What it verifies | Lives in |
|---|---|---|---|
| Unit | JUnit 5 + Mockito | Pure logic that doesn't need a real database or a Batch context: payslip gross/net pay computation, CSV row validation rules, the anomaly-detection threshold - no `JobLauncher`, no Spring context at all | `src/test/.../unit/` |
| Integration | `@SpringBatchTest` + Testcontainers (real PostgreSQL) | Individual steps run in isolation via `JobLauncherTestUtils.launchStep("importTimesheets", jobParameters)`: real skip behavior against a real CSV, rejection rows actually persisted, `calculatePayslips` writes the expected rows, repository constraints. `@StepScope` beans that read `JobParameters` (like `ExportPayrollSummaryTasklet`) are exercised with the `StepScopeTestExecutionListener` Spring Batch registers automatically under `@SpringBatchTest` | `src/test/.../integration/` |
| E2E | `@SpringBootTest` + `@SpringBatchTest` (`JobLauncherTestUtils.launchJob(jobParameters)`) | Complete `monthlyPayrollJob` runs end to end against a real database: the happy path (import → aggregate → calculate → export), the anomaly path (import → aggregate → flagged for review → `payrollFinalizeJob` on resume), and restart semantics - relaunching a `FAILED` execution with the same identifying `JobParameters` resumes from the failed step rather than re-running completed ones | `src/test/.../e2e/` |

`.github/workflows/ci.yml` runs `mvn verify` across the whole suite, so all three layers execute on every push/PR; a branch's checklist is not complete until the full suite passes locally.

## Git commit convention

All commits follow **Conventional Commits**, checked in CI on every Pull Request (`.github/workflows/pr-checks.yml`).

### Format

```
<type>(<scope>): <short summary>

<body - what was done and why, one sentence per file touched>

<footer - refs, breaking changes>
```

### Types

| Type | When to use |
|---|---|
| `feat` | New feature or file |
| `fix` | Bug fix |
| `refactor` | Code change that is neither a bug fix nor a feature |
| `test` | Adding or updating tests |
| `docs` | Documentation only |
| `chore` | Tooling, config, deps |
| `ci` | CI pipeline/workflow changes |
| `style` | Formatting, no logic change |
| `perf` | Performance improvement |

### Atomic commit rule

> **One commit per file added or modified.** Never group unrelated files in a single commit.

**Good:**
```
feat(timesheet-import): add TimesheetEntry entity

- Defines the TimesheetEntry JPA entity with employee_id, work_date, hours_worked.
```

**Bad:**
```
feat: add timesheet import feature with entity, reader, processor and writer
```

### Tooling

- **CI** (`.github/workflows/pr-checks.yml`): validates every commit message on the PR range (`git log <base>..<head>`) against the Conventional Commits pattern
- `.github/PULL_REQUEST_TEMPLATE.md`: branch name, task checklist, commit summary, test checklist (unit/integration/E2E pass, `mvn verify` green), code review checklist (no business logic in controllers, every job step has a listener, endpoints return `ApiResponse`, atomic commits)

## feature/core-architecture

Technical foundation: project scaffolding, Spring Batch/PostgreSQL setup, Docker, CI. No business logic yet.

### Tasks

- [x] Initialize the project (Maven, Java 17, Spring Boot 4.1.x, `groupId com.edgareldy`, `artifactId spring-batch-tutorial`)
- [x] `.gitignore` (Maven `target/`, IDE files, `.env`)
- [x] Dependencies: `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-batch`, `spring-boot-starter-validation`, `spring-boot-starter-actuator`, `flyway-core`, `flyway-database-postgresql` (Flyway 9+ split PostgreSQL support into its own module), `postgresql`, `lombok`, `springdoc-openapi-starter-webmvc-ui`
- [x] Test dependencies: `spring-boot-starter-test`, `spring-batch-test`, `testcontainers`
- [x] Package layout above, under `com.edgareldy.springbatchtutorial` - `entity`/`repository`/`batch` stay empty until later branches populate them
- [x] `BatchConfig`: `JobRepository` and `PlatformTransactionManager` explicitly configured against the same PostgreSQL database as the business schema
- [x] Flyway script `V1__init_schema.sql` (employees, timesheet_entries, payroll_runs, payslips, rejected_timesheet_entries)
- [x] Flyway script `V2__init_spring_batch_metadata.sql` (Spring Batch's official PostgreSQL schema)
- [x] `application.yml`: `spring.batch.job.enabled=false`
- [x] `GlobalExceptionHandler`, `ApiResponse<T>`, `PageResponse<T>`
- [x] `Employee` seed data (a small fixed set of employees, inserted via Flyway, since this tutorial doesn't build employee-management CRUD)
- [x] Sample file `sample-data/timesheets-import-sample.csv` (deliberately including a few invalid rows and one employee with an anomalously high hour count, to exercise later branches)
- [x] `docker-compose.yml` (app + PostgreSQL), `Dockerfile` (multi-stage)
- [x] `.github/workflows/ci.yml`: `mvn verify`
- [x] `.github/workflows/pr-checks.yml`: Conventional Commits check on the PR range
- [x] `.github/PULL_REQUEST_TEMPLATE.md`
- [x] Unit tests: `GlobalExceptionHandler` maps each exception type to the right status, always inside an `ApiResponse` with `success = false`
- [x] E2E test: `GET /actuator/health` returns 200

## feature/timesheet-import

Chunk-oriented step. First step of `monthlyPayrollJob`, runnable standalone for testing.

### Endpoints

| Method | URL | Description |
|---|---|---|
| POST | `/api/v1/payroll/runs` | Creates a `PayrollRun` for a period and launches `monthlyPayrollJob` (starting with `importTimesheets`) |
| GET | `/api/v1/payroll/runs/{id}` | Looks up a `PayrollRun`'s current status |

### Tasks

- [x] `TimesheetEntry`, `PayrollRun`, `PayrollRunStatus`, `RejectedTimesheetEntry` entities and repositories (plus `Employee`/`EmployeeRepository`, required by `TimesheetItemProcessor`)
- [x] `PayrollJobConfig`: defines `monthlyPayrollJob` with its first `Step`, `importTimesheets` (chunk size configurable, e.g. 100)
- [x] `TimesheetCsvItemReaderConfig` (`FlatFileItemReader<TimesheetCsvRow>`)
- [x] `TimesheetItemProcessor` (`ItemProcessor<TimesheetCsvRow, TimesheetEntry>`): validates `hours_worked` (> 0, ≤ 24), resolves `employee_id` from the CSV's email column via `EmployeeRepository`, throws a dedicated exception for an unknown employee to trigger a skip
- [x] `TimesheetItemWriter` (`ItemWriter<TimesheetEntry>`)
- [x] `TimesheetSkipListener` (`SkipListener<TimesheetCsvRow, TimesheetEntry>`): persists each rejected row into `rejected_timesheet_entries`, linked to the current `PayrollRun`
- [x] `.faultTolerant().skipLimit(...).skip(InvalidTimesheetRowException.class)` on the step
- [x] `ImportStepExecutionListener`: logs a read/written/skipped summary at step completion
- [x] `PayrollController`/`PayrollJobLauncherService`: creates a `PayrollRun` row (`status = STARTED`), then launches `monthlyPayrollJob` with `payrollRunId` + `period` as unique `JobParameters`
- [x] Unit tests: `TimesheetItemProcessor`'s validation rules as pure logic (mocked `EmployeeRepository`)
- [x] Integration tests (Testcontainers): `JobLauncherTestUtils.launchStep("importTimesheets", ...)` against the sample CSV - valid rows persisted, invalid rows land in `rejected_timesheet_entries` with the right reason
- [x] E2E test: launching `monthlyPayrollJob` with only this step wired stops cleanly after `importTimesheets` (later branches extend the flow)

## feature/payroll-calculation

Adds two steps to `monthlyPayrollJob`, chained after `importTimesheets`: one Tasklet, one chunk-oriented step.

### Tasks

- [x] `AggregateHoursTasklet` (Tasklet): a single grouped SQL query (`SUM(hours_worked) GROUP BY employee_id`) over the current run's `timesheet_entries`, exposed as a repository method (`TimesheetEntryRepository.aggregateHoursByEmployee(payrollRunId)`) reused by both this tasklet and `EmployeeHoursItemReader` below
- [x] Anomaly detection inside `AggregateHoursTasklet`: any employee whose aggregated hours exceed a configurable threshold (e.g. 300h/month) sets a boolean `hasAnomalies` flag (and the offending count) in the `StepExecution`'s `ExecutionContext` - the *only* thing this tasklet writes there, since `ExecutionContext` is meant for small metadata, not for carrying bulk aggregated rows between steps
- [x] `EmployeeHoursItemReader` (`JpaPagingItemReader`, or a custom `ItemReader` wrapping the same grouped repository query as the tasklet above): re-runs the aggregation as a paginated read, one page per chunk, rather than trying to pass the full result set through the `ExecutionContext` - the tasklet and this reader both call the same repository method, so the aggregation logic itself is defined once
- [x] `PayslipItemProcessor` (`ItemProcessor<EmployeeHoursAggregate, Payslip>`): computes `gross_pay = total_hours * hourly_rate` with a 1.5× multiplier on hours beyond 160/month, `deductions` as a flat percentage, `net_pay = gross_pay - deductions`, as a pure, independently testable calculation method
- [x] `PayslipItemWriter` (`ItemWriter<Payslip>`)
- [x] `PayrollJobConfig` updated: `importTimesheets` → `aggregateHoursPerEmployee` → `calculatePayslips`, in that order
- [x] Unit tests: gross/net pay computation as a pure function, including the overtime multiplier boundary (exactly 160h, 160.01h)
- [x] Integration tests (Testcontainers): `aggregateHoursPerEmployee` produces correct per-employee totals against real timesheet data; `calculatePayslips` persists the expected `Payslip` rows
- [x] E2E test: running the three chained steps against the sample CSV produces the expected payslips for every valid employee

## feature/conditional-flow

Inserts a `JobExecutionDecider` between `aggregateHoursPerEmployee` and `calculatePayslips`.

### Endpoints

| Method | URL | Description |
|---|---|---|
| POST | `/api/v1/payroll/runs/{id}/resume` | For a `PayrollRun` in `AWAITING_REVIEW`, launches `payrollFinalizeJob` (calculation + export only) |

### Tasks

- [x] `AnomalyReviewDecider` (`JobExecutionDecider`): reads the `hasAnomalies` flag set by `AggregateHoursTasklet`, returns a distinct `FlowExecutionStatus` (`REVIEW_REQUIRED` vs. `PROCEED`)
- [x] `FlagForReviewTasklet`: on the `REVIEW_REQUIRED` path, sets `PayrollRun.status = AWAITING_REVIEW` and ends the job flow (the `JobExecution` itself still completes normally - see [Why these library choices](#why-these-library-choices))
- [x] `PayrollJobConfig` updated: `.next(aggregateHoursPerEmployee).next(anomalyReviewDecider).on("REVIEW_REQUIRED").to(flagForReviewStep).from(anomalyReviewDecider).on("PROCEED").to(calculatePayslips)...`
- [x] Second `Job` bean, `payrollFinalizeJob`, reusing the existing `calculatePayslips` step (and, once available, `exportPayrollSummary`), launched by `POST /api/v1/payroll/runs/{id}/resume` after a human has reviewed the anomaly out-of-band; sets `PayrollRun.status` back to `STARTED` before launching
- [x] Business rule: `resume` on a `PayrollRun` not currently `AWAITING_REVIEW` returns a `BusinessRuleException` (422)
- [x] Unit tests: `AnomalyReviewDecider` returns the right status for both a clean and an anomalous `ExecutionContext`
- [x] Integration tests (Testcontainers): a run seeded with an anomalous employee stops at `AWAITING_REVIEW` with no `Payslip` rows written yet
- [x] E2E test: full anomaly path - import (with one employee over the threshold) → aggregate → flagged for review → `resume` → `calculatePayslips` runs and produces the missing payslips

## feature/export-and-scheduling

Adds the final step and the ways to trigger the whole job.

### Endpoints

| Method | URL | Description |
|---|---|---|
| GET | `/api/v1/payroll/runs` | Paginated list of past `PayrollRun`s |

### Tasks

- [x] `ExportPayrollSummaryTasklet` (Tasklet): one query joining `Payslip`/`Employee` for the current run, one `FlatFileItemWriter`-backed CSV write (`payroll-summary-<year>-<month>.csv`)
- [x] `PayrollJobConfig` updated: `calculatePayslips` → `exportPayrollSummary` → `PayrollRun.status = COMPLETED`, `completed_at` set
- [x] `SchedulingConfig` (`@EnableScheduling`): a `@Scheduled` cron trigger (e.g. `0 0 3 1 * *`, first day of the month) launching `monthlyPayrollJob` for the previous period automatically
- [x] `GET /api/v1/payroll/runs`: paginated listing backed by `PayrollRunRepository`, for an admin to see run history without querying `BATCH_JOB_EXECUTION` directly
- [x] Unit tests: the cron expression triggers at the expected instants (tested in isolation, not by waiting a real month)
- [x] Integration tests (Testcontainers): `exportPayrollSummary` produces a CSV with the exact expected rows for a known set of payslips
- [x] E2E test: the complete happy path from `POST /api/v1/payroll/runs` to a `COMPLETED` `PayrollRun` with a generated summary file, verified end to end

## feature/parallel-processing (bonus)

Demonstrates scaling `calculatePayslips` to a large employee count.

### Tasks

- [ ] `EmployeePartitioner` (`Partitioner`): splits employees into N partitions (by id range)
- [ ] Reconfigure `calculatePayslips` as a master/worker `Step` (`partitionStep`), with a dedicated `TaskExecutor` (`ThreadPoolTaskExecutor`) running partitions in parallel
- [ ] Execution time comparison (before/after partitioning), documented in the branch's README, against a large generated employee/timesheet dataset (e.g. 10,000 employees)
- [ ] Documented note on concurrent database write safety (per-partition transactions, no conflicting writes on the same `Payslip` rows)

### Execution time comparison

Measured with `CalculatePayslipsPartitioningBenchmark`
(`src/test/java/com/edgareldy/springbatchtutorial/integration/CalculatePayslipsPartitioningBenchmark.java`),
a one-off benchmark class deliberately named so Maven Surefire's default
`Test*`/`*Test`/`*Tests`/`*TestCase` include patterns never pick it up: it
never runs as part of `mvn verify` or CI, only on demand with
`mvn test -Dtest=CalculatePayslipsPartitioningBenchmark`, since wall-clock
timing assertions would be flaky on a shared CI runner. It generates 10,000
`Employee` rows and 40,000 `TimesheetEntry` rows (4 each, all below the
overtime threshold so the comparison measures partitioning, not overtime
math), then runs `calculatePayslips` twice against that same dataset: once
with `gridSize(1)` on a `SyncTaskExecutor` (single partition, running on the
calling thread, the honest sequential-equivalent baseline, since the actual
pre-partitioning `calculatePayslips` `Step` no longer exists to benchmark
directly), and once with `gridSize(8)` on a dedicated 8-thread
`ThreadPoolTaskExecutor`. Elapsed time is read from the real
`StepExecution.getStartTime()`/`getEndTime()` across every partition the run
produced (not a coarse wrapper around the launch call).

| Variant | `StepExecution` elapsed | Payslips written |
|---|---|---|
| Sequential (`gridSize=1`) | 25,088 ms (~25.1 s) | 10,000 |
| Parallel (`gridSize=8`) | 8,651 ms (~8.65 s) | 10,000 |
| **Speedup** | **2.90x** | |

The parallel run's logs confirm a genuine 8-way fan-out (all 8
`calculatePayslipsWorker:partitionN` executions completed within ~200ms of
each other, each on a disjoint, near-evenly-sized employee id range from
`EmployeePartitioner`), not one worker doing all the work while the rest sat
idle. The speedup is real but well short of 8x, which is the expected shape
for this kind of workload rather than a red flag: `spring.datasource.hikari.*`
is never overridden in this project, so the connection pool defaults to a
maximum of 10 connections, shared by all 8 worker threads plus the
`JobRepository`'s own step-bookkeeping writes (`BATCH_STEP_EXECUTION`
updates on every chunk commit) - a pool sized barely above the worker count
is a plausible ceiling on how close to linear the scaling can get. Bumping
`maximum-pool-size` well above 8 and re-running the benchmark is a natural
follow-up experiment for a reader who wants to push the comparison further,
deliberately left as an exercise rather than folded into this branch's
default configuration.

### Concurrent database write safety

`EmployeePartitioner` splits the `employees` id space into contiguous,
non-overlapping ranges (see its own Javadoc for the exact boundary math), so
by construction no two worker partitions ever process the same employee: two
threads can never compute or write a `Payslip` for the same `employee_id` at
the same time, so there is no read-modify-write race on the same row to
guard against, no need for pessimistic/optimistic locking, and no risk of one
partition's commit overwriting another's. Each `calculatePayslipsWorker`
partition also runs its own chunk-scoped transaction via the shared
`PlatformTransactionManager` (the same transaction manager every other
chunk-oriented step in this project already uses), so a failure in one
partition rolls back only that partition's own uncommitted chunk, never
another partition's already-committed work. The only shared, genuinely
concurrent resource across partitions is the database connection pool
itself (see the execution time comparison above), a throughput/latency
concern, not a correctness one: HikariCP hands out and returns connections
safely under concurrent use, so contention there can only slow partitions
down, never corrupt data.

## Order of work

1. `feature/core-architecture` → Pull Request to `develop`
2. `feature/timesheet-import` (depends on `core-architecture`) → Pull Request to `develop`
3. `feature/payroll-calculation` (depends on `timesheet-import`) → Pull Request to `develop`
4. `feature/conditional-flow` (depends on `payroll-calculation`) → Pull Request to `develop`
5. `feature/export-and-scheduling` (depends on `conditional-flow`) → Pull Request to `develop`
6. `feature/parallel-processing` (bonus, depends on `payroll-calculation`) → Pull Request to `develop`
7. `release/0.1.0` → `master`, tagged, once everything above is merged into `develop` and tested

## Code conventions

- Root package: `com.edgareldy.springbatchtutorial`
- DTOs: Java `record` types
- **Contract/implementation services**: interface at the root of `service/`, implementation in `service/impl/`
- Every REST endpoint returns an `ApiResponse<T>`
- Every `Job`/`Step` has a unique, explicit name (`monthlyPayrollJob`, `importTimesheets`, `aggregateHoursPerEmployee`, ...), never a generic one
- Every job run receives `JobParameters` that make it unique (`payrollRunId`, `period`), to avoid instance collisions in the `JobRepository`
- Every row rejected during import is **tracked** (`rejected_timesheet_entries`), never silently dropped
- Business-level state (`PayrollRun.status`) is always kept distinct from Spring Batch's own `JobExecution.status` - the two answer different questions and are never conflated in code or in the API responses

## Concepts covered

- Spring Batch architecture: `Job`, `Step`, chunk-oriented processing (`ItemReader`/`ItemProcessor`/`ItemWriter`)
- `Tasklet` steps, and when to prefer them over chunk-oriented processing
- Late-binding of `JobParameters` into `@StepScope` beans (`@Value("#{jobParameters['...']}")`)
- Conditional job flow with `JobExecutionDecider`, distinct from failing a step
- Reading and writing flat files (CSV) with `FlatFileItemReader`/`FlatFileItemWriter`
- Fault tolerance: skip, listeners (`SkipListener`, step/job execution listeners)
- `JobRepository`, `JobParameters`, job instance/execution management, restart semantics
- Triggering jobs via REST API (`JobLauncher`) and via scheduling (`@Scheduled`)
- Partitioning and parallel execution of a `Step` (bonus)
- Spring Batch metadata schema on PostgreSQL
- Testing Batch jobs at three layers (unit, integration, e2e) with `spring-batch-test`
- Generic `ApiResponse<T>` DTO, contract/implementation pattern
- git-flow branching model and Conventional Commits
- Containerization (Docker, docker-compose)
- Continuous integration (GitHub Actions)

## How to follow this tutorial

1. Clone the repository and check out `develop`
2. `git flow feature start core-architecture`, follow its task checklist, `git flow feature finish core-architecture`
3. Continue with `timesheet-import`, `payroll-calculation`, `conditional-flow`, `export-and-scheduling` in that order, each via `git flow feature start/finish`
4. Open a Pull Request to `develop` at the end of each branch (`git flow feature finish` merges locally; push and open the PR before finishing if review is required)
5. Run the project with `docker-compose up`, then trigger a run via `POST /api/v1/payroll/runs` with the `sample-data/timesheets-import-sample.csv` file
6. Browse Swagger UI at `http://localhost:8080/swagger-ui.html`
