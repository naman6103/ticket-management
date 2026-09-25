package com.ticketmanagement.rag.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

/**
 * Minimal in-memory {@link VectorStore} test double that supports exactly what
 * {@code TicketIngestionServiceImpl} needs (upsert-by-id, delete-by-{@code ticketId}-filter, and a
 * naive substring search) so ingestion's delete-then-write sequencing can be verified against
 * real store semantics instead of mocked interactions (T022).
 */
class InMemoryFakeVectorStore implements VectorStore {

  private final Map<String, Document> documentsById = new LinkedHashMap<>();

  @Override
  public void add(List<Document> documents) {
    for (Document document : documents) {
      documentsById.put(document.getId(), document);
    }
  }

  @Override
  public void delete(List<String> ids) {
    ids.forEach(documentsById::remove);
  }

  @Override
  public void delete(Filter.Expression filterExpression) {
    if (!(filterExpression.left() instanceof Filter.Key key) || !(filterExpression.right() instanceof Filter.Value value)) {
      throw new UnsupportedOperationException("Fake only supports simple key == value filters");
    }
    documentsById.values().removeIf(doc -> value.value().equals(doc.getMetadata().get(key.key())));
  }

  @Override
  public List<Document> similaritySearch(SearchRequest request) {
    return documentsById.values().stream()
        .filter(doc -> doc.getText() != null && doc.getText().contains(request.getQuery()))
        .toList();
  }
}
