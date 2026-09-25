# Test Strategy: Ticket Management REST API

Per `rules/testing.md` and constitution Principle III. Two tiers only: unit (services/validation, mocked collaborators) and integration (real Spring context + H2). No RAG involved in this feature, so the RAG-evaluation carve-out in `rules/testing.md` does not apply here.

## Unit tests (services + validation, no Spring context unless a slice is justified)

**`TicketServiceImplTest`** (repository mocked):
- `create()` sets status to `OPEN`, generates timestamps, delegates to repository save.
- `update()` applies only fields present in `TicketUpdateRequest`; fields left absent are untouched (verify via mock argument capture).
- `update()` on a non-existent id throws `TicketNotFoundException`.
- `transition()` — every valid transition from state-machine.md updates status and calls save (5 cases).
- `transition()` — every invalid transition from state-machine.md throws `InvalidTransitionException` and never calls save (verify `repository.save` not invoked) — this is the full matrix in state-machine.md's rejected-transitions table, parameterized.
- `transition()` on a non-existent id throws `TicketNotFoundException` before any transition check.
- Search/filter: given a keyword, the specification passed to the repository matches title-or-description containment (verify the built `Specification`/query, not a live DB).

**`CommentServiceImplTest`** (repository mocked):
- `addComment()` on an existing ticket persists a comment with a timestamp.
- `addComment()` on a non-existent ticket id throws `TicketNotFoundException`, no comment persisted.

**DTO / Bean Validation unit tests**:
- `TicketCreateRequest`: blank `title`/`description`/`assignee` → validation violation; missing `priority` → violation; invalid `priority` string → deserialization/validation failure.
- `TicketUpdateRequest`: all fields absent is valid (no-op update is allowed at the DTO level — service decides if that's meaningful); a present-but-blank `title` → violation.
- `CommentCreateRequest`: blank `content` → violation.
- `TicketTransitionRequest`: missing/invalid `targetStatus` → violation.

**`GlobalExceptionHandlerTest`**:
- `MethodArgumentNotValidException` → `400`, `code=VALIDATION_FAILED`, `details[]` populated with the offending field name.
- `TicketNotFoundException` → `404`, `code=TICKET_NOT_FOUND`.
- `InvalidTransitionException` → `409`, `code=INVALID_TRANSITION`.
- Unknown `status` filter value → `400`, `code=UNKNOWN_FILTER`.

## Integration tests (`@SpringBootTest`, H2, real Spring context)

**`TicketLifecycleIntegrationTest`** — the state-machine matrix, table-driven (per `rules/testing.md` "keep the matrix in one place"):

```java
@ParameterizedTest
@MethodSource("validTransitions")
void appliesValidTransition(TicketStatus from, TicketStatus target)
// asserts: 200, persisted status == target, updatedAt advanced

@ParameterizedTest
@MethodSource("invalidTransitions")
void rejectsInvalidTransition(TicketStatus from, TicketStatus target)
// asserts: 409, code == INVALID_TRANSITION, persisted status unchanged (still == from)
```

- `validTransitions()` supplies the 5 rows from state-machine.md's valid-transitions table.
- `invalidTransitions()` supplies all 20 rows from state-machine.md's rejected-transitions table (every from/to pair not in the valid set), so the suite fails the build if a new status is added without updating both tables (per `rules/testing.md`).

**`TicketCrudIntegrationTest`**:
- Create → Get by id round-trip returns matching fields and `status=OPEN` (spec SC-001).
- Partial update changes only the submitted field(s); a follow-up Get confirms untouched fields are unchanged.
- Update on a non-existent id → `404`.
- Create with blank title → `400`, ticket not persisted (assert list count unchanged).

**`TicketSearchAndFilterIntegrationTest`**:
- Seed tickets with distinct titles/descriptions/statuses.
- `q=<keyword>` returns exactly the matching tickets, none unrelated (SC-006).
- `status=<STATUS>` returns exactly tickets in that status, none others (SC-007).
- `status=<invalid-value>` → `400`, `code=UNKNOWN_FILTER`.
- No results for a keyword → `200` with empty `content[]`, not an error.
- List without filters is paginated (`page`, `size`, `totalElements`, `totalPages` all present and consistent).

**`CommentIntegrationTest`**:
- Add comment → appears in subsequent `GET /tickets/{id}` `comments[]` with content and timestamp.
- Add comment with blank content → `400`, not persisted.
- Add comment to a non-existent ticket id → `404`.

**`RestartPersistenceIntegrationTest`** (validates FR-008 / SC-005):
- Using a file-based H2 datasource (not in-memory) pointed at a test-scoped temp directory: create a ticket + comment, shut down and restart the Spring context against the same datasource file, then confirm both are still retrievable unchanged. (In the deployed system this is provided by MySQL's own durability; this test proves the application layer does not lose data across a restart of its own process.)

## Coverage checklist (traceability back to spec.md)

| Spec item | Covered by |
|---|---|
| FR-001, SC-001 | TicketCrudIntegrationTest |
| FR-002, FR-006, FR-007, FR-013, SC-006, SC-007 | TicketSearchAndFilterIntegrationTest |
| FR-003 | TicketCrudIntegrationTest, CommentIntegrationTest |
| FR-004 | TicketServiceImplTest, TicketCrudIntegrationTest |
| FR-005 | CommentServiceImplTest, CommentIntegrationTest |
| FR-008, SC-005 | RestartPersistenceIntegrationTest |
| FR-009, SC-002, SC-003 | TicketServiceImplTest, TicketLifecycleIntegrationTest |
| FR-010, FR-011, SC-004 | DTO validation unit tests, GlobalExceptionHandlerTest, TicketCrudIntegrationTest |
| FR-012 | TicketServiceImplTest, TicketCrudIntegrationTest, CommentIntegrationTest |
