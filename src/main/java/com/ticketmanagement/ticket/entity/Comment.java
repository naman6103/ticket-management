package com.ticketmanagement.ticket.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A note attached to a ticket. */
@Entity
@Table(name = "comments")
public class Comment {

  @Id
  @GeneratedValue
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(columnDefinition = "VARCHAR(36)")
  private UUID id;

  @Column(name = "ticket_id", nullable = false, columnDefinition = "VARCHAR(36)")
  @JdbcTypeCode(SqlTypes.VARCHAR)
  private UUID ticketId;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String content;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected Comment() {
    // JPA
  }

  public Comment(UUID ticketId, String content) {
    this.ticketId = ticketId;
    this.content = content;
  }

  @PrePersist
  void onCreate() {
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getTicketId() {
    return ticketId;
  }

  public String getContent() {
    return content;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
