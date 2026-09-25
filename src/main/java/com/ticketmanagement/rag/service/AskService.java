package com.ticketmanagement.rag.service;

import com.ticketmanagement.rag.dto.AskResponse;

/** Answers a natural-language question grounded only in retrieved ticket context. */
public interface AskService {

  /**
   * Retrieves relevant ticket knowledge and generates a grounded answer, or an explicit
   * "no relevant tickets found" response when nothing meets the configured similarity threshold.
   *
   * @param question the caller's natural-language question; assumed already validated (non-blank,
   *     within the length limit)
   * @return the grounded answer and its supporting ticket ID citations
   */
  AskResponse ask(String question);
}
