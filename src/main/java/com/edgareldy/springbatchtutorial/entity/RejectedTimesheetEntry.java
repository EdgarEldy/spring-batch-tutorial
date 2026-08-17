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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Maps the {@code rejected_timesheet_entries} table: a permanent trace of a
 * CSV row that {@code importTimesheets} skipped (invalid hours or an
 * unknown employee email), written by {@code TimesheetSkipListener} instead
 * of letting the row silently disappear.
 * <p>
 * Created by Edgar Muhamyangabo on 8/17/26
 * Author : Edgar Muhamyangabo
 * Date : 8/17/26
 * Project : spring-batch-tutorial
 */
@Entity
@Table(name = "rejected_timesheet_entries")
@Getter
@Setter
@NoArgsConstructor
public class RejectedTimesheetEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "raw_line", nullable = false, length = 500)
    private String rawLine;

    @Column(name = "reason", nullable = false, length = 255)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payroll_run_id", nullable = false)
    private PayrollRun payrollRun;
}
