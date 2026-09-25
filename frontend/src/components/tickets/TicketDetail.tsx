"use client";

import { useState } from "react";
import { StatusBadge } from "./StatusBadge";
import { TicketForm } from "./TicketForm";
import { TransitionControl } from "./TransitionControl";
import { CommentList } from "@/components/comments/CommentList";
import { CommentForm } from "@/components/comments/CommentForm";
import type { Ticket } from "@/types/ticket";

interface TicketDetailProps {
  ticket: Ticket;
  onUpdated?: (ticket: Ticket) => void;
}

/**
 * Ticket detail view (spec.md User Stories 3 and 4). Toggles in place
 * between read-only display and the edit form on the same route — no
 * navigation on entering/exiting edit mode or on a successful save.
 */
export function TicketDetail({ ticket, onUpdated }: TicketDetailProps) {
  const [isEditing, setIsEditing] = useState(false);

  if (isEditing) {
    return (
      <div className="card">
        <TicketForm
          mode="edit"
          ticket={ticket}
          onSuccess={(updated) => {
            // PATCH's response omits `comments` (backend returns
            // TicketResponse.withoutComments) — preserve the comments this
            // view already has rather than letting them disappear.
            onUpdated?.({ ...updated, comments: ticket.comments });
            setIsEditing(false);
          }}
        />
      </div>
    );
  }

  return (
    <article className="card">
      <header className="detailHeader">
        <h1>{ticket.title}</h1>
        <StatusBadge status={ticket.status} />
        <button type="button" onClick={() => setIsEditing(true)} className="buttonSecondary button">
          Edit
        </button>
        <TransitionControl ticketId={ticket.id} currentStatus={ticket.status} />
      </header>

      <dl className="fieldGrid">
        <dt>Description</dt>
        <dd>{ticket.description}</dd>

        <dt>Priority</dt>
        <dd>{ticket.priority}</dd>

        <dt>Assignee</dt>
        <dd>{ticket.assignee}</dd>

        <dt>Category</dt>
        <dd>{ticket.category ?? "—"}</dd>

        <dt>Created</dt>
        <dd>
          <time dateTime={ticket.createdAt}>{ticket.createdAt}</time>
        </dd>

        <dt>Updated</dt>
        <dd>
          <time dateTime={ticket.updatedAt}>{ticket.updatedAt}</time>
        </dd>
      </dl>

      <section>
        <h2>Comments</h2>
        <CommentList comments={ticket.comments ?? []} />
        <CommentForm ticketId={ticket.id} />
      </section>
    </article>
  );
}
