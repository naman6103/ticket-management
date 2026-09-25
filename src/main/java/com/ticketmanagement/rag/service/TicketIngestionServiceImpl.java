package com.ticketmanagement.rag.service;

import com.ticketmanagement.ticket.entity.Comment;
import com.ticketmanagement.ticket.entity.Ticket;
import com.ticketmanagement.ticket.repository.CommentRepository;
import com.ticketmanagement.ticket.repository.TicketRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Chunks a ticket's description and comments, embeds them, and stores them in the {@code
 * ticket-knowledge} vector store, superseding any previously stored chunks for that ticket
 * (rag-ingestion.md §2).
 */
@Service
public class TicketIngestionServiceImpl implements TicketIngestionService {

  private final TicketRepository ticketRepository;
  private final CommentRepository commentRepository;
  private final TicketChunkBuilder chunkBuilder;
  private final VectorStore vectorStore;

  public TicketIngestionServiceImpl(
      TicketRepository ticketRepository,
      CommentRepository commentRepository,
      TicketChunkBuilder chunkBuilder,
      VectorStore vectorStore) {
    this.ticketRepository = ticketRepository;
    this.commentRepository = commentRepository;
    this.chunkBuilder = chunkBuilder;
    this.vectorStore = vectorStore;
  }

  @Override
  @Transactional
  public void reingest(UUID ticketId) {
    Ticket ticket = ticketRepository.findById(ticketId).orElse(null);
    if (ticket == null) {
      return;
    }
    List<Comment> comments = commentRepository.findByTicketIdOrderByCreatedAtAscIdAsc(ticketId);
    List<TicketChunk> chunks = chunkBuilder.build(ticket, comments);

    Filter.Expression ticketFilter = new FilterExpressionBuilder().eq("ticketId", ticketId.toString()).build();
    vectorStore.delete(ticketFilter);

    if (!chunks.isEmpty()) {
      vectorStore.add(chunks.stream().map(this::toDocument).toList());
    }

    ticketRepository.markKnowledgeIndexed(ticketId);
  }

  private Document toDocument(TicketChunk chunk) {
    Map<String, Object> metadata = new HashMap<>();
    metadata.put("ticketId", chunk.ticketId().toString());
    metadata.put("status", chunk.status().name());
    metadata.put("priority", chunk.priority().name());
    metadata.put("assignee", chunk.assignee());
    if (chunk.category() != null) {
      metadata.put("category", chunk.category());
    }
    metadata.put("updatedAt", chunk.updatedAt().toString());
    return new Document(chunk.chunkId(), chunk.content(), metadata);
  }
}
