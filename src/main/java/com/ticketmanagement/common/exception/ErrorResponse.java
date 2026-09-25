package com.ticketmanagement.common.exception;

import java.time.Instant;
import java.util.List;

/** Shared structured error response shape returned by every rejected request. */
public record ErrorResponse(
    Instant timestamp,
    int status,
    String error,
    String code,
    String message,
    String path,
    List<FieldDetail> details) {

  public record FieldDetail(String field, Object rejectedValue, String message) {
  }
}
