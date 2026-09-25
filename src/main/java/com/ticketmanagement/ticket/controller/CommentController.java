package com.ticketmanagement.ticket.controller;

import com.ticketmanagement.ticket.dto.CommentCreateRequest;
import com.ticketmanagement.ticket.dto.CommentResponse;
import com.ticketmanagement.ticket.entity.Comment;
import com.ticketmanagement.ticket.service.CommentService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tickets/{ticketId}/comments")
public class CommentController {

  private final CommentService commentService;

  public CommentController(CommentService commentService) {
    this.commentService = commentService;
  }

  @PostMapping
  public ResponseEntity<CommentResponse> addComment(
      @PathVariable UUID ticketId, @Valid @RequestBody CommentCreateRequest request) {
    Comment comment = commentService.addComment(ticketId, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(CommentResponse.from(comment));
  }
}
