package com.ticketmanagement.ticket.service;

import com.ticketmanagement.rag.event.TicketChangedEvent;
import com.ticketmanagement.ticket.dto.CommentCreateRequest;
import com.ticketmanagement.ticket.entity.Comment;
import com.ticketmanagement.ticket.exception.TicketNotFoundException;
import com.ticketmanagement.ticket.repository.CommentRepository;
import com.ticketmanagement.ticket.repository.TicketRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class CommentServiceImpl implements CommentService {

  private final CommentRepository commentRepository;
  private final TicketRepository ticketRepository;
  private final ApplicationEventPublisher eventPublisher;

  public CommentServiceImpl(
      CommentRepository commentRepository, TicketRepository ticketRepository, ApplicationEventPublisher eventPublisher) {
    this.commentRepository = commentRepository;
    this.ticketRepository = ticketRepository;
    this.eventPublisher = eventPublisher;
  }

  @Override
  public Comment addComment(UUID ticketId, CommentCreateRequest request) {
    if (!ticketRepository.existsById(ticketId)) {
      throw new TicketNotFoundException(ticketId);
    }
    Comment comment = new Comment(ticketId, request.content());
    Comment saved = commentRepository.save(comment);
    eventPublisher.publishEvent(new TicketChangedEvent(ticketId));
    return saved;
  }

  @Override
  public List<Comment> getByTicketId(UUID ticketId) {
    return commentRepository.findByTicketIdOrderByCreatedAtAscIdAsc(ticketId);
  }
}
