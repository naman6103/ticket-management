"use client";

import { useEffect, useState } from "react";
import type { TicketStatus } from "@/types/ticket";

const STATUSES: TicketStatus[] = ["OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED", "CANCELLED"];
const DEBOUNCE_MS = 300;

interface SearchFilterBarProps {
  q: string;
  status: TicketStatus | undefined;
  onChange: (next: { q: string; status: TicketStatus | undefined }) => void;
}

/** Debounced keyword search + status filter, combinable (FR-004). */
export function SearchFilterBar({ q, status, onChange }: SearchFilterBarProps) {
  const [draftQ, setDraftQ] = useState(q);

  useEffect(() => {
    const handle = setTimeout(() => {
      if (draftQ !== q) {
        onChange({ q: draftQ, status });
      }
    }, DEBOUNCE_MS);
    return () => clearTimeout(handle);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [draftQ]);

  return (
    <div className="filterBar">
      <input
        type="search"
        aria-label="Search tickets"
        placeholder="Search by title or description"
        value={draftQ}
        onChange={(event) => setDraftQ(event.target.value)}
      />
      <select
        aria-label="Filter by status"
        value={status ?? ""}
        onChange={(event) =>
          onChange({
            q: draftQ,
            status: event.target.value === "" ? undefined : (event.target.value as TicketStatus),
          })
        }
      >
        <option value="">All statuses</option>
        {STATUSES.map((s) => (
          <option key={s} value={s}>
            {s}
          </option>
        ))}
      </select>
    </div>
  );
}
