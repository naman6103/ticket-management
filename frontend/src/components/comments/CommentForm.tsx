"use client";

import { useState, type FormEvent } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { addComment } from "@/lib/api/comments";
import { BrowserApiError } from "@/lib/api/browserFetch";
import { mapApiError, type MappedMessage } from "@/lib/errors/mapApiError";
import { ErrorDisplay } from "@/components/errors/ErrorDisplay";
import type { Ticket } from "@/types/ticket";

interface CommentFormProps {
  ticketId: string;
}

/**
 * Add-comment composer (User Story 5). On success, the new comment is
 * appended directly into the `["ticket", id]` query cache so it appears
 * immediately (FR-009, SC-006) without waiting on a refetch or reloading
 * the page.
 */
export function CommentForm({ ticketId }: CommentFormProps) {
  const queryClient = useQueryClient();
  const [content, setContent] = useState("");
  const [messages, setMessages] = useState<MappedMessage[]>([]);
  const [isSubmitting, setIsSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setMessages([]);
    setIsSubmitting(true);
    try {
      const comment = await addComment(ticketId, content);
      queryClient.setQueryData<Ticket>(["ticket", ticketId], (previous) =>
        previous
          ? { ...previous, comments: [...(previous.comments ?? []), comment] }
          : previous,
      );
      setContent("");
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
    <form onSubmit={handleSubmit} className="form">
      <div className="formField">
        <label htmlFor="comment-content">Add a comment</label>
        <textarea
          id="comment-content"
          value={content}
          onChange={(e) => setContent(e.target.value)}
        />
      </div>
      {messages.length > 0 && <ErrorDisplay messages={messages} />}
      <button type="submit" disabled={isSubmitting} className="button">
        Add comment
      </button>
    </form>
  );
}
