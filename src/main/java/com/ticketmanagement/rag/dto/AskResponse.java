package com.ticketmanagement.rag.dto;

import java.util.List;

/**
 * Response body for {@code POST /api/ai/ask} (rag-api-contract.md §2/§3).
 *
 * @param answer generated answer text, or the fixed "no relevant tickets found" string
 * @param ticketIds distinct ticket IDs whose chunks were included in the prompt; empty iff
 *     {@code noRelevantTicketsFound} is {@code true}
 * @param noRelevantTicketsFound {@code true} when retrieval found nothing at/above the
 *     configured similarity threshold
 */
public record AskResponse(String answer, List<String> ticketIds, boolean noRelevantTicketsFound) {}
