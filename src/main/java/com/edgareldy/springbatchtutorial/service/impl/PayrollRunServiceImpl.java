package com.edgareldy.springbatchtutorial.service.impl;

import com.edgareldy.springbatchtutorial.entity.PayrollRun;
import com.edgareldy.springbatchtutorial.entity.PayrollRunStatus;
import com.edgareldy.springbatchtutorial.exception.ResourceNotFoundException;
import com.edgareldy.springbatchtutorial.repository.PayrollRunRepository;
import com.edgareldy.springbatchtutorial.service.PayrollRunService;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

/**
 * Default {@link PayrollRunService} implementation, backed by
 * {@link PayrollRunRepository}.
 * <p>
 * Created by Edgar Muhamyangabo on 8/17/26
 * Author : Edgar Muhamyangabo
 * Date : 8/17/26
 * Project : spring-batch-tutorial
 */
@Service
public class PayrollRunServiceImpl implements PayrollRunService {

    private final PayrollRunRepository payrollRunRepository;

    public PayrollRunServiceImpl(PayrollRunRepository payrollRunRepository) {
        this.payrollRunRepository = payrollRunRepository;
    }

    @Override
    public PayrollRun createRun(int periodMonth, int periodYear) {
        PayrollRun run = new PayrollRun();
        run.setPeriodMonth(periodMonth);
        run.setPeriodYear(periodYear);
        run.setStatus(PayrollRunStatus.STARTED);
        run.setStartedAt(LocalDateTime.now());
        return payrollRunRepository.save(run);
    }

    @Override
    public PayrollRun getRun(Long id) {
        return payrollRunRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PayrollRun not found: " + id));
    }
}
