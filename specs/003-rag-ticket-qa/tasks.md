---

description: "Task list for Ticket Knowledge Q&A (RAG)"
---

# Tasks: Ticket Knowledge Q&A (RAG)

**Input**: Design documents from `/specs/003-rag-ticket-qa/`
**Prerequisites**: plan.md, spec.md, architecture.md, rag-ingestion.md, rag-api-contract.md, data-model.md, evaluation-strategy.md, test-strategy.md, quickstart.md

**Tests**: Not explicitly requested as TDD; test-strategy.md's deterministic tests are folded into each task's acceptance criteria rather than separate test-first tasks. RAG evaluation (Phase 7) is deliberately not a unit-test gate, per constitution Principle III.

**Organization**: Tasks are grouped by user story (spec.md priorities) so each story is independently implementable and testable. All file paths are relative to repo root.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: US1 = grounded answer (P1), US2 = honest no-match (P1), US3 = freshness on ticket change (P2)

---

## Phase 1: Setup

**Purpose**: Bring up the new infrastructure and dependencies before any application code is written.

- [X] T001 Extend `docker-compose.yml` with an `elasticsearch` service (single-node, `xpack.security.enabled=false`, `ES_JAVA_OPTS=-Xms512m -Xmx512m`, named volume, healthcheck) and an `ollama` service (named volume for model cache), per architecture.md §7. Add corresponding `ELASTICSEARCH_*`/`OLLAMA_*` variables to `.env.example` (no secrets — Elasticsearch has no auth locally, Ollama needs no key). **Acceptance**: `docker compose up` brings up `mysql`, `elasticsearch`, `ollama`, `app`, `frontend` with no manual step beyond `.env`; `curl localhost:9200` (or mapped port) returns a cluster health response; `app`'s healthcheck-gated startup waits on `elasticsearch` the same way it waits on `mysql` today.
- [X] T002 [P] Add Spring AI dependencies to `pom.xml`: `spring-ai-starter-model-ollama` and `spring-ai-starter-vector-store-elasticsearch` (version aligned with Spring Boot 3.3.4, per plan.md Technical Context). **Acceptance**: `mvn compile` succeeds with the new dependencies resolved; no other existing dependency version changes.
- [X] T003 [P] Add `ai.rag.retrieval.top-k` (`5`) and `ai.rag.retrieval.similarity-threshold` (`0.65`) plus `ai.rag.embedding-model` (`nomic-embed-text`) and `ai.rag.chat-model` (`llama3.1:8b`) to `src/main/resources/application.yml`, matching `rules/rag-vector-store.md`. **Acceptance**: keys are present with these exact names/defaults; no other config file hardcodes these values.

**Checkpoint**: Stack starts cleanly; Spring AI is on the classpath; retrieval config keys exist (unread until Phase 2).

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Shared ingestion engine and config binding that every user story depends on. No user story can be demoed until this phase is done.

**⚠️ CRITICAL**: Do not start Phase 3+ until this phase's checkpoint is met.

