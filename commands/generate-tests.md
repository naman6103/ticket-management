# Generate Tests

Generate unit and/or integration tests for a class or HTTP endpoint, following `rules/testing.md`. When the target can change or observe **ticket status**, cover the **full** valid and invalid transition matrix from `state-machine.md` — not happy paths only.

## Invocation

```text
/generate-tests [class-or-path | HTTP method + path] [--unit] [--integration]
```

| Argument | Meaning |
|----------|---------|
| Fully qualified or simple class name | Test that type (resolve under `src/main/java`) |
| Source path (`*.java`) | Test the class in that file |
| Endpoint (`GET /api/v1/tickets/{id}`, `POST …/transitions`) | Test the mapped controller method and the service it calls |
| `--unit` | Unit tests only (even if an integration suite would also apply) |
| `--integration` | Integration tests only |
| _(empty)_ | If the user @-mentioned a class/file or named an endpoint in the same message, use that; otherwise ask |

Text after the command name is `$ARGUMENTS`. Treat it as the generation target plus optional flags.

## Preconditions (do these first)

1. Read `rules/testing.md` in full.
2. Resolve the target:
   - **Class / path:** read the type, its public API, collaborators, thrown exceptions, and existing tests under `src/test/java` (do not duplicate cases).
   - **Endpoint:** find the `@RestController` mapping, the service method it calls, request/response DTOs, and `@Valid` constraints. Do not put business-rule tests in a controller unit test (see scope below).
3. Classify the work using **Choose test kind** below. Apply `--unit` / `--integration` as overrides.
4. **Ticket-status gate:** if the target reads, writes, validates, or HTTP-maps **ticket status / transitions / lifecycle events**, also:
   - Locate and read `state-machine.md` (search the repo; typical homes are repo root, `rules/`, or `docs/`).
   - If the file is missing or the matrix is incomplete, **stop**. Do not invent statuses, events, or allowed edges. Ask the user for the document or to complete it.
5. If the target cannot be resolved, stop and ask. Do not generate tests against a guessed API.

## Choose test kind

Match `rules/testing.md` (constitution Principle III).

| Target | Default tests | Notes |
|--------|----------------|-------|
| `*Service` / `*ServiceImpl`, validators, mappers, domain helpers | **Unit** | Mock collaborators; no Spring context unless a slice is justified |
| Bean Validation / custom constraint validators | **Unit** | Validator + constraint annotations; no DB |
| Ticket status / transition / state machine (service or persistence) | **Integration** (required) plus unit for pure decision helpers if they exist | H2; full matrix from `state-machine.md` |
| `*Controller` HTTP mapping | **Do not** unit-test the controller for domain rules | Prefer service unit tests + integration (or slice) for HTTP status/`code` on illegal transitions |
| Repository-only persistence of resulting status | **Integration** | Assert stored status after a transition |
| RAG / LLM / vector retrieval answer text | **Neither** as pass/fail prose asserts | Labeled eval pairs only; stubbed-retriever **guardrails** may be integration tests |

Generate both unit and integration when the target is a service that both has isolatable logic **and** applies ticket transitions — unless the user passed a single `--unit` or `--integration` flag.

## Scope

**In scope**

- Public service methods: return values, thrown exceptions, mock interactions
- Input validation (DTO constraints, custom validators, guard clauses)
- Ticket state machine: every documented valid and invalid `(from, event)` pair
- Persistence of status (and contract side effects: audit fields, idempotency) on H2
- Deterministic RAG **guardrails** with a **stubbed** retriever (empty → exact “no relevant tickets found”; hits → cited ticket IDs)

**Out of scope (do not generate)**

- `assertEquals(expectedAnswer, ragAnswer)` or any pass/fail on free-form model text
- Live Elasticsearch, production MySQL, or network LLM calls
- Controller tests that re-implement domain rules already covered on the service
- Unbounded E2E UI flows
- Eval-dataset authorship except pointing to labeled question → ticket ID fixtures if the user asked for RAG eval

## Checklist

Walk every applicable item. Skip items that cannot apply.

### From `rules/testing.md` — unit

- [ ] Fast, isolated; no Spring context unless a slice is justified
- [ ] Collaborators mocked; assert returns, exceptions, and interactions
- [ ] Services and input validation covered; DB / full HTTP stack not used as a unit test
- [ ] No asserts on LLM/RAG answer prose

### From `rules/testing.md` — integration

- [ ] Uses H2 (constitution: H2 for local/dev/test persistence)
- [ ] Table-driven or `@ParameterizedTest` + `@MethodSource` (or CSV/shared fixture)
- [ ] Suite fails the build if a documented transition is missing (completeness check)
- [ ] No pass/fail on RAG answer quality

### From `rules/testing.md` — state machine (mandatory if ticket status is in play)

Structure every case as: **given** current status → **when** action/event → **then** next status or rejection.

```text
@ParameterizedTest
@MethodSource("validTransitions")
void appliesValidTransition(Status from, Event event, Status expected)

@ParameterizedTest
@MethodSource("invalidTransitions")
void rejectsInvalidTransition(Status from, Event event, Class<? extends Exception> error)
```

