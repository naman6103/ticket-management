package com.ticketmanagement.rag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for {@code POST /api/ai/ask} (rag-api-contract.md §1). */
public record AskRequest(@NotBlank @Size(max = 1000) String question) {}
