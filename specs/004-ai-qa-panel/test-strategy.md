# Test Strategy: AI Q&A Panel

Extends `specs/002-ticket-management-ui/test-strategy.md` — same scope philosophy: this feature has no service/validation/state-machine logic of its own (constitution Principle III), so tests target rendering of already-generated backend responses, never the RAG answer's *content* (`rules/testing.md`: "Do not put `assertEquals(expectedAnswer, ragAnswer)`... on probabilistic model output"). Fixture answer text/citations used below are arbitrary stand-ins for a shape, not assertions about answer quality.

## Component tests (Vitest + React Testing Library)

**Scope**: the panel's three result states (grounded answer, no-match, error) and the disabled-while-pending behavior. Backend calls are mocked, exactly as Feature 2's component tests mock `lib/api/*`.

| Test | What it proves |
|---|---|
| `AiQaPanel` renders the answer text and a citation list item per `ticketId` from a mocked `noRelevantTicketsFound: false` response | spec.md User Story 1 Scenarios 1–2; citations render as a separate list, not inline in the prose (spec Clarification #1) |
| `AiQaPanel` clears a previously-rendered answer/citations before rendering a new question's result | spec.md User Story 1 Scenario 3, FR-010 |
| `CitationList` renders each link with `href="/tickets/{id}"` and `target="_blank"` | spec.md User Story 2, FR-005 |
| `AiQaPanel` renders `NoRelevantTicketsState` (no citation elements present) from a mocked `noRelevantTicketsFound: true` response | spec.md User Story 3 Scenario 1 |
| The no-match block and the `ErrorDisplay`-rendered failure block use different `data-testid`/CSS classes, asserted from each state rendered in isolation | FR-007, SC-003 — proves the two are not the same element/markup, not a visual/pixel diff |
| `AiQaPanel` disables the textarea and submit button while the mutation is pending (mocked slow/unresolved fetch) | spec Clarification #3, FR-011 |
| `AiQaPanel` renders a field-level error under the textarea from a mocked `400 VALIDATION_FAILED` response with `details[]` | FR-002, FR-013 |
| `AiQaPanel` renders the shared error banner (via `ErrorDisplay`) from a mocked `502 AI_GENERATION_FAILED`, and separately from a mocked `503 AI_RETRIEVAL_UNAVAILABLE` response | FR-008, data-model.md |
| `NavShell` renders a link to `/assistant` | FR-009, SC-004 |

**Out of scope for component tests** (same exclusion Feature 2's test-strategy.md makes, applied here): asserting on the *wording* of the generated `answer` text, or on retrieval/citation accuracy — that is Feature 3's evaluation concern (`evaluation-strategy.md`), not this feature's.

## End-to-end test (Playwright) — new flow, addition to Feature 2's suite

**Flow: create a ticket → ask the assistant about it → see it cited → click through to the ticket**

1. Navigate to `/tickets/new`, create a ticket with distinctive title/description text.
2. Navigate to `/assistant` via the nav link (not a direct URL, to also exercise FR-009).
3. Ask a question containing that distinctive text, submit.
4. Assert: an answer renders, and the citation list contains the new ticket's ID.
5. Click that citation.
6. Assert: a new tab opens showing that exact ticket's detail view (title/description match what was created in step 1); the original `/assistant` tab still shows the same answer/citations, undisturbed (spec Clarification #2).

**Why this one flow**: it's the shortest path touching the full real integration end-to-end — real ticket creation, real ingestion into the vector store (Feature 3), a real retrieval + citation, and a real cross-tab navigation. Every other branch (no-match, error) is already covered by the mocked component tests above and doesn't need the real Elasticsearch/Ollama stack to verify UI behavior.

**Explicitly not covered by this E2E test**: the no-match and error-state branches (component-tested above); RAG answer-quality/citation-accuracy grading, which belongs to Feature 3's own evaluation suite (`RagRetrievalEvaluationRunner`, `evaluation-strategy.md`), not this feature's tests.

## Test execution

Unchanged from Feature 2: `npm test` (Vitest, no Docker dependency, mocked backend calls) in CI on every push; `npm run test:e2e` (Playwright) against the full `docker compose up` stack — now also requiring `elasticsearch`/`ollama` healthy as a precondition for this feature's new flow, the same way Feature 3's own backend tests already require them.
