# Quickstart: AI Q&A Panel

Validates the feature end-to-end against the real stack (frontend + backend + Elasticsearch + Ollama), the same way Feature 2's quickstart validates its own flow.

## Prerequisites

- `.env` populated per `.env.example` — no new variables needed for this feature.
- `docker compose up` — brings up `mysql`, `elasticsearch`, `ollama`, `app` (backend), `frontend`. No new service is introduced.
- At least one ticket exists whose description/comments contain distinctive text a question can match (create one via the UI, or `POST /api/v1/tickets` if the stack is fresh — Feature 1's contract).

## Steps

1. Open the frontend, confirm a new nav link (e.g. "Ask Assistant") is visible from any screen (FR-009, SC-004).
2. Click it → lands on `/assistant`: a question input and submit control, no answer shown yet.
3. Ask a question matching the ticket's distinctive content and submit.
4. Confirm: a loading indicator appears; the input and submit control are disabled for the duration of the request (spec Clarification #3); on success, the answer text renders followed by a separate citation list with at least one entry (spec Clarification #1).
5. Click a citation → confirm it opens `/tickets/{id}` in a **new** browser tab for the exact ticket cited (spec Clarification #2, FR-005), and the original `/assistant` tab's answer/citations remain unchanged.
6. Ask an unrelated/nonsense question with no matching ticket content → confirm a distinct "no relevant tickets found" state renders (no citations, no answer text).
7. Stop the backend (`docker compose stop app`) and submit another question → confirm the shared error-display component renders a request-failure message, visually distinct from step 6's no-match state (FR-007, FR-008). Restart the backend afterward (`docker compose start app`).

## Expected outcome

All of spec.md's acceptance scenarios (User Stories 1–4) pass by inspection. No backend files changed. `docker compose up` still brings up the whole system with zero manual steps beyond `.env` (constitution Principle II).
