package com.ticketmanagement.rag.service;

import com.ticketmanagement.rag.config.RagAsyncConfig;
import com.ticketmanagement.rag.event.TicketChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Consumes {@link TicketChangedEvent} asynchronously and refreshes the affected ticket's
 * knowledge documents (rag-ingestion.md §3). Runs off the request thread so ticket
 * create/update/transition/comment API calls are never delayed by embedding or Elasticsearch
 * latency (FR-008).
 */
@Component
public class TicketChangeListener {

  private static final Logger log = LoggerFactory.getLogger(TicketChangeListener.class);

  private final TicketIngestionService ingestionService;

  public TicketChangeListener(TicketIngestionService ingestionService) {
    this.ingestionService = ingestionService;
  }

  @Async(RagAsyncConfig.INGESTION_EXECUTOR)
  @EventListener(TicketChangedEvent.class)
  public void onTicketChanged(TicketChangedEvent event) {
    try {
      ingestionService.reingest(event.ticketId());
    } catch (RuntimeException ex) {
      // The ticket write that triggered this event has already committed; a re-ingestion
      // failure (e.g. Elasticsearch/Ollama temporarily unreachable) must not surface as a
      // request failure. Logged for operator visibility only (rag-ingestion.md §3).
      log.error("Failed to re-ingest ticket {} for RAG knowledge base", event.ticketId(), ex);
    }
  }
}
