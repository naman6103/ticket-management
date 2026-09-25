package com.ticketmanagement.rag.service;

import com.ticketmanagement.ticket.entity.Ticket;
import com.ticketmanagement.ticket.repository.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/**
 * One-time backfill job for tickets that existed before this feature's ingestion pipeline first
 * ran (FR-017). Explicit opt-in only — set {@code ai.rag.backfill.enabled=true} for a single run,
 * then unset it; this MUST NOT run automatically on every application startup
 * (rag-ingestion.md §5).
 */
@Component
@ConditionalOnProperty(prefix = "ai.rag.backfill", name = "enabled", havingValue = "true")
public class TicketKnowledgeBackfillRunner implements CommandLineRunner {

  private static final Logger log = LoggerFactory.getLogger(TicketKnowledgeBackfillRunner.class);
  private static final int BATCH_SIZE = 100;

  private final TicketRepository ticketRepository;
  private final TicketIngestionService ingestionService;

  public TicketKnowledgeBackfillRunner(TicketRepository ticketRepository, TicketIngestionService ingestionService) {
    this.ticketRepository = ticketRepository;
    this.ingestionService = ingestionService;
  }

  @Override
  public void run(String... args) {
    Pageable pageable = PageRequest.of(0, BATCH_SIZE);
    int processed = 0;
    Page<Ticket> page = ticketRepository.findByKnowledgeIndexedFalse(pageable);
    while (!page.isEmpty()) {
      for (Ticket ticket : page.getContent()) {
        ingestionService.reingest(ticket.getId());
        processed++;
      }
      // Successfully processed tickets are now knowledgeIndexed=true and drop out of this
      // query, so re-requesting the same first page fetches the next still-pending batch.
      page = ticketRepository.findByKnowledgeIndexedFalse(pageable);
    }
    log.info("RAG knowledge backfill complete: {} ticket(s) indexed", processed);
  }
}
