package com.edgareldy.springbatchtutorial.batch.partition;

import com.edgareldy.springbatchtutorial.repository.EmployeeRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.batch.core.partition.Partitioner;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.stereotype.Component;

/**
 * Splits the {@code employees} id space into up to {@code gridSize}
 * contiguous, non-overlapping id ranges for {@code calculatePayslips}'s
 * master/worker partitioning, each range published as {@code minId}/
 * {@code maxId} in its own worker {@code ExecutionContext} (read back by
 * {@code EmployeeHoursItemReader} via {@code stepExecutionContext}). Only
 * {@link EmployeeRepository#findMinId()}/{@link EmployeeRepository#findMaxId()}
 * are queried, never the employee rows themselves, so the partitioning
 * decision stays O(1) regardless of employee count.
 * <p>
 * Range size is {@code ceil((maxId - minId + 1) / gridSize)}, walked from
 * {@code minId} to {@code maxId} so ranges are always contiguous and never
 * overlap; the last range is simply whatever remains, so no boundary is
 * hardcoded. When there are fewer distinct ids than {@code gridSize}, fewer
 * (smaller) partitions are produced instead of forcing empty ones - this
 * intentionally works the same whether {@code employees} has 5 rows (the
 * seeded sample data) or 10,000.
 * <p>
 * Created by Edgar Muhamyangabo on 8/18/26
 * Author : Edgar Muhamyangabo
 * Date : 8/18/26
 * Project : spring-batch-tutorial
 */
@Component
public class EmployeePartitioner implements Partitioner {

    static final String MIN_ID_KEY = "minId";
    static final String MAX_ID_KEY = "maxId";
    private static final String PARTITION_KEY_PREFIX = "partition";

    private final EmployeeRepository employeeRepository;

    public EmployeePartitioner(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
        Long minId = employeeRepository.findMinId();
        Long maxId = employeeRepository.findMaxId();
        Map<String, ExecutionContext> partitions = new LinkedHashMap<>();

        if (minId == null || maxId == null) {
            // No employees at all: PartitionStep treats an empty partition map as an
            // error, so hand the worker a single empty (minId > maxId) range instead -
            // it runs, legitimately reads zero items, and the step still completes.
            partitions.put(PARTITION_KEY_PREFIX + 0, rangeContext(1L, 0L));
            return partitions;
        }

        long targetRangeSize = (long) Math.ceil((maxId - minId + 1) / (double) gridSize);
        long start = minId;
        int partitionNumber = 0;
        while (start <= maxId) {
            long end = Math.min(start + targetRangeSize - 1, maxId);
            partitions.put(PARTITION_KEY_PREFIX + partitionNumber, rangeContext(start, end));
            start = end + 1;
            partitionNumber++;
        }
        return partitions;
    }

    private ExecutionContext rangeContext(long minId, long maxId) {
        ExecutionContext context = new ExecutionContext();
        context.putLong(MIN_ID_KEY, minId);
        context.putLong(MAX_ID_KEY, maxId);
        return context;
    }
}
