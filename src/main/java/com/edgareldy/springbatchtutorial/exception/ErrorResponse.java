package com.edgareldy.springbatchtutorial.exception;

import java.time.Instant;
import java.util.List;

/**
 * Structured error detail placed in the {@code data} field of an
 * {@code ApiResponse<ErrorResponse>} whenever {@link GlobalExceptionHandler}
 * handles an exception, so clients get more than just a plain message string.
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldError> fieldErrors
) {

    /**
     * A single field validation failure, used when Bean Validation rejects a
     * request body (see {@link GlobalExceptionHandler} handling of
     * {@code MethodArgumentNotValidException}).
     */
    public record FieldError(String field, String message) {
    }
}
