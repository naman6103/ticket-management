# Testing Conventions

Source of truth: constitution Principle III (Testing Standards). These rules apply to all backend test work.

## Unit tests

**Scope:** services and input validation only.

| In scope | Out of scope |
|----------|--------------|
| Service methods with collaborators mocked | Full HTTP stack / controllers (prefer API or slice tests elsewhere) |
| Bean Validation / custom validators | Database persistence |
| Pure domain helpers and mappers | Elasticsearch / vector store |
| Error mapping and guard clauses | End-to-end flows |

**Expectations:**

- Fast, isolated, no Spring context unless a slice is justified.
- Assert return values, thrown exceptions, and interactions with mocks.
- Do not assert LLM or RAG answer text here.

## Integration tests

**Scope:** ticket state machine and other multi-component flows that need a real (or test) Spring context and H2.

| In scope | Out of scope |
|----------|--------------|
| Every valid state transition | Pass/fail assertions on RAG answer quality |
| Every invalid transition (rejected with the expected error) | Production MySQL or live Elasticsearch unless a dedicated suite requires them |
| Persistence of resulting ticket state | Flaky network-dependent LLM calls |

**Expectations:**

- Use H2 for local/dev/test persistence per the constitution.
- Prefer table-driven or parameterized cases so the transition matrix stays complete as statuses are added.
- Fail the build if any documented transition is missing from the suite.

## State-machine transition tests

Structure each case as: **given** current status → **when** action/event → **then** next status or rejection.

```text
@ParameterizedTest
@MethodSource("validTransitions")
void appliesValidTransition(Status from, Event event, Status expected)

@ParameterizedTest
@MethodSource("invalidTransitions")
void rejectsInvalidTransition(Status from, Event event, Class<? extends Exception> error)
```

**Required coverage:**

1. Enumerate the full transition matrix (valid + invalid). Do not sample “happy paths” only.
2. For valid transitions: assert persisted status, side effects that are part of the contract (e.g. audit fields), and idempotency rules if defined.
3. For invalid transitions: assert no status change and a structured/domain error consistent with the API error shape.
4. Keep the matrix in one place (factory method, CSV, or shared fixture) so reviewers can spot gaps.

When a new status or event is added, update the matrix and tests in the same change.

## RAG evaluation vs deterministic testing

| | Deterministic tests (unit / integration) | RAG evaluation |
|--|------------------------------------------|----------------|
| **Purpose** | Prove exact behavior | Measure retrieval/answer quality |
| **Pass/fail** | Binary assert | Scores/metrics against labeled pairs |
| **Data** | Fixtures, H2 | Labeled question → expected ticket ID(s) (and optional gold answers) |
| **What to check** | Status, validation, errors | Recall@K, citation of ticket IDs, “no relevant tickets found” rate |
| **Where it runs** | CI gate (must pass) | Separate eval job/report; not a hard assert on free-form prose |

**Rules:**

- Do **not** put `assertEquals(expectedAnswer, ragAnswer)` (or similar) on probabilistic model output in unit or integration tests.
- Eval datasets MUST be labeled question/ticket pairs; store them as fixtures or config, not inline magic strings in service tests.
- Guardrail checks that are deterministic (e.g. empty retrieval → exact “no relevant tickets found”; response must include cited ticket IDs when hits exist) MAY live in integration tests with a stubbed retriever—not a live LLM.
- Tuning defaults (top-K, similarity threshold) stay in config; eval runs against those documented defaults.

## Quick checklist

- [ ] New service/validation logic has unit tests
- [ ] State-machine change updates the full valid/invalid transition matrix
- [ ] No pass/fail assertions on free-form RAG text
- [ ] RAG quality work adds/updates labeled eval pairs, not brittle unit asserts
