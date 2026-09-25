# UI Flow: Ticket Management Web UI

Screens map 1:1 onto Next.js App Router routes under `frontend/src/app/`. Every flow below is traceable to a spec.md user story.

## Navigation shell (applies to every screen)

- Persistent top-level nav (`layout.tsx`): app title/logo, link to ticket list, reserved-but-empty slot for the future AI Q&A panel entry point (FR-015) — rendered as nothing today, not a disabled placeholder (no UI for it yet, per FR-016/Assumptions).
- Shared error-display region (architecture.md) can render at page level (full-page error, e.g. list/detail load failure) or inline (form field errors, transition rejection).

---

## Screen 1: Ticket List (`/tickets`) — User Story 1

**Entry point**: default route on app load; also reachable via nav link from anywhere.

**Layout**:
- Search box (`q`) + status filter dropdown (`OPEN | IN_PROGRESS | RESOLVED | CLOSED | CANCELLED | (all)`), combinable (FR-004).
- Scrollable list of ticket rows: title, status (badge), priority, assignee (FR-001).
- "Create ticket" button → Screen 3.

**Flow**:
1. On load: `GET /api/v1/tickets?page=0&size=20` (no filters) → render rows, or "no tickets found" empty state if `totalElements === 0` (Scenario 5).
2. User types in search box (debounced) and/or picks a status → re-query with `q`/`status` params, reset to page 0, re-render (Scenarios 2–4).
3. User scrolls near bottom of the rendered list, and `page + 1 < totalPages` → fetch next page, append rows (FR-004a, Scenario 6). If the next-page fetch fails, show an inline "couldn't load more — retry" affordance at the list's bottom without disturbing already-loaded rows.
4. User clicks a row → navigate to Screen 2 (`/tickets/{id}`).
5. Backend unreachable on initial load → full-page "couldn't reach the server" state with retry (Edge Cases).

---

## Screen 2: Ticket Detail (`/tickets/[id]`) — User Stories 3, 4, 5, 6

**Layout** (single scrollable page, no tabs — keeps all info visible per FR-006):
- Header: title, status badge, transition control (a dropdown/button group showing only backend-state-machine-valid next statuses, per data-model.md's client-side lookup table).
- Field block: description, priority, assignee, category, created/updated timestamps — read-only by default, "Edit" button switches to Screen 2a's inline edit mode for this same route (not a separate page, to keep context).
- Comment history: chronological list (oldest → newest), each with content + timestamp; "no comments yet" empty state if none (Scenario 3).
- Comment composer: text box + submit, always visible below the comment history.

**Flow**:
1. On load: `GET /api/v1/tickets/{id}` → render all fields + comments, or "ticket not found" full-page state on `404` (Scenario 4).
2. **Edit** (User Story 4): click "Edit" → fields become inputs (title, description, priority, assignee-combobox); "Save" sends `PATCH` with only changed fields; on `200` re-render read-only view with new values; on `400 VALIDATION_FAILED` show field-level errors inline, stay in edit mode, values remain as typed (not reverted) so the user can fix and resubmit.
3. **Assignee-only change** (User Story 4 Scenario 3): same edit mode, user only touches the assignee field before saving — no different UI path, just a `PATCH` body containing only `assignee`.
4. **Add comment** (User Story 5): type in composer, submit → `POST` comment; on `201` prepend/append to comment list immediately (no reload, SC-006); on `400` (blank content) show inline error under composer, no comment added.
5. **Transition** (User Story 6): transition control only lists statuses valid from current status (client-side lookup, data-model.md); selecting one and confirming → `POST /transitions`; on `200` update header status badge immediately; on `409 INVALID_TRANSITION` show the backend's message naming current + attempted status inline near the control, control resets to unselected (Scenario 3, FR-013).

---

## Screen 3: Create Ticket (`/tickets/new`) — User Story 2

**Layout**: form with title, description, priority (select), assignee (combobox, FR-005a), submit button.

**Flow**:
1. Load: `GET /api/v1/assignees` in background to populate the combobox; empty list → combobox shows "no assignees yet — type a name to add one" and still accepts typed input (FR-005c).
2. Client-side pre-flight: obviously-blank required fields show a hint before submit attempt (does not replace backend validation — FR-011).
3. Submit → `POST /api/v1/tickets`; on `201` navigate to Screen 2 for the new ticket (Scenario 1); on `400 VALIDATION_FAILED` show per-field backend messages inline, keep entered values, do not create a ticket (Scenario 2/3).

---

## Cross-cutting: Error and empty states

| Situation | Presentation |
|---|---|
| Network/timeout failure (any screen) | Full-page or inline "couldn't reach the server" message + retry action (Edge Cases) |
| `404 TICKET_NOT_FOUND` | Dedicated not-found state (Screen 2) |
| `400 VALIDATION_FAILED` with `details[]` | Per-field inline messages next to the offending input |
| `409 INVALID_TRANSITION` | Inline message near the transition control, naming current + attempted status |
| `400 UNKNOWN_FILTER` | Shared error banner (should be unreachable under normal UI use — see contracts/consumed-api.md) |
| Empty list / zero search results | "No tickets found" empty state (Screen 1) |
| Zero comments | "No comments yet" empty state (Screen 2) |
| Empty assignee list | "No assignees yet — type a name to add one" (Screens 2, 3) |

All of the above route through the single shared error-display component (architecture.md), never a bespoke per-screen error UI.
