# Contract: Backend API Consumed by This Feature

This feature is a pure consumer of `specs/003-rag-ticket-qa/rag-api-contract.md` — see that file for the full, authoritative request/response/error shape of `POST /api/ai/ask`. This document lists only how this feature calls it and maps its outcomes to UI states. **No backend endpoint is added or changed** (spec.md FR-012).

| Endpoint | Used by | Success handling | Error handling |
|---|---|---|---|
| `POST /api/ai/ask` `{ question }` | AI Q&A panel submit | `noRelevantTicketsFound: false` → render `answer` + a separate citation list (one link per `ticketId`, → `/tickets/{id}`, new tab); `noRelevantTicketsFound: true` → render `NoRelevantTicketsState`, no citations (data-model.md, ui-flow.md) | `400 VALIDATION_FAILED` → field-level message under the question textarea when `details[]` present, else shared error banner (existing `mapApiError.ts` path); `502 AI_GENERATION_FAILED` / `503 AI_RETRIEVAL_UNAVAILABLE` → shared error banner via `ErrorDisplay` (new `mapApiError.ts` cases, data-model.md); network failure → existing synthesized `NETWORK_ERROR` banner |

## New frontend-only Route Handler (not a backend change)

| Route | Proxies to | Notes |
|---|---|---|
| `POST /api/ai/ask` (Next.js Route Handler, `frontend/src/app/api/ai/ask/route.ts`) | Backend `POST /api/ai/ask` | Same-origin relay for the browser, identical shape to `app/api/tickets/route.ts` (architecture.md, research.md §5). Introduces no new backend logic — the Java controller/service/endpoint already exist from Feature 3. |

## Contract stability note

Everything under "Backend API Consumed" already exists and is stable per Feature 3 (merged, tested). This feature must not assume or request any change to that endpoint's request/response/error shapes.
