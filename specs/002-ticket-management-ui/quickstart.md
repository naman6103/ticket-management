# Quickstart: Ticket Management Web UI

Validates that the feature works end-to-end against the real backend, via the single-command deployment the constitution requires.

## Prerequisites

- Docker + Docker Compose
- Repo root `.env` populated per `.env.example` (existing `MYSQL_*`/`APP_PORT` vars, plus the new frontend API base URL var introduced by this feature — see architecture.md §Deployment architecture)

## Bring up the whole stack

```bash
docker compose up --build
```

Expected: `mysql`, `app` (backend), and the new `frontend` service all report healthy/running; frontend reachable at `http://localhost:<FRONTEND_PORT>` (host-exposed) while internally it calls the backend at `http://app:8080`, never `localhost`.

## Manual validation scenarios

1. **Browse**: open the frontend URL → ticket list loads (empty state if no tickets exist yet, or rows if seeded via the API directly). Apply a status filter and a keyword search; confirm results narrow correctly (spec.md User Story 1).
2. **Create**: click "Create ticket", fill in title/description/priority, type a new name in the assignee field (list is empty on a fresh stack — combobox must accept the typed value per FR-005c), submit → lands on the new ticket's detail view with status `OPEN` (User Story 2).
3. **View detail**: confirm all fields, status, and (empty) comment history are visible; "no comments yet" state shown (User Story 3).
4. **Edit**: click "Edit", change title and assignee only, save → detail view reflects both changes; description/priority unchanged (User Story 4).
5. **Comment**: add a comment → appears immediately in the comment history without a page reload (User Story 5).
6. **Transition**: use the transition control to move the ticket from `OPEN` to `IN_PROGRESS` → status badge updates, control now offers only `RESOLVED`/`CANCELLED` (User Story 6).
7. **Error surfacing**: attempt an edit with a blank title → inline field error naming the exact reason (not "something went wrong"), matching whatever the backend's `details[]` says (FR-012).
8. **Not found**: navigate to `/tickets/00000000-0000-0000-0000-000000000000` (a well-formed but non-existent UUID) → "ticket not found" state (User Story 3 Scenario 4).

## Automated validation

```bash
# Component tests (no Docker required)
cd frontend && npm test

# E2E flow (requires the Docker Compose stack up and healthy)
cd frontend && npm run test:e2e
```

See `test-strategy.md` for what each covers.

## Contract references

- Full backend endpoint shapes: `specs/001-ticket-management-api/api-contract.md`
- What this feature calls and how responses map to screens: `contracts/consumed-api.md`
- Ticket status transition matrix: `specs/001-ticket-management-api/state-machine.md`
