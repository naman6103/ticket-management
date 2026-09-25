"use client";

import { useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { transitionTicket } from "@/lib/api/tickets";
import { BrowserApiError } from "@/lib/api/browserFetch";
import { mapApiError, type MappedMessage } from "@/lib/errors/mapApiError";
import { ErrorDisplay } from "@/components/errors/ErrorDisplay";
import { NEXT_STATUSES } from "@/lib/transitions";
import type { Ticket, TicketStatus } from "@/types/ticket";

interface TransitionControlProps {
  ticketId: string;
  currentStatus: TicketStatus;
}

/**
 * Status transition control (User Story 6). Only offers statuses the
 * client-side lookup table considers reachable from the current status
 * (FR-010) — the backend's response on submission is still authoritative
 * (FR-013): a 409 shows its exact rejection message and resets selection.
 */
export function TransitionControl({ ticketId, currentStatus }: TransitionControlProps) {
  const queryClient = useQueryClient();
  const [selected, setSelected] = useState<TicketStatus | "">("");
  const [messages, setMessages] = useState<MappedMessage[]>([]);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const options = NEXT_STATUSES[currentStatus];

  if (options.length === 0) {
    return null;
  }

  async function handleConfirm() {
    if (selected === "") return;
    setMessages([]);
    setIsSubmitting(true);
    try {
      const updated = await transitionTicket(ticketId, selected);
      // transitions endpoint also returns TicketResponse.withoutComments —
      // preserve the comments this view already has (same fix as edit).
      queryClient.setQueryData<Ticket>(["ticket", ticketId], (previous) =>
        previous ? { ...updated, comments: previous.comments } : previous,
      );
      setSelected("");
    } catch (error) {
      if (error instanceof BrowserApiError) {
        setMessages(mapApiError(error.payload));
        setSelected("");
      } else {
        throw error;
      }
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <div className="transitionControl">
      <label htmlFor="transition-select">Transition status</label>
      <select
        id="transition-select"
        value={selected}
        onChange={(e) => setSelected(e.target.value as TicketStatus)}
      >
        <option value="">Select next status</option>
        {options.map((status) => (
          <option key={status} value={status}>
            {status}
          </option>
        ))}
      </select>
      <button
        type="button"
        disabled={selected === "" || isSubmitting}
        onClick={handleConfirm}
        className="button"
      >
        Confirm
      </button>
      {messages.length > 0 && <ErrorDisplay messages={messages} />}
    </div>
  );
}
