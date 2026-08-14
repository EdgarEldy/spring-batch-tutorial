package com.edgareldy.springbatchtutorial.exception;

/**
 * Thrown when a requested entity does not exist (e.g. looking up a
 * PayrollRun by an id that is not in the database). Caught by
 * {@link GlobalExceptionHandler} and translated into a 404 response.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
