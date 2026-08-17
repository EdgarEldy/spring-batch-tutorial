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
 * {@code monthlyPayrollJob}, and looking up a run's current business
 * status. {@code POST /api/v1/payroll/runs} returns
 * {@code ApiResponse<PayrollRunResponse>} rather than
 * {@code ApiResponse<JobLaunchResponse>}: the endpoint's primary resource
 * is the {@code PayrollRun} it creates (REST POST-creates-a-resource
 * semantics), and the caller needs the run's {@code id} to poll
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
