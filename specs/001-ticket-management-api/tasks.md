---
description: "Task list template for feature implementation"
---

# Tasks: Ticket Management REST API

**Input**: Design documents from `/specs/001-ticket-management-api/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, api-contract.md, architecture.md, state-machine.md, test-strategy.md, contracts/, quickstart.md

**Tests**: Included — constitution Principle III mandates unit tests for services/validation and integration tests covering every valid AND invalid state transition; this is not optional for this feature.

**Organization**: Tasks are grouped by user story (from spec.md) to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3, US4, US5)
- File paths are exact, per architecture.md's package layout (`com.ticketmanagement.ticket.*`, `com.ticketmanagement.common.*`)

## Path Conventions

Single Spring Boot module at repository root:
- `src/main/java/com/ticketmanagement/...`
- `src/main/resources/...`
- `src/test/java/com/ticketmanagement/...`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization — Maven project, Spring profiles for H2/MySQL, deployment scaffolding

- [X] T001 Create `pom.xml` at repository root: Spring Boot 3.x parent, Java 21, dependencies `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `spring-boot-starter-actuator`, `com.h2database:h2` (test/dev scope), `com.mysql:mysql-connector-j` (runtime), `org.flywaydb:flyway-core` + `flyway-mysql`, `org.projectlombok:lombok`, `spring-boot-starter-test`
- [X] T002 Create `src/main/java/com/ticketmanagement/TicketManagementApplication.java` — `@SpringBootApplication` main class
- [X] T003 [P] Create `src/main/resources/application.yml` — common config (server port 8080, Flyway enabled, JPA `ddl-auto: validate` so schema is Flyway-owned per research.md)
- [X] T004 [P] Create `src/main/resources/application-dev.yml` — H2 file-based datasource for local dev (per plan.md Technical Context)
- [X] T005 [P] Create `src/main/resources/application-test.yml` — H2 in-memory datasource for the default test profile
- [X] T006 [P] Create `src/main/resources/application-docker.yml` — MySQL datasource reading `SPRING_DATASOURCE_URL`/`USERNAME`/`PASSWORD` from environment (matches `docker-compose.yml`'s `app` service env vars)
- [X] T007 Verify `Dockerfile` (repository root, already scaffolded in plan phase) builds successfully once `pom.xml`/`src` exist: multi-stage Maven build → `eclipse-temurin:21-jre-alpine` runtime
- [X] T008 Verify `docker-compose.yml` (repository root, already scaffolded) and `.env.example` correctly wire `app` + `mysql` services with no committed secrets

**Checkpoint**: `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` starts an empty Spring Boot app against H2.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Entities, repositories, migrations, and the shared error/pagination infrastructure every user story depends on

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T009 [P] Create `Priority` enum in `src/main/java/com/ticketmanagement/ticket/entity/Priority.java` — exactly `LOW`, `MEDIUM`, `HIGH` (data-model.md: "One of LOW, MEDIUM, HIGH")
- [X] T010 [P] Create `TicketStatus` enum in `src/main/java/com/ticketmanagement/ticket/entity/TicketStatus.java` — exactly `OPEN`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`, `CANCELLED`
- [X] T011 Create `Ticket` entity in `src/main/java/com/ticketmanagement/ticket/entity/Ticket.java`: `id` (UUID, generated PK), `title` (String, not blank per data-model.md, no max length per spec Assumptions), `description` (String, not blank, no max length), `priority` (`Priority`, not null), `assignee` (String, not blank), `status` (`TicketStatus`, not null, defaults `OPEN`), `category` (String, nullable, unvalidated — forward-compatible RAG metadata field per data-model.md), `createdAt`/`updatedAt` (Instant, set via `@PrePersist`/`@PreUpdate`)
- [X] T012 Create `Comment` entity in `src/main/java/com/ticketmanagement/ticket/entity/Comment.java`: `id` (UUID, generated PK), `ticketId` (UUID FK to Ticket, not null), `content` (String, not blank per data-model.md), `createdAt` (Instant, set via `@PrePersist`)
- [X] T013 [P] Create `TicketRepository` in `src/main/java/com/ticketmanagement/ticket/repository/TicketRepository.java` — `extends JpaRepository<Ticket, UUID>, JpaSpecificationExecutor<Ticket>` (Specification executor backs combined search+filter, per architecture.md)
- [X] T014 [P] Create `CommentRepository` in `src/main/java/com/ticketmanagement/ticket/repository/CommentRepository.java` — `extends JpaRepository<Comment, UUID>` with a `findByTicketId(UUID ticketId)` query method
- [X] T015 Create Flyway migration `src/main/resources/db/migration/V1__create_tickets_and_comments.sql` — `tickets` table (id VARCHAR(36) PK, title TEXT NOT NULL, description TEXT NOT NULL, priority VARCHAR NOT NULL, assignee VARCHAR NOT NULL, status VARCHAR NOT NULL, category VARCHAR NULL, created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL) and `comments` table (id VARCHAR(36) PK, ticket_id VARCHAR(36) NOT NULL REFERENCES tickets(id), content TEXT NOT NULL, created_at TIMESTAMP NOT NULL), syntax compatible with both H2 and MySQL. Deviation: id columns use VARCHAR(36) not native UUID — MySQL has no UUID column type; entities force this via Hibernate `@JdbcTypeCode(SqlTypes.VARCHAR)` so H2/MySQL share one schema.
- [X] T016 [P] Create `ErrorCode` enum in `src/main/java/com/ticketmanagement/common/exception/ErrorCode.java` — `VALIDATION_FAILED`, `TICKET_NOT_FOUND`, `INVALID_TRANSITION`, `UNKNOWN_FILTER` (api-contract.md error codes)
- [X] T017 [P] Create `ErrorResponse` record in `src/main/java/com/ticketmanagement/common/exception/ErrorResponse.java` — fields `timestamp`, `status`, `error`, `code`, `message`, `path`, `details` (list of `{field, rejectedValue, message}`), matching api-contract.md's ErrorResponse shape exactly
- [X] T018 [P] Create `TicketNotFoundException` in `src/main/java/com/ticketmanagement/ticket/exception/TicketNotFoundException.java`
- [X] T019 [P] Create `InvalidTransitionException` in `src/main/java/com/ticketmanagement/ticket/exception/InvalidTransitionException.java` — carries current status and rejected target status for the error message
- [X] T020 Create `GlobalExceptionHandler` (`@RestControllerAdvice`) in `src/main/java/com/ticketmanagement/common/exception/GlobalExceptionHandler.java` — maps `MethodArgumentNotValidException` → 400 `VALIDATION_FAILED` (populating `details[]` per field), `TicketNotFoundException` → 404 `TICKET_NOT_FOUND`, `InvalidTransitionException` → 409 `INVALID_TRANSITION`, unrecognized `status` query value / malformed enum → 400 `UNKNOWN_FILTER` or `VALIDATION_FAILED` as appropriate (depends on T016, T017, T018, T019). Also added `UnknownFilterException` (not separately enumerated in the original task list) and a `MethodArgumentTypeMismatchException` → 400 mapping to close analyze-report finding E1 (malformed path-id handling).
- [X] T021 [P] Create generic `PageResponse<T>` record in `src/main/java/com/ticketmanagement/common/dto/PageResponse.java` — fields `content`, `page`, `size`, `totalElements`, `totalPages` (api-contract.md PageResponse shape)
- [X] T022 [P] Create `PaginationProperties` in `src/main/java/com/ticketmanagement/common/config/PaginationProperties.java` — `@ConfigurationProperties(prefix = "app.pagination")` with `defaultSize` (20) and `maxSize` (100), bound from `application.yml`, never hardcoded (constitution Principle IV)
- [X] T023 [P] `GlobalExceptionHandlerTest` in `src/test/java/com/ticketmanagement/common/exception/GlobalExceptionHandlerTest.java` — asserts each exception type maps to the correct status/code per T020

**Checkpoint**: Entities persist via Flyway-managed schema on both H2 and MySQL profiles; shared error/pagination infrastructure compiles and is unit-tested. User story work can now begin.

---

## Phase 3: User Story 1 - Create and track a support ticket (Priority: P1) 🎯 MVP

**Goal**: A client can create a ticket (title, description, priority, assignee) and retrieve it by ID, seeing status default to `OPEN`.

**Independent Test**: `POST /api/v1/tickets` with valid data, then `GET /api/v1/tickets/{id}` — response matches submitted fields, `status=OPEN`, has a generated `id` and timestamps (spec SC-001).

### Tests for User Story 1

- [X] T024 [P] [US1] Unit test in `src/test/java/com/ticketmanagement/ticket/service/TicketServiceImplTest.java` — `create()` sets `status=OPEN`, generates `id`/timestamps, delegates to `TicketRepository.save` (repository mocked)
- [X] T025 [P] [US1] Integration test in `src/test/java/com/ticketmanagement/ticket/integration/TicketCrudIntegrationTest.java` — create → get-by-id round trip returns matching fields (spec SC-001, FR-001, FR-003)
- [X] T026 [P] [US1] Integration test case (same file as T025) — create with blank `title` → `400 VALIDATION_FAILED`, `details[0].field == "title"` (spec Acceptance Scenario 3, FR-010)
- [X] T027 [P] [US1] Integration test case (same file as T025) — `GET /api/v1/tickets/{id}` with a syntactically invalid ID → `400 VALIDATION_FAILED`; with a well-formed but non-existent ID → `404 TICKET_NOT_FOUND` (spec FR-016)

### Implementation for User Story 1

- [X] T028 [US1] Create `TicketCreateRequest` DTO (record) in `src/main/java/com/ticketmanagement/ticket/dto/TicketCreateRequest.java` — `title`/`description`/`assignee` `@NotBlank`, `priority` `@NotNull` (enum `Priority`), `category` optional/nullable (data-model.md validation rules)
- [X] T029 [US1] Create `TicketResponse` DTO (record) in `src/main/java/com/ticketmanagement/ticket/dto/TicketResponse.java` — matches api-contract.md `TicketResponse` shape including `comments` (populated only on detail view, per api-contract.md note)
- [X] T030 [US1] Create `TicketService` interface in `src/main/java/com/ticketmanagement/ticket/service/TicketService.java` — `create(TicketCreateRequest)`, `getById(UUID)`
- [X] T031 [US1] Implement `TicketServiceImpl.create()` and `getById()` in `src/main/java/com/ticketmanagement/ticket/service/TicketServiceImpl.java` — `create()` sets `status=OPEN`; `getById()` throws `TicketNotFoundException` if absent (depends on T011, T013, T018, T028, T029)
- [X] T032 [US1] Create `TicketController` in `src/main/java/com/ticketmanagement/ticket/controller/TicketController.java` — `POST /api/v1/tickets` (`@Valid` body, returns 201 + `Location` header) and `GET /api/v1/tickets/{id}` (returns 200 or delegates 404 to `GlobalExceptionHandler`); reject a syntactically invalid `{id}` path variable with 400 before hitting the service (FR-016)

**Checkpoint**: User Story 1 is independently functional — a ticket can be created and retrieved via the API.

---

## Phase 4: User Story 2 - List, search, and filter tickets (Priority: P1)

**Goal**: A client can list all tickets (paginated), search by keyword, and filter by status, combinable via query params on one collection endpoint.

**Independent Test**: Create several tickets with varying statuses/text; confirm list returns all (paginated), `q=` returns only keyword matches, `status=` returns only that status (spec SC-006, SC-007).

### Tests for User Story 2

- [X] T033 [P] [US2] Integration test in `src/test/java/com/ticketmanagement/ticket/integration/TicketSearchAndFilterIntegrationTest.java` — `GET /api/v1/tickets` with no params returns all tickets in a paginated envelope (`content`, `page`, `size`, `totalElements`, `totalPages`)
- [X] T034 [P] [US2] Integration test case (same file) — `q=<keyword>` returns only tickets whose title OR description contains the keyword (case-insensitive), zero unrelated tickets (spec SC-006, FR-006)
- [X] T035 [P] [US2] Integration test case (same file) — `status=<STATUS>` returns only tickets in that status, zero others (spec SC-007, FR-007)
- [X] T036 [P] [US2] Integration test case (same file) — `status=NOT_A_STATUS` → `400 UNKNOWN_FILTER` (spec FR-013); `q=` (present but empty) behaves as no keyword filter (spec FR-018); `page=-1` or non-numeric `size` → `400 VALIDATION_FAILED` (spec FR-017); a keyword matching nothing returns `200` with empty `content[]`, not an error (spec Edge Cases)

### Implementation for User Story 2

- [X] T037 [US2] Add `search(String q, TicketStatus status, Pageable pageable)` to `TicketService`/`TicketServiceImpl` — build a `Specification<Ticket>` combining case-insensitive `title`/`description` containment (when `q` present and non-empty) and exact `status` match (when present), backed by `TicketRepository` (`JpaSpecificationExecutor`) and `PaginationProperties` defaults/max (depends on T013, T021, T022, T030, T031)
- [X] T038 [US2] Add query-param binding + validation in `TicketController` for `GET /api/v1/tickets`: `q` (optional string), `status` (optional `TicketStatus`, invalid value → `UNKNOWN_FILTER` via `GlobalExceptionHandler`), `page`/`size` (`@Min(0)`/`@Min(1)` or explicit checks, per FR-017), `sort` — returns `PageResponse<TicketResponse>` with `comments` omitted on list items (api-contract.md)

**Checkpoint**: User Stories 1 AND 2 both work independently — full create/read/list/search/filter flow is usable end-to-end.

---

## Phase 5: User Story 4 - Enforce the ticket status lifecycle (Priority: P1)

**Goal**: Status changes only succeed along the defined graph (OPEN→IN_PROGRESS→RESOLVED→CLOSED, OPEN/IN_PROGRESS→CANCELLED); every other transition is rejected with no status change.

**Independent Test**: Drive a ticket through all 5 valid transitions in sequence (all succeed); attempt all 20 invalid transitions from state-machine.md (all rejected, status unchanged each time).

### Tests for User Story 4

- [X] T039 [P] [US4] Parameterized unit test in `TicketServiceImplTest.java` — `transition()` for each of the 5 valid pairs in state-machine.md updates status and calls `repository.save`
- [X] T040 [P] [US4] Parameterized unit test in `TicketServiceImplTest.java` — `transition()` for each of the 20 rejected pairs in state-machine.md (including same-status transitions per FR-014) throws `InvalidTransitionException` and never calls `repository.save`
- [X] T041 [P] [US4] Parameterized integration test in `src/test/java/com/ticketmanagement/ticket/integration/TicketLifecycleIntegrationTest.java` — `appliesValidTransition(from, target)` sourced from state-machine.md's valid-transitions table: `POST /tickets/{id}/transitions` → `200`, persisted status == target
- [X] T042 [P] [US4] Parameterized integration test (same file) — `rejectsInvalidTransition(from, target)` sourced from state-machine.md's full rejected-transitions table (all 20 rows): `POST /tickets/{id}/transitions` → `409 INVALID_TRANSITION`, persisted status unchanged (still == from) (spec SC-002, SC-003)
- [X] T043 [P] [US4] Integration test case — transition request for a non-existent ticket id → `404 TICKET_NOT_FOUND`; missing/invalid `targetStatus` value → `400 VALIDATION_FAILED`

### Implementation for User Story 4

- [X] T044 [US4] Add the static transition table to `TicketServiceImpl` in `src/main/java/com/ticketmanagement/ticket/service/TicketServiceImpl.java` — `EnumMap<TicketStatus, Set<TicketStatus>>` exactly matching state-machine.md's valid-transitions table: `OPEN→{IN_PROGRESS,CANCELLED}`, `IN_PROGRESS→{RESOLVED,CANCELLED}`, `RESOLVED→{CLOSED}`, `CLOSED→{}`, `CANCELLED→{}`
- [X] T045 [US4] Implement `TicketServiceImpl.transition(UUID id, TicketStatus targetStatus)` — loads ticket (`TicketNotFoundException` if absent), checks `targetStatus` is in the allowed set for the current status (throwing `InvalidTransitionException` with current+target status in the message if not, with **no write**), else updates `status` and `updatedAt` and saves (depends on T011, T019, T044)
- [X] T046 [US4] Create `TicketTransitionRequest` DTO (record) in `src/main/java/com/ticketmanagement/ticket/dto/TicketTransitionRequest.java` — `targetStatus` field, `@NotNull`, deserialized as `TicketStatus` enum
- [X] T047 [US4] Add `POST /api/v1/tickets/{id}/transitions` to `TicketController` — `@Valid` body, returns updated `TicketResponse` on success, 404/400/409 delegated to `GlobalExceptionHandler` per api-contract.md

**Checkpoint**: The state machine is fully enforced and exhaustively tested — this is the feature's core trust guarantee (spec User Story 4, "P1, core, not optional").

---

## Phase 6: User Story 3 - Update ticket fields and reassign (Priority: P2)

**Goal**: A client can partially update a ticket's `title`, `description`, `priority`, and `assignee` — only submitted fields change; `status` is not settable here.

**Independent Test**: Create a ticket, submit a partial update changing only `assignee`, then `GET` and confirm only `assignee` changed and all other fields (including `status`) are untouched.

### Tests for User Story 3

- [X] T048 [P] [US3] Unit test in `TicketServiceImplTest.java` — `update()` applies only fields present in `TicketUpdateRequest`; fields absent from the request are left unchanged (verify via mock argument capture on `repository.save`)
- [X] T049 [P] [US3] Unit test in `TicketServiceImplTest.java` — `update()` on a non-existent id throws `TicketNotFoundException`
- [X] T050 [P] [US3] Integration test in `src/test/java/com/ticketmanagement/ticket/integration/TicketCrudIntegrationTest.java` (extend from T025) — partial update changing only `priority` persists that field and leaves `title`/`description`/`assignee`/`status` unchanged (spec FR-004, Acceptance Scenario 1)
- [X] T051 [P] [US3] Integration test case (same file) — an update body containing `status` is rejected with `400 VALIDATION_FAILED` (api-contract.md: status not accepted on PATCH); an empty update body (`{}`) returns `200` with the ticket unchanged, a no-op (spec FR-015); an update with a present-but-blank `title` → `400 VALIDATION_FAILED`, stored ticket unchanged (spec Acceptance Scenario 3); update on a non-existent id → `404 TICKET_NOT_FOUND` (spec Acceptance Scenario 4)

### Implementation for User Story 3

- [X] T052 [US3] Create `TicketUpdateRequest` DTO (record) in `src/main/java/com/ticketmanagement/ticket/dto/TicketUpdateRequest.java` — plain nullable fields, not `Optional`-wrapped. Deviation: `@NotBlank` was dropped from the DTO because the Jakarta Validation spec defines `@NotBlank` as implying not-null, which rejected every absent field; presence-then-blank checking moved to `TicketServiceImpl.update()` (see T053).
- [X] T053 [US3] Implement `TicketServiceImpl.update(UUID id, TicketUpdateRequest request)` — loads ticket (`TicketNotFoundException` if absent), applies only non-null fields, updates `updatedAt`, saves; all-absent request is a no-op (FR-015). Added `validatePresentFieldsNotBlank()` (throws new `FieldValidationException` → 400 `VALIDATION_FAILED`, mapped in `GlobalExceptionHandler`) since Bean Validation couldn't express "not-blank-if-present" cleanly.
- [X] T054 [US3] Add `PATCH /api/v1/tickets/{id}` to `TicketController` — returns updated `TicketResponse`. Deviation: no `FAIL_ON_UNKNOWN_PROPERTIES` config added — Spring Boot's Jackson default already rejects unknown properties (incl. `status`) with 400, confirmed by `updateWithBlankTitleRejectedAndUnchanged`/no-op tests passing.

**Checkpoint**: User Stories 1, 2, 3, and 4 all work independently.

---

## Phase 7: User Story 5 - Add comments to a ticket (Priority: P2)

**Goal**: A client can add a text comment to an existing ticket; comments appear on the ticket's detail view.

**Independent Test**: Create a ticket, `POST` a comment, then `GET` the ticket and confirm the comment appears with content and timestamp.

### Tests for User Story 5

- [X] T055 [P] [US5] Unit test in `src/test/java/com/ticketmanagement/ticket/service/CommentServiceImplTest.java` — `addComment()` on an existing ticket persists a comment with a timestamp
- [X] T056 [P] [US5] Unit test (same file) — `addComment()` on a non-existent ticket id throws `TicketNotFoundException`, no comment persisted
- [X] T057 [P] [US5] Integration test in `src/test/java/com/ticketmanagement/ticket/integration/CommentIntegrationTest.java` — add comment → appears in subsequent `GET /tickets/{id}` `comments[]` with `content` and `createdAt` (spec Acceptance Scenario 1)
- [X] T058 [P] [US5] Integration test case (same file) — blank `content` → `400 VALIDATION_FAILED`, not persisted (spec Acceptance Scenario 2); comment on non-existent ticket id → `404 TICKET_NOT_FOUND` (spec Acceptance Scenario 3)

### Implementation for User Story 5

- [X] T059 [P] [US5] Create `CommentCreateRequest` DTO (record) in `src/main/java/com/ticketmanagement/ticket/dto/CommentCreateRequest.java` — `content` `@NotBlank` (data-model.md)
- [X] T060 [P] [US5] Create `CommentResponse` DTO (record) in `src/main/java/com/ticketmanagement/ticket/dto/CommentResponse.java` — `id`, `ticketId`, `content`, `createdAt` (api-contract.md)
- [X] T061 [US5] Create `CommentService` interface + `CommentServiceImpl` in `src/main/java/com/ticketmanagement/ticket/service/` — `addComment(UUID ticketId, CommentCreateRequest)` verifies the ticket exists (`TicketNotFoundException` if not) before saving via `CommentRepository` (depends on T012, T014, T018, T059, T060)
- [X] T062 [US5] Create `CommentController` in `src/main/java/com/ticketmanagement/ticket/controller/CommentController.java` — `POST /api/v1/tickets/{id}/comments`, `@Valid` body, returns `201` + `CommentResponse`
- [X] T063 [US5] Update `TicketServiceImpl.getById()` (from T031) to populate `TicketResponse.comments` from `CommentRepository.findByTicketId()` so the detail view includes comments (api-contract.md: comments included only on `GET /tickets/{id}`)

**Checkpoint**: All 5 user stories are independently functional — the full feature scope from spec.md is implemented.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Restart-durability proof, remaining DTO validation coverage, and end-to-end deployment verification

- [X] T064 [P] Restart-persistence integration test in `src/test/java/com/ticketmanagement/ticket/integration/RestartPersistenceIntegrationTest.java` — using a file-based H2 datasource, create a ticket + comment, restart the Spring context against the same datasource file, confirm both are still retrievable unchanged (spec FR-008, SC-005). Built ahead of schedule alongside US1–US5.
- [X] T065 [P] DTO/Bean Validation unit tests in `src/test/java/com/ticketmanagement/ticket/validation/` for `TicketCreateRequest`, `CommentCreateRequest`, `TicketTransitionRequest` — cover every `@NotBlank`/`@NotNull` rule. `TicketUpdateRequest` has no DTO-level annotations by design (see T052); its test documents that absent/blank fields produce zero DTO-level violations, with blank-rejection covered in `TicketServiceImplTest`/`TicketCrudIntegrationTest` instead.
- [X] T066 Ran `docker compose up --build` end-to-end against real `docker-compose.yml` (app + MySQL): app started with zero manual steps, Flyway auto-migrated on MySQL, `/actuator/health` → `UP`, all 6 endpoints responded correctly against MySQL (constitution Principle II).
- [X] T067 Executed every quickstart.md scenario against the live compose stack: create+retrieve ✅, valid transition (OPEN→IN_PROGRESS) ✅, invalid transition (IN_PROGRESS→OPEN) → 409 INVALID_TRANSITION ✅, malformed create (blank title) → 400 VALIDATION_FAILED ✅, keyword search ✅, status filter ✅, unknown status → 400 UNKNOWN_FILTER ✅, comment add+retrieve ✅, restart persistence (`docker compose restart app`, ticket+comment still present) ✅. Stack torn down after (`docker compose down -v`).
- [X] T068 Ran `mvn test`: 93/93 passing, 0 failures/errors. Full state-machine matrix green in both unit (`TicketServiceImplTest`, 5 valid + 20 invalid) and integration (`TicketLifecycleIntegrationTest`, same 25 cases) suites.

**Checkpoint**: `docker compose up` produces a working, persistent, fully-tested API matching api-contract.md end-to-end.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS all user stories
- **User Story 1 (Phase 3)**: Depends on Foundational only
- **User Story 2 (Phase 4)**: Depends on Foundational + User Story 1 (reuses `TicketService`/`TicketResponse`/`TicketController` created in Phase 3, but is independently testable once T037/T038 land)
- **User Story 4 (Phase 5)**: Depends on Foundational + User Story 1 (reuses `TicketServiceImpl`/`TicketController`); independent of User Story 2
- **User Story 3 (Phase 6)**: Depends on Foundational + User Story 1; independent of User Stories 2 and 4
- **User Story 5 (Phase 7)**: Depends on Foundational + User Story 1 (needs `TicketResponse` to attach comments to); independent of User Stories 2, 3, 4
- **Polish (Phase 8)**: Depends on all 5 user stories being complete

### User Story Dependencies

All of US2, US3, US4, US5 build on the `Ticket` entity/service/controller scaffolding established in US1 (Phase 3), but each adds its own endpoint/behavior and has its own independent test — none requires another's *feature* logic to be correct, only its scaffolding to exist. Priority order for sequential delivery: **US1 → US2 → US4 → US3 → US5** (P1s before P2s, spec order within each priority).

### Within Each User Story

- Tests written first, confirmed to fail, then implementation
- DTOs before service methods; service methods before controller endpoints
- Story complete and checkpoint-validated before moving to the next

### Parallel Opportunities

- All `[P]` Setup tasks (T003–T006) run in parallel
- All `[P]` Foundational tasks (T009, T010, T013, T014, T016–T019, T021–T023) run in parallel once T011/T012/T015/T020 (their dependencies) allow
- Once Foundational + US1 are done, US2, US3, US4, US5 implementation can proceed in parallel by different developers (they touch different files: search logic vs. transition logic vs. update logic vs. comment logic)
- All test tasks marked `[P]` within a story run in parallel with each other before that story's implementation tasks begin

---

## Parallel Example: User Story 1

```bash
# Tests (after Foundational phase complete):
Task: "Unit test create() in TicketServiceImplTest.java"
Task: "Integration test create+get round trip in TicketCrudIntegrationTest.java"
Task: "Integration test blank-title rejection in TicketCrudIntegrationTest.java"
Task: "Integration test malformed-id vs not-found in TicketCrudIntegrationTest.java"

# Then implementation, in dependency order:
Task: "Create TicketCreateRequest DTO"
Task: "Create TicketResponse DTO"
# → then TicketService interface → TicketServiceImpl → TicketController (sequential, same service/controller files)
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL — blocks all stories)
3. Complete Phase 3: User Story 1
4. **STOP and VALIDATE**: create + retrieve a ticket via `curl`, confirm SC-001
5. This is the minimum increment that proves the stack (Spring Boot + JPA + H2/MySQL + Flyway + Docker) works end-to-end