- [ ] Matrix source is `state-machine.md`, not ad-hoc samples
- [ ] **Full** Cartesian coverage of documented statuses × events (or the document’s explicit matrix): every **valid** edge and every **invalid** pair
- [ ] Valid: persisted status, contract side effects, idempotency if the machine defines it
- [ ] Invalid: **no** status change; structured/domain error aligned with API error shape (`code` such as `INVALID_TRANSITION`, HTTP `409` if this is an API-level test)
- [ ] One factory / CSV / shared fixture for the matrix so gaps are reviewable
- [ ] Completeness: test or fixture enumerates all statuses and events from the document and fails if a pair is absent from both valid and invalid lists

When `state-machine.md` adds a status or event, the matrix and these tests must be updated in the same change (generate the updated factory, do not leave a partial table).

## Transition matrix procedure

Use this whenever the ticket-status gate fired.

1. Parse `state-machine.md` into:
   - Status set `S`
   - Event/action set `E`
   - Valid map `(s, e) → s'` (and any guards, terminal states, idempotent self-transitions)
   - Expected error type/`code` for illegal moves (if unspecified, use the project’s domain exception + `INVALID_TRANSITION`)
2. Build **validTransitions**: every documented allowed edge.
3. Build **invalidTransitions**: every `(s, e)` in `S × E` that is **not** a valid edge (unless the document marks a cell as “n/a” / ignored — then omit and record why in a comment on the factory).
4. Add a **completeness** test, for example:

```text
void transitionMatrixIsComplete()
  // |valid| + |invalid| + |explicitly-ignored| == |S| * |E|
  // every member of S and E from state-machine.md appears
```

5. Do not “sample” representative invalid cases. Missing cells are a generation failure.

## File layout and stack

- Tests: JUnit 5, Java 21, Google style (2-space indent, 100 columns, no wildcard imports) per `rules/java-springboot.md`
- Mirror production packages under `src/test/java`
- Names: `{Class}Test` (unit), `{Class}IT` or `{Feature}StateMachineIT` (integration)
- Integration: `@SpringBootTest` (or a justified slice) + H2 test profile; no production MySQL
- Do not introduce extra test libraries unless already in the build
- Prefer Java records / test fixtures over copied entity graphs

## Output format (mandatory)

1. Short plan: target, test kind(s), whether the state-machine matrix applies, path(s) to write.
2. **Write** the test sources (create or update). Prefer updating an existing test class over a second overlapping class.
3. After writing, summarize:

```markdown
## Generated
- **Target:** {class or METHOD /api/v1/...}
- **Kind:** unit | integration | both
- **Files:** `src/test/java/...`
- **State machine:** not applicable | full matrix from `state-machine.md` (|S|=N, |E|=M, valid=A, invalid=B)
- **Skipped:** {RAG prose asserts, live ES, ...}
```

4. If existing tests already cover a method, extend them; list what was added vs left unchanged.
5. Do not implement production code unless a test cannot compile without a missing test double already implied by the source. Do not “fix” the domain to make a guessed transition pass.

## Behavior rules

- Read `rules/testing.md` every run; read `state-machine.md` every run that touches ticket status.
- Never invent transition edges. The document is the only matrix.
- Never add binary asserts on probabilistic RAG output.
- Keep tests independent, deterministic, and focused (Arrange–Act–Assert).
- Match real type names, exceptions, and DTO fields from source — not this command’s examples.
- If both a service and an endpoint are in scope, put domain matrix tests on the service/IT layer; HTTP tests only assert status + error `code`/`body` shape for one valid and the parameterized invalid set **or** a thin API IT that reuses the same matrix fixture.

## Example (illustrative)

Target: `POST /api/v1/tickets/{id}/transitions` and `TicketService.applyTransition`.

```markdown
## Generated
- **Target:** `TicketService.applyTransition` + `POST /api/v1/tickets/{id}/transitions`
- **Kind:** both (unit for guards; integration for matrix + persistence)
- **Files:** `.../TicketServiceTest.java`, `.../TicketStateMachineIT.java`
- **State machine:** full matrix from `state-machine.md`
- **Skipped:** RAG answer text; live Elasticsearch
```

Illustrative integration skeleton (replace types from the real machine):

```java
@ParameterizedTest
@MethodSource("validTransitions")
void appliesValidTransition(TicketStatus from, TicketEvent event, TicketStatus expected) {
  // given ticket in `from`
  // when service.applyTransition(id, event)
  // then persisted status == expected; contract side effects
}

@ParameterizedTest
@MethodSource("invalidTransitions")
void rejectsInvalidTransition(
    TicketStatus from, TicketEvent event, Class<? extends Exception> error) {
  // given ticket in `from`
  // when / then exception type; status unchanged; API `code` if HTTP IT
}

static Stream<Arguments> validTransitions() { /* from state-machine.md */ }

static Stream<Arguments> invalidTransitions() { /* remainder of S × E */ }
```
