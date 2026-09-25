"use client";

import { useState, type FormEvent } from "react";
import { createTicket, updateTicket, type TicketUpdateInput } from "@/lib/api/tickets";
import { BrowserApiError } from "@/lib/api/browserFetch";
import { mapApiError, type MappedMessage } from "@/lib/errors/mapApiError";
import { ErrorDisplay } from "@/components/errors/ErrorDisplay";
import { AssigneeCombobox } from "./AssigneeCombobox";
import type { Priority, Ticket } from "@/types/ticket";

const PRIORITIES: Priority[] = ["LOW", "MEDIUM", "HIGH"];

type TicketFormProps =
  | { mode: "create"; onSuccess: (ticket: Ticket) => void }
  | { mode: "edit"; ticket: Ticket; onSuccess: (ticket: Ticket) => void };

/**
 * Ticket create/edit form (User Stories 2 and 4). Client-side pre-flight
 * hints are display-only (FR-011) — the backend's response is always
 * authoritative; any backend rejection is shown verbatim and entered
 * values are kept as-is so the user can fix and resubmit (spec.md User
 * Story 2 Scenario 3, User Story 4 Scenario 2). In edit mode, only fields
 * the user actually touched are sent (FR-007) — untouched fields are
 * omitted from the PATCH body entirely, not merely left at their prior
 * value, so a field never accidentally "changes" itself back to its own
 * pre-fill.
 */
export function TicketForm(props: TicketFormProps) {
  const isEdit = props.mode === "edit";
  const initial = isEdit ? props.ticket : undefined;

  const [title, setTitle] = useState(initial?.title ?? "");
  const [description, setDescription] = useState(initial?.description ?? "");
  const [priority, setPriority] = useState<Priority | "">(initial?.priority ?? "");
  const [assignee, setAssignee] = useState(initial?.assignee ?? "");
  const [touched, setTouched] = useState<Set<string>>(new Set());
  const [fieldMessages, setFieldMessages] = useState<Record<string, string>>({});
  const [bannerMessages, setBannerMessages] = useState<MappedMessage[]>([]);
  const [isSubmitting, setIsSubmitting] = useState(false);

  function markTouched(field: string) {
    setTouched((prev) => {
      const next = new Set(prev);
      next.add(field);
      return next;
    });
  }

  function validateClientSide(): Record<string, string> {
    const errors: Record<string, string> = {};
    const checks: [string, string][] = [
      ["title", title],
      ["description", description],
      ["priority", priority],
      ["assignee", assignee],
    ];
    for (const [field, value] of checks) {
      if (isEdit && !touched.has(field)) continue;
      if (value.trim() === "") errors[field] = "must not be blank";
    }
    return errors;
  }

  function buildEditPatch(): TicketUpdateInput {
    const patch: TicketUpdateInput = {};
    if (touched.has("title")) patch.title = title;
    if (touched.has("description")) patch.description = description;
    if (touched.has("priority") && priority !== "") patch.priority = priority as Priority;
    if (touched.has("assignee")) patch.assignee = assignee;
    return patch;
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setBannerMessages([]);

    const clientErrors = validateClientSide();
    if (Object.keys(clientErrors).length > 0) {
      setFieldMessages(clientErrors);
      return;
    }
    setFieldMessages({});
    setIsSubmitting(true);

    try {
      const ticket = isEdit
        ? await updateTicket(props.ticket.id, buildEditPatch())
        : await createTicket({ title, description, priority: priority as Priority, assignee });
      props.onSuccess(ticket);
    } catch (error) {
      if (error instanceof BrowserApiError) {
        const messages = mapApiError(error.payload);
        const nextFieldMessages: Record<string, string> = {};
        const nextBannerMessages: MappedMessage[] = [];
        for (const message of messages) {
          if (message.kind === "field") {
            nextFieldMessages[message.fieldName] = message.text;
          } else {
            nextBannerMessages.push(message);
          }
        }
        setFieldMessages(nextFieldMessages);
        setBannerMessages(nextBannerMessages);
      } else {
        throw error;
      }
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="form">
      {bannerMessages.length > 0 && <ErrorDisplay messages={bannerMessages} />}

      <div className="formField">
        <label htmlFor="title">Title</label>
        <input
          id="title"
          value={title}
          onChange={(e) => {
            setTitle(e.target.value);
            markTouched("title");
          }}
        />
        {fieldMessages.title && (
          <ErrorDisplay
            messages={[{ kind: "field", fieldName: "title", text: fieldMessages.title }]}
          />
        )}
      </div>

      <div className="formField">
        <label htmlFor="description">Description</label>
        <textarea
          id="description"
          value={description}
          onChange={(e) => {
            setDescription(e.target.value);
            markTouched("description");
          }}
        />
        {fieldMessages.description && (
          <ErrorDisplay
            messages={[
              { kind: "field", fieldName: "description", text: fieldMessages.description },
            ]}
          />
        )}
      </div>

      <div className="formField">
        <label htmlFor="priority">Priority</label>
        <select
          id="priority"
          value={priority}
          onChange={(e) => {
            setPriority(e.target.value as Priority);
            markTouched("priority");
          }}
        >
          <option value="">Select priority</option>
          {PRIORITIES.map((p) => (
            <option key={p} value={p}>
              {p}
            </option>
          ))}
        </select>
        {fieldMessages.priority && (
          <ErrorDisplay
            messages={[{ kind: "field", fieldName: "priority", text: fieldMessages.priority }]}
          />
        )}
      </div>

      <div className="formField">
        <AssigneeCombobox
          id="assignee"
          label="Assignee"
          value={assignee}
          onChange={(value) => {
            setAssignee(value);
            markTouched("assignee");
          }}
        />
        {fieldMessages.assignee && (
          <ErrorDisplay
            messages={[{ kind: "field", fieldName: "assignee", text: fieldMessages.assignee }]}
          />
        )}
      </div>

      <button type="submit" disabled={isSubmitting} className="button">
        {isEdit ? "Save changes" : "Create ticket"}
      </button>
    </form>
  );
}
