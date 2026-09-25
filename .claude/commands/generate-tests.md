---
description: Generate unit/integration tests for a class or endpoint
argument-hint: [class-or-endpoint]
---

Generate unit/integration tests for $ARGUMENTS following `rules/testing.md`.

- Unit tests: cover service methods and validation logic per the unit-test
  scope in `rules/testing.md` (mocked collaborators, no Spring context
  unless a slice is justified).
- Integration tests: cover multi-component flows needing a real/test Spring
  context and H2, per the integration-test scope in `rules/testing.md`.
- If `$ARGUMENTS` touches ticket status (reads, writes, or transitions it):
  read `state-machine.md` and generate the FULL valid/invalid transition
  matrix as parameterized tests (see the `validTransitions` /
  `invalidTransitions` pattern in `rules/testing.md`). Do not sample a
  subset of transitions — every row in the matrix needs a case.
- Do not write pass/fail assertions on free-form RAG/LLM output; if
  `$ARGUMENTS` involves RAG behavior, follow the RAG evaluation guidance in
  `rules/testing.md` instead (labeled question/ticket pairs, not asserts).
- Match the project's existing test structure/naming if tests already
  exist for sibling classes; otherwise follow `rules/testing.md` conventions.

If `state-machine.md` does not exist yet, say so explicitly and generate
only the non-transition tests, rather than guessing at the transition matrix.
