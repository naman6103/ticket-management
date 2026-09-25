package com.ticketmanagement.rag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ticketmanagement.rag.config.RagRetrievalProperties;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

class TicketRetrievalTest {

  private VectorStore vectorStore;
  private RagRetrievalProperties retrievalProperties;
  private TicketRetrieval ticketRetrieval;

  @BeforeEach
  void setUp() {
    vectorStore = mock(VectorStore.class);
    retrievalProperties = new RagRetrievalProperties();
    ticketRetrieval = new TicketRetrieval(vectorStore, retrievalProperties);
  }

  @Test
  void searchUsesConfiguredTopKAndSimilarityThreshold() {
    retrievalProperties.setTopK(7);
    retrievalProperties.setSimilarityThreshold(0.42);
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

    ticketRetrieval.retrieve("Have we seen payment failures before?");

    ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
    verify(vectorStore).similaritySearch(captor.capture());
    assertThat(captor.getValue().getTopK()).isEqualTo(7);
    assertThat(captor.getValue().getSimilarityThreshold()).isEqualTo(0.42);
  }

  @Test
  void changingConfiguredValuesChangesTheSearchRequest() {
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

    retrievalProperties.setTopK(3);
    ticketRetrieval.retrieve("q1");
    retrievalProperties.setTopK(9);
    ticketRetrieval.retrieve("q2");

    ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
    verify(vectorStore, times(2)).similaritySearch(captor.capture());
    assertThat(captor.getAllValues().get(0).getTopK()).isEqualTo(3);
    assertThat(captor.getAllValues().get(1).getTopK()).isEqualTo(9);
  }

  @Test
  void questionMentioningATicketIdTriggersAnExactIdPreFilterAlongsideSemanticSearch() {
    // T025: measured evaluation showed semantic search alone finds 0% of ticket-ID-specific
    // questions (an ID has no semantic meaning to match on). The semantic search below returns
    // nothing on its own; only the exact-ID filter search finds the ticket.
    String ticketId = "3fa85f64-5717-4562-b3fc-2c963f66afa6";
    Document exactMatch = new Document(
        ticketId + ":description", "Refund failed for order 12345", Map.of("ticketId", ticketId));
    when(vectorStore.similaritySearch(argMatching(req -> !req.hasFilterExpression()))).thenReturn(List.of());
    when(vectorStore.similaritySearch(argMatching(SearchRequest::hasFilterExpression)))
        .thenReturn(List.of(exactMatch));

    List<Document> result = ticketRetrieval.retrieve("What was the resolution for ticket " + ticketId + "?");

    assertThat(result).containsExactly(exactMatch);
  }

  @Test
  void exactIdPreFilterDoesNotDuplicateChunksAlreadyFoundSemantically() {
    String ticketId = "3fa85f64-5717-4562-b3fc-2c963f66afa6";
    Document doc =
        new Document(ticketId + ":description", "Refund failed for order 12345", Map.of("ticketId", ticketId));
    when(vectorStore.similaritySearch(argMatching(req -> !req.hasFilterExpression()))).thenReturn(List.of(doc));
    when(vectorStore.similaritySearch(argMatching(SearchRequest::hasFilterExpression))).thenReturn(List.of(doc));

    List<Document> result = ticketRetrieval.retrieve("What was the resolution for ticket " + ticketId + "?");

    assertThat(result).containsExactly(doc);
  }

  @Test
  void questionWithoutATicketIdSkipsTheExactIdPreFilterEntirely() {
    when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

    ticketRetrieval.retrieve("Have we seen payment failures before?");

    verify(vectorStore, times(1)).similaritySearch(any(SearchRequest.class));
  }

  private static SearchRequest argMatching(java.util.function.Predicate<SearchRequest> predicate) {
    return org.mockito.ArgumentMatchers.argThat(req -> req != null && predicate.test(req));
  }
}
