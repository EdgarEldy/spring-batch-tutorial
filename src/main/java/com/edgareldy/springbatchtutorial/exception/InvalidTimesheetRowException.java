package com.edgareldy.springbatchtutorial.exception;

/**
 * Thrown by {@code TimesheetItemProcessor} when a CSV row fails validation
 * (hours not in {@code (0, 24]}) or its email does not resolve to a known
 * {@code Employee}. Registered as a skippable exception on the
 * {@code importTimesheets} step (
 * {@code .faultTolerant().skipLimit(...).skip(InvalidTimesheetRowException.class)}
 * ) so the row is routed to {@code TimesheetSkipListener} instead of
 * failing the whole step; this is an internal batch-processing signal, not
 * surfaced to REST clients, so it is not handled by
 * {@link GlobalExceptionHandler}.
 * <p>
 * Created by Edgar Muhamyangabo on 8/17/26
 * Author : Edgar Muhamyangabo
 * Date : 8/17/26
 * Project : spring-batch-tutorial
 */
public class InvalidTimesheetRowException extends RuntimeException {

    public InvalidTimesheetRowException(String message) {
        super(message);
    }

    public InvalidTimesheetRowException(String message, Throwable cause) {
        super(message, cause);
    }
}
