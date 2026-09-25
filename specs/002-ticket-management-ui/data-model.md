# Data Model: Ticket Management Web UI

This feature does not own or persist any data — every entity below is a TypeScript view over shapes already defined in `specs/001-ticket-management-api/api-contract.md`, plus the one new `Assignee` shape backing the new endpoint (research.md §5). No database, no ORM, no frontend-side persistence beyond in-memory query cache (React Query).

## Ticket

Mirrors `TicketResponse` (api-contract.md). Read from the API; never constructed client-side except as a `TicketFormValues` draft before submission.

| Field | Type | Notes |
|---|---|---|
| `id` | `string` (UUID) | Immutable; used for routing (`/tickets/[id]`) |
| `title` | `string` | Required, not blank (create/edit) |
| `description` | `string` | Required, not blank (create/edit) |
| `priority` | `"LOW" \| "MEDIUM" \| "HIGH"` | Required (create); optional on edit (unchanged if omitted) |
| `assignee` | `string` | Required (create); sourced from the assignee combobox (picked or typed) |
| `status` | `"OPEN" \| "IN_PROGRESS" \| "RESOLVED" \| "CLOSED" \| "CANCELLED"` | Never directly editable — only via `POST /transitions` |
| `category` | `string \| null` | Optional, freeform |
| `createdAt` | `string` (ISO-8601) | Read-only |
| `updatedAt` | `string` (ISO-8601) | Read-only |
| `comments` | `Comment[] \| undefined` | Present only on `GET /tickets/{id}` responses; omitted (not present) on list/search responses per api-contract.md |

**Validation rules (display-only — see FR-011)**: The UI pre-flights obviously-blank required fields to show inline hints before submission, but the backend's response is always the source of truth; any backend rejection overrides/supersedes client-side assumptions and is shown verbatim (FR-012).

## Comment

Mirrors `CommentResponse`.

| Field | Type | Notes |
|---|---|---|
| `id` | `string` (UUID) | |
| `ticketId` | `string` (UUID) | Foreign reference to the parent ticket |
| `content` | `string` | Required, not blank |
| `createdAt` | `string` (ISO-8601) | Used for chronological ordering (oldest first, per User Story 3 Scenario 2) |

## TicketPage (list/search/filter result)

Mirrors `PageResponse<TicketResponse>`.

| Field | Type | Notes |
|---|---|---|
| `content` | `Ticket[]` | Comments omitted on each item |
| `page` | `number` | Zero-based |
| `size` | `number` | |
| `totalElements` | `number` | |
| `totalPages` | `number` | Drives whether `useInfiniteQuery` requests another page (FR-004a) |

## ApiError

Mirrors the shared `ErrorResponse` shape (api-contract.md), consumed by the shared error-display component (architecture.md).

| Field | Type | Notes |
|---|---|---|
| `timestamp` | `string` (ISO-8601) | |
| `status` | `number` | HTTP status |
| `error` | `string` | Reason phrase |
| `code` | `"VALIDATION_FAILED" \| "TICKET_NOT_FOUND" \| "INVALID_TRANSITION" \| "UNKNOWN_FILTER"` | Drives which UI message template is used |
| `message` | `string` | Human-readable summary shown as the primary error text |
| `path` | `string` | Not shown to the user; useful for debugging/telemetry only |
| `details` | `{ field: string; rejectedValue: unknown; message: string }[]` | When present, rendered as per-field inline messages (FR-012) |

## Assignee (new, this feature)

Backed by the new `GET /api/v1/assignees` endpoint (research.md §5).

| Field | Type | Notes |
|---|---|---|
| `assignees` | `string[]` | Distinct, non-null assignee values already used on existing tickets, sorted alphabetically; empty array is a valid response (FR-005c "no assignees yet" state) |

## Ticket status transition (client-side derivation)

Not a backend entity — a UI-side lookup mirroring `specs/001-ticket-management-api/state-machine.md`, used only to decide which transition buttons to *offer* (FR-010). The backend's response on submission remains authoritative (FR-013); this table is advisory/display-only and can go stale relative to the backend without causing incorrect behavior, only a possible extra round-trip rejection (Edge Cases, spec.md).

| Current status | Offered next statuses |
|---|---|
| `OPEN` | `IN_PROGRESS`, `CANCELLED` |
| `IN_PROGRESS` | `RESOLVED`, `CANCELLED` |
| `RESOLVED` | `CLOSED` |
| `CLOSED` | *(none — terminal)* |
| `CANCELLED` | *(none — terminal)* |
