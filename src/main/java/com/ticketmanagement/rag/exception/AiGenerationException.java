package com.ticketmanagement.rag.exception;

/** Thrown when the chat model call fails or times out after a successful retrieval (FR-015). */
public class AiGenerationException extends RuntimeException {

  public AiGenerationException(String message, Throwable cause) {
    super(message, cause);
  }
}
