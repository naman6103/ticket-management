package com.ticketmanagement.common.exception;

import java.util.List;

/** Thrown for manual field-level validation performed outside Bean Validation's null-safety rules. */
public class FieldValidationException extends RuntimeException {

  private final List<ErrorResponse.FieldDetail> details;

  public FieldValidationException(List<ErrorResponse.FieldDetail> details) {
    super("Request validation failed");
    this.details = details;
  }

  public List<ErrorResponse.FieldDetail> getDetails() {
    return details;
  }
}
