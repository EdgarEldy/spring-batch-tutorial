package com.edgareldy.springbatchtutorial.exception;

/**
 * Thrown when an operation violates a domain rule (e.g. resuming a
 * PayrollRun that is not currently AWAITING_REVIEW). Caught by
 * {@link GlobalExceptionHandler} and translated into a 422 response.
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
