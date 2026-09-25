package com.ticketmanagement.rag.controller;

import com.ticketmanagement.rag.dto.AskRequest;
import com.ticketmanagement.rag.dto.AskResponse;
import com.ticketmanagement.rag.service.AskService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Natural-language question answering over ticket history (rag-api-contract.md). */
@RestController
@RequestMapping("/api/ai")
public class AskController {

  private final AskService askService;

  public AskController(AskService askService) {
    this.askService = askService;
  }

  /**
   * Answers a question grounded only in retrieved ticket context.
   *
   * @param request the question to answer
   * @return the grounded answer and its supporting ticket ID citations
   */
  @PostMapping("/ask")
  public AskResponse ask(@Valid @RequestBody AskRequest request) {
    return askService.ask(request.question());
  }
}
