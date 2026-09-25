# Test Strategy: Ticket Management Web UI

Per constitution Principle III, the frontend itself has no service/validation/state-machine logic to unit-test (FR-011 explicitly forbids reimplementing that logic client-side) — so this feature's tests focus on what the UI *is* responsible for: correctly rendering data, correctly displaying validation/error states the backend returns, and correctly wiring one full user journey end-to-end. This is a deliberate, narrower scope than backend testing, not a gap — the state machine and validation rules are already exhaustively tested at the API layer (Feature 1's `test-strategy.md`/`state-machine.md`).

## Component tests (Vitest + React Testing Library)

**Scope**: form rendering, client-side pre-flight hints, and — the highest-value target — correct rendering of backend-driven validation/error states (FR-012, FR-013). Backend calls are mocked (MSW or manual fetch mocks) so these tests are fast and deterministic; they assert on *rendering*, never on real network behavior.

| Test | What it proves |
|---|---|
| `TicketForm` renders blank-field hints before submit when title/description/assignee are empty | Client-side pre-flight hints appear without a network call (spec.md User Story 2 Scenario 2, display-only per FR-011) |
| `TicketForm` renders per-field backend errors from a mocked `400 VALIDATION_FAILED` response with `details[]` | Each `{field, message}` renders next to its input, not as a generic banner (FR-012, spec.md User Story 2 Scenario 3) |
| `TicketForm` renders a single banner when `details[]` is empty but `message` is present | Fallback path in `mapApiError.ts` (architecture.md) is exercised |
| `CommentForm` shows "content is required" inline error on a mocked blank-content `400` and does not clear the composer | spec.md User Story 5 Scenario 2 |
| `TransitionControl` renders only the statuses in the client-side lookup table for a given current status (e.g. `CLOSED` → no options) | FR-010, data-model.md lookup table, spec.md User Story 6 Scenario 1 |
| `TransitionControl` renders the backend's `409 INVALID_TRANSITION` message (naming current + attempted status) from a mocked response, and resets selection | FR-013, spec.md User Story 6 Scenario 3 |
| `TicketList` renders "no tickets found" when a mocked page response has `totalElements: 0` | spec.md User Story 1 Scenario 5 |
| `TicketList` triggers a next-page fetch when scroll nears bottom and `page + 1 < totalPages`, and does not when it's the last page | FR-004a |
| `CommentList` renders "no comments yet" when `comments: []` | spec.md User Story 3 Scenario 3 |
| `AssigneeCombobox` renders "no assignees yet — type a name to add one" when `GET /assignees` returns `[]`, and still accepts a typed value | FR-005c |
| `ErrorDisplay` renders the three message kinds (`field`, `banner`, `fullpage`) distinctly given a mapped message model | architecture.md error-mapping contract, reused consistently across screens |

**Out of scope for component tests** (explicitly, to avoid the anti-pattern the constitution warns against for RAG — applied here by analogy): asserting on exact copy/wording beyond what the spec requires (e.g. exact pixel layout, exact CSS) — tests assert on presence/content of the *required* information, not visual styling.

## End-to-end test (Playwright)

**One required flow**, run against the full Docker Compose stack (frontend + backend + MySQL) so it validates the real integration, not mocks:

**Flow: create ticket → edit it → transition its status**

1. Navigate to `/tickets/new`, fill in title/description/priority/assignee (typing a new assignee name, exercising FR-005c/combobox-typed-value path), submit.
2. Assert: navigated to the new ticket's detail view (`/tickets/{id}`), status shown as `OPEN`, all entered fields visible.
3. Click "Edit", change the title and priority, save.
4. Assert: detail view shows the updated title/priority; description/assignee unchanged (partial-update semantics, spec.md User Story 4 Scenario 1).
5. Use the transition control to move `OPEN → IN_PROGRESS` (a valid transition per state-machine.md).
6. Assert: status badge now shows `IN_PROGRESS`; the transition control now offers only `RESOLVED`/`CANCELLED` (re-rendered per the new current status).

**Why this one flow**: it is the shortest path that touches create, edit (with partial-update semantics), and a real backend-validated transition — the three most state-changing capabilities in the feature — against the real API contract, catching integration mismatches (e.g. field-name typos between `types/` and the actual backend response) that mocked component tests cannot.

**Explicitly not covered by this required E2E test** (left to component tests or future additions, not this feature's one mandated flow): search/filter, infinite scroll, comment-adding, and invalid-transition rejection — each already has a targeted component test above, and spec.md does not require more than the one E2E flow.

## Test execution

- Component tests: `npm test` (Vitest), run in CI on every push; no Docker dependency (all backend calls mocked).
- E2E test: `npm run test:e2e` (Playwright), run against `docker compose up` (this feature's frontend + Feature 1's backend + MySQL); requires the stack healthy before running, mirroring how a reviewer would actually validate the feature (quickstart.md).
