package com.ticketmanagement.ticket.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketmanagement.ticket.repository.TicketRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TicketCrudIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private TicketRepository ticketRepository;

  @Test
  void createThenGetRoundTripMatchesSubmittedFields() throws Exception {
    String body = """
        {"title":"Login broken","description":"500 on login","priority":"HIGH","assignee":"jane"}
        """;

    String location = mockMvc.perform(post("/api/v1/tickets").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("OPEN"))
        .andReturn().getResponse().getHeader("Location");

    mockMvc.perform(get(location))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Login broken"))
        .andExpect(jsonPath("$.description").value("500 on login"))
        .andExpect(jsonPath("$.priority").value("HIGH"))
        .andExpect(jsonPath("$.assignee").value("jane"))
        .andExpect(jsonPath("$.status").value("OPEN"));
  }

  @Test
  void createWithBlankTitleRejected() throws Exception {
    long before = ticketRepository.count();
    String body = """
        {"title":"","description":"d","priority":"HIGH","assignee":"a"}
        """;

    mockMvc.perform(post("/api/v1/tickets").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.details[0].field").value("title"));

    assertThat(ticketRepository.count()).isEqualTo(before);
  }

  @Test
  void malformedIdIsValidationErrorNotFoundIdIs404() throws Exception {
    mockMvc.perform(get("/api/v1/tickets/not-a-uuid"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

    mockMvc.perform(get("/api/v1/tickets/" + java.util.UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("TICKET_NOT_FOUND"));
  }

  @Test
  void partialUpdateChangesOnlySubmittedField() throws Exception {
    String createBody = """
        {"title":"T","description":"D","priority":"LOW","assignee":"a"}
        """;
    String location = mockMvc.perform(post("/api/v1/tickets").contentType(MediaType.APPLICATION_JSON).content(createBody))
        .andReturn().getResponse().getHeader("Location");

    mockMvc.perform(patch(location).contentType(MediaType.APPLICATION_JSON).content("{\"priority\":\"HIGH\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.priority").value("HIGH"))
        .andExpect(jsonPath("$.title").value("T"))
        .andExpect(jsonPath("$.description").value("D"))
        .andExpect(jsonPath("$.assignee").value("a"));
  }

  @Test
  void emptyUpdateBodyIsNoOp() throws Exception {
    String createBody = """
        {"title":"T2","description":"D2","priority":"LOW","assignee":"a"}
        """;
    String location = mockMvc.perform(post("/api/v1/tickets").contentType(MediaType.APPLICATION_JSON).content(createBody))
        .andReturn().getResponse().getHeader("Location");

    mockMvc.perform(patch(location).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("T2"))
        .andExpect(jsonPath("$.description").value("D2"));
  }

  @Test
  void updateWithBlankTitleRejectedAndUnchanged() throws Exception {
    String createBody = """
        {"title":"Keep","description":"D","priority":"LOW","assignee":"a"}
        """;
    String location = mockMvc.perform(post("/api/v1/tickets").contentType(MediaType.APPLICATION_JSON).content(createBody))
        .andReturn().getResponse().getHeader("Location");

    mockMvc.perform(patch(location).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

    mockMvc.perform(get(location)).andExpect(jsonPath("$.title").value("Keep"));
  }

  @Test
  void updateOnNonExistentIdReturns404() throws Exception {
    mockMvc.perform(patch("/api/v1/tickets/" + java.util.UUID.randomUUID())
            .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"x\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("TICKET_NOT_FOUND"));
  }
}
