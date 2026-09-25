package com.ticketmanagement.rag.eval;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketmanagement.rag.config.RagRetrievalProperties;
import com.ticketmanagement.rag.service.TicketIngestionService;
import com.ticketmanagement.rag.service.TicketRetrieval;
import com.ticketmanagement.ticket.dto.CommentCreateRequest;
import com.ticketmanagement.ticket.dto.TicketCreateRequest;
import com.ticketmanagement.ticket.entity.Ticket;
import com.ticketmanagement.ticket.service.CommentService;
import com.ticketmanagement.ticket.service.TicketService;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Retrieval-only evaluation harness (test-strategy.md §2, evaluation-strategy.md §2): seeds the
 * known fixture tickets ({@link EvalTicketFixtures}), runs the labeled golden set
 * (rag-eval/golden-set.json) through {@link TicketRetrieval} — the same retrieval path production
 * uses, including the exact-ID pre-filter (FR-014, T025) — and reports Recall@K, Precision@K, and
 * no-match accuracy broken out per golden-set {@code type}, plus a semantic-only
 * similarity-threshold sweep to inform tuning.
 *
 * <p>Deliberately named so Surefire's default {@code **&#47;*Test.java} / {@code **&#47;*Tests.java}
 * inclusion pattern does not pick it up: this is a quality report, not a CI must-pass gate
 * (constitution Principle III). Run explicitly with:
 *
 * <pre>mvn test -Dtest=RagRetrievalEvaluationRunner -Dspring.elasticsearch.uris=... -Dspring.ai.ollama.base-url=...</pre>
 */
@SpringBootTest(
    properties = {
      "spring.ai.vectorstore.elasticsearch.initialize-schema=true",
    })
@ActiveProfiles("test")
class RagRetrievalEvaluationRunner {

