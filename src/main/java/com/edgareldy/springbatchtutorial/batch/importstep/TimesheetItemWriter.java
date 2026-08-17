package com.edgareldy.springbatchtutorial.batch.importstep;

import com.edgareldy.springbatchtutorial.entity.TimesheetEntry;
import com.edgareldy.springbatchtutorial.repository.TimesheetEntryRepository;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.stereotype.Component;

/**
 * Persists a chunk of validated {@link TimesheetEntry} rows produced by
 * {@code TimesheetItemProcessor} during {@code importTimesheets}.
 * <p>
 * Created by Edgar Muhamyangabo on 8/17/26
 * Author : Edgar Muhamyangabo
 * Date : 8/17/26
 * Project : spring-batch-tutorial
 */
@Component
public class TimesheetItemWriter implements ItemWriter<TimesheetEntry> {

    private final TimesheetEntryRepository timesheetEntryRepository;

    public TimesheetItemWriter(TimesheetEntryRepository timesheetEntryRepository) {
        this.timesheetEntryRepository = timesheetEntryRepository;
    }

    @Override
    public void write(Chunk<? extends TimesheetEntry> chunk) {
        timesheetEntryRepository.saveAll(chunk.getItems());
    }
}