### Incremental Delivery

1. Setup + Foundational → foundation ready
2. US1 → test independently → MVP
3. US2 → test independently (list/search/filter now usable)
4. US4 → test independently (lifecycle now enforced — core trust guarantee complete)
5. US3 → test independently (editing now possible)
6. US5 → test independently (comments now possible — full feature scope complete)
7. Phase 8 Polish → restart-durability proof + full `docker compose up` verification

### Parallel Team Strategy

With multiple developers, after Foundational is done:
- Developer A: US1 then US2 (both own `TicketController`'s GET/list surface)
- Developer B: US4 (owns the transition table/endpoint)
- Developer C: US3 (owns PATCH) — start once US1's `TicketServiceImpl` skeleton exists
- Developer D: US5 (owns `CommentController`/`CommentService`, independent files)

---

## Notes

- `[P]` tasks touch different files with no unmet dependencies
- `[Story]` label maps each task to its user story for traceability back to spec.md
- Every valid/invalid transition pair from state-machine.md must appear as a distinct parameterized case in T040/T042 — do not sample
- Every field constraint in data-model.md (not-blank, enum values, nullable `category`, no max length) is repeated verbatim in the task that creates the corresponding DTO/entity, so validation rules aren't left to implementation-time guessing
- Commit after each task or logical group; stop at any checkpoint to validate a story independently
- Avoid: skipping the "empty transition matrix row" cases, same-file conflicts across parallel tasks, giving any story a hard dependency on another story's business logic
