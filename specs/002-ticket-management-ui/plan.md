# Implementation Plan: Ticket Management Web UI

**Branch**: `002-ticket-management-ui` | **Date**: 2026-09-25 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-ticket-management-ui/spec.md`

## Summary

A React/TypeScript frontend that consumes Feature 1's Ticket Management REST API to let support agents browse (with status filter + keyword search + infinite scroll), create, view, edit, comment on, and transition tickets — with a shared navigation shell, a typed API-client layer, and a shared error-display component so a future AI Q&A panel (Feature 4) slots in without restructuring. The UI performs no ticket business logic itself (validation, transition legality) — it renders whatever the backend's structured responses say. One narrow backend addition is included: a small read-only `GET /api/v1/assignees` endpoint (distinct assignee values already on tickets) to back the assignee combobox, since Feature 1's API has no such endpoint.

## Technical Context

**Language/Version**: TypeScript 5.x, Node.js 20 LTS (build/dev tooling)

**Primary Dependencies**: React 18, Next.js 14 (App Router), a typed fetch-based HTTP client generated/hand-typed against `specs/001-ticket-management-api/api-contract.md`, React Query (TanStack Query) for server-state caching/infinite-scroll pagination, no CSS framework mandated (plain CSS Modules) — kept minimal per constitution's stack-discipline principle.

**Storage**: N/A (frontend holds no persistent storage of its own; all ticket/comment/assignee data lives behind Feature 1's API and the new read-only assignee endpoint)

**Testing**: Vitest + React Testing Library for component tests; Playwright for the one end-to-end flow test (create → edit → transition)

**Target Platform**: Modern evergreen desktop browsers (Chrome/Firefox/Edge/Safari, last 2 versions), served from a Docker container behind a lightweight static/reverse-proxy web server

**Project Type**: Web application (frontend + existing backend) — `frontend/` added alongside the existing backend at repo root

**Performance Goals**: List/detail views interactive within 1s on a warm cache against a local/dev backend (no hard SLA specified by spec; SC-001/SC-002 give user-facing time budgets, not network-level targets)

**Constraints**: No new ticket business logic in the UI (FR-011); all error/empty/not-found states must show backend-specific messages, not generic ones (FR-012, FR-013); environment-driven API base URL, no hardcoded host, no secrets committed (deployment requirement); single `docker compose up` must still bring up the whole system (constitution Principle II)

**Scale/Scope**: 6 user stories, ~6 screens/flows (list, detail, create, edit, comment, transition), single internal-agent user type, no auth system introduced

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Check | Status |
|---|---|---|
| I. Tech Stack Discipline | Frontend MUST be React or Next.js | **PASS** — Next.js 14 (React 18) chosen, matches user's "React or Next.js" instruction and constitution verbatim |
| I. Tech Stack Discipline | All external interfaces MUST be REST | **PASS** — UI only talks to Feature 1's existing REST API plus one new REST endpoint (`GET /api/v1/assignees`), both under `/api/v1` |
| II. Single-Command Deployment | Whole system starts with one `docker compose up`, each service has its own Dockerfile/compose entry, no manual setup beyond `.env` | **PASS (by design)** — `frontend/Dockerfile` (multi-stage) + a new `frontend` service appended to the existing root `docker-compose.yml`, reachable at the backend's compose service name (`app`), not `localhost` |
| III. Test-First & State Machine Coverage | Services/validation logic have unit tests; state machine has exhaustive integration tests | **N/A for this feature's UI code** (no service/validation logic or state machine lives in the frontend — FR-011 forbids it); test-strategy.md instead defines component tests for the UI's own responsibilities (rendering, form validation *display*, error mapping) plus one E2E flow. The new `GET /api/v1/assignees` backend endpoint, when implemented, MUST get the same unit-test treatment as any other Feature 1 endpoint — flagged as a task-level obligation, not waived |
| IV. API Consistency & Boundary Validation | REST naming conventions; shared structured error shape; boundary validation before business logic | **PASS** — new endpoint follows `/api/v1/assignees` (plural noun); UI maps the existing shared `ErrorResponse` shape 1:1 to on-screen messages (architecture.md); UI performs no boundary validation as a "primary defense" — all validation is display-only, backend remains authoritative (FR-011) |
| V. RAG Grounding & Guardrails | N/A | **N/A** — no RAG/AI functionality in this feature (explicitly out of scope, FR-016) |
| Security & Configuration | No secrets committed; `.env`-driven credentials; `.env.example` kept current | **PASS (by design)** — API base URL is environment-driven (`NEXT_PUBLIC_API_BASE_URL` or equivalent build/runtime env var), no host hardcoded, `.env.example` updated with the new variable |

No violations requiring Complexity Tracking justification.

**Post-Phase-1 re-check**: Design artifacts (research.md, data-model.md, contracts/, ui-flow.md, architecture.md, test-strategy.md, quickstart.md) introduce no new dependencies, storage, or services beyond what's listed above. Table still holds unchanged.

## Project Structure

### Documentation (this feature)

```text
specs/002-ticket-management-ui/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── ui-flow.md            # Phase 1 output (screens/flows)
├── architecture.md       # Phase 1 output (component structure, state, error mapping, AI-panel extensibility)
├── test-strategy.md      # Phase 1 output (component + E2E test approach)
├── quickstart.md         # Phase 1 output (run/validate steps)
├── contracts/            # Phase 1 output (frontend's consumed + newly-added API contracts)
└── tasks.md              # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
# Existing backend (Feature 1, untouched except the one new read-only endpoint)
src/
├── main/java/com/ticketmanagement/...
└── test/java/com/ticketmanagement/...

pom.xml
Dockerfile
docker-compose.yml        # extended with a `frontend` service in this feature

# New: frontend (this feature)
frontend/
├── Dockerfile             # multi-stage: node build → lightweight static/proxy server
├── package.json
├── next.config.ts
├── src/
│   ├── app/                       # Next.js App Router: routes/screens
│   │   ├── layout.tsx             # shared navigation shell (FR-015)
│   │   ├── tickets/
│   │   │   ├── page.tsx           # list view (filter/search/infinite scroll)
│   │   │   ├── new/page.tsx       # create form
│   │   │   └── [id]/page.tsx      # detail view (fields, comments, edit, transitions)
│   ├── components/
│   │   ├── layout/                # nav shell, page shell (reserves future AI-panel slot)
│   │   ├── tickets/                # ticket list row, ticket form, status badge, transition control
│   │   ├── comments/               # comment list, comment form
│   │   └── errors/                 # shared error-display component (FR-012, FR-013)
│   ├── lib/
│   │   ├── api/                   # typed HTTP client, one module per Feature 1 resource + assignees
│   │   └── errors/                 # ErrorResponse → UI-message mapping (architecture.md)
│   └── types/                      # TypeScript types mirroring api-contract.md shapes
└── tests/
    ├── components/                 # Vitest + RTL component tests
    └── e2e/                        # Playwright: create → edit → transition
```

**Structure Decision**: Web application layout (Option 2 pattern) — existing backend stays at repo root as-is; a new top-level `frontend/` directory holds the entire Next.js app, kept fully independent of backend source so the backend's Java/Maven build is untouched. `docker-compose.yml` at repo root is extended (not replaced) with a `frontend` service alongside `mysql` and `app`.

## Complexity Tracking

*No Constitution Check violations — table not applicable.*
