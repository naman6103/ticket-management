package com.ticketmanagement.ticket.dto;

import com.ticketmanagement.ticket.entity.TicketStatus;
import jakarta.validation.constraints.NotNull;

public record TicketTransitionRequest(@NotNull TicketStatus targetStatus) {
}
