package com.ticketmanagement.ticket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ticketmanagement.ticket.dto.CommentCreateRequest;
import com.ticketmanagement.ticket.entity.Comment;
import com.ticketmanagement.ticket.exception.TicketNotFoundException;
import com.ticketmanagement.ticket.repository.CommentRepository;
import com.ticketmanagement.ticket.repository.TicketRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CommentServiceImplTest {

  private CommentRepository commentRepository;
  private TicketRepository ticketRepository;
  private CommentServiceImpl service;

  @BeforeEach
  void setUp() {
    commentRepository = mock(CommentRepository.class);
    ticketRepository = mock(TicketRepository.class);
    service = new CommentServiceImpl(commentRepository, ticketRepository);
  }

  @Test
  void addCommentPersistsWhenTicketExists() {
    UUID ticketId = UUID.randomUUID();
    when(ticketRepository.existsById(ticketId)).thenReturn(true);
    when(commentRepository.save(any(Comment.class))).thenAnswer(inv -> inv.getArgument(0));

    Comment result = service.addComment(ticketId, new CommentCreateRequest("hello"));

    assertThat(result.getContent()).isEqualTo("hello");
    assertThat(result.getTicketId()).isEqualTo(ticketId);
  }

  @Test
  void addCommentOnMissingTicketThrows() {
    UUID ticketId = UUID.randomUUID();
    when(ticketRepository.existsById(ticketId)).thenReturn(false);

    assertThatThrownBy(() -> service.addComment(ticketId, new CommentCreateRequest("hello")))
        .isInstanceOf(TicketNotFoundException.class);

    verify(commentRepository, never()).save(any());
  }

  @Test
  void getByTicketIdReturnsRepositoryResultInChronologicalOrder() {
    UUID ticketId = UUID.randomUUID();
    Comment first = new Comment(ticketId, "first");
    Comment second = new Comment(ticketId, "second");
    when(commentRepository.findByTicketIdOrderByCreatedAtAscIdAsc(ticketId))
        .thenReturn(List.of(first, second));

    List<Comment> result = service.getByTicketId(ticketId);

    assertThat(result).containsExactly(first, second);
  }
}
