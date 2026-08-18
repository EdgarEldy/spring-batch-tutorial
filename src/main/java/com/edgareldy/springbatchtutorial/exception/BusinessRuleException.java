package com.edgareldy.springbatchtutorial.exception;

/**
 * Thrown when an operation violates a domain rule (e.g. resuming a
 * PayrollRun that is not currently AWAITING_REVIEW). Caught by
 * {@link GlobalExceptionHandler} and translated into a 422 response.
 * <p>
 * Created by Edgar Muhamyangabo on 8/15/26
 * Author : Edgar Muhamyangabo
 * Date : 8/15/26
 * Project : spring-batch-tutorial
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
