package com.ticketmanagement.rag.event;

import java.util.UUID;

/**
 * Published after a ticket's persisted content changes (create, update, transition, or a comment
 * is added), so its knowledge documents can be refreshed (rag-ingestion.md §3).
 *
 * @param ticketId the ticket whose knowledge documents must be re-ingested
 */
public record TicketChangedEvent(UUID ticketId) {}
