package com.ticketmanagement.rag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ticketmanagement.rag.dto.AskResponse;
import com.ticketmanagement.rag.exception.AiGenerationException;
import com.ticketmanagement.rag.exception.AiRetrievalUnavailableException;
import com.ticketmanagement.ticket.service.CommentService;
import com.ticketmanagement.ticket.service.TicketService;
import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

class AskServiceImplTest {

  private TicketRetrieval ticketRetrieval;
  private ChatClient.Builder chatClientBuilder;
  private ChatClient chatClient;
  private ChatClient.ChatClientRequestSpec requestSpec;
  private ChatClient.CallResponseSpec callResponseSpec;
  private AskServiceImpl service;

  @BeforeEach
  void setUp() {
    ticketRetrieval = mock(TicketRetrieval.class);
    chatClientBuilder = mock(ChatClient.Builder.class);
    chatClient = mock(ChatClient.class);
    requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
    callResponseSpec = mock(ChatClient.CallResponseSpec.class);

    when(chatClientBuilder.build()).thenReturn(chatClient);
    when(chatClient.prompt()).thenReturn(requestSpec);
    when(requestSpec.system(anyString())).thenReturn(requestSpec);
    when(requestSpec.user(anyString())).thenReturn(requestSpec);
    when(requestSpec.call()).thenReturn(callResponseSpec);

    service = new AskServiceImpl(ticketRetrieval, chatClientBuilder);
  }

  @Test
  void citesOnlyTicketIdsFromRetrievedChunksNotFromModelText() {
    Document fromTicketA = new Document("A:description", "Payment gateway timeout", Map.of("ticketId", "A"));
    Document fromTicketB = new Document("B:comment:1", "Retried with backoff", Map.of("ticketId", "B"));
    when(ticketRetrieval.retrieve(anyString())).thenReturn(List.of(fromTicketA, fromTicketB));
    when(callResponseSpec.content())
        .thenReturn("Yes, per ticket C this was a known issue."); // model wrongly mentions C

    AskResponse response = service.ask("Have we seen payment failures before?");

    assertThat(response.ticketIds()).containsExactlyInAnyOrder("A", "B");
    assertThat(response.ticketIds()).doesNotContain("C");
    assertThat(response.noRelevantTicketsFound()).isFalse();
  }

  @Test
  void chatModelFailureThrowsAiGenerationException() {
    Document doc = new Document("A:description", "Payment gateway timeout", Map.of("ticketId", "A"));
    when(ticketRetrieval.retrieve(anyString())).thenReturn(List.of(doc));
    when(requestSpec.call()).thenThrow(new RuntimeException("timed out"));

    assertThatThrownBy(() -> service.ask("question")).isInstanceOf(AiGenerationException.class);
  }

  @Test
  void retrievalFailureThrowsAiRetrievalUnavailableException() {
    when(ticketRetrieval.retrieve(anyString())).thenThrow(new RuntimeException("connection refused"));

    assertThatThrownBy(() -> service.ask("question")).isInstanceOf(AiRetrievalUnavailableException.class);
    verifyNoInteractions(chatClient);
  }

  @Test
  void emptyRetrievalReturnsExactNoRelevantTicketsShapeWithoutCallingChatModel() {
    when(ticketRetrieval.retrieve(anyString())).thenReturn(List.of());

    AskResponse response = service.ask("What caused the satellite launch delay?");

    assertThat(response).isEqualTo(new AskResponse(AskServiceImpl.NO_RELEVANT_TICKETS_MESSAGE, List.of(), true));
    verifyNoInteractions(chatClient);
  }

  @Test
  void retrievedButInsufficientContextIsDistinctFromNoMatch() {
    // Retrieval found something (unlike the empty-retrieval "no relevant tickets" case above),
    // but it doesn't actually answer this specific question — the model says so per the system
    // prompt instruction (architecture.md §5 / FR-016), and the response still cites what was
    // retrieved rather than reporting zero tickets.
    Document fromTicketA = new Document("A:description", "Payment gateway timeout", Map.of("ticketId", "A"));
    when(ticketRetrieval.retrieve(anyString())).thenReturn(List.of(fromTicketA));
    when(callResponseSpec.content())
        .thenReturn("The retrieved tickets do not answer this question.");

    AskResponse response = service.ask("What browser was the customer using?");

    assertThat(response.answer()).isEqualTo("The retrieved tickets do not answer this question.");
    assertThat(response.ticketIds()).containsExactly("A");
    assertThat(response.noRelevantTicketsFound()).isFalse();
  }

  @Test
  void hasNoDependencyOnTicketMutatingOrNotificationServices() {
    Constructor<?>[] constructors = AskServiceImpl.class.getDeclaredConstructors();
    assertThat(constructors).hasSize(1);
    List<Class<?>> parameterTypes = List.of(constructors[0].getParameterTypes());

    assertThat(parameterTypes).doesNotContain(TicketService.class, CommentService.class);
  }
}
