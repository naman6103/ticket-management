package com.ticketmanagement.rag.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketmanagement.ticket.entity.Comment;
import com.ticketmanagement.ticket.entity.Priority;
import com.ticketmanagement.ticket.entity.Ticket;
import java.util.List;
import org.junit.jupiter.api.Test;

class TicketChunkBuilderTest {

  private final TicketChunkBuilder builder = new TicketChunkBuilder();

  @Test
  void returnsOneChunkPerDescriptionAndComment() {
    Ticket ticket = new Ticket("Payment fails", "Checkout times out", Priority.HIGH, "alice", "billing");
    Comment first = new Comment(ticket.getId(), "Retried with backoff");
    Comment second = new Comment(ticket.getId(), "Confirmed resolved");

    List<TicketChunk> chunks = builder.build(ticket, List.of(first, second));

    assertThat(chunks).hasSize(3);
    assertThat(chunks.get(0).chunkId()).endsWith(":description");
    assertThat(chunks.get(0).content()).isEqualTo("Checkout times out");
    assertThat(chunks.get(0).status()).isEqualTo(ticket.getStatus());
    assertThat(chunks.get(0).priority()).isEqualTo(Priority.HIGH);
    assertThat(chunks.get(0).assignee()).isEqualTo("alice");
    assertThat(chunks.get(0).category()).isEqualTo("billing");
    assertThat(chunks.get(1).chunkId()).contains(":comment:").endsWith(String.valueOf(first.getId()));
    assertThat(chunks.get(1).content()).isEqualTo("Retried with backoff");
    assertThat(chunks.get(2).content()).isEqualTo("Confirmed resolved");
  }

  @Test
  void skipsCommentsWithBlankContent() {
    Ticket ticket = new Ticket("t", "d", Priority.LOW, "bob", null);
    Comment blank = new Comment(ticket.getId(), "   ");

    List<TicketChunk> chunks = builder.build(ticket, List.of(blank));

    assertThat(chunks).hasSize(1); // description only
  }

  @Test
  void splitsContentOverThresholdOnBlankLineBoundaries() {
    String paragraph = "x".repeat(1200);
    String longDescription = paragraph + "\n\n" + paragraph + "\n\n" + paragraph;
    Ticket ticket = new Ticket("t", longDescription, Priority.MEDIUM, "carol", null);

    List<TicketChunk> chunks = builder.build(ticket, List.of());

    assertThat(chunks).hasSize(3);
    assertThat(chunks.get(0).chunkId()).endsWith(":description:part1");
    assertThat(chunks.get(1).chunkId()).endsWith(":description:part2");
    assertThat(chunks.get(2).chunkId()).endsWith(":description:part3");
    chunks.forEach(chunk -> assertThat(chunk.content()).isEqualTo(paragraph));
  }

  @Test
  void keepsOverThresholdContentAsSingleChunkWhenNoBlankLineBoundaryExists() {
    String longDescription = "x".repeat(2500);
    Ticket ticket = new Ticket("t", longDescription, Priority.LOW, "dave", null);

    List<TicketChunk> chunks = builder.build(ticket, List.of());

    assertThat(chunks).hasSize(1);
    assertThat(chunks.get(0).chunkId()).endsWith(":description");
    assertThat(chunks.get(0).content()).isEqualTo(longDescription);
  }
}
