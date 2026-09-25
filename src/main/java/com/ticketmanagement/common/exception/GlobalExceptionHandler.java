package com.ticketmanagement.common.exception;

import com.ticketmanagement.ticket.exception.InvalidTransitionException;
import com.ticketmanagement.ticket.exception.TicketNotFoundException;
import com.ticketmanagement.ticket.exception.UnknownFilterException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Single source of truth mapping domain/validation exceptions to the shared structured error shape. */
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    List<ErrorResponse.FieldDetail> details = ex.getBindingResult().getFieldErrors().stream()
        .map(fe -> new ErrorResponse.FieldDetail(fe.getField(), fe.getRejectedValue(), fe.getDefaultMessage()))
        .toList();
    return build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "Request validation failed", request, details);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleUnreadable(
      HttpMessageNotReadableException ex, HttpServletRequest request) {
    return build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "Malformed request body", request, List.of());
  }

  @ExceptionHandler(FieldValidationException.class)
  public ResponseEntity<ErrorResponse> handleFieldValidation(
      FieldValidationException ex, HttpServletRequest request) {
    return build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "Request validation failed", request, ex.getDetails());
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ErrorResponse> handleConstraintViolation(
      ConstraintViolationException ex, HttpServletRequest request) {
    List<ErrorResponse.FieldDetail> details = ex.getConstraintViolations().stream()
        .map(v -> new ErrorResponse.FieldDetail(
            v.getPropertyPath().toString(), v.getInvalidValue(), v.getMessage()))
        .toList();
    return build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "Request validation failed", request, details);
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ErrorResponse> handleTypeMismatch(
      MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
    ErrorResponse.FieldDetail detail = new ErrorResponse.FieldDetail(
        ex.getName(), ex.getValue(), "must be a validly formatted value");
    return build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "Request validation failed", request, List.of(detail));
  }

  @ExceptionHandler(TicketNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleNotFound(TicketNotFoundException ex, HttpServletRequest request) {
    return build(HttpStatus.NOT_FOUND, ErrorCode.TICKET_NOT_FOUND, ex.getMessage(), request, List.of());
  }

  @ExceptionHandler(InvalidTransitionException.class)
  public ResponseEntity<ErrorResponse> handleInvalidTransition(
      InvalidTransitionException ex, HttpServletRequest request) {
    return build(HttpStatus.CONFLICT, ErrorCode.INVALID_TRANSITION, ex.getMessage(), request, List.of());
  }

  @ExceptionHandler(UnknownFilterException.class)
  public ResponseEntity<ErrorResponse> handleUnknownFilter(
      UnknownFilterException ex, HttpServletRequest request) {
    ErrorResponse.FieldDetail detail = new ErrorResponse.FieldDetail(ex.getField(), null, ex.getMessage());
    return build(HttpStatus.BAD_REQUEST, ErrorCode.UNKNOWN_FILTER, ex.getMessage(), request, List.of(detail));
  }

  private ResponseEntity<ErrorResponse> build(
      HttpStatus status, ErrorCode code, String message, HttpServletRequest request,
      List<ErrorResponse.FieldDetail> details) {
    ErrorResponse body = new ErrorResponse(
        Instant.now(), status.value(), status.getReasonPhrase(), code.name(), message,
        request.getRequestURI(), details);
    return ResponseEntity.status(status).body(body);
  }
}