- [X] T004 Create `com.ticketmanagement.rag.config.RagRetrievalProperties` (`@ConfigurationProperties(prefix = "ai.rag.retrieval")`, a Java record with `topK` (int) and `similarityThreshold` (double) fields, per rag-api-contract.md §5) in `src/main/java/com/ticketmanagement/rag/config/RagRetrievalProperties.java`, and register it (`@EnableConfigurationProperties` or component scan, matching how `PaginationProperties` is wired today). **Acceptance**: changing `ai.rag.retrieval.top-k` in `application.yml`/env changes the bound `RagRetrievalProperties.topK()` value with no code change; a unit test confirms binding.
- [X] T005 Configure the Elasticsearch `ticket-knowledge` index mapping (architecture.md §6.1: `embedding` as `dense_vector` dim 768 cosine similarity, `content` text, `ticketId`/`chunkId`/`status`/`priority`/`assignee`/`category` as `keyword`, `updatedAt` as `date`) — either via Spring AI's `ElasticsearchVectorStore` auto-mapping configuration (`initializeSchema: true` with matching dimensions) or an explicit index-creation bean in `com.ticketmanagement.rag.config`. **Acceptance**: on application startup against a clean Elasticsearch, the `ticket-knowledge` index exists with the fields and types listed above (verified via `GET ticket-knowledge/_mapping`).
- [X] T006 [P] Add `com.ticketmanagement.rag.event.TicketChangedEvent` (carries `ticketId: UUID`) in `src/main/java/com/ticketmanagement/rag/event/TicketChangedEvent.java`. **Acceptance**: a plain immutable event type with one field, no behavior.
- [X] T007 Implement the knowledge-document builder: `com.ticketmanagement.rag.service.TicketChunkBuilder` in `src/main/java/com/ticketmanagement/rag/service/TicketChunkBuilder.java`, producing one chunk `{chunkId: "{ticketId}:description", text: ticket.description}` and one chunk per comment `{chunkId: "{ticketId}:comment:{commentId}", text: comment.body}` (architecture.md §3), with each chunk over ~2000 characters further split on blank-line boundaries as `{chunkId}:part{N}`. Each produced chunk carries the ticket's `ticketId`, `status`, `priority`, `assignee`, `category` metadata snapshot. Blank `content` (defensive only) is skipped, per data-model.md's validation rule. **Acceptance**: given a ticket with a description and N comments, the builder returns exactly N+1 chunks (before any long-content splitting) with the exact `chunkId` format above and correct metadata; a unit test covers the >2000-char sub-split fallback.
- [X] T008 Add the `knowledgeIndexed` flag (FR-018, rag-ingestion.md §2/§5): a Flyway migration `src/main/resources/db/migration/V2__add_tickets_knowledge_indexed.sql` adding `knowledge_indexed BOOLEAN NOT NULL DEFAULT FALSE` to the `tickets` table; add the corresponding `knowledgeIndexed` field (with getter and a package-private/internal setter) to `Ticket` in `src/main/java/com/ticketmanagement/ticket/entity/Ticket.java`; add `List<Ticket> findByKnowledgeIndexedFalse(Pageable pageable)` to `TicketRepository`. **Acceptance**: a fresh H2/MySQL schema created via Flyway has the new column defaulting to `false`; existing rows (in a non-empty pre-existing database) also default to `false` after migration; the repository query returns only tickets with `knowledgeIndexed = false`.
- [X] T009 Implement `com.ticketmanagement.rag.service.TicketIngestionService` / `TicketIngestionServiceImpl` in `src/main/java/com/ticketmanagement/rag/service/`: `reingest(UUID ticketId)` loads the ticket + comments (via `TicketRepository`/`CommentRepository`), builds chunks via `TicketChunkBuilder`, embeds each chunk's text (Spring AI `EmbeddingModel`, Ollama `nomic-embed-text`), deletes all existing `ticket-knowledge` documents where `ticketId` matches, then upserts the new chunk documents keyed by `chunkId` (architecture.md §6.1, rag-ingestion.md §2). After a successful upsert, if the loaded ticket's `knowledgeIndexed` is `false`, set it to `true` and save it via `TicketRepository` (T008's field) — this MUST only happen after ingestion has actually succeeded, never before or on failure. **Acceptance**: calling `reingest` twice in a row for an unchanged ticket leaves exactly the same document set (idempotent, FR-009-adjacent); calling it after a ticket's comment count changes leaves exactly the current chunk set with no leftover stale chunk; after one successful `reingest` call, the ticket's `knowledgeIndexed` is `true`; if the Elasticsearch upsert throws, `knowledgeIndexed` remains unchanged (not set to `true` on failure).
- [X] T010 Wire ticket creation to ingestion: in `TicketServiceImpl.create(...)`, after `ticketRepository.save(ticket)` succeeds, publish `TicketChangedEvent(ticket.getId())` via `ApplicationEventPublisher`. Add `com.ticketmanagement.rag.service.TicketChangeListener` (`@Component`, `@Async @EventListener(TicketChangedEvent.class)`) in `src/main/java/com/ticketmanagement/rag/service/TicketChangeListener.java` that calls `TicketIngestionService.reingest(event.ticketId())`. Add a bounded `@Async` executor bean if none exists yet (constitution Principle II — no unbounded thread growth). **Acceptance**: creating a ticket via `POST /api/v1/tickets` returns its normal response without waiting on ingestion (response time unaffected by Elasticsearch/Ollama latency, verified by a test asserting the controller call completes before/without invoking `TicketIngestionService` synchronously), and shortly after, a `GET ticket-knowledge/_search` for that ticket's `ticketId` finds its description chunk (FR-008).
- [X] T011 [P] Add `AI_GENERATION_FAILED` (502) and `AI_RETRIEVAL_UNAVAILABLE` (503) error codes to the shared `GlobalExceptionHandler` in `com.ticketmanagement.common.exception`, mapped from new `com.ticketmanagement.rag.exception.AiGenerationException` / `AiRetrievalUnavailableException` types, following the existing `ErrorResponse` shape (`rules/api-standards.md`). **Acceptance**: throwing either exception from any controller produces the shared JSON error shape with the correct `status`/`code`, verified by a slice test.
- [X] T012 Implement the one-time backfill job (FR-017, FR-018, rag-ingestion.md §5): a `CommandLineRunner`/`ApplicationRunner` (or dedicated admin-only endpoint), gated behind an explicit opt-in (e.g. a Spring profile or config flag, not run automatically on every startup), that pages through `TicketRepository.findByKnowledgeIndexedFalse(pageable)` (T008) — not `findAll()` — and calls `TicketIngestionService.reingest(ticketId)` for every ticket returned; T009's `reingest` sets `knowledgeIndexed = true` on success, so each processed ticket naturally drops out of this query on any subsequent run. Log the count of tickets processed on completion. **Acceptance**: given a database with N pre-existing tickets where `knowledgeIndexed = false` (created before this feature was deployed, with no `TicketChangedEvent` ever published for them) and M tickets already `knowledgeIndexed = true`, running the backfill processes exactly the N tickets (not N+M) and makes them retrievable via a `ticket-knowledge` search by `ticketId`; interrupting the job partway through and re-running it only reprocesses tickets still flagged `false` (no wasted re-embedding of already-indexed tickets); it does not run automatically as a side effect of normal application startup (spec SC-008, FR-017).

**Checkpoint**: A ticket created via Feature 1's API is chunked, embedded, and stored in Elasticsearch asynchronously, and pre-existing tickets can be brought in via the backfill job. Config is bound and live. No `/api/ai/ask` endpoint exists yet — that's Phase 3.

---

## Phase 3: User Story 1 - Ask about past incidents and get a grounded answer (Priority: P1) 🎯 MVP

**Goal**: `POST /api/ai/ask` returns an answer grounded only in retrieved ticket chunks, with correct ticket ID citations.

**Independent Test**: Seed tickets on a known topic (Phase 2 ingestion makes them retrievable), call `POST /api/ai/ask` with a question about that topic, verify the answer text plus correct citations.

### Implementation for User Story 1

- [X] T013 [P] [US1] Add `com.ticketmanagement.rag.dto.AskRequest` (record, `question` field, `@NotBlank @Size(max = 1000)` per data-model.md's Question entity / FR-004) and `com.ticketmanagement.rag.dto.AskResponse` (record: `answer: String`, `ticketIds: List<String>`, `noRelevantTicketsFound: boolean`) in `src/main/java/com/ticketmanagement/rag/dto/`. **Acceptance**: bean validation on `AskRequest` rejects blank and >1000-character values; DTOs are records per `rules/java-springboot.md`.
- [X] T014 [US1] Add `com.ticketmanagement.rag.controller.AskController` with `POST /api/ai/ask` (`@Valid @RequestBody AskRequest`) delegating to `AskService.ask(...)`, in `src/main/java/com/ticketmanagement/rag/controller/AskController.java`. **Acceptance**: an invalid request (blank/over-length `question`) returns `400 VALIDATION_FAILED` with `details[]` before any service call (verified by a slice test asserting the service is never invoked).
- [X] T015 [US1] Implement `com.ticketmanagement.rag.service.AskService` / `AskServiceImpl` retrieval step: embed the question (same `EmbeddingModel`/model as ingestion — `nomic-embed-text`), run a similarity search against the `ticket-knowledge` index using `RagRetrievalProperties.topK()` and filtering to results at/above `similarityThreshold()` (architecture.md §2 steps 2–3). **Acceptance**: a unit test with a stubbed `VectorStore` confirms the search request uses the currently-configured top-K and threshold values (not hardcoded numbers) — changing the config in the test changes the call arguments.
- [X] T016 [US1] Implement the generation step in `AskServiceImpl`: when retrieval returns ≥1 chunk above threshold, build a prompt containing only the retrieved chunks' `content` + their `ticketId` metadata plus a system instruction restricting the model to that context (architecture.md §2 step 5, §5), call the Ollama chat model (`llama3.1:8b`) via Spring AI `ChatClient` exactly once, and map the result to `AskResponse` with `ticketIds` set to the **distinct set of ticket IDs whose chunks were included in the prompt** (not parsed from the model's text) and `noRelevantTicketsFound = false` (FR-007, architecture.md §2 step 7). **Acceptance**: a unit test with a stubbed `ChatClient` returning fixed text, and a stubbed retriever returning known chunks from tickets A and B, asserts `ticketIds == [A, B]` regardless of what the stubbed model's text says (including a case where the stub text wrongly mentions ticket C — C must not appear in `ticketIds`).
- [X] T017 [US1] Wire T011's error handling into `AskServiceImpl`: on chat-model failure/timeout, throw `AiGenerationException`; on embedding-call/vector-store failure, throw `AiRetrievalUnavailableException`. **Acceptance**: a unit test with a `ChatClient` stub that throws produces a `502 AI_GENERATION_FAILED` response end-to-end (not a partial `AskResponse`), per FR-015.
- [X] T018 [US1] Enforce the structural no-side-effects guarantee (FR-013): confirm/ensure `AskServiceImpl`'s constructor has no dependency on `TicketService`, `CommentService`, or any notification component, and that no `@Tool` functions are registered on its `ChatClient`. Add a unit test asserting `AskServiceImpl`'s declared constructor parameter types contain none of `TicketService`, `CommentService`, or a notification interface. **Acceptance**: the test fails if such a dependency is ever added, catching a future regression at compile-adjacent test time rather than relying on prompt wording.

**Checkpoint**: `POST /api/ai/ask` returns grounded, cited answers for questions with a real match. User Story 1 is independently demoable.

---

## Phase 4: User Story 2 - Honest "no answer" when nothing relevant exists (Priority: P1)

**Goal**: Questions with no matching or insufficient ticket content get an explicit, honest response — never a fabricated one.

**Independent Test**: Ask about a topic absent from ticket history; verify the exact "no relevant tickets found" shape with no citations and no chat-model call.

### Implementation for User Story 2

- [X] T019 [US2] In `AskServiceImpl`, short-circuit before generation when retrieval returns zero hits or all hits are below `similarityThreshold()`: return `AskResponse(answer = "No relevant tickets found for this question.", ticketIds = [], noRelevantTicketsFound = true)` without calling the chat model (FR-006, rag-api-contract.md §3, architecture.md §2 step 4). **Acceptance**: a unit test with a stubbed retriever returning zero/below-threshold hits asserts the exact response shape above AND asserts the stubbed `ChatClient` was never invoked (verify-zero-interactions).
- [X] T020 [US2] Add the "retrieved-but-insufficient" guardrail (FR-016): extend the system prompt instruction from T016 so that when retrieved chunks exist but don't answer the specific question, the model is instructed to state the retrieved tickets don't answer the question, and add this scenario as a stubbed-model test case distinct from T019's zero-hit case. **Acceptance**: a unit test with a stubbed retriever returning real chunks but a stubbed `ChatClient` response stating "the retrieved tickets do not answer this question" round-trips correctly into `AskResponse.answer` with `noRelevantTicketsFound = false` and non-empty `ticketIds` (still cites what was retrieved, per architecture.md §5) — distinguishing this from T019's `noRelevantTicketsFound = true` case.

**Checkpoint**: Both guardrail paths (no-match, retrieved-but-insufficient) are covered and distinguishable. User Stories 1 and 2 together satisfy the full grounding contract.

---

## Phase 5: User Story 3 - Knowledge base stays current as tickets change (Priority: P2)

**Goal**: Ticket updates, transitions, and new comments refresh the knowledge base asynchronously, without slowing the triggering request.

**Independent Test**: Update a ticket's resolution comment with a new distinctive detail; after the async refresh, ask about that detail and see it (and the old detail no longer as the top match).

### Implementation for User Story 3

- [X] T021 [US3] Publish `TicketChangedEvent(ticket.getId())` from `TicketServiceImpl.update(...)` and `TicketServiceImpl.transition(...)` (after their respective `ticketRepository.save(...)` calls), and from `CommentServiceImpl`'s add-comment method (after the comment is saved), reusing the same `TicketChangeListener` from T010 (rag-ingestion.md §3). **Acceptance**: updating a ticket's description, transitioning its status, or adding a comment each independently triggers a `reingest` call (verified via an integration test with a spied/mocked `TicketIngestionService`), and each triggering API call's response completes without waiting on that call (FR-008's "MUST NOT delay").
- [X] T022 [US3] Verify end-to-end supersession: after T021 wiring, confirm that editing a comment's content (or removing one, if supported) results in the old chunk no longer being served (T009's delete-then-write already deletes by `ticketId`, so this task is verification, not new logic). **Acceptance**: an integration test updates a ticket's description, re-triggers ingestion, and asserts a search for the old description text no longer returns that ticket's description chunk while a search for the new text does (FR-009).

