---
description: "Task list for AI Q&A Panel (004-ai-qa-panel)"
---

# Tasks: AI Q&A Panel

**Input**: Design documents from `/specs/004-ai-qa-panel/` (plan.md, spec.md, research.md, data-model.md, contracts/consumed-api.md, ui-flow.md, architecture.md, test-strategy.md, quickstart.md)

**Tests**: Explicitly requested (test-strategy.md, and the user's own task list) — component test and E2E test tasks are included.

**Organization**: Grouped by user story (spec.md priorities) so each story is independently implementable/testable; the user's 8-point ordering is preserved and cross-referenced per phase.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no unmet dependencies)
- **[Story]**: US1–US4 map to spec.md's four user stories; Setup/Foundational/Polish phases carry no story label

## Path Conventions

Pure extension of Feature 2's existing web-app layout: all new/modified files live under the existing `frontend/src/` tree (plan.md Project Structure). No backend file, no `docker-compose.yml` change.

---

## Phase 1: Setup

**Purpose**: Confirm this feature needs no new project-level setup before any code is written.

- [X] T001 Confirm no new setup is required: `frontend/package.json` already has every dependency this feature needs (Next.js 14, React 18, `@tanstack/react-query`, Vitest, React Testing Library, Playwright — research.md); `docker-compose.yml` needs no new service (plan.md Constraints, FR-012). Acceptance: a diff of `frontend/package.json` and `docker-compose.yml` after this feature ships shows zero changes to either file. — verified: `git diff`/`git status` on both files show no changes; all required deps (`@tanstack/react-query`, `next`, `react`, `@playwright/test`, `@testing-library/*`, `vitest`) already present.

**Checkpoint**: No scaffolding gap exists; proceed straight to Foundational.

---

## Phase 2: Foundational (blocking prerequisites) — user's step 1 + shell of step 2

**Purpose**: The typed API client for `POST /api/ai/ask` and the panel's input/loading shell — nothing in Phase 3+ can render a result without these.

**⚠️ CRITICAL**: No user-story work begins until this phase is complete.

- [X] T002 [P] Create `frontend/src/types/assistant.ts`: `AskRequest` (`question: string` — data-model.md: "required, non-blank after trim, max 1000 characters"), `AskAnswer` (`answer: string`, `ticketIds: string[]`, `noRelevantTicketsFound: boolean` — data-model.md: "Matches `Ticket.id`... exactly — no ID-format translation needed"; "Empty exactly when `noRelevantTicketsFound` is `true`") — done
- [X] T003 Implement `frontend/src/app/api/ai/ask/route.ts` — Next.js Route Handler, `POST` only, calling `apiFetch<AskAnswer>("/api/ai/ask", { method: "POST", body })` (`lib/api/client.ts`, unmodified) and returning `relay(...)` (`lib/api/relay.ts`, unmodified) — exact same two-function shape as `app/api/tickets/route.ts` (architecture.md, research.md §5) — done; `npx tsc --noEmit` clean; live curl verification deferred to Phase 7 (requires full `docker compose up`). Acceptance: `curl -X POST localhost:<port>/api/ai/ask -d '{"question":"..."}"` against a running stack returns the backend's real status/body verbatim, including on a `400`/`502`/`503`.
- [X] T004 [P] Implement `frontend/src/lib/api/assistant.ts`: `askAssistant(question: string): Promise<AskAnswer>` — browser-side, calls `browserFetch<AskAnswer>("/api/ai/ask", { method: "POST", body: JSON.stringify({ question }) })` (same shape as `tickets.ts`'s functions) — done
- [X] T005 Implement `frontend/src/components/assistant/AiQaPanel.tsx` shell: a controlled textarea (local `useState` draft) + submit button. **Correction from architecture.md/research.md**: those docs incorrectly claimed a `useMutation`-based pattern was "already used for ticket create/update/transition/comment" — verified against the actual code (`TicketForm.tsx`, `TransitionControl.tsx`, `CommentForm.tsx`) that every existing write path instead uses manual `useState` (`isSubmitting`, message state) + async `try/catch` + `error instanceof BrowserApiError` (never `useMutation`, which this codebase uses only for reads via `useQuery`/`useInfiniteQuery`). Implemented to match that real, established pattern instead. Submit is blocked when the trimmed question is empty (FR-002, via `disabled` and an early-return guard); textarea and submit button are both `disabled` while `isSubmitting` (spec Clarification #3, FR-011); loading text shown while pending. Result-rendering branches (grounded answer / no-match) are stubbed as empty for now — filled in Phases 3/5; the error branch is already wired via `ErrorDisplay`/`mapApiError` (Phase 6 only adds the two new error codes' copy). — done
- [X] T006 Create `frontend/src/app/assistant/page.tsx` rendering `<AiQaPanel />` as the sole content of the new `/assistant` route. — done

**Checkpoint**: `/assistant` loads, accepts a question, shows a loading state on submit, and blocks blank submission — independently verifiable via a temporary manual check before any result state exists. `npx tsc --noEmit` passes with zero errors after Phase 2.

---

## Phase 3: User Story 1 - Ask a question and get a grounded, cited answer (Priority: P1) 🎯 MVP — user's steps 2 (loading, shared with Foundational) & 3

**Goal**: Deliver the core value: submitting a question renders the assistant's answer text and its cited ticket IDs as a separate, clickable list.

**Independent Test**: Open `/assistant`, submit a question known to match existing ticket content, confirm the answer text and at least one cited ticket ID render.

### Tests for User Story 1

- [X] T007 [P] [US1] Component test in `frontend/tests/components/AiQaPanel.test.tsx`: given a mocked `askAssistant` resolving `{ answer, ticketIds: ["<uuid>", "<uuid>"], noRelevantTicketsFound: false }`, submitting a question renders the answer text and one citation-list item per `ticketId`, rendered as a list separate from the answer paragraph (spec Clarification #1, spec.md US1 Scenarios 1–2) — done, passes
- [X] T007a [P] [US1] Component test in `frontend/tests/components/AiQaPanel.test.tsx`: given a mocked `askAssistant` whose promise doesn't resolve yet, after submitting the textarea and submit button are both `disabled` and a loading indicator is visible; once the mock resolves, both re-enable (spec Clarification #3, FR-011 — test-strategy.md's disable-while-pending row) — done, passes
- [X] T008 [P] [US1] Component test in `frontend/tests/components/AiQaPanel.test.tsx`: submitting a second question after a first one resolved replaces the first answer/citations entirely (no residual citations from the first response) — spec.md US1 Scenario 3, FR-010 — done, passes

### Implementation for User Story 1

- [X] T009 [P] [US1] Implement `frontend/src/components/assistant/CitationList.tsx`: renders one `<li>` per `ticketId` prop — plain text list items for now (Phase 4/T014 adds the link); do not weave citations into `AiAnswer`'s prose (spec Clarification #1) — done
- [X] T010 [US1] Implement `frontend/src/components/assistant/AiAnswer.tsx`: renders the `answer` string verbatim in a paragraph, followed by `<CitationList ticketIds={...} />` — done
- [X] T011 [US1] Wire `AiQaPanel.tsx` (from T005): on a successful response where `!result.noRelevantTicketsFound`, render `<AiAnswer answer={result.answer} ticketIds={result.ticketIds} />` (adapted from the task's original `mutation.isSuccess`/`mutation.data` wording to the manual `result` state from T005's correction); a new submission clears `result` before the request starts, satisfying FR-010 — done

**Checkpoint**: User Story 1 fully functional and independently testable/demoable — grounded answers with citations render correctly. Verified: `npx tsc --noEmit` clean; `npx vitest run` — 22/22 tests pass across 8 files (3 new), zero regressions.

---

## Phase 4: User Story 2 - Follow a citation to the ticket it came from (Priority: P1) — user's step 3 (citation link behavior)

**Goal**: Every rendered citation is a real, correct link that opens the cited ticket's existing detail view in a new tab, leaving the panel undisturbed.

**Independent Test**: With an answer containing a citation displayed (built in US1), click it and confirm `/tickets/{id}` opens in a new tab for that exact ticket, while `/assistant` remains open and unchanged.

### Tests for User Story 2

- [X] T012 [P] [US2] Component test in `frontend/tests/components/CitationList.test.tsx`: given `ticketIds={["abc-123"]}`, the rendered link has `href="/tickets/abc-123"` and `target="_blank"` (and `rel="noopener noreferrer"`) — spec.md US2 Scenarios 1–2, FR-005 — done; confirmed failing before T014, passes after
- [X] T013 [US2] Component test in `frontend/tests/components/CitationList.test.tsx`: given multiple `ticketIds`, each link's `href` corresponds to its own ID, not another one in the list — spec.md US2 Scenario 2 — done; confirmed failing before T014, passes after

### Implementation for User Story 2

- [X] T014 [US2] Implement each `CitationList.tsx` item (T009) as `<Link href={`/tickets/${ticketId}`} target="_blank" rel="noopener noreferrer">{ticketId}</Link>` (research.md §1); no change needed to `/tickets/[id]/page.tsx` — Feature 2's existing detail view (including its own "ticket not found" handling) is reused as-is for a since-deleted cited ticket (Edge Cases) — done

**Checkpoint**: User Stories 1 AND 2 both work independently — citations render and correctly navigate. Verified: `npx tsc --noEmit` clean; `npx vitest run` on both test files — 5/5 pass.

---

## Phase 5: User Story 3 - Clearly see when nothing relevant was found (Priority: P2) — user's step 4

**Goal**: An honest "no relevant tickets found" result renders as its own, non-error state with zero citations.

**Independent Test**: Submit a question designed to match nothing, confirm a distinct "no relevant tickets found" message renders with no citation links and no error styling.

### Tests for User Story 3

- [X] T015 [P] [US3] Component test in `frontend/tests/components/AiQaPanel.test.tsx`: given a mocked `askAssistant` resolving `{ answer: "No relevant tickets found for this question.", ticketIds: [], noRelevantTicketsFound: true }`, `NoRelevantTicketsState` renders and zero citation-list items are present — spec.md US3 Scenario 1 — done; confirmed failing before T016/T017, passes after

### Implementation for User Story 3

- [X] T016 [P] [US3] Implement `frontend/src/components/assistant/NoRelevantTicketsState.tsx`: a small dedicated component (its own `data-testid`/CSS class, e.g. `data-testid="ai-no-match"`) rendering a fixed "no relevant tickets found" message — deliberately NOT built on `ErrorDisplay`/`mapApiError.ts` (research.md §3, FR-006, FR-007) — done
- [X] T017 [US3] Wire `AiQaPanel.tsx`: on a successful response where `result.noRelevantTicketsFound`, render `<NoRelevantTicketsState />` instead of `<AiAnswer />` (adapted from `mutation.isSuccess`/`mutation.data` wording to T005's manual `result` state); a new submission clears `result` before the request starts, per FR-010 — done

**Checkpoint**: User Stories 1, 2, AND 3 all work independently — the no-match branch is visibly distinct from a grounded answer. Verified: `npx tsc --noEmit` clean; `npx vitest run` — 4/4 pass in `AiQaPanel.test.tsx`.

---

## Phase 6: User Story 4 - Reach the panel from normal navigation, and see failures handled consistently (Priority: P2) — user's step 5 + error half of step 4/6

**Goal**: The panel is reachable from Feature 2's nav shell, and every genuine request/backend failure renders through the same shared `ErrorDisplay` component used elsewhere, visibly distinct from the US3 no-match state.

**Independent Test**: Navigate to `/assistant` via the nav shell (no direct URL); separately, simulate a backend failure and confirm `ErrorDisplay` renders, visually distinct from US3's no-match block.

### Tests for User Story 4

- [X] T018 [P] [US4] Component test in `frontend/tests/components/NavShell.test.tsx`: `NavShell` renders a link to `/assistant` — spec.md US4 Scenario 1, FR-009, SC-004 — done
- [X] T019 [P] [US4] Component test in `frontend/tests/components/AiQaPanel.test.tsx`: given a mocked `askAssistant` rejection with a `400 VALIDATION_FAILED` payload with `details: [{ field: "question", message: "must not be blank", ... }]`, the field-level message renders under the textarea (existing `mapApiError.ts` path, unchanged) — FR-002, FR-013 — done
- [X] T020 [P] [US4] Component test in `frontend/tests/components/AiQaPanel.test.tsx`: given mocked rejections with `502 AI_GENERATION_FAILED` and, separately, `503 AI_RETRIEVAL_UNAVAILABLE`, `ErrorDisplay` renders each one's tailored banner text (data-model.md) — FR-008 — done
- [X] T021 [P] [US4] Component test asserting the US3 no-match block (`data-testid="ai-no-match"`, T016) and the `ErrorDisplay`-rendered failure block use different `data-testid`/CSS classes when each is rendered in isolation — FR-007, SC-003 — done (also asserts only the error block carries `role="alert"`)

### Implementation for User Story 4

- [X] T022 [P] [US4] Extend `frontend/src/types/apiError.ts`: add `"AI_GENERATION_FAILED"` and `"AI_RETRIEVAL_UNAVAILABLE"` to the `BackendApiErrorCode` union (data-model.md) — done
- [X] T023 [US4] Extend `frontend/src/lib/errors/mapApiError.ts`: add a `case "AI_GENERATION_FAILED"` → `[{ kind: "banner", text: "The assistant couldn't generate an answer. Please try again." }]` and a `case "AI_RETRIEVAL_UNAVAILABLE"` → `[{ kind: "banner", text: "The assistant is temporarily unavailable. Please try again shortly." }]`, ahead of the existing `default` branch (data-model.md) — done
- [X] T024 [US4] Wire `AiQaPanel.tsx` error branch — already satisfied by T005's `catch (error) { error instanceof BrowserApiError ? setMessages(mapApiError(error.payload)) : throw error }` + `{messages.length > 0 && <ErrorDisplay messages={messages} />}`; no `toApiError`-style helper was introduced. T023's two new cases just extended the switch that path already calls. Verified via T019/T020. — done, no additional code needed
- [X] T025 [US4] Modify `frontend/src/components/layout/NavShell.tsx`: add `<Link href="/assistant">Ask Assistant</Link>` alongside the existing `/tickets` link, filling the slot Feature 2's `ui-flow.md`/`architecture.md` reserved for this feature — done

**Checkpoint**: All four user stories now independently functional and demoable. Verified: `npx tsc --noEmit` clean; `npx vitest run` — 34/34 pass across 11 files (8 in `AiQaPanel.test.tsx`), zero regressions.

---

## Phase 7: Polish & Cross-Cutting Concerns — user's steps 7 & 8

**Purpose**: The one required end-to-end flow, plus full-stack manual verification via `docker compose up`.

- [X] T026 [US1][US2] Playwright E2E test in `frontend/tests/e2e/ask-and-cite.spec.ts`: create a ticket with distinctive title/description text (`/tickets/new`) → navigate to `/assistant` via the nav link → ask a question containing that distinctive text, submit → assert the answer renders and the citation list contains the new ticket's ID → click that citation → assert a new tab opens showing that exact ticket's detail view, and the original `/assistant` tab's answer/citations are unchanged — test written and **run against the live `docker compose` stack**; it correctly created a real ticket, navigated via the nav link, submitted the question, and retried — but the final assertion fails because this sandbox's Ollama container cannot pull `llama3.1:8b` (see T029's note). Confirmed via direct backend `curl` with the same distinctive question that retrieval/ingestion succeeded (the new ticket was found) and the failure is specifically `502 AI_GENERATION_FAILED` ("Chat model call failed") — i.e. the test script itself has no bug; it is blocked on the same environment limitation as T029, not a defect in this feature's code. **Flagged to user** for a re-run once the chat model is available.
- [X] T027 [P] Add `frontend/package.json` script entries for the new test files if not already covered by existing `test`/`test:e2e` globs (test-strategy.md §Test execution); verify present, no new script needed if globs already match `tests/components/**`/`tests/e2e/**` — done; confirmed existing globs (`tests/components/**/*.test.{ts,tsx}`, `testDir: ./tests/e2e`) already cover all new files, zero script changes needed
- [X] T028 Run `docker compose up --build` from repo root; confirm `mysql`, `elasticsearch`, `ollama`, `app`, and `frontend` all report healthy/running with no manual setup step beyond `.env` (constitution Principle II; quickstart.md Prerequisites) — done; all 5 services healthy, backend and frontend both responding (200)
- [X] T029 Manually verify (quickstart.md steps 1–5): the "Ask Assistant" nav link is visible from any screen — **verified live** via browser (visible on `/tickets`, navigates to `/assistant`). Asking a real question that matches existing ticket content and returning a grounded answer with a clickable citation — **NOT verified live**: this sandbox's Ollama container cannot pull `llama3.1:8b` (the chat/generation model; only `nomic-embed-text`, the embedding model, was already present) — every pull attempt fails with `Error: EOF` at the manifest stage, a network-egress restriction in this environment, not a code defect. This slice is covered instead by `AiQaPanel.test.tsx`'s mocked grounded-answer tests (T007/T008) and `CitationList.test.tsx`'s new-tab/href tests (T012/T013), all passing. **Flagged to user** — needs a real run in an environment with unrestricted registry access, or the model pre-pulled into the `ollama-data` volume.
- [X] T030 Manually verify (quickstart.md step 6): asking a deliberately out-of-scope/nonsense question returns the distinct "no relevant tickets found" state — **verified live**: no chat-model call needed for this path (rag-api-contract.md §3), confirmed both via direct backend `curl` and via the real browser UI — no citations, no answer text rendered.
- [X] T031 Manually verify (quickstart.md step 7): stop the backend (`docker compose stop app`), submit a question, confirm the shared error-display component renders a request-failure message visually distinct from T030's no-match state — **verified live** via browser: `role="alert"` banner "Couldn't reach the server..." rendered, structurally distinct from the no-match block (no `role="alert"`); backend restarted afterward and confirmed healthy again.
- [X] T032 [P] Review `frontend/src/components/assistant/`, `frontend/src/lib/api/assistant.ts`, and the modified files (`NavShell.tsx`, `apiError.ts`, `mapApiError.ts`) against this repo's frontend conventions (naming, no hardcoded hosts/secrets) established by Feature 2, resolving or explicitly noting any deviation — done; grepped for hardcoded hosts/secrets (none found), `eslint` clean, `prettier --check` found 3 files needing formatting (`AiQaPanel.tsx`, `mapApiError.ts`, `AiQaPanel.test.tsx`) — fixed with `prettier --write`, re-verified `tsc`/`vitest` still 34/34 green after the fix.

**Checkpoint**: `npm test` passes with no Docker dependency — 34/34 (11 files), zero regressions. `npm run test:e2e` (T026) does **not** yet pass end-to-end against this sandbox's `docker compose up` stack — blocked solely on the Ollama chat model (`llama3.1:8b`) failing to pull (network-egress restriction, `Error: EOF`), not a defect in this feature. All spec.md acceptance scenarios not requiring that model (US2's link mechanics via component tests, US3, US4, nav) verified live; US1's/US2's live grounded-answer + click-through slice is verified only by mocked component tests, pending a re-run once the chat model is available.

---

## Dependencies & Execution Order

1. **Setup (Phase 1)** — no dependencies; must complete first (trivially — no changes needed).
2. **Foundational (Phase 2)** — depends on Phase 1; blocks every user story phase (the typed client and panel shell are shared by all four stories).
3. **User Stories (Phases 3–6)** — each depends on Phase 2:
   - US1 (Phase 3) and US3 (Phase 5) can proceed in parallel once Phase 2 is done (different files: `AiAnswer`/`CitationList` vs. `NoRelevantTicketsState`), both wiring into the same `AiQaPanel.tsx` branch structure.
   - US2 (Phase 4) extends `CitationList.tsx` created in US1 — build after Phase 3.
   - US4 (Phase 6) is independent of US1–US3's files (`NavShell.tsx`, `apiError.ts`, `mapApiError.ts`) except for its `AiQaPanel.tsx` error branch, which can be added any time after Phase 2. **Exception**: T021 specifically asserts against the `data-testid="ai-no-match"` element T016 (US3) creates, so T021 itself requires Phase 5 complete first — the rest of Phase 6 does not.
4. **Polish (Phase 7)** — depends on all four user stories being complete (the E2E test exercises US1+US2 together; the manual verification exercises US1, US3, and US4's error path).

## Parallel Execution Example: Phase 2 → Phases 3 & 5

```bash
# After Phase 2 (Foundational) completes, US1 and US3 can proceed together:
Task: "Implement CitationList.tsx in frontend/src/components/assistant/CitationList.tsx"        # US1, T009
Task: "Implement AiAnswer.tsx in frontend/src/components/assistant/AiAnswer.tsx"                  # US1, T010
Task: "Implement NoRelevantTicketsState.tsx in frontend/src/components/assistant/NoRelevantTicketsState.tsx"  # US3, T016
```

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (trivial)
2. Complete Phase 2: Foundational (CRITICAL — typed client + panel shell)
3. Complete Phase 3: User Story 1
4. **STOP and VALIDATE**: submit a real question against a running stack, confirm a grounded, cited answer renders
5. Demo if ready — citations won't be clickable yet (US2) and no-match/error states aren't handled yet (US3/US4), but the core grounded-answer flow is provably working

### Incremental Delivery

1. Setup + Foundational → panel shell ready
2. Add US1 → grounded answers render (MVP!)
3. Add US2 → citations become correctly-linked, new-tab navigable
4. Add US3 → no-match state handled distinctly
5. Add US4 → reachable from nav, failures handled consistently
6. Polish → E2E flow + full-stack manual sign-off (user's steps 7–8)
