package com.edgareldy.springbatchtutorial.dto.common;

import java.time.Instant;

/**
 * Generic envelope wrapping every API response, success or error, so all
 * endpoints share the same response contract.
 */
public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        Instant timestamp
) {

    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, message, data, Instant.now());
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null, Instant.now());
    }
}
