package com.ticketmanagement.rag.exception;

/**
 * Thrown when the question cannot be embedded or the vector store is unreachable
 * (rag-api-contract.md §4).
 */
public class AiRetrievalUnavailableException extends RuntimeException {

  public AiRetrievalUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
