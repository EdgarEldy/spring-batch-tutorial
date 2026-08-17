package com.edgareldy.springbatchtutorial.repository;

import com.edgareldy.springbatchtutorial.entity.TimesheetEntry;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link TimesheetEntry}, used by
 * {@code TimesheetItemWriter} to persist validated rows during
 * {@code importTimesheets}. The per-employee hour aggregation query used by
 * {@code aggregateHoursPerEmployee} (feature/payroll-calculation) is added
 * here once that branch needs it.
 * <p>
 * Created by Edgar Muhamyangabo on 8/17/26
 * Author : Edgar Muhamyangabo
 * Date : 8/17/26
 * Project : spring-batch-tutorial
 */
public interface TimesheetEntryRepository extends JpaRepository<TimesheetEntry, Long> {
}
