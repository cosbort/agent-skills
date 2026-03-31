package com.company.app.handler;

import com.company.app.exception.ApplicationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Instant;

/**
 * Global exception handler using ProblemDetail (RFC 7807).
 *
 * All error responses follow the standardized format:
 * {
 *   "type": "about:blank",
 *   "title": "ResourceNotFoundException",
 *   "status": 404,
 *   "detail": "Product not found with id: 42",
 *   "timestamp": "2026-03-13T10:30:00Z"
 * }
 *
 * Extends ResponseEntityExceptionHandler to handle all Spring MVC
 * exceptions (validation, method not allowed, etc.) with ProblemDetail.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /**
     * Handle all custom application exceptions.
     * Because ApplicationException is sealed, this covers the entire
     * known exception hierarchy exhaustively.
     */
    @ExceptionHandler(ApplicationException.class)
    public ProblemDetail handleApplicationException(ApplicationException ex) {
        log.warn("Application exception [{}]: {}",
                ex.getClass().getSimpleName(), ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                ex.getHttpStatus(), ex.getMessage());
        problem.setTitle(ex.getClass().getSimpleName());
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    /**
     * Handle Bean Validation errors from @Valid on request bodies.
     * Collects all field-level errors into a structured list.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Validation Failed");
        problem.setDetail("One or more fields have invalid values");

        var errors = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .toList();

        problem.setProperty("errors", errors);
        problem.setProperty("timestamp", Instant.now());

        log.warn("Validation error: {}", errors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    /**
     * Catch-all for unexpected exceptions.
     * Logs the full stack trace but never exposes internals to the client.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGlobalException(Exception ex) {
        log.error("Unexpected error", ex);

        ProblemDetail problem = ProblemDetail.forStatus(
                HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setTitle("Internal Server Error");
        problem.setDetail("An unexpected error occurred");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}
