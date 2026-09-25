package com.ticketmanagement.ticket.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
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
class TicketSearchAndFilterIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @BeforeEach
  void seed() throws Exception {
    create("Login bug", "cannot login to app", "HIGH", "a");
    create("Payment issue", "checkout fails", "MEDIUM", "b");
    create("Unrelated ticket", "printer offline", "LOW", "c");
  }

  private void create(String title, String desc, String priority, String assignee) throws Exception {
    String body = String.format(
        "{\"title\":\"%s\",\"description\":\"%s\",\"priority\":\"%s\",\"assignee\":\"%s\"}",
        title, desc, priority, assignee);
    mockMvc.perform(post("/api/v1/tickets").contentType(MediaType.APPLICATION_JSON).content(body));
  }

  @Test
  void listReturnsPaginatedEnvelope() throws Exception {
    mockMvc.perform(get("/api/v1/tickets"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.totalElements").exists());
  }

  @Test
  void keywordSearchReturnsOnlyMatches() throws Exception {
    mockMvc.perform(get("/api/v1/tickets").param("q", "login"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].title").value("Login bug"));
  }

  @Test
  void statusFilterReturnsOnlyMatchingStatus() throws Exception {
    String location = mockMvc.perform(post("/api/v1/tickets").contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"Cancel me\",\"description\":\"d\",\"priority\":\"LOW\",\"assignee\":\"a\"}"))
        .andReturn().getResponse().getHeader("Location");
    mockMvc.perform(post(location + "/transitions").contentType(MediaType.APPLICATION_JSON)
        .content("{\"targetStatus\":\"CANCELLED\"}"));

    mockMvc.perform(get("/api/v1/tickets").param("status", "OPEN"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(3));

    mockMvc.perform(get("/api/v1/tickets").param("status", "CANCELLED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].title").value("Cancel me"));
  }

  @Test
  void unknownStatusFilterRejected() throws Exception {
    mockMvc.perform(get("/api/v1/tickets").param("status", "NOT_A_STATUS"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("UNKNOWN_FILTER"));
  }

  @Test
  void emptyKeywordActsAsNoFilter() throws Exception {
    mockMvc.perform(get("/api/v1/tickets").param("q", ""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(3));
  }

  @Test
  void negativePageRejected() throws Exception {
    mockMvc.perform(get("/api/v1/tickets").param("page", "-1"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void noMatchReturnsEmptyContentNotError() throws Exception {
    mockMvc.perform(get("/api/v1/tickets").param("q", "zzzznomatch"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(0));
  }
}
