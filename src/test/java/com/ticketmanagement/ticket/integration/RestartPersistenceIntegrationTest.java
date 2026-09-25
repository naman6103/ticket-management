package com.ticketmanagement.ticket.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketmanagement.ticket.entity.Comment;
import com.ticketmanagement.ticket.entity.Priority;
import com.ticketmanagement.ticket.entity.Ticket;
import com.ticketmanagement.ticket.repository.CommentRepository;
import com.ticketmanagement.ticket.repository.TicketRepository;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import com.ticketmanagement.TicketManagementApplication;

/**
 * Proves data survives an application restart (FR-008, SC-005) using a file-based H2
 * datasource shared across two independently started Spring contexts.
 */
class RestartPersistenceIntegrationTest {

  @Test
  void dataSurvivesRestart(@TempDir Path tempDir) {
    String dbUrl = "jdbc:h2:file:" + tempDir.resolve("restart-test") + ";AUTO_SERVER=TRUE";
    UUID ticketId;

    try (ConfigurableApplicationContext ctx = start(dbUrl)) {
      TicketRepository ticketRepository = ctx.getBean(TicketRepository.class);
      CommentRepository commentRepository = ctx.getBean(CommentRepository.class);

      Ticket ticket = ticketRepository.save(new Ticket("Persist me", "desc", Priority.LOW, "a", null));
      commentRepository.save(new Comment(ticket.getId(), "a comment"));
      ticketId = ticket.getId();
    }

    try (ConfigurableApplicationContext ctx = start(dbUrl)) {
      TicketRepository ticketRepository = ctx.getBean(TicketRepository.class);
      CommentRepository commentRepository = ctx.getBean(CommentRepository.class);

      Ticket reloaded = ticketRepository.findById(ticketId).orElseThrow();
      assertThat(reloaded.getTitle()).isEqualTo("Persist me");
      assertThat(commentRepository.findByTicketIdOrderByCreatedAtAscIdAsc(ticketId)).hasSize(1);
    }
  }

  private ConfigurableApplicationContext start(String dbUrl) {
    return new SpringApplicationBuilder(TicketManagementApplication.class)
        .properties(
            "spring.datasource.url=" + dbUrl,
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "server.port=0")
        .run();
  }
}
