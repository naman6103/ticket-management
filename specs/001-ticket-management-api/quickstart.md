# Quickstart: Validate the Ticket Management REST API

Prerequisites: Docker + Docker Compose (for the full stack) or a local JDK 21 + Maven (for the H2 dev profile). See `api-contract.md` for full request/response schemas and `data-model.md` for field definitions.

## Option A — Run the full stack (MySQL via Docker)

```bash
cp .env.example .env   # fill in DB credentials; .env is git-ignored
docker compose up --build
```

App comes up on `http://localhost:8080` against MySQL, with Flyway migrations applied automatically — no manual DB setup.

## Option B — Run locally against H2 (dev profile)

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

## Validation scenarios

Run these against whichever base URL you started (`http://localhost:8080/api/v1`):

1. **Create + retrieve** (proves SC-001):
   ```bash
   curl -s -X POST $BASE/tickets -H 'Content-Type: application/json' -d '{
     "title": "Login page returns 500",
     "description": "Users see a 500 error after login.",
     "priority": "HIGH",
     "assignee": "jane.doe"
   }'
   # capture "id" from the response, then:
   curl -s $BASE/tickets/{id}
   ```
   Expect: `201` then `200`; `status` is `OPEN`; all submitted fields match.

2. **Valid transition, then invalid transition** (proves SC-002 / SC-003):
   ```bash
   curl -s -X POST $BASE/tickets/{id}/transitions -d '{"targetStatus":"IN_PROGRESS"}' -H 'Content-Type: application/json'
   curl -s -X POST $BASE/tickets/{id}/transitions -d '{"targetStatus":"OPEN"}' -H 'Content-Type: application/json'
   ```
   Expect: first call `200` with `status: IN_PROGRESS`; second call `409` with `code: INVALID_TRANSITION`, and a follow-up `GET /tickets/{id}` still shows `IN_PROGRESS`.

3. **Malformed input rejected** (proves SC-004):
   ```bash
   curl -s -X POST $BASE/tickets -H 'Content-Type: application/json' -d '{"title": "", "description": "x", "priority": "HIGH", "assignee": "a"}'
   ```
   Expect: `400`, `code: VALIDATION_FAILED`, `details[0].field == "title"`.

4. **Search and filter** (proves SC-006 / SC-007):
   ```bash
   curl -s "$BASE/tickets?q=login"
   curl -s "$BASE/tickets?status=OPEN"
   curl -s "$BASE/tickets?status=NOT_A_STATUS"
   ```
   Expect: first two return only matching tickets; third returns `400` with `code: UNKNOWN_FILTER`.

5. **Comment** (proves comment flow):
   ```bash
   curl -s -X POST $BASE/tickets/{id}/comments -H 'Content-Type: application/json' -d '{"content": "Reproduced on staging."}'
   curl -s $BASE/tickets/{id}
   ```
   Expect: `201`, then the comment appears in the ticket's `comments[]`.

6. **Restart persistence** (proves SC-005): stop and restart the compose stack (`docker compose restart app`, or stop/start with the same MySQL volume) and repeat step 1's `GET` — the ticket created earlier must still be returned unchanged.

## Automated verification

The scenarios above are formalized as integration tests in `test-strategy.md` (`TicketCrudIntegrationTest`, `TicketLifecycleIntegrationTest`, `TicketSearchAndFilterIntegrationTest`, `CommentIntegrationTest`, `RestartPersistenceIntegrationTest`) — run `./mvnw test` to execute the full suite instead of manual curl calls.
