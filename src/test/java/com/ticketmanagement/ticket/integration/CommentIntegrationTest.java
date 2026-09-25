package com.ticketmanagement.ticket.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class CommentIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  private String createTicket() throws Exception {
    return mockMvc.perform(post("/api/v1/tickets").contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"T\",\"description\":\"D\",\"priority\":\"LOW\",\"assignee\":\"a\"}"))
        .andReturn().getResponse().getHeader("Location");
  }

  @Test
  void addCommentAppearsOnTicketDetail() throws Exception {
    String location = createTicket();

    mockMvc.perform(post(location + "/comments").contentType(MediaType.APPLICATION_JSON)
            .content("{\"content\":\"Reproduced on staging.\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.content").value("Reproduced on staging."));

    mockMvc.perform(get(location))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.comments.length()").value(1))
        .andExpect(jsonPath("$.comments[0].content").value("Reproduced on staging."));
  }

  @Test
  void commentsReturnedInChronologicalOrder() throws Exception {
    String location = createTicket();

    mockMvc.perform(post(location + "/comments").contentType(MediaType.APPLICATION_JSON)
        .content("{\"content\":\"First\"}"));
    mockMvc.perform(post(location + "/comments").contentType(MediaType.APPLICATION_JSON)
        .content("{\"content\":\"Second\"}"));
    mockMvc.perform(post(location + "/comments").contentType(MediaType.APPLICATION_JSON)
        .content("{\"content\":\"Third\"}"));

    mockMvc.perform(get(location))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.comments.length()").value(3))
        .andExpect(jsonPath("$.comments[0].content").value("First"))
        .andExpect(jsonPath("$.comments[1].content").value("Second"))
        .andExpect(jsonPath("$.comments[2].content").value("Third"));
  }

  @Test
  void blankCommentRejected() throws Exception {
    String location = createTicket();

    mockMvc.perform(post(location + "/comments").contentType(MediaType.APPLICATION_JSON)
            .content("{\"content\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void commentOnMissingTicketReturns404() throws Exception {
    mockMvc.perform(post("/api/v1/tickets/" + java.util.UUID.randomUUID() + "/comments")
            .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"hi\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("TICKET_NOT_FOUND"));
  }
}
