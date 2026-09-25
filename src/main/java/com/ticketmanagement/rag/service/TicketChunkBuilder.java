package com.ticketmanagement.rag.service;

import com.ticketmanagement.ticket.entity.Comment;
import com.ticketmanagement.ticket.entity.Ticket;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Splits a ticket's description and comments into knowledge-document chunks (architecture.md §3):
 * one chunk per description, one chunk per comment — the natural, already-bounded unit boundaries
 * ticket content is written in — with a fallback split on blank-line boundaries for any single
 * chunk over {@link #LONG_CONTENT_THRESHOLD} characters.
 */
@Component
public class TicketChunkBuilder {

  private static final int LONG_CONTENT_THRESHOLD = 2000;
  private static final String BLANK_LINE_BOUNDARY = "\\r?\\n\\s*\\r?\\n";

  /**
   * Builds every knowledge-document chunk for a ticket's current state.
   *
   * @param ticket the ticket whose description is chunked
   * @param comments the ticket's comments, one chunk per non-blank comment
   * @return chunks in a stable, deterministic order; never {@code null}
   */
  public List<TicketChunk> build(Ticket ticket, List<Comment> comments) {
    List<TicketChunk> chunks = new ArrayList<>();
    addChunk(chunks, ticket, ticket.getId() + ":description", ticket.getDescription());
    for (Comment comment : comments) {
      addChunk(chunks, ticket, ticket.getId() + ":comment:" + comment.getId(), comment.getContent());
    }
    return chunks;
  }

  private void addChunk(List<TicketChunk> chunks, Ticket ticket, String chunkId, String content) {
    if (content == null || content.isBlank()) {
      return;
    }
    if (content.length() <= LONG_CONTENT_THRESHOLD) {
      chunks.add(toChunk(ticket, chunkId, content));
      return;
    }
    String[] parts = content.split(BLANK_LINE_BOUNDARY);
    if (parts.length <= 1) {
      // No blank-line boundary to split on; keep as a single (over-threshold) chunk.
      chunks.add(toChunk(ticket, chunkId, content));
      return;
    }
    int partNumber = 1;
    for (String part : parts) {
      if (part.isBlank()) {
        continue;
      }
      chunks.add(toChunk(ticket, chunkId + ":part" + partNumber, part.trim()));
      partNumber++;
    }
  }

  private TicketChunk toChunk(Ticket ticket, String chunkId, String content) {
    return new TicketChunk(
        chunkId,
        content,
        ticket.getId(),
        ticket.getStatus(),
        ticket.getPriority(),
        ticket.getAssignee(),
        ticket.getCategory(),
        ticket.getUpdatedAt());
  }
}
