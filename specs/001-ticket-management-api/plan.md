# Implementation Plan: Ticket Management REST API

**Branch**: `001-ticket-management-api` | **Date**: 2026-09-25 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-ticket-management-api/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

Backend-only REST API for a support ticket system: create/list/view/update tickets, add comments, search by keyword, filter by status, and enforce a fixed status lifecycle (OPEN → IN_PROGRESS → RESOLVED → CLOSED, with OPEN/IN_PROGRESS → CANCELLED; all other transitions rejected). Built as a Java 21 + Spring Boot service, persisting to MySQL in production and H2 for local/dev/test, following the project's layered Controller → Service → Repository architecture with an explicit, service-layer-enforced transition table for the state machine. Ships with a multi-stage Dockerfile and a docker-compose stack (app + MySQL) configured entirely via environment variables, runnable with a single `docker compose up`.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot (Web, Data JPA, Validation), Spring Boot Actuator (health checks for compose), Flyway (schema versioning so MySQL and H2 start from the same migrations), Lombok (DTOs only, per project convention)

**Storage**: MySQL (prod, via `docker-compose.yml`), H2 file-based (local dev / test), selected via Spring profiles (`prod`/`docker`, `dev`, `test`)

**Testing**: JUnit 5 + Spring Boot Test (`@DataJpaTest`, `@SpringBootTest` slices), Mockito for service-layer unit tests, H2 for integration tests

**Target Platform**: Linux container (Docker), JVM 21 runtime

**Project Type**: Single backend web service (REST API only — no frontend in this feature)

**Performance Goals**: No explicit throughput target specified by the feature; standard synchronous REST request handling is sufficient (not a stated NFR — out of scope for this feature per spec Assumptions)

**Constraints**: No authentication/authorization (explicit scope exclusion); no multi-tenancy; must survive application restart with no data loss (FR-008); must run via a single `docker compose up` with no manual setup step (constitution Principle II)

**Scale/Scope**: 5 REST resources/behaviors (tickets CRUD-ish, comments, search, filter, transitions) over 2 entities (Ticket, Comment); scope is a single bounded-context backend module, no microservices split

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Check | Status |
|---|---|---|
| I. Tech Stack Discipline | Java 21 + Spring Boot; MySQL (prod) / H2 (dev/test); no other DB engine introduced; no vector store needed by this feature (RAG is Feature 3) | PASS |
| II. Single-Command Deployment | `docker compose up` starts app + MySQL; no manual migration/seed step — Flyway migrations run automatically on app startup; config via `.env` (git-ignored) with `.env.example` checked in | PASS |
| III. Test-First & State Machine Coverage | Unit tests planned for services/validation; integration tests planned covering all 5 valid transitions and every invalid transition (including all "back to OPEN" attempts); no RAG involved in this feature so the RAG-evaluation carve-out does not apply | PASS |
| IV. API Consistency & Boundary Validation | Endpoints use plural nouns, versioned under `/api/v1`; one shared structured error shape for all failures; validation happens at controller/DTO boundary via Bean Validation before reaching services | PASS |
| V. RAG Grounding & Guardrails | Not applicable — this feature does not implement retrieval or the assistant. `category` field is added to the Ticket entity now (nullable, unvalidated) solely so Feature 3 has stable metadata to filter/retrieve on later, per explicit plan input; no RAG logic lives here | PASS (N/A, forward-compatible field only) |
| Security & Configuration | No secrets committed; DB credentials via environment variables sourced from a git-ignored `.env`; `.env.example` lists required variable names | PASS |

No violations. Complexity Tracking section not needed.

## Project Structure

### Documentation (this feature)

```text
specs/001-ticket-management-api/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output — Ticket & Comment entities
├── api-contract.md      # Phase 1 output — full REST contract
├── architecture.md      # Phase 1 output — module/package layout & request flow
├── state-machine.md     # Phase 1 output — formal transition table
├── test-strategy.md     # Phase 1 output — unit + integration test plan
├── quickstart.md        # Phase 1 output — runnable validation guide
├── contracts/           # Phase 1 output — machine-readable contract index
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
Dockerfile                     # multi-stage build: Maven build stage → slim JRE runtime stage
docker-compose.yml              # app + mysql, env-driven, no committed secrets
.env.example                    # documents required env vars (no values committed)

src/
├── main/
│   ├── java/com/ticketmanagement/
│   │   ├── TicketManagementApplication.java
│   │   ├── ticket/
│   │   │   ├── controller/     # TicketController, CommentController
│   │   │   ├── service/        # TicketService/TicketServiceImpl (owns state machine)
│   │   │   ├── repository/     # TicketRepository, CommentRepository (Spring Data JPA)
│   │   │   ├── entity/         # Ticket, Comment, Priority, TicketStatus
│   │   │   ├── dto/            # *Request / *Response records
│   │   │   └── exception/      # TicketNotFoundException, InvalidTransitionException
│   │   └── common/
│   │       ├── config/         # @ConfigurationProperties (pagination defaults, etc.)
│   │       ├── exception/      # GlobalExceptionHandler, ErrorCode, ErrorResponse
│   │       └── dto/             # shared PageResponse<T> wrapper
│   └── resources/
│       ├── application.yml               # common config
│       ├── application-dev.yml           # H2, local
│       ├── application-test.yml          # H2, tests
│       ├── application-docker.yml        # MySQL, compose
│       └── db/migration/                 # Flyway SQL migrations (shared across profiles)
└── test/
    └── java/com/ticketmanagement/ticket/
        ├── service/            # unit tests (mocked repository)
        ├── validation/         # DTO/Bean Validation unit tests
        └── integration/        # @SpringBootTest + H2: full transition matrix, CRUD, search, filter
```

**Structure Decision**: Single Spring Boot module (Option 1 style), package-by-domain under `com.ticketmanagement.ticket` plus a `com.ticketmanagement.common` package for cross-cutting error handling and shared DTOs, per `rules/java-springboot.md`. No frontend directory — this feature is backend-only.

## Complexity Tracking

*No Constitution Check violations — this section is intentionally empty.*
