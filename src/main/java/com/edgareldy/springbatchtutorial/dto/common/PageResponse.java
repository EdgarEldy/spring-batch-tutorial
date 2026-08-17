package com.edgareldy.springbatchtutorial.dto.common;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Generic paginated content DTO used as the {@code data} payload of
 * {@link ApiResponse} on every list endpoint, instead of a plain list.
 * <p>
 * Created by Edgar Muhamyangabo on 8/15/26
 * Author : Edgar Muhamyangabo
 * Date : 8/15/26
 * Project : spring-batch-tutorial
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
