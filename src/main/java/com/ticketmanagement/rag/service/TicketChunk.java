package com.ticketmanagement.rag.service;

import com.ticketmanagement.ticket.entity.Priority;
import com.ticketmanagement.ticket.entity.TicketStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * A single knowledge-document chunk derived from one ticket's description or one comment
 * (architecture.md §3), before embedding.
 *
 * @param chunkId deterministic identifier; used as the Elasticsearch document {@code _id}
 * @param content the raw text to embed
 * @param ticketId source ticket's identifier
 * @param status ticket status snapshot at ingestion time
 * @param priority ticket priority snapshot at ingestion time
 * @param assignee ticket assignee snapshot at ingestion time
 * @param category ticket category snapshot at ingestion time (nullable, per Feature 1)
 * @param updatedAt ticket's {@code updatedAt} snapshot at ingestion time
 */
public record TicketChunk(
    String chunkId,
    String content,
    UUID ticketId,
    TicketStatus status,
    Priority priority,
    String assignee,
    String category,
    Instant updatedAt) {}
