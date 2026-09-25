package com.ticketmanagement.rag.service;

import com.ticketmanagement.rag.dto.AskResponse;
import com.ticketmanagement.rag.exception.AiGenerationException;
import com.ticketmanagement.rag.exception.AiRetrievalUnavailableException;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

/**
 * Single retrieval-then-generate flow (FR-013): retrieves via {@link TicketRetrieval}, and — only
 * when relevant chunks are found — asks the chat model to answer strictly from that retrieved
 * context (architecture.md §2, §5).
 *
 * <p>Deliberately has no dependency on any ticket-mutating or notification service and registers
 * no tool/function callbacks with its {@link ChatClient} — there is no code path a question can
 * reach that changes state outside of this single request (FR-013).
 */
@Service
public class AskServiceImpl implements AskService {

  static final String NO_RELEVANT_TICKETS_MESSAGE = "No relevant tickets found for this question.";

  private static final String SYSTEM_PROMPT_TEMPLATE =
      """
      You are a support assistant answering questions about ticket history.
      Answer ONLY using the ticket context below — never use general knowledge.
      If the context does not answer the specific question asked, say so explicitly
      rather than guessing or stretching the context into an answer it doesn't support.

      Ticket context:
      %s
      """;

  private final TicketRetrieval ticketRetrieval;
  private final ChatClient chatClient;

  public AskServiceImpl(TicketRetrieval ticketRetrieval, ChatClient.Builder chatClientBuilder) {
    this.ticketRetrieval = ticketRetrieval;
    this.chatClient = chatClientBuilder.build();
  }

  @Override
  public AskResponse ask(String question) {
    List<Document> retrieved = retrieve(question);
    if (retrieved.isEmpty()) {
      return new AskResponse(NO_RELEVANT_TICKETS_MESSAGE, List.of(), true);
    }
    return generate(question, retrieved);
  }

  private List<Document> retrieve(String question) {
    try {
      return ticketRetrieval.retrieve(question);
    } catch (RuntimeException ex) {
      throw new AiRetrievalUnavailableException("Failed to search the ticket knowledge base", ex);
    }
  }

  private AskResponse generate(String question, List<Document> retrieved) {
    String context = retrieved.stream()
        .map(doc -> "Ticket " + doc.getMetadata().get("ticketId") + ": " + doc.getText())
        .collect(Collectors.joining("\n\n"));
    List<String> ticketIds = retrieved.stream()
        .map(doc -> String.valueOf(doc.getMetadata().get("ticketId")))
        .distinct()
        .toList();

    String answer;
    try {
      answer = chatClient
          .prompt()
          .system(SYSTEM_PROMPT_TEMPLATE.formatted(context))
          .user(question)
          .call()
          .content();
    } catch (RuntimeException ex) {
      throw new AiGenerationException("Chat model call failed", ex);
    }
    return new AskResponse(answer, ticketIds, false);
  }
}
