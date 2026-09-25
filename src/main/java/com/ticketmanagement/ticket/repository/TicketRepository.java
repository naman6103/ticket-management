package com.ticketmanagement.ticket.repository;

import com.ticketmanagement.ticket.entity.Ticket;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketRepository extends JpaRepository<Ticket, UUID>, JpaSpecificationExecutor<Ticket> {

  @Query("SELECT DISTINCT t.assignee FROM Ticket t WHERE t.assignee IS NOT NULL ORDER BY t.assignee ASC")
  List<String> findDistinctAssignees();

  /**
   * Returns tickets that have never had their RAG knowledge documents successfully indexed
   * (feature 003-rag-ticket-qa, FR-017/FR-018) — the one-time backfill job's selection query.
   */
  Page<Ticket> findByKnowledgeIndexedFalse(Pageable pageable);

  /**
   * Sets {@code knowledgeIndexed = true} for a single ticket via a bulk update, bypassing the
   * entity's {@code @PreUpdate} lifecycle callback so this internal indexing bookkeeping does not
   * change {@code updatedAt} (which should reflect real content changes only).
   *
   * @return 1 if the ticket existed and was not already marked, 0 otherwise
   */
  @Modifying
  @Query("UPDATE Ticket t SET t.knowledgeIndexed = true WHERE t.id = :id AND t.knowledgeIndexed = false")
  int markKnowledgeIndexed(@Param("id") UUID id);
}
