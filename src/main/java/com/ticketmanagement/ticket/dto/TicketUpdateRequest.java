package com.ticketmanagement.ticket.dto;

import com.ticketmanagement.ticket.entity.Priority;

/**
 * All fields nullable — partial update. A null field (absent JSON key) leaves the stored value
 * unchanged. Not annotated with {@code @NotBlank}: per the Jakarta Validation spec, {@code @NotBlank}
 * implies not-null, which would reject every absent field. Presence-then-blank checking is done in
 * {@link com.ticketmanagement.ticket.service.TicketServiceImpl#update}. No {@code status} field:
 * transitions go through the dedicated endpoint.
 */
public record TicketUpdateRequest(
    String title,
    String description,
    Priority priority,
    String assignee,
    String category) {
}
