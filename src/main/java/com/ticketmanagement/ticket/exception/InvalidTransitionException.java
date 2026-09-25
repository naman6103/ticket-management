package com.ticketmanagement.ticket.exception;

import com.ticketmanagement.ticket.entity.TicketStatus;

public class InvalidTransitionException extends RuntimeException {

  private final TicketStatus currentStatus;
  private final TicketStatus targetStatus;

  public InvalidTransitionException(TicketStatus currentStatus, TicketStatus targetStatus) {
    super("Cannot transition ticket from " + currentStatus + " to " + targetStatus);
    this.currentStatus = currentStatus;
    this.targetStatus = targetStatus;
  }

  public TicketStatus getCurrentStatus() {
    return currentStatus;
  }

  public TicketStatus getTargetStatus() {
    return targetStatus;
  }
}
