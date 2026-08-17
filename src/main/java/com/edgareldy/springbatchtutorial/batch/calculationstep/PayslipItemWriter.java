package com.edgareldy.springbatchtutorial.batch.calculationstep;

import com.edgareldy.springbatchtutorial.entity.Payslip;
import com.edgareldy.springbatchtutorial.repository.PayslipRepository;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.stereotype.Component;

/**
 * Persists a chunk of {@link Payslip} rows computed by
 * {@code PayslipItemProcessor} during {@code calculatePayslips}.
 * <p>
 * Created by Edgar Muhamyangabo on 8/17/26
 * Author : Edgar Muhamyangabo
 * Date : 8/17/26
 * Project : spring-batch-tutorial
 */
@Component
public class PayslipItemWriter implements ItemWriter<Payslip> {

    private final PayslipRepository payslipRepository;

    public PayslipItemWriter(PayslipRepository payslipRepository) {
        this.payslipRepository = payslipRepository;
    }

    @Override
    public void write(Chunk<? extends Payslip> chunk) {
        payslipRepository.saveAll(chunk.getItems());
    }
}
