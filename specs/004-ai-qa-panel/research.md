# Research: AI Q&A Panel

Tech stack, backend contract, and frontend architecture are all fixed by prior features (Feature 2's UI, Feature 3's `rag-api-contract.md`). "Research" here means confirming which existing precedent to follow for each decision point the spec's clarifications raised, not evaluating open technology choices.

## 1. New-tab citation navigation (spec Clarification #2)

**Decision**: `<Link href={\`/tickets/${id}\`} target="_blank" rel="noopener noreferrer">`.

**Rationale**: Next.js `<Link>` is already used everywhere else (`NavShell`, `TicketRow`); `target="_blank"` is the standard, zero-dependency way to open a new tab from a real, crawlable, keyboard-accessible anchor. `rel="noopener noreferrer"` stops the new tab from holding a handle back to the opener window — standard practice, no cost.

**Alternatives considered**: `window.open()` inside a click handler — rejected; it trades a declarative anchor for an imperative escape hatch with no benefit here.

## 2. Disable-input-while-pending (spec Clarification #3)

**Decision**: a manual `isSubmitting` `useState`, gating the textarea and submit button; not TanStack Query's `useMutation`.

**Rationale (corrected during implementation)**: this document originally claimed `useMutation` was "already used for ticket create/update/transition/comment." That was checked against the code during `/speckit-implement` and found to be false — `TicketForm.tsx`, `TransitionControl.tsx`, and `CommentForm.tsx` all use manual `useState` (`isSubmitting`) + an async `try/catch` handler; `useMutation` does not appear anywhere in this codebase, which reserves TanStack Query for reads (`useQuery`/`useInfiniteQuery`) only. Matching that real, established pattern is more consistent than introducing the first `useMutation` usage for a single form.

**Alternatives considered**: `useMutation` — rejected on correction; would have been the only mutation-hook usage in an otherwise manual-`useState`-for-writes codebase, working against the grain rather than with it.

## 3. "No relevant tickets found" vs. request-failure presentation (FR-007)

**Decision**: the no-match state renders via a small dedicated component (`NoRelevantTicketsState`), **not** routed through `ErrorDisplay`/`mapApiError.ts`. Genuine request/backend failures (network, 400/502/503) continue through the existing shared `ErrorDisplay`, exactly like every other screen.

**Rationale**: `noRelevantTicketsFound: true` is a `200 OK` — rag-api-contract.md §3 states outright that it is "not an error" — so it must never enter the error-mapping pipeline Feature 2 built for `ErrorResponse` payloads. Feature 2's own `ErrorDisplay` "banner" kind is already deliberately reused for two different meanings today (benign empty states like "No tickets found"/"No comments yet," and soft backend errors like `UNKNOWN_FILTER`), all with identical blue styling. Reusing it a third time for the AI no-match state would make it visually indistinguishable from a real backend failure banner (e.g. `AI_RETRIEVAL_UNAVAILABLE`) rendered the same way — directly conflicting with this feature's FR-007. A separate, distinctly-styled component resolves that conflict without touching Feature 2's existing `ErrorDisplay` behavior anywhere else.

**Alternatives considered**: add a 4th `MappedMessage` kind (e.g. `"info"`) to `mapApiError.ts`'s output type — rejected; `mapApiError.ts` only ever translates real `ErrorResponse` payloads, and the no-match response is never one of those, so extending that pipeline for a case that never flows through it is the wrong seam to extend.

## 4. New backend-error codes surfaced to the UI

**Decision**: extend `ApiErrorCode`/`BackendApiErrorCode` (`frontend/src/types/apiError.ts`) with `"AI_GENERATION_FAILED"` and `"AI_RETRIEVAL_UNAVAILABLE"`; add explicit `mapApiError.ts` cases for both (banner kind, tailored copy) instead of falling through to the generic `default` branch.

**Rationale**: FR-013 asks for a validation-specific message "where feasible based on the information the backend response provides" — the backend already returns a distinct `code` for each of these failure modes (rag-api-contract.md §4), so mapping each to its own copy is a same-shape extension of a switch statement Feature 2 already built for exactly this purpose.

**Alternatives considered**: leave both under the existing `default` ("couldn't reach the server") case — rejected as a missed, nearly-free opportunity to tell the agent "the assistant is temporarily unavailable" vs. a bare network failure, using information the backend already sends.

## 5. Frontend Route Handler for `POST /api/ai/ask`

**Decision**: `frontend/src/app/api/ai/ask/route.ts`, built with the exact `apiFetch` + `relay` pair already used by `app/api/tickets/route.ts`, proxying to the backend's `POST /api/ai/ask` (no `/v1` prefix — the backend path is version-prefix-free per rag-api-contract.md's own naming-deviation note; do not add one).

**Rationale**: this is the "shared API-client pattern" seam Feature 2's architecture.md explicitly reserved for this feature. Deviating from it (e.g. calling the backend directly from the browser) would break the Docker-network-hostname constraint — `client.ts` only works server-side because `API_BASE_URL` resolves a Compose service name a browser cannot reach.

**Alternatives considered**: none — this is a fixed, pre-existing pattern, not an open decision.
