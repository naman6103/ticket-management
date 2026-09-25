# Data Model: AI Q&A Panel

This feature owns no persisted data. Every shape below is a TypeScript view over what Feature 3's already-existing `POST /api/ai/ask` returns (`rag-api-contract.md`), plus a small extension to Feature 2's shared `ApiError` type. No database, no ORM, no frontend-side persistence beyond in-memory mutation state (React Query).

## AskRequest

| Field | Type | Notes |
|---|---|---|
| `question` | `string` | Required, non-blank after trim, max 1000 characters (backend-enforced). Client pre-flights only the obvious blank/whitespace-only case (FR-002); exact length/content validation stays backend-authoritative, mirroring Feature 2's FR-011 "display-only" precedent for pre-flight checks. |

## AskAnswer (mirrors the `200` response, rag-api-contract.md §2/§3)

| Field | Type | Notes |
|---|---|---|
| `answer` | `string` | Shown verbatim; never truncated, re-worded, or parsed for embedded ticket mentions client-side (spec Clarification #1 — citations come only from `ticketIds`, never parsed out of this text). |
| `ticketIds` | `string[]` (UUID) | Rendered as a separate citation list below the answer (spec Clarification #1). Matches `Ticket.id` (Feature 2's `data-model.md`) exactly — no ID-format translation needed. Empty exactly when `noRelevantTicketsFound` is `true`. |
| `noRelevantTicketsFound` | `boolean` | Drives the three-way render branch: `false` with a non-empty `answer` → grounded-answer view; `true` → `NoRelevantTicketsState`. This is a `200 OK` field, never an error signal (research.md §3). |

## ApiError (extension of Feature 2's shape, `frontend/src/types/apiError.ts`)

`BackendApiErrorCode` gains two members:

| Code | Status | UI treatment |
|---|---|---|
| `AI_GENERATION_FAILED` | `502` | Shared error banner: "The assistant couldn't generate an answer. Please try again." |
| `AI_RETRIEVAL_UNAVAILABLE` | `503` | Shared error banner: "The assistant is temporarily unavailable. Please try again shortly." |

`VALIDATION_FAILED` (blank/too-long question) already has a working case in `mapApiError.ts` (field-level if `details[]` present, else banner) — no change needed; a question-field validation error renders inline under the textarea via the existing `details[]` path.

## Citation (view-only, not a wire type)

A UI-only pairing derived from `ticketIds`: `{ ticketId: string }[]`, mapped 1:1 to link targets `/tickets/{ticketId}` opened in a new tab (spec Clarification #2). No separate backend shape exists or is needed — `ticketIds` is already a flat array.
