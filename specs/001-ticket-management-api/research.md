# Phase 0 Research: Ticket Management REST API

All Technical Context items were specified directly by the plan input (Java 21, Spring Boot, MySQL/H2, Docker) — no `NEEDS CLARIFICATION` markers remain. This document records the supporting decisions made while turning that stack choice into a concrete design.

## Decision: Schema management via Flyway

- **Decision**: Use Flyway migrations under `src/main/resources/db/migration`, run automatically on startup, shared by both the H2 and MySQL profiles.
- **Rationale**: Constitution Principle II requires zero manual setup steps. Flyway lets `docker compose up` (MySQL) and local `./mvnw test` (H2) both build the same schema from the same SQL, so dev/test/prod never drift.
- **Alternatives considered**: Hibernate `ddl-auto: update` — rejected because it is non-deterministic across dialects (H2 vs MySQL) and gives no reviewable schema history; plain manual SQL setup — rejected because it violates the single-command-deployment requirement.

## Decision: State machine enforcement location

- **Decision**: Encode the transition table as a static, explicit map (`Map<TicketStatus, Set<TicketStatus>>` or an equivalent enum-driven switch) inside `TicketServiceImpl`, checked before any status write.
- **Rationale**: Constitution Principle IV requires business rules to live in the service layer, not the controller or entity. An explicit table (rather than scattered `if` statements) makes every valid/invalid pair visible in one place and trivially testable via the required full transition matrix (Principle III).
- **Alternatives considered**: A full State pattern (one class per status) — rejected as over-engineering for 5 valid edges; validation annotations on the entity — rejected because transition legality depends on the *current* persisted state, which Bean Validation cannot see.

## Decision: Partial update semantics

- **Decision**: Ticket field updates use `PATCH /api/v1/tickets/{id}` with a request DTO whose fields are all optional (`Optional<T>` or nullable wrapper types); only present fields are applied.
- **Rationale**: Confirmed via `/speckit-clarify` — clients must be able to change one field without resending the whole ticket. `PATCH` is the correct HTTP verb per `rules/api-standards.md`.
- **Alternatives considered**: `PUT` full-replace — rejected per clarification; `POST` action-style update — rejected, not idiomatic REST for field mutation.

## Decision: Status transitions as a sub-resource action

- **Decision**: Expose status changes via `POST /api/v1/tickets/{id}/transitions` with a body naming the target status, distinct from the general `PATCH` field update.
- **Rationale**: `rules/api-standards.md` explicitly calls out `POST /api/v1/tickets/{id}/transitions` as the sanctioned pattern for non-CRUD actions, and keeps state-machine validation logically separate from plain field edits (different failure mode: `409 INVALID_TRANSITION` vs `400 VALIDATION_FAILED`).
- **Alternatives considered**: Folding `status` into the general `PATCH` body — rejected because it conflates two different validation paths (field validation vs. transition-graph validation) and would force every `PATCH` handler to special-case one field.

## Decision: Search and filter as query parameters on the list endpoint

- **Decision**: `GET /api/v1/tickets?q={keyword}&status={STATUS}&page=&size=&sort=` — one collection endpoint, filters combine via query params.
- **Rationale**: Matches `rules/api-standards.md` filtering conventions directly; keeps the resource path stable regardless of which filters are applied.
- **Alternatives considered**: Separate `/search` and `/filter` endpoints — rejected, violates the "filtering MUST NOT change path structure" rule and duplicates pagination logic.

## Decision: `category` field on Ticket for future RAG use

- **Decision**: Add a nullable `category` string column to Ticket now, exposed in the API DTOs as optional, with no validation constraint and no UI/API requirement to set it in this feature.
- **Rationale**: Explicit plan input requests it so Feature 3 (RAG assistant) has stable metadata to retrieve/filter on without a later migration. It is additive and optional, so it does not expand this feature's functional scope or violate spec boundaries.
- **Alternatives considered**: Omitting it and adding it in Feature 3 — rejected per explicit plan instruction, since retrofitting a column onto an existing table is exactly the kind of avoidable migration this decision heads off.

## Decision: Docker packaging

- **Decision**: Multi-stage `Dockerfile` — stage 1 `maven:3.9-eclipse-temurin-21` (or equivalent) builds the jar; stage 2 copies only the built jar into a minimal `eclipse-temurin:21-jre` runtime image.
- **Rationale**: Keeps the shipped image small and avoids bundling build tooling in production, standard Spring Boot practice.
- **Alternatives considered**: Single-stage build — rejected, ships an unnecessarily large image with the full JDK + Maven cache.
