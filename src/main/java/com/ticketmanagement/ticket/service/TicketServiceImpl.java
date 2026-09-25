package com.ticketmanagement.ticket.service;

import com.ticketmanagement.common.exception.ErrorResponse;
import com.ticketmanagement.common.exception.FieldValidationException;
import com.ticketmanagement.rag.event.TicketChangedEvent;
import com.ticketmanagement.ticket.dto.TicketCreateRequest;
import com.ticketmanagement.ticket.dto.TicketUpdateRequest;
import com.ticketmanagement.ticket.entity.Ticket;
import com.ticketmanagement.ticket.entity.TicketStatus;
import com.ticketmanagement.ticket.exception.InvalidTransitionException;
import com.ticketmanagement.ticket.exception.TicketNotFoundException;
import com.ticketmanagement.ticket.repository.TicketRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Service
public class TicketServiceImpl implements TicketService {

  private static final Map<TicketStatus, Set<TicketStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(TicketStatus.class);

  static {
    ALLOWED_TRANSITIONS.put(TicketStatus.OPEN, Set.of(TicketStatus.IN_PROGRESS, TicketStatus.CANCELLED));
    ALLOWED_TRANSITIONS.put(TicketStatus.IN_PROGRESS, Set.of(TicketStatus.RESOLVED, TicketStatus.CANCELLED));
    ALLOWED_TRANSITIONS.put(TicketStatus.RESOLVED, Set.of(TicketStatus.CLOSED));
    ALLOWED_TRANSITIONS.put(TicketStatus.CLOSED, Set.of());
    ALLOWED_TRANSITIONS.put(TicketStatus.CANCELLED, Set.of());
  }

  private final TicketRepository ticketRepository;
  private final ApplicationEventPublisher eventPublisher;

  public TicketServiceImpl(TicketRepository ticketRepository, ApplicationEventPublisher eventPublisher) {
    this.ticketRepository = ticketRepository;
    this.eventPublisher = eventPublisher;
  }

  @Override
  public Ticket create(TicketCreateRequest request) {
    Ticket ticket = new Ticket(
        request.title(), request.description(), request.priority(), request.assignee(), request.category());
    Ticket saved = ticketRepository.save(ticket);
    eventPublisher.publishEvent(new TicketChangedEvent(saved.getId()));
    return saved;
  }

  @Override
  public Ticket getById(UUID id) {
    return ticketRepository.findById(id).orElseThrow(() -> new TicketNotFoundException(id));
  }

  @Override
  public Ticket update(UUID id, TicketUpdateRequest request) {
    validatePresentFieldsNotBlank(request);
    Ticket ticket = getById(id);
    if (request.title() != null) {
      ticket.setTitle(request.title());
    }
    if (request.description() != null) {
      ticket.setDescription(request.description());
    }
    if (request.priority() != null) {
      ticket.setPriority(request.priority());
    }
    if (request.assignee() != null) {
      ticket.setAssignee(request.assignee());
    }
    if (request.category() != null) {
      ticket.setCategory(request.category());
    }
    Ticket saved = ticketRepository.save(ticket);
    eventPublisher.publishEvent(new TicketChangedEvent(saved.getId()));
    return saved;
  }

  private void validatePresentFieldsNotBlank(TicketUpdateRequest request) {
    List<ErrorResponse.FieldDetail> details = new ArrayList<>();
    if (request.title() != null && request.title().isBlank()) {
      details.add(new ErrorResponse.FieldDetail("title", request.title(), "must not be blank"));
    }
    if (request.description() != null && request.description().isBlank()) {
      details.add(new ErrorResponse.FieldDetail("description", request.description(), "must not be blank"));
    }
    if (request.assignee() != null && request.assignee().isBlank()) {
      details.add(new ErrorResponse.FieldDetail("assignee", request.assignee(), "must not be blank"));
    }
    if (!details.isEmpty()) {
      throw new FieldValidationException(details);
    }
  }

  @Override
  public Ticket transition(UUID id, TicketStatus targetStatus) {
    Ticket ticket = getById(id);
    TicketStatus current = ticket.getStatus();
    if (!ALLOWED_TRANSITIONS.get(current).contains(targetStatus)) {
      throw new InvalidTransitionException(current, targetStatus);
    }
    ticket.setStatus(targetStatus);
    Ticket saved = ticketRepository.save(ticket);
    eventPublisher.publishEvent(new TicketChangedEvent(saved.getId()));
    return saved;
  }

  @Override
  public Page<Ticket> search(String keyword, TicketStatus status, Pageable pageable) {
    Specification<Ticket> spec = (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      if (keyword != null && !keyword.isBlank()) {
        String pattern = "%" + keyword.toLowerCase() + "%";
        predicates.add(cb.or(
            cb.like(cb.lower(root.get("title")), pattern),
            cb.like(cb.lower(root.get("description")), pattern)));
      }
      if (status != null) {
        predicates.add(cb.equal(root.get("status"), status));
      }
      return cb.and(predicates.toArray(new Predicate[0]));
    };
    return ticketRepository.findAll(spec, pageable);
  }

  @Override
  public List<String> listDistinctAssignees() {
    return ticketRepository.findDistinctAssignees();
  }
}