**Checkpoint**: All three user stories are independently functional. The knowledge base never serves stale content after any Feature 1 write.

---

## Phase 6: Retrieval Evaluation

**Purpose**: Measure retrieval/grounding quality against the labeled golden set — a quality report, not a CI must-pass gate (constitution Principle III, test-strategy.md §2).

- [X] T023 [P] Create the golden-set fixture at `src/test/resources/rag-eval/golden-set.json` per evaluation-strategy.md §1, covering ≥3 topical, ≥2 specific-ticket, ≥2 no-match, ≥1 low-similarity, and ≥1 retrieved-but-insufficient questions against a known set of seeded test tickets. **Acceptance**: the fixture has at least one entry per category listed above, each with `question`, `expectedTicketIds`, and `type`.
- [X] T024 Implement the evaluation harness in `src/test/java/com/ticketmanagement/rag/eval/` that runs retrieval-only (no generation) for each golden-set item and computes Recall@K, Precision@K, and no-match accuracy (evaluation-strategy.md §2), **broken out per golden-set `type`** (topical, specific-ticket, no-match, low-similarity, retrieved-but-insufficient) so the "specific-ticket" category's recall is separately visible — this is the FR-014 measurement (spec.md FR-014, rag-api-contract.md §6). Produce a report (console output or a written report file) rather than JUnit pass/fail assertions wired into the standard build. **Acceptance**: running the harness against the seeded golden set produces overall Recall@K/Precision@K ≥ 90% and no-match accuracy = 100% (spec SC-001/SC-002), plus a distinct recall figure for the "specific-ticket" category alone; the harness is excluded from (or clearly separated within) the default `mvn test` must-pass suite, per `rules/testing.md`.
- [X] T025 Evaluate FR-014 (ticket-ID-specific questions) and add the exact-ID pre-filter fallback if needed: using T024's per-category report, check the "specific-ticket" recall figure. If it is < 90% (the same bar as SC-001), implement the exact-ID pre-filter noted in rag-api-contract.md §6 — when the question text contains a token matching a known ticket ID (UUID pattern), query Elasticsearch by `ticketId` keyword match in addition to (not instead of) the vector search, and prioritize/include those chunks in the prompt regardless of similarity score. If the recall figure is ≥ 90%, explicitly record that the pre-filter was evaluated and found unnecessary — do not skip this decision silently. **Acceptance**: either (a) the pre-filter is implemented and re-running T024 shows "specific-ticket" recall ≥ 90%, or (b) a written note (in this spec folder or the eval report) records that FR-014's best-effort semantic-search behavior already met the bar without a pre-filter, with the measured figure cited.
- [ ] T026 Perform the manual grounding review from evaluation-strategy.md §3 (sentence-level claim-support check, citation-necessity check, reversal check) against the "topical" and "specific-ticket" golden-set answers produced by T024's harness, and record the per-item pass/fail results (e.g. in a short report checked into `specs/003-rag-ticket-qa/` or attached to the eval harness output). **Acceptance**: zero findings of a cited ticket ID not supporting a claim in its answer (spec SC-003); any finding is either fixed (prompt/config tuning) or explicitly logged as a known issue, not silently dropped.

