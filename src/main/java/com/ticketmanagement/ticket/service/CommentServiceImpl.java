package com.ticketmanagement.ticket.service;

import com.ticketmanagement.ticket.dto.CommentCreateRequest;
import com.ticketmanagement.ticket.entity.Comment;
import com.ticketmanagement.ticket.exception.TicketNotFoundException;
import com.ticketmanagement.ticket.repository.CommentRepository;
import com.ticketmanagement.ticket.repository.TicketRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CommentServiceImpl implements CommentService {

  private final CommentRepository commentRepository;
  private final TicketRepository ticketRepository;

  public CommentServiceImpl(CommentRepository commentRepository, TicketRepository ticketRepository) {
    this.commentRepository = commentRepository;
    this.ticketRepository = ticketRepository;
  }

  @Override
  public Comment addComment(UUID ticketId, CommentCreateRequest request) {
    if (!ticketRepository.existsById(ticketId)) {
      throw new TicketNotFoundException(ticketId);
    }
    Comment comment = new Comment(ticketId, request.content());
    return commentRepository.save(comment);
  }

  @Override
  public List<Comment> getByTicketId(UUID ticketId) {
    return commentRepository.findByTicketId(ticketId);
  }
}
