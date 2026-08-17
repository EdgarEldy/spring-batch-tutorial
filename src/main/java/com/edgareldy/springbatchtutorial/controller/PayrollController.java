package com.edgareldy.springbatchtutorial.controller;

import com.edgareldy.springbatchtutorial.dto.batch.CreatePayrollRunRequest;
import com.edgareldy.springbatchtutorial.dto.batch.PayrollRunResponse;
import com.edgareldy.springbatchtutorial.dto.common.ApiResponse;
import com.edgareldy.springbatchtutorial.entity.PayrollRun;
import com.edgareldy.springbatchtutorial.service.PayrollJobLauncherService;
import com.edgareldy.springbatchtutorial.service.PayrollRunService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the payroll run lifecycle: creating a run and launching
 * {@code monthlyPayrollJob}, looking up a run's current business status,
 * and resuming a run that was flagged {@code AWAITING_REVIEW} by launching
 * {@code payrollFinalizeJob} once a human has reviewed it out-of-band.
 * {@code POST /api/v1/payroll/runs} and {@code POST /api/v1/payroll/runs/{id}/resume}
 * both return {@code ApiResponse<PayrollRunResponse>} rather than
 * {@code ApiResponse<JobLaunchResponse>}: the endpoints' primary resource
 * is the {@code PayrollRun} they act on (REST POST-creates/updates-a-resource
 * semantics), and the caller needs the run's business state to poll
 * {@code GET /api/v1/payroll/runs/{id}} afterward - a bare
 * {@code JobExecution} id/status wouldn't give it that.
 * <p>
 * Created by Edgar Muhamyangabo on 8/17/26
 * Author : Edgar Muhamyangabo
 * Date : 8/17/26
 * Project : spring-batch-tutorial
 */
@RestController
@RequestMapping("/api/v1/payroll/runs")
public class PayrollController {

    private final PayrollRunService payrollRunService;
    private final PayrollJobLauncherService payrollJobLauncherService;

    public PayrollController(
            PayrollRunService payrollRunService,
            PayrollJobLauncherService payrollJobLauncherService) {
        this.payrollRunService = payrollRunService;
        this.payrollJobLauncherService = payrollJobLauncherService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PayrollRunResponse>> createRun(
            @Valid @RequestBody CreatePayrollRunRequest request) {
        PayrollRun payrollRun = payrollRunService.createRun(request.periodMonth(), request.periodYear());
        payrollJobLauncherService.launchMonthlyPayrollJob(payrollRun);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(toResponse(payrollRun), "Payroll run created and monthlyPayrollJob launched"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PayrollRunResponse>> getRun(@PathVariable Long id) {
        PayrollRun payrollRun = payrollRunService.getRun(id);
        return ResponseEntity.ok(ApiResponse.success(toResponse(payrollRun), "Payroll run retrieved"));
    }

    @PostMapping("/{id}/resume")
    public ResponseEntity<ApiResponse<PayrollRunResponse>> resumeRun(@PathVariable Long id) {
        PayrollRun payrollRun = payrollRunService.resumeRun(id);
        payrollJobLauncherService.launchPayrollFinalizeJob(payrollRun);
        return ResponseEntity.ok(
                ApiResponse.success(toResponse(payrollRun), "Payroll run resumed and payrollFinalizeJob launched"));
    }

    private PayrollRunResponse toResponse(PayrollRun payrollRun) {
        return new PayrollRunResponse(
                payrollRun.getId(),
                payrollRun.getPeriodMonth(),
                payrollRun.getPeriodYear(),
                payrollRun.getStatus(),
                payrollRun.getStartedAt(),
                payrollRun.getCompletedAt());
    }
}