  private static final String INDEX_NAME = "ticket-knowledge";
  private static final int DIAGNOSTIC_TOP_K = 20;
  private static final double[] THRESHOLD_SWEEP = {0.0, 0.2, 0.3, 0.4, 0.5, 0.6, 0.65, 0.7};
  private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)}}");

  @Autowired private TicketService ticketService;
  @Autowired private CommentService commentService;
  @Autowired private TicketIngestionService ingestionService;
  @Autowired private TicketRetrieval ticketRetrieval;
  @Autowired private VectorStore vectorStore;
  @Autowired private RagRetrievalProperties retrievalProperties;
  @Autowired private ElasticsearchClient elasticsearchClient;

  private Map<String, String> ticketKeyToId;

  @BeforeEach
  void resetIndexAndReseed() throws IOException {
    // Each run must be self-contained: without this, re-running the harness accumulates
    // duplicate near-identical fixture content across runs under different ticket IDs, silently
    // corrupting precision.
    elasticsearchClient.deleteByQuery(
        DeleteByQueryRequest.of(d -> d.index(INDEX_NAME).query(q -> q.matchAll(m -> m)).refresh(true)));
    seedFixtureTickets();
  }

  @Test
  void evaluateRetrievalAgainstGoldenSet() throws Exception {
    List<GoldenSetItem> goldenSet = loadGoldenSet();
    List<ResolvedItem> resolvedItems = goldenSet.stream()
        .map(item -> new ResolvedItem(item, substitutePlaceholders(item.question()), resolveExpectedIds(item.expectedTicketKeys())))
        .toList();

    System.out.println();
    System.out.println("=== RAG Retrieval Evaluation Report ===");
    System.out.println("--- Production retrieval path (TicketRetrieval: semantic search + exact-ID pre-filter) ---");
    reportProductionRetrieval(resolvedItems);

    System.out.println();
    System.out.println("--- Semantic-only similarity-threshold sweep (top-K=" + retrievalProperties.getTopK() + ") ---");
    List<ScoredItem> scoredItems = new ArrayList<>();
    for (ResolvedItem resolved : resolvedItems) {
      List<Document> candidates = vectorStore.similaritySearch(
          SearchRequest.builder().query(resolved.question()).topK(DIAGNOSTIC_TOP_K).similarityThresholdAll().build());
      scoredItems.add(new ScoredItem(resolved, candidates));
    }
    for (double threshold : THRESHOLD_SWEEP) {
      printSemanticOnlyMetricsAtThreshold(scoredItems, retrievalProperties.getTopK(), threshold);
    }
    System.out.println("========================================");
    System.out.println();
  }

  private void reportProductionRetrieval(List<ResolvedItem> resolvedItems) {
    Map<String, CategoryStats> statsByType = new HashMap<>();
    for (ResolvedItem resolved : resolvedItems) {
      Set<String> retrievedIds = new HashSet<>();
      for (Document doc : ticketRetrieval.retrieve(resolved.question())) {
        Object ticketId = doc.getMetadata().get("ticketId");
        if (ticketId != null) {
          retrievedIds.add(ticketId.toString());
        }
      }
      accumulate(statsByType, resolved.item().type(), resolved.expectedIds(), retrievedIds);
    }

    List<String> typesInOrder =
        List.of("topical", "specific-ticket", "no-match", "low-similarity", "retrieved-but-insufficient");
    for (String type : typesInOrder) {
      CategoryStats stats = statsByType.get(type);
      if (stats == null) {
        continue;
      }
      if (stats.isNoMatchStyle) {
        System.out.printf(
            "%-25s items=%-3d no-match-accuracy=%.1f%%%n", type, stats.total, percent(stats.noMatchCorrect, stats.total));
      } else {
        System.out.printf(
            "%-25s items=%-3d recall=%.1f%%  precision=%.1f%%%n",
            type, stats.total, percentSum(stats.recallSum, stats.total), percentSum(stats.precisionSum, stats.total));
      }
    }
    OverallStats overall = aggregateExcludingNoMatchStyle(statsByType);
    System.out.printf(
        "%-25s items=%-3d recall=%.1f%%  precision=%.1f%%  (no-match-accuracy=%.1f%%)%n",
        "OVERALL",
        overall.count,
        percentSum(overall.recallSum, overall.count),
        percentSum(overall.precisionSum, overall.count),
        percent(overall.noMatchCorrect, overall.noMatchTotal));
  }

  private void printSemanticOnlyMetricsAtThreshold(List<ScoredItem> scoredItems, int topK, double threshold) {
    Map<String, CategoryStats> statsByType = new HashMap<>();
    for (ScoredItem scored : scoredItems) {
      Set<String> retrievedIds = new HashSet<>();
      int taken = 0;
      for (Document doc : scored.candidates()) {
        if (taken >= topK) {
          break;
        }
        Double score = doc.getScore();
        if (score != null && score < threshold) {
          continue;
        }
        Object ticketId = doc.getMetadata().get("ticketId");
        if (ticketId != null) {
          retrievedIds.add(ticketId.toString());
        }
        taken++;
      }
      accumulate(statsByType, scored.resolved().item().type(), scored.resolved().expectedIds(), retrievedIds);
    }

    OverallStats overall = aggregateExcludingNoMatchStyle(statsByType);
    System.out.printf(
        "threshold=%.2f  overall(excl. no-match/low-sim) recall=%.1f%%  precision=%.1f%%  no-match-accuracy=%.1f%%%n",
        threshold,
        percentSum(overall.recallSum, overall.count),
        percentSum(overall.precisionSum, overall.count),
        percent(overall.noMatchCorrect, overall.noMatchTotal));
  }

  private void accumulate(
      Map<String, CategoryStats> statsByType, String type, Set<String> expectedIds, Set<String> retrievedIds) {
    CategoryStats stats = statsByType.computeIfAbsent(type, t -> new CategoryStats());
    stats.total++;
    if (expectedIds.isEmpty()) {
      stats.isNoMatchStyle = true;
      if (retrievedIds.isEmpty()) {
        stats.noMatchCorrect++;
      }
    } else {
      Set<String> truePositives = new HashSet<>(expectedIds);
      truePositives.retainAll(retrievedIds);
      stats.recallSum += (double) truePositives.size() / expectedIds.size();
      stats.precisionSum += retrievedIds.isEmpty() ? 0.0 : (double) truePositives.size() / retrievedIds.size();
    }
  }

  private OverallStats aggregateExcludingNoMatchStyle(Map<String, CategoryStats> statsByType) {
    OverallStats overall = new OverallStats();
    for (CategoryStats stats : statsByType.values()) {
      if (stats.isNoMatchStyle) {
        overall.noMatchCorrect += stats.noMatchCorrect;
        overall.noMatchTotal += stats.total;
      } else {
        overall.recallSum += stats.recallSum;
        overall.precisionSum += stats.precisionSum;
        overall.count += stats.total;
      }
    }
    return overall;
  }

  private static double percent(int numerator, int denominator) {
    return denominator == 0 ? 0 : (double) numerator / denominator * 100;
  }

  private static double percentSum(double sum, int denominator) {
    return denominator == 0 ? 0 : sum / denominator * 100;
  }

  private void seedFixtureTickets() {
    ticketKeyToId = new HashMap<>();
    for (EvalTicketFixtures fixture : EvalTicketFixtures.all()) {
      Ticket ticket = ticketService.create(new TicketCreateRequest(
          fixture.title(), fixture.description(), fixture.priority(), fixture.assignee(), fixture.category()));
      for (String comment : fixture.comments()) {
        commentService.addComment(ticket.getId(), new CommentCreateRequest(comment));
      }
      // Ingest synchronously (bypassing the async event listener) so results are deterministic
      // for this evaluation run rather than racing a background thread.
      ingestionService.reingest(ticket.getId());
      ticketKeyToId.put(fixture.key(), ticket.getId().toString());
    }
  }

  private String substitutePlaceholders(String question) {
    Matcher matcher = PLACEHOLDER.matcher(question);
    StringBuilder result = new StringBuilder();
    while (matcher.find()) {
      String resolvedId = ticketKeyToId.get(matcher.group(1));
      matcher.appendReplacement(result, resolvedId == null ? matcher.group() : resolvedId);
    }
    matcher.appendTail(result);
    return result.toString();
  }

  private Set<String> resolveExpectedIds(List<String> keys) {
    Set<String> ids = new HashSet<>();
    for (String key : keys) {
      ids.add(ticketKeyToId.get(key));
    }
    return ids;
  }

  private List<GoldenSetItem> loadGoldenSet() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    try (InputStream in = getClass().getResourceAsStream("/rag-eval/golden-set.json")) {
      return mapper.readValue(in, mapper.getTypeFactory().constructCollectionType(List.class, GoldenSetItem.class));
    }
  }

  private record ResolvedItem(GoldenSetItem item, String question, Set<String> expectedIds) {}

  private record ScoredItem(ResolvedItem resolved, List<Document> candidates) {}

  private static final class CategoryStats {
    int total;
    double recallSum;
    double precisionSum;
    int noMatchCorrect;
    boolean isNoMatchStyle;
  }

  private static final class OverallStats {
    double recallSum;
    double precisionSum;
    int count;
    int noMatchCorrect;
    int noMatchTotal;
  }
}
