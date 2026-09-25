---
description: "Task list for Ticket Management Web UI (002-ticket-management-ui)"
---

# Tasks: Ticket Management Web UI

**Input**: Design documents from `/specs/002-ticket-management-ui/` (plan.md, spec.md, research.md, data-model.md, contracts/consumed-api.md, ui-flow.md, architecture.md, test-strategy.md, quickstart.md)

**Tests**: Explicitly requested (test-strategy.md) — component test and E2E test tasks are included.

**Organization**: Grouped by user story (spec.md priorities) so each story is independently implementable/testable; user's 10-point ordering is preserved and cross-referenced per phase.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no unmet dependencies)
- **[Story]**: US1–US6 map to spec.md's six user stories; Setup/Foundational/Polish phases carry no story label

## Path Conventions

Web app layout per plan.md: existing backend at repo root (`src/main/java/com/ticketmanagement/...`), new `frontend/` directory at repo root for the entire Next.js app.

---

## Phase 1: Setup — user's step 1 (scaffolding, Dockerfile, compose, env)

**Purpose**: Get an empty-but-runnable frontend into the existing Docker Compose stack before any feature code exists.

- [X] T001 Scaffold Next.js 14 (App Router) + TypeScript project in `frontend/` (`frontend/package.json`, `frontend/next.config.mjs`, `frontend/tsconfig.json`, `frontend/src/app/layout.tsx` placeholder, `frontend/src/app/page.tsx` placeholder) — note: `next.config.ts` is unsupported on Next.js 14 (requires 15+), used `.mjs` instead
- [X] T002 [P] Add TanStack Query, Vitest, React Testing Library, Playwright as dependencies in `frontend/package.json`; configure `frontend/vitest.config.ts` and `frontend/playwright.config.ts`
- [X] T003 [P] Configure ESLint + Prettier for `frontend/` (`frontend/.eslintrc.json`, `frontend/.prettierrc`)
- [X] T004 Write `frontend/Dockerfile`: multi-stage — `node:20-alpine` build stage (`npm ci && npm run build`), `node:20-alpine` runtime stage running `next start` as non-root user, `EXPOSE 3000` (per architecture.md §Deployment architecture) — verified: `docker build` succeeds, container serves placeholder page on port 3000 as non-root
- [X] T005 Extend root `docker-compose.yml` with a `frontend` service: `build: ./frontend`, `depends_on: app` (Feature 1's backend service name), environment variable for the API base URL pointing at `http://app:8080` (compose-internal DNS, never `localhost`), host port mapping via `${FRONTEND_PORT:-3000}:3000` — note: env var is `API_BASE_URL` (server-side only, no `NEXT_PUBLIC_` prefix) since it's consumed by Next.js Route Handlers acting as a backend-for-frontend proxy, not by browser code directly (browsers cannot resolve Compose service names) — verified via `docker compose config` and a live `docker compose up`
- [X] T006 Add the new API-base-URL environment variable (`API_BASE_URL`, not `NEXT_PUBLIC_API_BASE_URL` — see T005 note) and `FRONTEND_PORT` to root `.env.example` with placeholder/example values, matching the existing `MYSQL_*`/`APP_PORT` pattern; no secrets committed

**Checkpoint**: `docker compose up --build` brings up `mysql` + `app` + `frontend`; frontend serves a placeholder page reachable on the host; frontend container reaches the backend at `http://app:8080` internally — **verified live**: all three services started healthy, `curl localhost:13001/` → 200, `curl localhost:8080/api/v1/tickets` → 200, and `docker exec` into the frontend container successfully called `http://app:8080/api/v1/tickets` confirming compose-network DNS resolution works end-to-end.

---

## Phase 2: Foundational (blocking prerequisites) — user's steps 2 & 3 + new backend endpoint

**Purpose**: Typed API client, shared layout/nav shell, shared error-display component, and the one new backend endpoint (FR-005b) — nothing in Phase 3+ can be built without these.

**⚠️ CRITICAL**: No user-story work begins until this phase is complete.

- [X] T007 [P] Create TypeScript types mirroring data-model.md in `frontend/src/types/ticket.ts` (`Ticket`: `id: string`, `title: string`, `description: string`, `priority: "LOW" | "MEDIUM" | "HIGH"`, `assignee: string`, `status: "OPEN" | "IN_PROGRESS" | "RESOLVED" | "CLOSED" | "CANCELLED"`, `category: string | null`, `createdAt: string`, `updatedAt: string`, `comments?: Comment[]`)
- [X] T008 [P] Create `frontend/src/types/comment.ts` (`Comment`: `id: string`, `ticketId: string`, `content: string`, `createdAt: string`)
- [X] T009 [P] Create `frontend/src/types/page.ts` (`TicketPage`: `content: Ticket[]`, `page: number`, `size: number`, `totalElements: number`, `totalPages: number`)
- [X] T010 [P] Create `frontend/src/types/apiError.ts` (`ApiError`: `timestamp: string`, `status: number`, `error: string`, `code: "VALIDATION_FAILED" | "TICKET_NOT_FOUND" | "INVALID_TRANSITION" | "UNKNOWN_FILTER"`, `message: string`, `path: string`, `details: { field: string; rejectedValue: unknown; message: string }[]`) — note: `code` widened to include a synthesized `"NETWORK_ERROR"` value (not a backend code) so client-side failures share the same mapping path
- [X] T011 [P] Create `frontend/src/types/assignee.ts` (`AssigneesResponse`: `assignees: string[]`)
- [X] T012 Implement `frontend/src/lib/api/client.ts`: server-side-only `fetch` wrapper (used only from Next.js Route Handlers, never imported by client components) reading `API_BASE_URL` (set in T006 — no `NEXT_PUBLIC_` prefix, since browsers cannot resolve the Compose service name) at request time, JSON parsing, throws a typed `ApiRequestError` (data-model.md shape) on non-2xx responses, synthesizes a fixed "couldn't reach the server" payload on network/timeout failure (no raw stack trace ever surfaces) — guarded with the `server-only` package so an accidental client-component import fails the build
- [X] T012a Implement Route Handlers under `frontend/src/app/api/` (`tickets/route.ts`, `tickets/[id]/route.ts`, `tickets/[id]/transitions/route.ts`, `tickets/[id]/comments/route.ts`, `assignees/route.ts`) that call `client.ts` server-side and forward the backend's response/status/body verbatim — this is the actual network boundary the browser talks to (same-origin), never `http://app:8080` directly — all 5 marked `export const dynamic = "force-dynamic"` after `next build` tried to statically prerender `/api/assignees` at build time (no `API_BASE_URL` available then) and failed; verified live via `docker compose up`: 200/201/400/404/409 all relayed with the backend's exact body and status
- [X] T013 [P] Implement `frontend/src/lib/api/tickets.ts` — browser-side functions used by components, calling the same-origin Route Handlers from T012a (e.g. `fetch("/api/tickets")`), not the backend directly: `createTicket`, `listTickets(q?, status?, page?, size?)`, `getTicket(id)`, `updateTicket(id, partial)`, `transitionTicket(id, targetStatus)` — matching `specs/001-ticket-management-api/api-contract.md` endpoint shapes exactly
- [X] T014 [P] Implement `frontend/src/lib/api/comments.ts` (browser-side, calls the T012a Route Handlers): `addComment(ticketId, content)`
- [X] T015 [P] Implement `frontend/src/lib/api/assignees.ts` (browser-side, calls the T012a Route Handlers): `listAssignees()`
- [X] T016 Implement `frontend/src/lib/errors/mapApiError.ts`: translates `ApiError.code` into a `{ kind: "field" | "banner" | "fullpage"; text: string; fieldName?: string }[]` model per the table in architecture.md §Error mapping (VALIDATION_FAILED → per-field or banner depending on `details[]`; TICKET_NOT_FOUND → fullpage; INVALID_TRANSITION → banner near transition control; UNKNOWN_FILTER → banner; network failure → fullpage/banner "couldn't reach the server")
- [X] T017 [P] Implement `frontend/src/components/errors/ErrorDisplay.tsx`: renders the three message kinds from T016's model consistently; this is the only component any screen uses for error/empty-state presentation
- [X] T018 [P] Implement `frontend/src/components/layout/NavShell.tsx`: persistent top-level nav with a link to the ticket list and a reserved-but-currently-empty slot for the future AI Q&A panel (no placeholder UI rendered, per FR-016)
- [X] T019 Wire `NavShell.tsx`, a React Query `QueryClientProvider` (via a `Providers.tsx` client-component wrapper — required since `QueryClient` needs a `"use client"` boundary), and a page-level `PageErrorBoundary` (using `ErrorDisplay.tsx`) into `frontend/src/app/layout.tsx`
- [X] T020 Backend (new, scoped exception per FR-005b/research.md §5): implement `GET /api/v1/assignees` returning distinct, non-null, alphabetically-sorted `assignee` values across all tickets — added `AssigneeController` in `src/main/java/com/ticketmanagement/ticket/controller/`, a `@Query` distinct-assignees method on `TicketRepository`, `listDistinctAssignees()` on `TicketService`/`TicketServiceImpl`, and `AssigneesResponse` DTO — verified live: empty-system → `{"assignees":[]}`, after creating a ticket → `{"assignees":["jane.doe"]}`
- [X] T021 Backend: unit test for the new distinct-assignees service method in `src/test/java/com/ticketmanagement/ticket/service/TicketServiceImplTest.java`, per constitution Principle III — covers both a populated and an empty-tickets-table case; full suite re-run: 31/31 tests pass in that class (was 29, +2 new), no regressions across all 93 backend tests

**Checkpoint**: typed API client, shared nav/error components, and the new assignees endpoint all exist and are independently verifiable (e.g. via a temporary test page or `curl`) before any screen is built.

---

## Phase 3: User Story 1 - Browse and find tickets (P1) — user's step 4

**Goal**: List view with status filter, keyword search, and infinite scroll (spec.md User Story 1).

**Independent Test**: Load `/tickets` against seeded backend data, apply a status filter, type a search keyword, confirm results match; scroll to trigger a second page load.

- [X] T022 [P] [US1] Component test: `TicketList` renders "no tickets found" when a mocked page response has `totalElements: 0`, in `frontend/tests/components/TicketList.test.tsx`
- [X] T023 [P] [US1] Component test: `TicketList` triggers a next-page fetch when scroll nears bottom and `page + 1 < totalPages`, and does not on the last page, in `frontend/tests/components/TicketList.test.tsx` — caught a real bug while writing this: a plain `ref` read inside a `useEffect` gated on unrelated query-state deps misses the sentinel `<div>` mounting when `hasNextPage` is `false` from the very first render (single-page case) instead of transitioning `undefined → false`; fixed `TicketList.tsx` to use a callback ref that attaches the `IntersectionObserver` exactly when the node mounts
- [X] T024 [US1] Implement `frontend/src/components/tickets/TicketRow.tsx`: renders title, status badge, priority, assignee for one ticket (FR-001)
- [X] T025 [US1] Implement `frontend/src/components/tickets/SearchFilterBar.tsx`: debounced keyword input (`q`) + status dropdown, combinable (FR-004)
- [X] T026 [US1] Implement `frontend/src/components/tickets/TicketList.tsx`: `useInfiniteQuery` (TanStack Query) against `listTickets`, renders `TicketRow` per item, appends next page automatically near scroll-bottom (FR-004a), renders "no tickets found" via `ErrorDisplay` when empty, shows inline "couldn't load more — retry" on a failed next-page fetch without disturbing already-loaded rows
- [X] T027 [US1] Implement `frontend/src/app/tickets/page.tsx`: composes `SearchFilterBar` + `TicketList` + "Create ticket" link to `/tickets/new`; full-page "couldn't reach the server" state (via `ErrorDisplay`) on initial-load failure — verified live in a real headless browser (Playwright) against `docker compose up`: nav, search bar, status dropdown, and both seeded tickets (title/status/priority/assignee) render correctly through the full proxy chain

**Checkpoint**: User Story 1 fully functional and independently testable/demoable.

---

## Phase 4: User Story 2 - Create a new ticket (P1) — user's step 5

**Goal**: Creation form (title, description, priority, assignee) with backend-driven validation-error display (spec.md User Story 2).

**Independent Test**: Submit valid values → new `OPEN` ticket created and shown; submit with a blank required field → specific field-level message, no ticket created.

- [X] T028 [P] [US2] Component test: `TicketForm` (create mode) renders blank-field hints before submit when title/description/assignee are empty, in `frontend/tests/components/TicketForm.test.tsx` — also covers `priority` (required per api-contract.md, not explicitly named in this task but same rule)
- [X] T029 [P] [US2] Component test: `TicketForm` renders per-field backend errors from a mocked `400 VALIDATION_FAILED` response with `details[]` next to each offending input, in `frontend/tests/components/TicketForm.test.tsx`
- [X] T030 [P] [US2] Component test: `TicketForm` renders a single banner (via `ErrorDisplay`) when `details[]` is empty but `message` is present, in `frontend/tests/components/TicketForm.test.tsx`
- [X] T031 [P] [US2] Component test: `AssigneeCombobox` renders "no assignees yet — type a name to add one" when `GET /assignees` returns `[]`, and still accepts a typed value, in `frontend/tests/components/AssigneeCombobox.test.tsx` — plus a second test for filtering/selecting from a populated list
- [X] T032 [US2] Implement `frontend/src/components/tickets/AssigneeCombobox.tsx`: ARIA `combobox` pattern backed by `listAssignees()` (React Query `useQuery(["assignees"])`), filters options as the user types, accepts a typed value not in the list (FR-005a, FR-005c)
- [X] T033 [US2] Implement `frontend/src/components/tickets/TicketForm.tsx` in "create" mode: title/description/priority(select)/assignee(`AssigneeCombobox`) fields; client-side blank-field pre-flight hints (display-only, FR-011); on submit calls `createTicket`, maps any `400 VALIDATION_FAILED` via `mapApiError.ts` to inline field/banner errors (FR-012), does not clear entered values on rejection
- [X] T034 [US2] Implement `frontend/src/app/tickets/new/page.tsx`: renders `TicketForm` (create mode); on successful creation (`201`), navigates to `/tickets/{id}` — verified live end-to-end with a real headless browser (Playwright) against `docker compose up`: filled the real form, submitted, backend created the ticket with exact field values, router navigated to the new ticket's real UUID (404 on that route is expected — detail page is Phase 5, not yet built)

**Checkpoint**: User Story 2 fully functional and independently testable/demoable; combinable with User Story 1 (new tickets appear in the list).

---

## Phase 5: User Story 3 - View full ticket detail and history (P1) — user's step 6 (view portion)

**Goal**: Detail view showing all fields, status, and full comment history (spec.md User Story 3).

**Independent Test**: Open a ticket (seeded with comments) and confirm every field, status, and every comment (in order) render; open a non-existent ticket id and confirm a "not found" state.

- [X] T035 [P] [US3] Component test: `CommentList` renders "no comments yet" when `comments: []`, in `frontend/tests/components/CommentList.test.tsx` — also added a test proving client-side chronological rendering regardless of input array order (defensive, see T038 note)
- [X] T036 [P] [US3] Component test: detail page/component renders a "ticket not found" state on a mocked `404 TICKET_NOT_FOUND`, in `frontend/tests/components/TicketDetail.test.tsx`
- [X] T037 [US3] Implement `frontend/src/components/tickets/StatusBadge.tsx`: renders current status — also refactored `TicketRow` (Phase 3) to reuse it instead of its own inline status span
- [X] T038 [US3] Implement `frontend/src/components/comments/CommentList.tsx`: renders comments oldest→newest with content + timestamp; "no comments yet" empty state via `ErrorDisplay`-consistent styling (spec.md User Story 3 Scenario 3) — **real bug found and fixed at the actual source**: live testing against the real backend showed comments rendered out of order even with the client-side sort in place, because `CommentRepository.findByTicketId` had **no `ORDER BY` at all** (undefined DB row order) and `Comment.createdAt` was a plain `TIMESTAMP` column (1-second precision — MySQL/H2 both truncate sub-second parts), so rapid successive comments got identical stored timestamps and any sort became a coin-flip. Fixed the backend: added `findByTicketIdOrderByCreatedAtAscIdAsc`, and widened the `created_at` column to `TIMESTAMP(6)` (microsecond precision) in the V1 migration (safe to edit directly — nothing has ever shipped past this branch). Re-verified live: three comments posted back-to-back now come back with distinct microsecond timestamps in correct order. Added `CommentIntegrationTest.commentsReturnedInChronologicalOrder` and a `CommentServiceImplTest` ordering test; full backend suite now 97/97 (was 95, +2)
- [X] T039 [US3] Implement `frontend/src/components/tickets/TicketDetail.tsx` (read-only mode): renders title, description, priority, assignee, category, status (`StatusBadge`), created/updated timestamps, embeds `CommentList`
- [X] T040 [US3] Implement `frontend/src/app/tickets/[id]/page.tsx`: `useQuery(["ticket", id])` against `getTicket`; renders `TicketDetail` on success; renders "ticket not found" via `ErrorDisplay` on `404 TICKET_NOT_FOUND` (mapped by `mapApiError.ts`) — verified live end-to-end: real ticket with real comments rendered correctly in a headless browser, and a well-formed non-existent UUID correctly showed the not-found state

**Checkpoint**: User Story 3 fully functional and independently testable/demoable (works against tickets seeded directly via the API, without needing the create form).

---

## Phase 6: User Story 4 - Edit ticket fields and reassign (P2) — user's step 6 (edit portion)

**Goal**: Edit title/description/priority/assignee from the detail view, including assignee-only changes (spec.md User Story 4).

**Independent Test**: Open a ticket, change title+priority+assignee together, save, confirm only those fields changed; then change only assignee and confirm no other field is affected.

- [X] T041 [P] [US4] Component test: `TicketForm` (edit mode) submits a `PATCH` body containing only the fields the user actually changed, in `frontend/tests/components/TicketForm.test.tsx`
- [X] T042 [P] [US4] Component test: `TicketForm` (edit mode) shows field-level errors inline on a mocked `400 VALIDATION_FAILED` and stays in edit mode with the user's typed values intact, in `frontend/tests/components/TicketForm.test.tsx`
- [X] T043 [US4] Extend `frontend/src/components/tickets/TicketForm.tsx` with an "edit" mode: pre-fills from the current `Ticket`, reuses `AssigneeCombobox`, tracks which fields were actually touched, calls `updateTicket(id, changedFieldsOnly)` on save — reworked the props to a discriminated union (`{mode:"create",...} | {mode:"edit", ticket, ...}`) so create/edit callers are type-checked distinctly; updated `app/tickets/new/page.tsx` for the renamed `onSuccess` prop
- [X] T044 [US4] Extend `frontend/src/components/tickets/TicketDetail.tsx` with an "Edit" button toggling between read-only display and `TicketForm` (edit mode) in place, on the same route; on successful save, re-renders read-only view with updated values (no navigation) — **real bug caught before it shipped**: the backend's `PATCH` response omits `comments` (`TicketController.update` returns `TicketResponse.withoutComments`), so naively replacing the cached ticket after a save would have made the comment list disappear from the screen after every edit; fixed by merging the prior `ticket.comments` back into the updated ticket before it reaches the query cache (`app/tickets/[id]/page.tsx` now uses `useQueryClient().setQueryData` for in-place updates, no navigation/reload). Verified live end-to-end: edited title + assignee only via a real headless browser, confirmed description/priority unchanged, confirmed the pre-existing comment was still visible after save, and cross-checked the backend directly — exact match

**Checkpoint**: User Story 4 fully functional and independently testable/demoable.

---

## Phase 7: User Story 5 - Add a comment (P2) — user's step 7

**Goal**: Add comments from the detail view, appearing immediately without a full reload (spec.md User Story 5).

**Independent Test**: Open a ticket, submit a comment, confirm it appears at the end of the comment history without a page reload; submit a blank comment and confirm it's rejected with a specific message.

- [X] T045 [P] [US5] Component test: `CommentForm` shows "content is required" inline error on a mocked blank-content `400 VALIDATION_FAILED` and does not clear the composer, in `frontend/tests/components/CommentForm.test.tsx` — design correction made while writing this test: the original plan had a client-side blank pre-check (like `TicketForm`'s), but that would make this exact scenario (a mocked *backend* 400 for blank content) permanently unreachable since the client would always intercept first; removed the redundant client-side check for this one field so the backend-error path is real, not dead code
- [X] T046 [P] [US5] Component test: `CommentForm` appends the new comment to `CommentList` on successful submission without a full page reload, in `frontend/tests/components/CommentForm.test.tsx`
- [X] T047 [US5] Implement `frontend/src/components/comments/CommentForm.tsx`: text box + submit; calls `addComment`; on `201` invalidates/updates the `["ticket", id]` React Query cache so `CommentList` re-renders with the new comment (SC-006); on `400 VALIDATION_FAILED` (blank content) shows inline error under the composer, does not clear it
- [X] T048 [US5] Wire `CommentForm` into `frontend/src/components/tickets/TicketDetail.tsx`, always visible below `CommentList`

**Checkpoint**: User Story 5 fully functional and independently testable/demoable.

---

## Phase 8: User Story 6 - Transition ticket status safely (P2) — user's step 8

**Goal**: Status transition controls that only offer valid next states and surface backend rejection reasons (spec.md User Story 6).

**Independent Test**: Open tickets in different statuses, confirm only the state-machine-valid next statuses are offered (e.g. none for `CLOSED`/`CANCELLED`); perform a valid transition and confirm the status updates; force an invalid one and confirm the backend's specific rejection message appears.

- [X] T049 [P] [US6] Component test: `TransitionControl` renders only the statuses in the client-side lookup table for a given current status (e.g. `CLOSED` → no options, `OPEN` → `IN_PROGRESS`/`CANCELLED`), in `frontend/tests/components/TransitionControl.test.tsx`
- [X] T050 [P] [US6] Component test: `TransitionControl` renders the backend's `409 INVALID_TRANSITION` message (naming current + attempted status) from a mocked response, and resets selection afterward, in `frontend/tests/components/TransitionControl.test.tsx` — also added a fourth test proving comments survive a transition (see T052 note)
- [X] T051 [US6] Add the status-transition lookup table from data-model.md to `frontend/src/lib/transitions.ts` (`OPEN → [IN_PROGRESS, CANCELLED]`, `IN_PROGRESS → [RESOLVED, CANCELLED]`, `RESOLVED → [CLOSED]`, `CLOSED → []`, `CANCELLED → []`)
- [X] T052 [US6] Implement `frontend/src/components/tickets/TransitionControl.tsx`: renders only the lookup table's next statuses for the current status; on selection+confirm calls `transitionTicket`; on `200` invalidates `["ticket", id]` cache so `StatusBadge`/the control itself re-render with the new status; on `409 INVALID_TRANSITION` shows the backend's message (via `ErrorDisplay`) inline near the control and resets to unselected (FR-013) — applied the same comments-preservation fix as T044 preemptively: `TicketController.transition` also returns `TicketResponse.withoutComments`, so the cache update merges `previous.comments` back in rather than letting a transition silently wipe the comment list
- [X] T053 [US6] Wire `TransitionControl` into `frontend/src/components/tickets/TicketDetail.tsx` header, next to `StatusBadge`

**Checkpoint**: User Story 6 fully functional and independently testable/demoable. All six user stories now independently deliverable.

---

## Phase 9: Cross-cutting tests — user's step 9

**Purpose**: The remaining test-strategy.md items not already covered inline in Phases 3–8, plus the one required E2E flow.

- [X] T054 [P] Component test: `ErrorDisplay` renders the three message kinds (`field`, `banner`, `fullpage`) distinctly given a mapped message model, in `frontend/tests/components/ErrorDisplay.test.tsx`
- [X] T055 Playwright E2E test in `frontend/tests/e2e/create-edit-transition.spec.ts`: navigate to `/tickets/new` → fill title/description/priority/assignee (typing a new assignee name) → submit → assert navigated to `/tickets/{id}` with status `OPEN` and all fields visible → click Edit, change title + priority, save → assert updated fields, description/assignee unchanged → use `TransitionControl` to move `OPEN → IN_PROGRESS` → assert status badge updates and control now offers only `RESOLVED`/`CANCELLED` — **run for real against the live `docker compose` stack** (frontend on port 13001 via `E2E_BASE_URL`), passed on the first run
- [X] T056 Add `frontend/package.json` scripts: `test` (Vitest run of all `frontend/tests/components/`), `test:e2e` (Playwright run of `frontend/tests/e2e/`) — already added in Phase 1 (T002); verified present

**Checkpoint**: `npm test` passes with no Docker dependency; `npm run test:e2e` passes against a running `docker compose up` stack.

---

## Phase 10: Polish & final validation — user's step 10

**Purpose**: Full-stack manual verification via the single-command deployment, matching quickstart.md.

- [X] T057 Run `docker compose up --build` from repo root; confirm `mysql`, `app`, and `frontend` all report healthy/running with no manual setup step beyond `.env` — confirmed: all three services healthy/running from a fresh MySQL volume
- [X] T058 Manually verify: create a ticket via the UI (typing a new assignee name against an empty assignee list), confirm it lands on the new ticket's detail view with status `OPEN` — verified live via headless browser: created, navigated to real UUID, status `OPEN` (note: assignee list was already populated from earlier phase testing, not genuinely empty this run — the empty-list path itself is covered by `AssigneeCombobox.test.tsx`)
- [X] T059 Manually verify: edit that ticket (title + assignee only), confirm only those fields changed — verified: description and other untouched fields remained exactly as created
- [X] T060 Manually verify: transition that ticket `OPEN → IN_PROGRESS`, confirm status badge updates and offered transitions narrow accordingly — verified: badge updated, dropdown narrowed to `RESOLVED`/`CANCELLED`
- [X] T061 Manually verify: force a validation error (blank title on edit) and confirm the UI shows the backend's specific field-level message, not a generic "something went wrong" (FR-012) — verified: exact backend "must not be blank" message shown inline
- [X] T062 Manually verify: navigate to a well-formed but non-existent ticket UUID, confirm the "ticket not found" state renders (spec.md User Story 3 Scenario 4) — verified: not-found state rendered, zero console errors across all five checks
- [X] T063 [P] Review `frontend/` against `rules/` conventions applicable to this codebase (naming, no hardcoded hosts/secrets) and against ux checklist `specs/002-ticket-management-ui/checklists/ux.md`, resolving or explicitly deferring any open items — `rules/*.md` are Java/Spring-specific (no frontend-specific rules doc exists); grepped `frontend/src` for hardcoded hosts/secrets: none found (one code comment mentioning `http://app:8080` as an explanatory note, not a literal value); `.env` confirmed still git-ignored, `.env.example` has placeholders only. `ux.md`: 14/34 resolved (see Phase 5 note); remaining 20 are real, explicitly-deferred gaps (accessibility, exact UI copy, some edge cases), not silently dropped

---

## Dependencies & execution order

1. **Setup (Phase 1)** — no dependencies; must complete first.
2. **Foundational (Phase 2)** — depends on Phase 1; blocks every user story phase (types, API client, error mapping, nav shell, and the new backend assignees endpoint are shared by all).
3. **User Stories (Phases 3–8)** — each depends only on Phase 2, not on each other:
   - US1 (Phase 3) and US2 (Phase 4) can proceed in parallel once Phase 2 is done (different files: `TicketList`/`SearchFilterBar` vs. `TicketForm`/`AssigneeCombobox`).
   - US3 (Phase 5) depends on nothing from US1/US2 beyond Phase 2 (can be demoed against API-seeded tickets alone), but in practice is easiest to verify once US2 exists to create a ticket to view.
   - US4 (Phase 6) extends `TicketForm`/`TicketDetail` from US3 — build after Phase 5.
   - US5 (Phase 7) extends `TicketDetail` from US3 — can run in parallel with Phase 6 (different new files: `CommentForm.tsx` vs. `TicketForm.tsx` edits), both depending on Phase 5.
   - US6 (Phase 8) extends `TicketDetail` from US3 — can run in parallel with Phases 6–7 (different new files: `TransitionControl.tsx`, `transitions.ts`).
4. **Cross-cutting tests (Phase 9)** — depends on all of Phases 3–8 (E2E flow exercises US2, US4, US6 together).
5. **Polish & validation (Phase 10)** — depends on everything above; final gate.

## Parallel execution examples

- **Within Phase 2**: T007–T011 (type files) in parallel; T013–T015 (browser-side API modules) in parallel once T012a (Route Handlers) exists; T017/T018 (`ErrorDisplay`, `NavShell`) in parallel once T016 exists; T020–T021 (backend endpoint + its test) can proceed in parallel with the frontend Phase 2 tasks since they're a different codebase area.
- **Across stories**: once Phase 2 is done, one contributor can take US1 (Phase 3) while another takes US2 (Phase 4); after US3 (Phase 5) lands, US4/US5/US6 (Phases 6–8) can be split across three contributors in parallel.
- **Within each story's tests**: e.g. T028–T031 (US2's four component tests) are all `[P]` — different assertions on files that don't yet conflict, can be written before or alongside T032–T034's implementation (test-first if desired, though not mandated).

## Implementation strategy (MVP first)

- **MVP = User Story 1 + User Story 2** (Phases 1–4): a support agent can browse/search/filter existing tickets and create new ones. This alone delivers standalone value and satisfies SC-001/SC-002/SC-005 for the create+browse loop.
- **Next increment = User Story 3** (Phase 5): full detail view, unlocking every subsequent story.
- **Then User Stories 4, 5, 6** (Phases 6–8) can land in any order or in parallel — none blocks the others, only Phase 5.
- **Final increments** = Phase 9 (tests) and Phase 10 (deployment validation) — required before calling the feature done per the constitution's test-first and single-command-deployment principles, but not required for an internal demo of a given story.
