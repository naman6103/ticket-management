package com.ticketmanagement.ticket.controller;

import com.ticketmanagement.common.config.PaginationProperties;
import com.ticketmanagement.common.dto.PageResponse;
import com.ticketmanagement.ticket.dto.CommentResponse;
import com.ticketmanagement.ticket.dto.TicketCreateRequest;
import com.ticketmanagement.ticket.dto.TicketResponse;
import com.ticketmanagement.ticket.dto.TicketTransitionRequest;
import com.ticketmanagement.ticket.dto.TicketUpdateRequest;
import com.ticketmanagement.ticket.entity.Ticket;
import com.ticketmanagement.ticket.entity.TicketStatus;
import com.ticketmanagement.ticket.exception.UnknownFilterException;
import com.ticketmanagement.ticket.service.CommentService;
import com.ticketmanagement.ticket.service.TicketService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tickets")
@Validated
public class TicketController {

  private static final Set<String> ALLOWED_LIST_PARAMS = Set.of("q", "status", "page", "size");

  private final TicketService ticketService;
  private final CommentService commentService;
  private final PaginationProperties paginationProperties;

  public TicketController(
      TicketService ticketService, CommentService commentService, PaginationProperties paginationProperties) {
    this.ticketService = ticketService;
    this.commentService = commentService;
    this.paginationProperties = paginationProperties;
  }

  @PostMapping
  public ResponseEntity<TicketResponse> create(@Valid @RequestBody TicketCreateRequest request) {
    Ticket ticket = ticketService.create(request);
    TicketResponse body = TicketResponse.withoutComments(ticket);
    return ResponseEntity.created(URI.create("/api/v1/tickets/" + ticket.getId())).body(body);
  }

  @GetMapping("/{id}")
  public TicketResponse getById(@PathVariable UUID id) {
    Ticket ticket = ticketService.getById(id);
    List<CommentResponse> comments = commentService.getByTicketId(id).stream().map(CommentResponse::from).toList();
    return TicketResponse.from(ticket, comments);
  }

  @GetMapping
  public PageResponse<TicketResponse> list(
      @RequestParam Map<String, String> queryParams,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(required = false) @Min(1) Integer size) {
    rejectUnknownFilters(queryParams);
    TicketStatus statusFilter = parseStatus(status);
    int effectiveSize = size == null ? paginationProperties.getDefaultSize() : Math.min(size, paginationProperties.getMaxSize());
    Page<Ticket> result = ticketService.search(q, statusFilter, PageRequest.of(page, effectiveSize));
    List<TicketResponse> content = result.getContent().stream().map(TicketResponse::withoutComments).toList();
    return new PageResponse<>(content, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
  }

  @PatchMapping("/{id}")
  public TicketResponse update(@PathVariable UUID id, @Valid @RequestBody TicketUpdateRequest request) {
    Ticket ticket = ticketService.update(id, request);
    return TicketResponse.withoutComments(ticket);
  }

  @PostMapping("/{id}/transitions")
  public TicketResponse transition(@PathVariable UUID id, @Valid @RequestBody TicketTransitionRequest request) {
    Ticket ticket = ticketService.transition(id, request.targetStatus());
    return TicketResponse.withoutComments(ticket);
  }

  private void rejectUnknownFilters(Map<String, String> queryParams) {
    queryParams.keySet().stream()
        .filter(key -> !ALLOWED_LIST_PARAMS.contains(key))
        .findFirst()
        .ifPresent(key -> {
          throw new UnknownFilterException(key, "Unrecognized query parameter: " + key);
        });
  }

  private TicketStatus parseStatus(String status) {
    if (status == null || status.isBlank()) {
      return null;
    }
    try {
      return TicketStatus.valueOf(status);
    } catch (IllegalArgumentException e) {
      throw new UnknownFilterException("status", "Unrecognized status value: " + status);
    }
  }
}
