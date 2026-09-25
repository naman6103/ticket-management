package com.ticketmanagement.ticket.service;

import com.ticketmanagement.ticket.dto.CommentCreateRequest;
import com.ticketmanagement.ticket.entity.Comment;
import java.util.List;
import java.util.UUID;

public interface CommentService {

  Comment addComment(UUID ticketId, CommentCreateRequest request);

  List<Comment> getByTicketId(UUID ticketId);
}
