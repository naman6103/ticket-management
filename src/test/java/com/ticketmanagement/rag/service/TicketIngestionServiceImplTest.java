package com.ticketmanagement.rag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ticketmanagement.ticket.entity.Priority;
import com.ticketmanagement.ticket.entity.Ticket;
import com.ticketmanagement.ticket.repository.CommentRepository;
import com.ticketmanagement.ticket.repository.TicketRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TicketIngestionServiceImplTest {

  @Mock private TicketRepository ticketRepository;
  @Mock private CommentRepository commentRepository;
  @Mock private VectorStore vectorStore;

  private TicketIngestionServiceImpl service;
  private UUID ticketId;
  private Ticket ticket;

  @BeforeEach
  void setUp() {
    service = new TicketIngestionServiceImpl(
        ticketRepository, commentRepository, new TicketChunkBuilder(), vectorStore);
    ticketId = UUID.randomUUID();
    ticket = new Ticket("Payment fails", "Checkout times out", Priority.HIGH, "alice", "billing");
    ReflectionTestUtils.setField(ticket, "id", ticketId);
    ReflectionTestUtils.setField(ticket, "updatedAt", java.time.Instant.now());
  }

  @Test
  void deletesStaleChunksThenUpsertsCurrentOnesThenMarksIndexed() {
    when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
    when(commentRepository.findByTicketIdOrderByCreatedAtAscIdAsc(ticketId)).thenReturn(List.of());

    service.reingest(ticketId);

    ArgumentCaptor<Filter.Expression> filterCaptor = ArgumentCaptor.forClass(Filter.Expression.class);
    verify(vectorStore).delete(filterCaptor.capture());
    assertThat(filterCaptor.getValue().toString()).contains(ticketId.toString());

    ArgumentCaptor<List<Document>> documentsCaptor = ArgumentCaptor.forClass(List.class);
    verify(vectorStore).add(documentsCaptor.capture());
    List<Document> documents = documentsCaptor.getValue();
    assertThat(documents).hasSize(1);
    assertThat(documents.get(0).getId()).endsWith(":description");
    assertThat(documents.get(0).getMetadata()).containsEntry("ticketId", ticketId.toString());

    verify(ticketRepository).markKnowledgeIndexed(ticketId);
  }

  @Test
  void doesNothingWhenTicketNoLongerExists() {
    when(ticketRepository.findById(ticketId)).thenReturn(Optional.empty());

    service.reingest(ticketId);

    verify(vectorStore, never()).delete(any(Filter.Expression.class));
    verify(vectorStore, never()).add(anyList());
    verify(ticketRepository, never()).markKnowledgeIndexed(any());
  }

  @Test
  void deleteAlwaysRunsBeforeAddSoStaleChunksCannotOutliveTheNewOnes() {
    when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
    when(commentRepository.findByTicketIdOrderByCreatedAtAscIdAsc(ticketId)).thenReturn(List.of());

    service.reingest(ticketId);

    var inOrder = org.mockito.Mockito.inOrder(vectorStore, ticketRepository);
    inOrder.verify(vectorStore, times(1)).delete(any(Filter.Expression.class));
    inOrder.verify(vectorStore, times(1)).add(anyList());
    inOrder.verify(ticketRepository, times(1)).markKnowledgeIndexed(ticketId);
  }

  @Test
  void updatedDescriptionSupersedesTheOldOneInSearchResults() {
    // T022: verifies against a store with real (if naive) search semantics — not mocked
    // interactions — that FR-009's supersession guarantee actually holds end-to-end.
    InMemoryFakeVectorStore fakeStore = new InMemoryFakeVectorStore();
    TicketIngestionServiceImpl serviceWithFakeStore =
        new TicketIngestionServiceImpl(ticketRepository, commentRepository, new TicketChunkBuilder(), fakeStore);
    when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
    when(commentRepository.findByTicketIdOrderByCreatedAtAscIdAsc(ticketId)).thenReturn(List.of());

    serviceWithFakeStore.reingest(ticketId);
    assertThat(fakeStore.similaritySearch(searchFor("Checkout times out"))).isNotEmpty();

    ticket.setDescription("Refund failed instead");
    serviceWithFakeStore.reingest(ticketId);

    assertThat(fakeStore.similaritySearch(searchFor("Checkout times out"))).isEmpty();
    assertThat(fakeStore.similaritySearch(searchFor("Refund failed instead"))).isNotEmpty();
  }

  private static org.springframework.ai.vectorstore.SearchRequest searchFor(String query) {
    return org.springframework.ai.vectorstore.SearchRequest.builder().query(query).build();
  }
}
