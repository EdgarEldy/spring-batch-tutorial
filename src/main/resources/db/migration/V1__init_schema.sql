CREATE TABLE employees (
    id            BIGSERIAL PRIMARY KEY,
    first_name    VARCHAR(100)   NOT NULL,
    last_name     VARCHAR(100)   NOT NULL,
    email         VARCHAR(255)   NOT NULL UNIQUE,
    department    VARCHAR(100)   NOT NULL,
    hourly_rate   NUMERIC(10,2)  NOT NULL CHECK (hourly_rate > 0),
    bank_account  VARCHAR(34)    NOT NULL
);

CREATE TABLE timesheet_entries (
    id            BIGSERIAL PRIMARY KEY,
    employee_id   BIGINT         NOT NULL REFERENCES employees (id),
    work_date     DATE           NOT NULL,
    hours_worked  NUMERIC(4,2)   NOT NULL CHECK (hours_worked > 0 AND hours_worked <= 24),
    source_file   VARCHAR(255)   NOT NULL,
    imported_at   TIMESTAMP      NOT NULL
);

CREATE INDEX idx_timesheet_entries_employee_id ON timesheet_entries (employee_id);

CREATE TABLE payroll_runs (
    id            BIGSERIAL PRIMARY KEY,
    period_month  INT            NOT NULL CHECK (period_month BETWEEN 1 AND 12),
    period_year   INT            NOT NULL,
    status        VARCHAR(30)    NOT NULL CHECK (status IN ('STARTED', 'AWAITING_REVIEW', 'COMPLETED', 'FAILED')),
    started_at    TIMESTAMP      NOT NULL,
    completed_at  TIMESTAMP
);

CREATE TABLE payslips (
    id             BIGSERIAL PRIMARY KEY,
    payroll_run_id BIGINT        NOT NULL REFERENCES payroll_runs (id),
    employee_id    BIGINT        NOT NULL REFERENCES employees (id),
    total_hours    NUMERIC(6,2)  NOT NULL,
    gross_pay      NUMERIC(10,2) NOT NULL,
    deductions     NUMERIC(10,2) NOT NULL,
    net_pay        NUMERIC(10,2) NOT NULL,
    generated_at   TIMESTAMP     NOT NULL
);

CREATE INDEX idx_payslips_payroll_run_id ON payslips (payroll_run_id);

CREATE TABLE rejected_timesheet_entries (
    id             BIGSERIAL PRIMARY KEY,
    raw_line       VARCHAR(500)  NOT NULL,
    reason         VARCHAR(255)  NOT NULL,
    payroll_run_id BIGINT        NOT NULL REFERENCES payroll_runs (id)
);

CREATE INDEX idx_rejected_timesheet_entries_payroll_run_id ON rejected_timesheet_entries (payroll_run_id);

-- Fixed seed set of employees, this tutorial does not build employee-management CRUD.
INSERT INTO employees (first_name, last_name, email, department, hourly_rate, bank_account) VALUES
    ('Alice', 'Martin', 'alice.martin@example.com', 'Engineering', 45.00, 'FR7630006000011234567890189'),
    ('Bob', 'Dupont', 'bob.dupont@example.com', 'Engineering', 40.00, 'FR7630006000011234567890190'),
    ('Carla', 'Silva', 'carla.silva@example.com', 'Sales', 35.00, 'FR7630006000011234567890191'),
    ('David', 'Chen', 'david.chen@example.com', 'Sales', 38.00, 'FR7630006000011234567890192'),
    ('Emma', 'Rossi', 'emma.rossi@example.com', 'HR', 42.00, 'FR7630006000011234567890193');
