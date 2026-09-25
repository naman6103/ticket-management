package com.ticketmanagement.rag.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketmanagement.rag.dto.AskResponse;
import com.ticketmanagement.rag.service.AskService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AskController.class)
class AskControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private AskService askService;

  @Test
  void blankQuestionReturns400ValidationFailedWithoutInvokingService() throws Exception {
    mockMvc
        .perform(post("/api/ai/ask").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

    verify(askService, never()).ask(anyString());
  }

  @Test
  void overLengthQuestionReturns400ValidationFailedWithoutInvokingService() throws Exception {
    String longQuestion = "x".repeat(1001);
    mockMvc
        .perform(post("/api/ai/ask")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"question\":\"" + longQuestion + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

    verify(askService, never()).ask(anyString());
  }

  @Test
  void validQuestionDelegatesToServiceAndReturnsItsResponse() throws Exception {
    when(askService.ask("Have we seen payment failures before?"))
        .thenReturn(new AskResponse("Yes, per TKT-1.", List.of("TKT-1"), false));

    mockMvc
        .perform(post("/api/ai/ask")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"question\":\"Have we seen payment failures before?\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.answer").value("Yes, per TKT-1."))
        .andExpect(jsonPath("$.ticketIds[0]").value("TKT-1"))
        .andExpect(jsonPath("$.noRelevantTicketsFound").value(false));
  }
}
