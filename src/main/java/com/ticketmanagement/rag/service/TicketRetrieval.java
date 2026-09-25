package com.ticketmanagement.rag.service;

import com.ticketmanagement.rag.config.RagRetrievalProperties;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Component;

/**
 * Retrieval step shared by {@link AskServiceImpl} (production) and the retrieval evaluation
 * harness ({@code rag.eval.RagRetrievalEvaluationRunner}) — a single source of truth so the
 * harness's measured recall/precision actually reflects what a real question sees, including the
 * exact-ID pre-filter (FR-014, T025).
 */
@Component
public class TicketRetrieval {

  /**
   * Matches a ticket ID (Feature 1's UUID identifier) mentioned literally in a question, e.g.
   * "What was the resolution for ticket 3fa85f64-5717-4562-b3fc-2c963f66afa6?" (FR-014). Measured
   * evaluation (evaluation-strategy.md, T025) showed semantic search alone found 0% of
   * "specific-ticket" golden-set items — an identifier carries no semantic meaning an embedding
   * model can match on — so this exact-match pre-filter supplements (never replaces) semantic
   * search.
   */
  private static final Pattern TICKET_ID_PATTERN =
      Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

  private final VectorStore vectorStore;
  private final RagRetrievalProperties retrievalProperties;

  public TicketRetrieval(VectorStore vectorStore, RagRetrievalProperties retrievalProperties) {
    this.vectorStore = vectorStore;
    this.retrievalProperties = retrievalProperties;
  }

  /**
   * Runs the configured semantic search, plus — when the question literally mentions a ticket ID
   * — an exact-match pre-filter for that ticket, merged and de-duplicated by chunk ID.
   *
   * @param question the caller's natural-language question
   * @return retrieved chunks in semantic-then-exact-match order; never {@code null}
   */
  public List<Document> retrieve(String question) {
    SearchRequest semanticRequest = SearchRequest.builder()
        .query(question)
        .topK(retrievalProperties.getTopK())
        .similarityThreshold(retrievalProperties.getSimilarityThreshold())
        .build();
    // LinkedHashMap keyed by chunk ID: preserves semantic-ranking order while de-duplicating
    // against the exact-ID pre-filter below (a chunk should never be counted/cited twice).
    Map<String, Document> merged = new LinkedHashMap<>();
    for (Document doc : vectorStore.similaritySearch(semanticRequest)) {
      merged.put(doc.getId(), doc);
    }

    Matcher matcher = TICKET_ID_PATTERN.matcher(question);
    if (matcher.find()) {
      String mentionedTicketId = matcher.group();
      SearchRequest exactIdRequest = SearchRequest.builder()
          .query(question)
          .topK(retrievalProperties.getTopK())
          .similarityThresholdAll()
          .filterExpression(new FilterExpressionBuilder().eq("ticketId", mentionedTicketId).build())
          .build();
      for (Document doc : vectorStore.similaritySearch(exactIdRequest)) {
        merged.putIfAbsent(doc.getId(), doc);
      }
    }
    return List.copyOf(merged.values());
  }
}
