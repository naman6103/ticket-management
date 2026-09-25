package com.ticketmanagement.ticket.dto;

import com.ticketmanagement.ticket.entity.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TicketCreateRequest(
    @NotBlank String title,
    @NotBlank String description,
    @NotNull Priority priority,
    @NotBlank String assignee,
    String category) {
}