---

## Phase 7: Polish & Cross-Cutting Concerns

**DEFERRED (2026-09-25)**: T027 and T028's acceptance criteria require a real, live `docker compose up` stack
(a genuinely generated chat answer; the full quickstart run). Neither is satisfiable via mocks — mocking the chat
model/vector store/Elasticsearch would make the "verdict" or "result" meaningless, since the whole point of these
tasks is checking real system behavior, not re-testing logic already covered by unit tests (T013–T026). Per explicit
instruction, docker was not run in this pass; both tasks remain unchecked and ready to execute as-is whenever a live
stack is available (chat model `llama3.1:8b` was still downloading into the `ollama` container as of this note).

- [ ] T027 Run `/review-rag-output` (per `commands/review-rag-output.md`) against at least one real, live-generated `POST /api/ai/ask` answer (not a stubbed one) from the running stack, and record the result (pass/fail per claim, any abstain/no-citation findings) in `specs/003-rag-ticket-qa/`. **Acceptance**: the command's per-claim verdicts are captured in writing; any FAIL verdict is triaged (fixed or explicitly accepted as a known limitation) before considering this feature done.
- [ ] T028 Run the quickstart.md validation guide end-to-end (grounded answer, no-match, freshness-after-update, guardrail-against-actions, config-change scenarios) against the full `docker compose up` stack. **Acceptance**: every scenario in quickstart.md produces the documented expected result.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies.
- **Foundational (Phase 2)**: Depends on Phase 1 — BLOCKS all user stories (US1–US3 all need ingestion + config + index to exist).
- **User Story 1 (Phase 3)**: Depends on Phase 2 only.
- **User Story 2 (Phase 4)**: Depends on Phase 2 and on T015/T016 (the retrieval + prompt scaffolding US1 builds) — implement after US1, though its guardrail logic is a distinct, independently testable addition.
- **User Story 3 (Phase 5)**: Depends on Phase 2 (T009/T010's `reingest`/listener) only — does not depend on US1 or US2, and could be implemented in parallel with them by a different developer.
- **Evaluation (Phase 6)**: Depends on US1 + US2 both being implemented (needs real grounded and no-match/insufficient behavior to evaluate).
- **Polish (Phase 7)**: Depends on all prior phases.

### Parallel Opportunities

- T002, T003 (Phase 1) in parallel.
- T006 (Phase 2) in parallel with T004/T005.
- T013 (Phase 3) in parallel with nothing else in its phase (T014–T018 depend on it or on each other sequentially).
- US3 (Phase 5) can be staffed in parallel with US1+US2 (Phases 3–4) once Phase 2 is done, since it touches `TicketServiceImpl`/`CommentServiceImpl` write paths rather than the ask flow.
- T023 (golden-set fixture) can be authored in parallel with Phase 3–5 implementation, ready for T024 once US1/US2 land.

---

## Parallel Example: Phase 1 + start of Phase 2

```bash
# Phase 1, in parallel:
Task: "Add Spring AI dependencies to pom.xml"
Task: "Add ai.rag.retrieval.* config keys to application.yml"

# Early Phase 2, in parallel with T004/T005:
Task: "Create TicketChangedEvent in com.ticketmanagement.rag.event"
```

---

## Implementation Strategy

### MVP First (User Story 1 only)

1. Phase 1 (Setup) → Phase 2 (Foundational) → Phase 3 (US1).
2. **STOP and VALIDATE**: ask a grounded question against seeded tickets, confirm answer + correct citations.
3. This is a demoable MVP even without US2's guardrail or US3's freshness — but ship US2 immediately after, since an ungrounded no-match path is a correctness gap, not a nice-to-have (both are P1 in spec.md).

### Incremental Delivery

1. Setup + Foundational → ingestion works end-to-end for newly created tickets.
2. US1 → grounded answers work → demo.
3. US2 → honest no-match/insufficient handling → demo (P1 complete).
4. US3 → freshness on update/transition/comment → demo (P2 complete).
5. Evaluation (Phase 6) → quality report against the golden set.
6. Polish (Phase 7) → `/review-rag-output` check, full quickstart validation.
