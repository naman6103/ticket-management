# Test Strategy: Ticket Knowledge Q&A (RAG)

**Feature**: 003-rag-ticket-qa | **Status**: Design

Per `rules/testing.md` and constitution Principle III, this feature splits testing into two fundamentally different
tracks: deterministic tests (must-pass CI gate) and RAG evaluation (quality measurement, not a binary gate).

## 1. What is deterministic (must-pass, standard unit/integration tests)

Everything in the pipeline *except* the model's generated prose is deterministic and gets ordinary tests:

| Area | Test type | What it asserts |
|---|---|---|
| Chunking (`TicketIngestionService`) | Unit (mocked `EmbeddingModel`/`VectorStore`) | Given a ticket + N comments, produces exactly the expected chunk IDs and text (description chunk, one chunk per comment, sub-split only when over the length threshold) |
| Metadata mapping | Unit | Chunk documents carry the correct `ticketId`/`status`/`priority`/`assignee`/`category` snapshot |
| Re-ingestion delete-then-write | Integration (embedded/test Elasticsearch or a test double `VectorStore`) | After a ticket update that removes a comment, the corresponding stale chunk is no longer present; after any update, exactly the current chunk set exists — no duplicates, no leftovers (FR-009) |
| Event publication | Unit/slice | `TicketServiceImpl.create/update/transition` and `CommentServiceImpl.add` each publish `TicketChangedEvent` with the correct ticket ID |
| Async, non-blocking refresh | Integration | Ticket create/update/transition API response completes without waiting on ingestion (e.g. ingestion service call is verified to happen off the request thread) |
| Request validation (`AskController`) | Unit/slice (`@WebMvcTest`-style) | Blank/missing question → `400 VALIDATION_FAILED`; question > 1000 chars → `400 VALIDATION_FAILED`; valid question passes validation |
| Guardrail: no/low-similarity retrieval | Integration (stubbed retriever returning zero/below-threshold results) | Response is exactly the fixed "no relevant tickets found" shape (`ticketIds: []`, `noRelevantTicketsFound: true`); chat model is never invoked (verify no interaction with the mocked `ChatClient`) |
| Citation integrity | Unit (stubbed retriever returning known chunks + stubbed chat response) | `ticketIds` in the response equals the distinct ticket IDs of the chunks that were actually retrieved/included in the prompt, regardless of what the stubbed model's text says |
| Generation failure handling | Integration (chat client stub throws/times out) | Response is a structured `502 AI_GENERATION_FAILED` error, not a partial/fabricated answer |
| No side-effecting actions | Unit | `AskServiceImpl`/its Spring AI wiring has no dependency on ticket-mutating services, notification services, or any `@Tool` definitions — a "create a ticket for this"-style question cannot reach a mutating code path (verified by asserting no such collaborator is injected/callable, not by asking the live model) |
| Configuration binding | Unit | `RagRetrievalProperties` binds `top-k`/`similarity-threshold` from `application.yml`/env correctly; changing the property changes the value used by the retriever (verified via a config-only test, not a live model call) |

None of these tests assert on free-form generated text content — where a chat-model response is involved, it is
stubbed with a fixed, known string so the test is checking the surrounding logic (citation extraction, error
mapping), not model behavior.

## 2. What is RAG evaluation (quality measurement, not a CI must-pass gate)

Covered in detail in evaluation-strategy.md: golden-set precision/recall on retrieval, no-match accuracy, and manual
grounding review. These run against a real (or realistically stubbed) embedding/vector-store/chat pipeline and
produce scores/reports, not pass/fail assertions wired into the standard build. A regression here is a signal to
retune configuration or revisit chunking/prompting (architecture.md), not a build-breaking test failure — consistent
with `rules/testing.md`'s explicit prohibition on `assertEquals(expectedAnswer, ragAnswer)`-style tests.

## 3. Explicitly out of scope for testing here

- Elasticsearch's own vector search correctness (trusted third-party behavior).
- Ollama's embedding/generation quality in the abstract (covered qualitatively in architecture.md's trade-off
  discussion, not re-benchmarked here).
- Load/performance testing of the ask endpoint (no SLA was set in the spec beyond "a few seconds," per plan.md's
  Technical Context).
