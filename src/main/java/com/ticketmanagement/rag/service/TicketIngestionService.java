package com.ticketmanagement.rag.service;

import java.util.UUID;

/** Refreshes a ticket's knowledge documents in the RAG vector store (rag-ingestion.md §2). */
public interface TicketIngestionService {

  /**
   * Rebuilds and stores every knowledge-document chunk for the given ticket's current state,
   * superseding any previously stored chunks for that ticket. Does nothing if the ticket no
   * longer exists (e.g. it was deleted after the triggering event was published).
   *
   * @param ticketId the ticket to (re)ingest
   */
  void reingest(UUID ticketId);
}
