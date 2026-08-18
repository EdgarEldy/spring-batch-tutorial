package com.edgareldy.springbatchtutorial.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.edgareldy.springbatchtutorial.batch.partition.EmployeePartitioner;
import com.edgareldy.springbatchtutorial.repository.EmployeeRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.infrastructure.item.ExecutionContext;

/**
 * Exercises {@link EmployeePartitioner#partition}'s pure id-range math
 * directly against a mocked {@link EmployeeRepository}: no
 * {@code JobLauncher}, no Spring context, no real database, since
 * {@code partition} only ever calls {@code findMinId()}/{@code findMaxId()}
 * before doing arithmetic on the result.
 * <p>
 * Covers an evenly divisible range (1-100, grid size 4, four equal 25-wide
 * ranges), a range that does not divide evenly (contiguous, non-overlapping,
 * covering the full span with no gaps or duplicated ids), fewer distinct ids
 * than the requested grid size (fewer, smaller partitions rather than forced
 * empty ones), and the empty-{@code employees}-table case
 * ({@code findMinId()}/{@code findMaxId()} both null, producing exactly one
 * partition with the documented inverted {@code minId=1}/{@code maxId=0}
 * range).
 * <p>
 * {@code MIN_ID_KEY}/{@code MAX_ID_KEY} are
 * package-private on {@code batch.partition}, a different package from this
 * project's consolidated {@code unit} test package (see every other class in
 * this suite: tests never live alongside the production class they cover),
 * so the {@code "minId"}/{@code "maxId"} literals are duplicated here rather
 * than referenced directly.
 * <p>
 * Created by Edgar Muhamyangabo on 8/18/26
 * Author : Edgar Muhamyangabo
 * Date : 8/18/26
 * Project : spring-batch-tutorial
 */
@ExtendWith(MockitoExtension.class)
class EmployeePartitionerTest {

    private static final String MIN_ID_KEY = "minId";
    private static final String MAX_ID_KEY = "maxId";

    @Mock
    private EmployeeRepository employeeRepository;

    private EmployeePartitioner partitioner;

    @Test
    void evenlyDivisibleRange_producesEqualContiguousRanges() {
        partitioner = new EmployeePartitioner(employeeRepository);
        when(employeeRepository.findMinId()).thenReturn(1L);
        when(employeeRepository.findMaxId()).thenReturn(100L);

        Map<String, ExecutionContext> partitions = partitioner.partition(4);

        assertThat(partitions).hasSize(4);
        List<long[]> ranges = orderedRanges(partitions);
        assertThat(ranges.get(0)).containsExactly(1L, 25L);
        assertThat(ranges.get(1)).containsExactly(26L, 50L);
        assertThat(ranges.get(2)).containsExactly(51L, 75L);
        assertThat(ranges.get(3)).containsExactly(76L, 100L);
        assertRangesCoverExactly(ranges, 1L, 100L);
    }

    @Test
    void unevenRange_staysContiguousNonOverlappingAndCoversTheFullSpan() {
        partitioner = new EmployeePartitioner(employeeRepository);
        when(employeeRepository.findMinId()).thenReturn(1L);
        when(employeeRepository.findMaxId()).thenReturn(10L);

        Map<String, ExecutionContext> partitions = partitioner.partition(3);

        // ceil(10 / 3) = 4-wide ranges: [1,4], [5,8], [9,10].
        assertThat(partitions).hasSize(3);
        List<long[]> ranges = orderedRanges(partitions);
        assertThat(ranges.get(0)).containsExactly(1L, 4L);
        assertThat(ranges.get(1)).containsExactly(5L, 8L);
        assertThat(ranges.get(2)).containsExactly(9L, 10L);
        assertRangesCoverExactly(ranges, 1L, 10L);
    }

    @Test
    void fewerDistinctIdsThanGridSize_producesFewerPartitionsNotEmptyOnes() {
        partitioner = new EmployeePartitioner(employeeRepository);
        when(employeeRepository.findMinId()).thenReturn(1L);
        when(employeeRepository.findMaxId()).thenReturn(1L);

        Map<String, ExecutionContext> partitions = partitioner.partition(4);

        assertThat(partitions).hasSize(1);
        ExecutionContext context = partitions.values().iterator().next();
        assertThat(context.getLong(MIN_ID_KEY)).isEqualTo(1L);
        assertThat(context.getLong(MAX_ID_KEY)).isEqualTo(1L);
    }

    @Test
    void tinyRangeSmallerThanGridSize_producesFewerNonEmptyPartitions() {
        partitioner = new EmployeePartitioner(employeeRepository);
        when(employeeRepository.findMinId()).thenReturn(1L);
        when(employeeRepository.findMaxId()).thenReturn(2L);

        Map<String, ExecutionContext> partitions = partitioner.partition(4);

        // Only 2 distinct ids: at most 2 partitions come out, never 4 forced
        // (some necessarily empty) ranges.
        assertThat(partitions).hasSizeLessThanOrEqualTo(2);
        List<long[]> ranges = orderedRanges(partitions);
        assertRangesCoverExactly(ranges, 1L, 2L);
        for (long[] range : ranges) {
            assertThat(range[0]).isLessThanOrEqualTo(range[1]);
        }
    }

    @Test
    void emptyEmployeesTable_producesExactlyOneInvertedEmptyRangePartition() {
        partitioner = new EmployeePartitioner(employeeRepository);
        when(employeeRepository.findMinId()).thenReturn(null);
        when(employeeRepository.findMaxId()).thenReturn(null);

        Map<String, ExecutionContext> partitions = partitioner.partition(4);

        assertThat(partitions).hasSize(1);
        ExecutionContext context = partitions.values().iterator().next();
        assertThat(context.getLong(MIN_ID_KEY)).isEqualTo(1L);
        assertThat(context.getLong(MAX_ID_KEY)).isEqualTo(0L);
    }

    /** Ranges in partition-number order (partition0, partition1, ...), each as {minId, maxId}. */
    private List<long[]> orderedRanges(Map<String, ExecutionContext> partitions) {
        List<long[]> ranges = new ArrayList<>();
        int index = 0;
        while (partitions.containsKey("partition" + index)) {
            ExecutionContext context = partitions.get("partition" + index);
            ranges.add(new long[] {
                    context.getLong(MIN_ID_KEY),
                    context.getLong(MAX_ID_KEY)
            });
            index++;
        }
        assertThat(ranges).hasSize(partitions.size());
        return ranges;
    }

    /**
     * Verifies the given ranges, in order, are contiguous, non-overlapping,
     * and together cover exactly {@code [expectedMin, expectedMax]} with no
     * gaps or duplicated ids.
     */
    private void assertRangesCoverExactly(List<long[]> ranges, long expectedMin, long expectedMax) {
        assertThat(ranges.get(0)[0]).isEqualTo(expectedMin);
        assertThat(ranges.get(ranges.size() - 1)[1]).isEqualTo(expectedMax);
        for (int i = 0; i < ranges.size() - 1; i++) {
            assertThat(ranges.get(i + 1)[0]).isEqualTo(ranges.get(i)[1] + 1);
        }
    }
}
