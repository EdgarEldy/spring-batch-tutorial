package com.edgareldy.springbatchtutorial.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.edgareldy.springbatchtutorial.dto.common.ApiResponse;
import com.edgareldy.springbatchtutorial.exception.BusinessRuleException;
import com.edgareldy.springbatchtutorial.exception.ErrorResponse;
import com.edgareldy.springbatchtutorial.exception.GlobalExceptionHandler;
import com.edgareldy.springbatchtutorial.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

/**
 * Verifies that {@link GlobalExceptionHandler} maps each handled exception
 * type to the right HTTP status and always wraps the resulting
 * {@link ErrorResponse} in an {@code ApiResponse} with {@code success = false},
 * without ever leaking a raw exception message for unexpected failures.
 * <p>
 * Created by Edgar Muhamyangabo on 8/17/26
 * Author : Edgar Muhamyangabo
 * Date : 8/17/26
 * Project : spring-batch-tutorial
 */
@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    private static final String REQUEST_URI = "/api/v1/payroll-runs/42";

    @Mock
    private HttpServletRequest request;

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void handleResourceNotFound_mapsTo404AndKeepsExceptionMessage() {
        when(request.getRequestURI()).thenReturn(REQUEST_URI);
        ResourceNotFoundException ex = new ResourceNotFoundException("PayrollRun 42 not found");

        ResponseEntity<ApiResponse<ErrorResponse>> response = handler.handleResourceNotFound(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ApiResponse<ErrorResponse> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.success()).isFalse();
        assertThat(body.message()).isEqualTo("PayrollRun 42 not found");
        assertThat(body.data()).isNotNull();
        assertThat(body.data().status()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(body.data().error()).isEqualTo(HttpStatus.NOT_FOUND.getReasonPhrase());
        assertThat(body.data().message()).isEqualTo("PayrollRun 42 not found");
        assertThat(body.data().path()).isEqualTo(REQUEST_URI);
        assertThat(body.data().fieldErrors()).isNull();
        assertThat(body.data().timestamp()).isNotNull();
    }

    @Test
    void handleBusinessRule_mapsTo422AndKeepsExceptionMessage() {
        when(request.getRequestURI()).thenReturn(REQUEST_URI);
        BusinessRuleException ex = new BusinessRuleException("PayrollRun is not AWAITING_REVIEW");

        ResponseEntity<ApiResponse<ErrorResponse>> response = handler.handleBusinessRule(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        ApiResponse<ErrorResponse> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.success()).isFalse();
        assertThat(body.message()).isEqualTo("PayrollRun is not AWAITING_REVIEW");
        assertThat(body.data()).isNotNull();
        assertThat(body.data().status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.value());
        assertThat(body.data().error()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.getReasonPhrase());
        assertThat(body.data().path()).isEqualTo(REQUEST_URI);
        assertThat(body.data().fieldErrors()).isNull();
    }

    @Test
    void handleValidation_mapsTo400AndPopulatesFieldErrors() {
        when(request.getRequestURI()).thenReturn(REQUEST_URI);
        MethodArgumentNotValidException ex = org.mockito.Mockito.mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = org.mockito.Mockito.mock(BindingResult.class);
        FieldError periodError = new FieldError("payrollRunRequest", "period", "must not be blank");
        FieldError employeeIdError = new FieldError("payrollRunRequest", "employeeId", "must be positive");
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(periodError, employeeIdError));

        ResponseEntity<ApiResponse<ErrorResponse>> response = handler.handleValidation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ApiResponse<ErrorResponse> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.success()).isFalse();
        assertThat(body.message()).isEqualTo("Validation failed");
        assertThat(body.data()).isNotNull();
        assertThat(body.data().status()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(body.data().error()).isEqualTo(HttpStatus.BAD_REQUEST.getReasonPhrase());
        assertThat(body.data().path()).isEqualTo(REQUEST_URI);
        assertThat(body.data().fieldErrors())
                .containsExactlyInAnyOrder(
                        new ErrorResponse.FieldError("period", "must not be blank"),
                        new ErrorResponse.FieldError("employeeId", "must be positive"));
    }

    @Test
    void handleGeneric_mapsTo500AndNeverExposesRawExceptionMessage() {
        when(request.getRequestURI()).thenReturn(REQUEST_URI);
        when(request.getMethod()).thenReturn("POST");
        Exception ex = new IllegalStateException("connection refused: db-host:5432");

        ResponseEntity<ApiResponse<ErrorResponse>> response = handler.handleGeneric(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        ApiResponse<ErrorResponse> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.success()).isFalse();
        assertThat(body.message()).isEqualTo("Unexpected error");
        assertThat(body.message()).doesNotContain("connection refused");
        assertThat(body.data()).isNotNull();
        assertThat(body.data().status()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(body.data().error()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase());
        assertThat(body.data().message()).isEqualTo("Unexpected error");
        assertThat(body.data().message()).doesNotContain("connection refused");
        assertThat(body.data().path()).isEqualTo(REQUEST_URI);
        assertThat(body.data().fieldErrors()).isNull();
    }
}
