package com.ticketmanagement.ticket.dto;

import com.ticketmanagement.ticket.entity.Priority;
import com.ticketmanagement.ticket.entity.Ticket;
import com.ticketmanagement.ticket.entity.TicketStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TicketResponse(
    UUID id,
    String title,
    String description,
    Priority priority,
    String assignee,
    TicketStatus status,
    String category,
    Instant createdAt,
    Instant updatedAt,
    List<CommentResponse> comments) {

  public static TicketResponse from(Ticket ticket, List<CommentResponse> comments) {
    return new TicketResponse(
        ticket.getId(), ticket.getTitle(), ticket.getDescription(), ticket.getPriority(),
        ticket.getAssignee(), ticket.getStatus(), ticket.getCategory(),
        ticket.getCreatedAt(), ticket.getUpdatedAt(), comments);
  }

  public static TicketResponse withoutComments(Ticket ticket) {
    return from(ticket, null);
  }
}
