package com.ticketmanagement.ticket.service;

import com.ticketmanagement.ticket.dto.TicketCreateRequest;
import com.ticketmanagement.ticket.dto.TicketUpdateRequest;
import com.ticketmanagement.ticket.entity.Ticket;
import com.ticketmanagement.ticket.entity.TicketStatus;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TicketService {

  Ticket create(TicketCreateRequest request);

  Ticket getById(UUID id);

  Ticket update(UUID id, TicketUpdateRequest request);

  Ticket transition(UUID id, TicketStatus targetStatus);

  Page<Ticket> search(String keyword, TicketStatus status, Pageable pageable);
}
