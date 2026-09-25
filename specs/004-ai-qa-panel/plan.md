# Implementation Plan: AI Q&A Panel

**Branch**: `004-ai-qa-panel` | **Date**: 2026-09-25 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/004-ai-qa-panel/spec.md`

## Summary

A new `/assistant` screen that lets a support agent ask a free-text question against Feature 3's existing `POST /api/ai/ask` endpoint and see the grounded answer with clickable, new-tab ticket citations, or an honest "no relevant tickets found" state — reusing Feature 2's navigation shell, BFF relay pattern, and shared error-display component exactly as those three seams were built to allow (Feature 2 architecture.md §"How the layout leaves room for the future AI Q&A panel"). No backend change; the only new server-side file is a thin Next.js Route Handler proxy (frontend-only plumbing, mirroring the existing `app/api/tickets/route.ts` pattern), plus new UI components and types.

## Technical Context

**Language/Version**: TypeScript 5.x, Node.js 20 LTS — unchanged, same container as Feature 2

**Primary Dependencies**: Next.js 14 (App Router), React 18, the existing fetch-based `lib/api/client.ts` + `lib/api/relay.ts` pair; the ask call uses a manual `useState` + async handler, matching every other write path in this codebase (corrected during implementation — see research.md §2) — no new dependency added

**Storage**: N/A — this feature owns no persisted data; each question is answered independently (stateless per `POST /api/ai/ask`), same as Feature 2's UI holds no storage of its own

**Testing**: Vitest + React Testing Library (component tests: grounded-answer / no-match / error renders); Playwright (one new E2E flow: create ticket → ask → cited → click through)

**Target Platform**: Same evergreen desktop browsers, same Docker container/image as Feature 2 — no new service

**Project Type**: Web application (frontend-only addition) — extends the existing `frontend/` tree; no backend files touched

**Performance Goals**: No new SLA beyond spec.md's user-facing ones (SC-001, SC-004); the backend's own response-time behavior is Feature 3's concern (`rag-api-contract.md`), not renegotiated here

**Constraints**: No new backend endpoints or services (FR-012, spec.md Out of scope); MUST reuse Feature 2's `NavShell`/`ErrorDisplay`/BFF-relay pattern as-is; no `docker-compose.yml` changes

**Scale/Scope**: 1 new screen/route, 4 new components, 1 new `lib/api` module, 1 new Route Handler, a 2-member extension to the existing `ApiErrorCode` union, 1 new nav link

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Check | Status |
|---|---|---|
| I. Tech Stack Discipline | Frontend MUST be React or Next.js; no new stack/service introduced | **PASS** — extends the existing Next.js 14/React 18 frontend; no new backend, no new dependency |
| II. Single-Command Deployment | Whole system starts with one `docker compose up`, no manual setup | **PASS** — no `docker-compose.yml` change; reuses the existing `frontend` service/image |
| III. Test-First & State Machine Coverage | Services/validation logic have unit tests; RAG output not pass/fail-tested | **N/A for this feature's own code** (no service/validation/state-machine logic lives in the frontend — same reasoning as Feature 2's plan.md); test-strategy.md's component/E2E tests assert only on *rendering* of already-generated backend fields, never on `answer` text content or citation accuracy (`rules/testing.md`) |
| IV. API Consistency & Boundary Validation | REST naming; shared error shape; boundary validation before business logic | **PASS** — no new backend endpoint (FR-012); the new frontend Route Handler mirrors `POST /api/ai/ask` 1:1 with no re-validation of its own, matching `app/api/tickets/route.ts`'s existing pattern |
| V. RAG Grounding & Guardrails | Chunking/embedding/retrieval-tuning config; citation + no-match guardrails | **PASS (by non-involvement)** — this feature touches none of retrieval, embedding, or chunking; it only renders what Feature 3's already-guardrailed endpoint returns, and never fabricates an answer or bypasses the "no relevant tickets found" contract (FR-006) |
| Security & Configuration | No secrets committed; env-driven credentials | **PASS** — the new Route Handler reuses the existing `API_BASE_URL` env var; nothing new added to `.env.example` |
| Spec-Driven Change Workflow | Traceable spec → plan → tasks | **PASS** — this plan follows `/speckit-specify` → `/speckit-clarify` → `/speckit-plan` |

No violations requiring Complexity Tracking justification.

**Post-Phase-1 re-check**: Design artifacts (research.md, data-model.md, contracts/, ui-flow.md, architecture.md, test-strategy.md, quickstart.md) introduce no new dependency, storage, or service beyond what's listed above. Table still holds unchanged.

## Project Structure

### Documentation (this feature)

```text
specs/004-ai-qa-panel/
├── plan.md               # This file (/speckit-plan command output)
├── research.md           # Phase 0 output (/speckit-plan command)
├── data-model.md         # Phase 1 output (/speckit-plan command)
├── ui-flow.md            # Phase 1 output (screen/flow, extends Feature 2's ui-flow.md)
├── architecture.md       # Phase 1 output (component structure, error mapping, extends Feature 2's architecture.md)
├── test-strategy.md      # Phase 1 output (component + E2E test approach, extends Feature 2's test-strategy.md)
├── quickstart.md         # Phase 1 output (run/validate steps)
├── contracts/            # Phase 1 output (consumed-api.md — Feature 3's rag-api-contract.md, no new backend contract)
└── tasks.md              # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
# Existing backend (Feature 1/3, untouched by this feature)
src/
└── main/java/com/ticketmanagement/...

