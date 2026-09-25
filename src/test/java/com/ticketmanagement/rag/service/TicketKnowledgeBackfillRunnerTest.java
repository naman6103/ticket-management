package com.ticketmanagement.rag.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ticketmanagement.ticket.entity.Priority;
import com.ticketmanagement.ticket.entity.Ticket;
import com.ticketmanagement.ticket.repository.TicketRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

class TicketKnowledgeBackfillRunnerTest {

  @Test
  void reingestsEveryPendingTicketAcrossMultiplePagesUntilNoneRemain() {
    TicketRepository ticketRepository = mock(TicketRepository.class);
    TicketIngestionService ingestionService = mock(TicketIngestionService.class);
    TicketKnowledgeBackfillRunner runner =
        new TicketKnowledgeBackfillRunner(ticketRepository, ingestionService);

    Ticket first = ticketWithId();
    Ticket second = ticketWithId();
    // First call returns a page with two pending tickets; second call (after they'd have been
    // marked indexed by a real reingest) returns empty, ending the loop.
    when(ticketRepository.findByKnowledgeIndexedFalse(any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(first, second)))
        .thenReturn(Page.empty());

    runner.run();

    verify(ingestionService, times(1)).reingest(first.getId());
    verify(ingestionService, times(1)).reingest(second.getId());
    verify(ticketRepository, times(2)).findByKnowledgeIndexedFalse(any(Pageable.class));
  }

  @Test
  void doesNothingWhenNoTicketsArePending() {
    TicketRepository ticketRepository = mock(TicketRepository.class);
    TicketIngestionService ingestionService = mock(TicketIngestionService.class);
    when(ticketRepository.findByKnowledgeIndexedFalse(any(Pageable.class))).thenReturn(Page.empty());
    TicketKnowledgeBackfillRunner runner =
        new TicketKnowledgeBackfillRunner(ticketRepository, ingestionService);

    runner.run();

    verify(ingestionService, times(0)).reingest(any());
  }

  private Ticket ticketWithId() {
    Ticket ticket = new Ticket("t", "d", Priority.LOW, "a", null);
    ReflectionTestUtils.setField(ticket, "id", UUID.randomUUID());
    return ticket;
  }
}
