package com.ticketmanagement.ticket.exception;

/** Thrown when a list/search request specifies a filter value outside the defined domain (e.g. an unrecognized status). */
public class UnknownFilterException extends RuntimeException {

  private final String field;

  public UnknownFilterException(String field, String message) {
    super(message);
    this.field = field;
  }

  public String getField() {
    return field;
  }
}
