# Architecture: AI Q&A Panel

Extends `specs/002-ticket-management-ui/architecture.md`. Fills the three seams that document named as reserved for this feature (its §"How the layout leaves room for the future AI Q&A panel") — no change to how those seams themselves work for any other screen.

## Component structure (addition)

```text
frontend/src/
├── app/
│   ├── assistant/
│   │   └── page.tsx                    # Screen 4: /assistant (uses AiQaPanel)
│   └── api/
│       └── ai/ask/route.ts             # Route Handler: relays POST /api/ai/ask (mirrors api/tickets/route.ts)
├── components/
│   ├── layout/
│   │   └── NavShell.tsx                 # MODIFIED: fills the reserved nav slot with a link to /assistant
│   └── assistant/
│       ├── AiQaPanel.tsx                # question input + submit + result-state switch (a/b/c, ui-flow.md)
│       ├── AiAnswer.tsx                 # renders answer text + <CitationList>
│       ├── CitationList.tsx             # one <Link target="_blank"> per ticketId, → /tickets/{id}
│       └── NoRelevantTicketsState.tsx   # dedicated no-match block (research.md §3)
├── lib/
│   ├── api/
│   │   └── assistant.ts                 # askAssistant(question): Promise<AskAnswer> — same shape as tickets.ts
│   └── errors/
│       └── mapApiError.ts               # MODIFIED: + AI_GENERATION_FAILED, AI_RETRIEVAL_UNAVAILABLE cases
└── types/
    ├── apiError.ts                       # MODIFIED: BackendApiErrorCode + 2 members
    └── assistant.ts                      # AskRequest, AskAnswer
```

## State management

- **Server state / write path**: a manual `useState` + async `try/catch` handler in `AiQaPanel.tsx`, matching every other write path in this codebase (`TicketForm.tsx`, `TransitionControl.tsx`, `CommentForm.tsx`) — **not** TanStack Query's `useMutation`, which this codebase reserves for reads only (`useQuery`/`useInfiniteQuery`). An earlier draft of this document claimed `useMutation` was "already used for ticket create/update/transition/comment"; that claim was wrong and is corrected here after checking the actual components. `isSubmitting` gates the disabled state (spec Clarification #3); `result`/`messages` state drives which of the three result states renders. Each new submission clears both `result` and `messages` before the request starts, which is what satisfies FR-010 ("replace, never mix").
- **Local/UI-only state**: the question textarea's own draft value (`useState`), exactly like every other form in the app.
- **No client-side grounding/business logic**: this feature does not decide relevance, similarity, or what counts as "no relevant tickets" — it only renders `noRelevantTicketsFound` as given. Constitution Principle V stays entirely on the backend side (see plan.md's Constitution Check).

## Error mapping (extension of Feature 2's table)

```text
ErrorResponse.code             → UI treatment
──────────────────────────────────────────────────────────────
AI_GENERATION_FAILED (502)     → shared error banner, tailored copy (data-model.md)
AI_RETRIEVAL_UNAVAILABLE (503) → shared error banner, tailored copy (data-model.md)
VALIDATION_FAILED (400)        → unchanged existing case (field if details[] present, else banner) —
                                  question field errors render under the textarea
```

**Critical distinction from every other screen**: the `noRelevantTicketsFound: true` response is a `200 OK`, never an `ApiError` — so it is never passed to `mapApiError.ts`/`ErrorDisplay` at all. `AiQaPanel.tsx` branches on the successful payload's own boolean before any error-mapping code runs. See research.md §3 for why this deliberately does *not* reuse `ErrorDisplay`'s "banner" kind for the no-match state.

## How this feature fills Feature 2's three reserved seams

1. **Shared navigation shell**: `NavShell.tsx`'s one `<Link href="/tickets">` becomes two links; no other screen's markup or route changes.
2. **Shared API-client pattern**: `lib/api/assistant.ts` + `app/api/ai/ask/route.ts` follow the exact same two-file shape (`browserFetch` from a typed client module → same-origin Route Handler → `apiFetch`/`relay` → backend) already used by `tickets.ts`/`app/api/tickets/route.ts`. `client.ts` and `relay.ts` are used unmodified.
3. **Shared error-display component**: every genuine failure (validation, 502, 503, network) renders through the existing `ErrorDisplay`, with two new `mapApiError.ts` cases as the only change to that pipeline.

## Deployment architecture

No changes. Same `frontend` Docker image/service, same `API_BASE_URL` env var, no new compose service, no new secret. The new Route Handler calls the backend at the same base URL already configured (`API_BASE_URL`), just a different path (`/api/ai/ask` — no `/v1` prefix, per rag-api-contract.md's own naming-deviation note; this is not a typo).
