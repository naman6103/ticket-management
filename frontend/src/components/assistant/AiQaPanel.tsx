"use client";

import { useState, type FormEvent } from "react";
import { askAssistant } from "@/lib/api/assistant";
import { BrowserApiError } from "@/lib/api/browserFetch";
import { mapApiError, type MappedMessage } from "@/lib/errors/mapApiError";
import { ErrorDisplay } from "@/components/errors/ErrorDisplay";
import { AiAnswer } from "./AiAnswer";
import { NoRelevantTicketsState } from "./NoRelevantTicketsState";
import type { AskAnswer } from "@/types/assistant";

/**
 * Ask-the-assistant panel (spec.md User Stories 1-4). Each submission is
 * independent — no multi-turn context is kept (spec.md Assumptions) — so a
 * new question simply replaces whatever `result`/`messages` are currently
 * shown (FR-010), rather than accumulating a history.
 */
export function AiQaPanel() {
  const [question, setQuestion] = useState("");
  const [result, setResult] = useState<AskAnswer | null>(null);
  const [messages, setMessages] = useState<MappedMessage[]>([]);
  const [isSubmitting, setIsSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (question.trim() === "" || isSubmitting) {
      return;
    }
    setResult(null);
    setMessages([]);
    setIsSubmitting(true);
    try {
      const answer = await askAssistant(question);
      setResult(answer);
    } catch (error) {
      if (error instanceof BrowserApiError) {
        setMessages(mapApiError(error.payload));
      } else {
        throw error;
      }
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <div className="aiQaPanel">
      <form onSubmit={handleSubmit} className="form">
        <div className="formField">
          <label htmlFor="ai-question">Ask a question</label>
          <textarea
            id="ai-question"
            value={question}
            onChange={(e) => setQuestion(e.target.value)}
            disabled={isSubmitting}
          />
        </div>
        <button type="submit" disabled={isSubmitting || question.trim() === ""} className="button">
          Ask
        </button>
      </form>
      {isSubmitting && <p role="status">Asking the assistant…</p>}
      {messages.length > 0 && <ErrorDisplay messages={messages} />}
      {result &&
        (result.noRelevantTicketsFound ? (
          <NoRelevantTicketsState />
        ) : (
          <AiAnswer answer={result.answer} ticketIds={result.ticketIds} />
        ))}
    </div>
  );
}
