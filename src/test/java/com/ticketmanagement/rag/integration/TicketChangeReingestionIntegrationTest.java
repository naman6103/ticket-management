package com.ticketmanagement.rag.integration;

import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketmanagement.rag.service.TicketIngestionService;
import com.ticketmanagement.ticket.entity.Priority;
import com.ticketmanagement.ticket.entity.Ticket;
import com.ticketmanagement.ticket.entity.TicketStatus;
import com.ticketmanagement.ticket.repository.TicketRepository;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies FR-008's re-ingestion trigger: ticket create/update/transition and comment-add each
 * publish a {@code TicketChangedEvent} that asynchronously reaches {@link TicketIngestionService}
 * (rag-ingestion.md §3). {@link TicketIngestionService} is mocked so this test never touches a
 * live Elasticsearch/Ollama (rules/testing.md).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TicketChangeReingestionIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private TicketRepository ticketRepository;

  @MockBean private TicketIngestionService ingestionService;

  @Test
  void creatingATicketTriggersAsyncReingestionWithoutDelayingTheResponse() throws Exception {
    String body = """
        {"title":"T","description":"D","priority":"HIGH","assignee":"alice","category":null}""";

    String response = mockMvc
        .perform(post("/api/v1/tickets").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated())
        .andReturn()
        .getResponse()
        .getContentAsString();
    UUID ticketId = UUID.fromString(objectMapper.readTree(response).get("id").asText());

    // The HTTP response above already returned; reingest happens asynchronously afterward.
    await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> verify(ingestionService).reingest(ticketId));
  }

  @Test
  void updatingATicketTriggersAsyncReingestion() throws Exception {
    Ticket ticket = ticketRepository.save(new Ticket("T", "D", Priority.LOW, "bob", null));
    clearInvocations(ingestionService);

    mockMvc
        .perform(patch("/api/v1/tickets/{id}", ticket.getId())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"description":"Updated description"}"""))
        .andExpect(status().isOk());

    await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> verify(ingestionService).reingest(ticket.getId()));
  }

  @Test
  void transitioningATicketTriggersAsyncReingestion() throws Exception {
    Ticket ticket = ticketRepository.save(new Ticket("T", "D", Priority.LOW, "carol", null));
    clearInvocations(ingestionService);

    mockMvc
        .perform(post("/api/v1/tickets/{id}/transitions", ticket.getId())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"targetStatus\":\"" + TicketStatus.IN_PROGRESS + "\"}"))
        .andExpect(status().isOk());

    await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> verify(ingestionService).reingest(ticket.getId()));
  }

  @Test
  void addingACommentTriggersAsyncReingestion() throws Exception {
    Ticket ticket = ticketRepository.save(new Ticket("T", "D", Priority.LOW, "dave", null));
    clearInvocations(ingestionService);

    mockMvc
        .perform(post("/api/v1/tickets/{id}/comments", ticket.getId())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"content":"Resolved by retrying with backoff"}"""))
        .andExpect(status().isCreated());

    await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> verify(ingestionService).reingest(ticket.getId()));
  }
}
