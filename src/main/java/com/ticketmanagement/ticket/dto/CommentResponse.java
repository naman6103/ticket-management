package com.ticketmanagement.ticket.dto;

import com.ticketmanagement.ticket.entity.Comment;
import java.time.Instant;
import java.util.UUID;

public record CommentResponse(UUID id, UUID ticketId, String content, Instant createdAt) {

  public static CommentResponse from(Comment comment) {
    return new CommentResponse(comment.getId(), comment.getTicketId(), comment.getContent(), comment.getCreatedAt());
  }
}
