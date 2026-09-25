# Contract: Backend API Consumed by This Feature

This feature is a pure consumer of `specs/001-ticket-management-api/api-contract.md` — see that file for the full, authoritative shape of every endpoint below. This document lists only which endpoints the UI calls, where, and how responses/errors map to screens. It does not redefine the backend contract.

| Endpoint | Used by (screen/action) | Success handling | Error handling |
|---|---|---|---|
| `POST /api/v1/tickets` | Create form | Navigate to `/tickets/{id}` (new ticket's detail view) | `400 VALIDATION_FAILED` → inline field errors on the form (architecture.md §Error Mapping) |
| `GET /api/v1/tickets?q=&status=&page=&size=` | List view | Render page; `useInfiniteQuery` requests next page near scroll-bottom while `page + 1 < totalPages` | `400 UNKNOWN_FILTER` → shared error banner (should not occur under normal use; only if a stale UI sends a status value the backend no longer recognizes) |
| `GET /api/v1/tickets/{id}` | Detail view | Render all fields + `comments[]` | `404 TICKET_NOT_FOUND` → "ticket not found" screen (User Story 3 Scenario 4) |
| `PATCH /api/v1/tickets/{id}` | Edit form | Re-render detail view with updated fields | `400 VALIDATION_FAILED` → inline field errors; `404 TICKET_NOT_FOUND` → "ticket not found" (ticket deleted/changed between load and save) |
| `POST /api/v1/tickets/{id}/transitions` | Status transition control | Re-render detail view with new status | `400 VALIDATION_FAILED` → generic-but-specific "invalid target status" message (should not occur — UI only offers valid-looking enum values); `404` → "ticket not found"; `409 INVALID_TRANSITION` → inline message naming current + attempted status (FR-013), transition control resets to no selection |
| `POST /api/v1/tickets/{id}/comments` | Comment form (on detail view) | Append new comment to the visible comment list without full reload (SC-006) | `400 VALIDATION_FAILED` (blank content) → inline error under comment box; `404 TICKET_NOT_FOUND` → "ticket not found" |

## New endpoint introduced by this feature

| Endpoint | Used by | Success handling | Error handling |
|---|---|---|---|
| `GET /api/v1/assignees` | Create form, Edit form (assignee combobox) | Populate combobox options; empty array → "no assignees yet — type a name to add one" (FR-005c) | Any 5xx → combobox falls back to type-only mode with a shared error note; does not block form usage |

**Request/response shape**: see `data-model.md` §Assignee. No request body; `200 OK` always (no documented failure mode beyond generic server errors, since it takes no parameters). Implementing this endpoint is a backend task tracked alongside this feature's frontend tasks (constitution Principle III still applies to it — unit tests required when it's built).

## Contract stability note

Everything under "Backend API Consumed" already exists and is stable per Feature 1 (merged, tested). This feature must not assume any change to those endpoints' request/response shapes. The only new contract surface is `GET /api/v1/assignees`, scoped and documented above.
