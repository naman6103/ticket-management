package com.ticketmanagement.ticket.service;

import com.ticketmanagement.ticket.dto.TicketCreateRequest;
import com.ticketmanagement.ticket.dto.TicketUpdateRequest;
import com.ticketmanagement.ticket.entity.Ticket;
import com.ticketmanagement.ticket.entity.TicketStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TicketService {

  Ticket create(TicketCreateRequest request);

  Ticket getById(UUID id);

  Ticket update(UUID id, TicketUpdateRequest request);

  Ticket transition(UUID id, TicketStatus targetStatus);

  Page<Ticket> search(String keyword, TicketStatus status, Pageable pageable);

  /**
   * Returns the distinct, non-null assignee values already used across all tickets, sorted
   * alphabetically.
   *
   * @return an empty list when no ticket has an assignee yet
   */
  List<String> listDistinctAssignees();
}
