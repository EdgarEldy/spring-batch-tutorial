package com.edgareldy.springbatchtutorial.repository;

import com.edgareldy.springbatchtutorial.entity.RejectedTimesheetEntry;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link RejectedTimesheetEntry}, used by
 * {@code TimesheetSkipListener} to persist every row skipped by
 * {@code importTimesheets} rather than letting it silently disappear.
 * <p>
 * Created by Edgar Muhamyangabo on 8/17/26
 * Author : Edgar Muhamyangabo
 * Date : 8/17/26
 * Project : spring-batch-tutorial
 */
public interface RejectedTimesheetEntryRepository extends JpaRepository<RejectedTimesheetEntry, Long> {
}
