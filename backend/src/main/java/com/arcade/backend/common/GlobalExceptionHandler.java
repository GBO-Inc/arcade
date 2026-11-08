package com.arcade.backend.common;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  private final Clock clock;

  public GlobalExceptionHandler(Clock clock) {
    this.clock = clock;
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
    List<ErrorResponse.FieldError> fieldErrors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(e -> new ErrorResponse.FieldError(e.getField(), e.getDefaultMessage()))
            .toList();
    return build(HttpStatus.BAD_REQUEST, "Validation failed", fieldErrors);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
    return build(HttpStatus.BAD_REQUEST, "Malformed request body", null);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
    // Spring's own exceptions (ResponseStatusException, unknown path, wrong method, ...)
    // carry their status; NoResourceFoundException is not an ErrorResponseException.
    if (ex instanceof org.springframework.web.ErrorResponse springError) {
      return build(springError.getStatusCode(), springError.getBody().getDetail(), null);
    }
    log.error("Unhandled exception", ex);
    return build(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error", null);
  }

  private ResponseEntity<ErrorResponse> build(
      HttpStatusCode status, String message, List<ErrorResponse.FieldError> fieldErrors) {
    HttpStatus known = HttpStatus.resolve(status.value());
    String error = known != null ? known.getReasonPhrase() : "Error";
    ErrorResponse body =
        new ErrorResponse(
            Instant.now(clock),
            status.value(),
            error,
            message != null ? message : error,
            fieldErrors);
    return ResponseEntity.status(status).body(body);
  }
}
