package com.ticketmanagement.ticket.controller;

import com.ticketmanagement.ticket.dto.AssigneesResponse;
import com.ticketmanagement.ticket.service.TicketService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/assignees")
public class AssigneeController {

  private final TicketService ticketService;

  public AssigneeController(TicketService ticketService) {
    this.ticketService = ticketService;
  }

  /**
   * Returns the distinct, non-null assignee values already used across existing tickets.
   *
   * @return 200 with an alphabetically-sorted list, empty when no ticket has an assignee yet
   */
  @GetMapping
  public AssigneesResponse list() {
    return new AssigneesResponse(ticketService.listDistinctAssignees());
  }
}
