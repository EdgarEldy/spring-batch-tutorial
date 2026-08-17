package com.edgareldy.springbatchtutorial.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Maps the {@code timesheet_entries} table: a single validated clock-in row
 * (one employee, one work date, one hours-worked value) persisted by the
 * {@code importTimesheets} step once {@code TimesheetItemProcessor} has
 * resolved the employee and validated the hours. Deliberately carries no
 * {@code payroll_run_id} - a run's totals are computed later by aggregating
 * over a date range, not by tagging entries at import time.
 * <p>
 * Created by Edgar Muhamyangabo on 8/17/26
 * Author : Edgar Muhamyangabo
 * Date : 8/17/26
 * Project : spring-batch-tutorial
 */
@Entity
@Table(name = "timesheet_entries")
@Getter
@Setter
@NoArgsConstructor
public class TimesheetEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "hours_worked", nullable = false, precision = 4, scale = 2)
    private BigDecimal hoursWorked;

    @Column(name = "source_file", nullable = false, length = 255)
    private String sourceFile;

    @Column(name = "imported_at", nullable = false)
    private LocalDateTime importedAt;
}