pom.xml
Dockerfile
docker-compose.yml        # unchanged — no new service

# Existing frontend (Feature 2), extended by this feature
frontend/
├── Dockerfile             # unchanged
├── package.json           # unchanged — no new dependency
├── src/
│   ├── app/
│   │   ├── layout.tsx             # unchanged
│   │   ├── tickets/...            # unchanged (list/create/detail — Feature 2)
│   │   ├── assistant/
│   │   │   └── page.tsx           # NEW — Screen 4: /assistant
│   │   └── api/
│   │       ├── tickets/...        # unchanged
│   │       └── ai/ask/route.ts    # NEW — Route Handler relaying POST /api/ai/ask
│   ├── components/
│   │   ├── layout/
│   │   │   └── NavShell.tsx       # MODIFIED — fills the reserved AI-panel nav slot
│   │   ├── tickets/, comments/    # unchanged
│   │   ├── errors/
│   │   │   └── ErrorDisplay.tsx   # unchanged — reused as-is
│   │   └── assistant/             # NEW
│   │       ├── AiQaPanel.tsx
│   │       ├── AiAnswer.tsx
│   │       ├── CitationList.tsx
│   │       └── NoRelevantTicketsState.tsx
│   ├── lib/
│   │   ├── api/
│   │   │   ├── client.ts, relay.ts, browserFetch.ts   # unchanged — reused as-is
│   │   │   ├── tickets.ts, comments.ts, assignees.ts  # unchanged
│   │   │   └── assistant.ts       # NEW — askAssistant(question)
│   │   └── errors/
│   │       └── mapApiError.ts     # MODIFIED — + AI_GENERATION_FAILED, AI_RETRIEVAL_UNAVAILABLE cases
│   └── types/
│       ├── ticket.ts, comment.ts, page.ts, assignee.ts  # unchanged
│       ├── apiError.ts            # MODIFIED — BackendApiErrorCode + 2 members
│       └── assistant.ts           # NEW — AskRequest, AskAnswer
└── tests/
    ├── components/
    │   └── AiQaPanel.test.tsx, CitationList.test.tsx, NoRelevantTicketsState.test.tsx   # NEW
    └── e2e/
        ├── create-edit-transition.spec.ts   # unchanged (Feature 2)
        └── ask-and-cite.spec.ts             # NEW
```

**Structure Decision**: Pure extension of Feature 2's existing web-application layout — no new top-level directory, no new backend, no new Docker service. Every new file lands inside `frontend/src/` following the exact module shape (`app/<route>/page.tsx`, `components/<domain>/`, `lib/api/<domain>.ts`, `types/<domain>.ts`) Feature 2 already established, and the three files marked MODIFIED are additive (a new nav link, two new switch cases, two new union members) — no existing behavior for Feature 2's screens changes.

## Complexity Tracking

*No Constitution Check violations — table not applicable.*
