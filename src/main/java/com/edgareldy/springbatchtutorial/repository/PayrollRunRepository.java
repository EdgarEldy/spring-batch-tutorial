package com.edgareldy.springbatchtutorial.repository;

import com.edgareldy.springbatchtutorial.entity.PayrollRun;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link PayrollRun}, used by
 * {@code PayrollRunService} to create, look up, and paginate payroll run
 * rows (the inherited {@code findAll(Pageable)} backs
 * {@code PayrollRunService.listRuns} for {@code GET /api/v1/payroll/runs}),
 * and by {@code TimesheetSkipListener} to attach rejected rows to the run in
 * progress.
 * <p>
 * Created by Edgar Muhamyangabo on 8/17/26
 * Author : Edgar Muhamyangabo
 * Date : 8/17/26
 * Project : spring-batch-tutorial
 */
public interface PayrollRunRepository extends JpaRepository<PayrollRun, Long> {
}
