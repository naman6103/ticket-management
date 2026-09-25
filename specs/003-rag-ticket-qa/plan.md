# Implementation Plan: Ticket Knowledge Q&A (RAG)

**Branch**: `003-rag-ticket-qa` | **Date**: 2026-09-25 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/003-rag-ticket-qa/spec.md`

## Summary

Add a backend-only Retrieval-Augmented Generation (RAG) endpoint, `POST /api/ai/ask`, that answers natural-language
questions about ticket history using only content retrieved from a vector store built from Feature 1's tickets
(description, comments, resolution notes). Technical approach: Spring AI orchestrates ingestion (chunk → embed →
store) and query (embed question → similarity search → prompt an LLM with only the retrieved chunks → return answer
+ cited ticket IDs). Elasticsearch (via Spring AI's `ElasticsearchVectorStore`) is the vector store. Embeddings are
generated locally via Ollama (`nomic-embed-text`) per the Clarifications in spec.md. Re-ingestion on ticket
create/update/transition/comment is event-driven and asynchronous, so ticket-write latency is unaffected.

## Technical Context

**Language/Version**: Java 21 (existing backend)

**Primary Dependencies**: Spring Boot 3.3.4 (existing), Spring AI 1.0.x (`spring-ai-starter-model-ollama`,
`spring-ai-starter-vector-store-elasticsearch`), Elasticsearch 8.x, Ollama (embedding model `nomic-embed-text`;
chat/generation model TBD in architecture.md)

**Storage**: MySQL/H2 (existing, unchanged — source of truth for tickets) + Elasticsearch (new — vector store for
ticket knowledge documents only)

**Testing**: JUnit + Spring Boot Test (existing conventions, `rules/testing.md`); RAG-specific evaluation via a
labeled question→ticket golden set (not pass/fail unit assertions on generated text), per constitution Principle III

**Target Platform**: Linux server (Docker Compose), same as Feature 1

**Project Type**: Web service — single Spring Boot backend module, new `com.ticketmanagement.rag` package alongside
existing `com.ticketmanagement.ticket`

**Performance Goals**: Not independently benchmarked in this feature; `POST /api/ai/ask` should complete within a
few seconds under local Ollama generation (documented as a known trade-off of local models in architecture.md, not a
hard SLA)

**Constraints**: Single `docker compose up` must still bring up the whole system (constitution Principle II); no
external API keys/secrets required for the default embedding path (Ollama is local); ticket create/update/transition
API latency must not regress due to re-ingestion (async requirement from spec Clarifications)

**Scale/Scope**: Ticket volumes consistent with Feature 1 (no stated high-scale requirement); one Elasticsearch index
for ticket knowledge documents

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Check | Status |
|---|---|---|
| I. Tech Stack Discipline | Backend stays Java 21 + Spring Boot + Spring AI; vector store is Elasticsearch via Spring AI's `VectorStore` abstraction; no new database engine introduced | PASS |
| II. Single-Command Deployment | `docker-compose.yml` gains an Elasticsearch service and an Ollama service, each with its own image/config; no manual setup step beyond `.env` | PASS (see architecture.md deployment section) |
| III. Test-First & State Machine Coverage | Deterministic parts (chunking, metadata mapping, config binding, guardrail logic) get unit/integration tests; RAG answer quality is evaluated separately via a labeled golden set, never `assertEquals` on generated text | PASS (see test-strategy.md, evaluation-strategy.md) |
| IV. API Consistency & Boundary Validation | `POST /api/ai/ask` uses a DTO request/response, boundary validation (question length/blank) at the controller, and the shared error shape for failures | PASS (see rag-api-contract.md) |
| V. RAG Grounding & Guardrails | Chunking, embedding model, top-K/similarity threshold documented and configurable; answers cite only retrieved tickets; explicit "no relevant tickets found" on empty/low-similarity retrieval | PASS (see architecture.md, rag-api-contract.md) |

No violations requiring Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/003-rag-ticket-qa/
├── plan.md                    # This file
├── architecture.md            # Phase 0+1: component diagram, request flow, chunking & embedding decisions
├── rag-ingestion.md           # Phase 1: ticket → knowledge document pipeline, re-ingestion trigger
├── rag-api-contract.md        # Phase 1: POST /api/ai/ask request/response contract (serves as /contracts/)
├── data-model.md              # Phase 1: Ticket Knowledge Document / Question / Answer entities
├── evaluation-strategy.md     # Phase 1: labeled golden set, precision/recall, manual grounding checks
├── test-strategy.md           # Phase 1: deterministic unit/integration tests vs. RAG evaluation split
├── quickstart.md              # Phase 1: runnable end-to-end validation guide
└── tasks.md                   # Phase 2 output (/speckit-tasks — not created by /speckit-plan)
```

Note: this feature folds Phase 0 "research" directly into `architecture.md` (component diagram + justified
technology decisions) rather than a separate `research.md`, since the user-requested deliverable set already covers
the same ground with concrete, checked-in decisions.

### Source Code (repository root)

```text
src/main/resources/db/migration/
└── V2__add_tickets_knowledge_indexed.sql   # adds tickets.knowledge_indexed (FR-018)

src/main/java/com/ticketmanagement/
├── ticket/                        # Feature 1 (existing, minimal additive changes)
│   ├── controller/
│   ├── service/
│   │   └── TicketServiceImpl.java # publishes TicketChangedEvent after create/update/transition
│   ├── entity/
│   │   └── Ticket.java            # + knowledgeIndexed field (FR-018)
│   ├── repository/
│   │   └── TicketRepository.java  # + findByKnowledgeIndexedFalse(Pageable)
│   └── dto/
└── rag/                            # New: this feature
    ├── controller/
    │   └── AskController.java              # POST /api/ai/ask
    ├── service/
    │   ├── AskService.java / AskServiceImpl.java       # retrieval → generation orchestration
    │   ├── TicketChunkBuilder.java                      # ticket + comments → chunks (architecture.md §3)
    │   ├── TicketIngestionService.java / Impl.java     # chunk → embed → upsert; sets knowledgeIndexed
    │   ├── TicketChangeListener.java                    # @EventListener(async) → triggers re-ingestion
    │   └── TicketBackfillRunner.java                    # one-time backfill job (FR-017)
    ├── event/
    │   └── TicketChangedEvent.java
    ├── config/
    │   └── RagRetrievalProperties.java     # @ConfigurationProperties: top-K, similarity threshold
    ├── dto/
    │   └── AskRequest.java / AskResponse.java
    └── exception/
        └── AiGenerationException.java / AiRetrievalUnavailableException.java

src/test/java/com/ticketmanagement/rag/
├── service/            # unit tests: chunking, metadata mapping, guardrail logic (mocked VectorStore/ChatModel)
├── integration/         # integration tests: ingestion-on-event, no-match guardrail response shape
└── eval/                # golden-set evaluation harness (see evaluation-strategy.md), not part of the CI must-pass gate
```

Note: an earlier draft of this structure listed a Spring AI `advisors/` package for prompt/guardrail wiring; the
chosen implementation builds the grounded prompt directly in `AskServiceImpl` (architecture.md §2 steps 5–6) instead,
so no `advisors/` package exists in the final structure.

**Structure Decision**: Single Spring Boot module (matches Feature 1). RAG code lives in a new sibling domain
package `com.ticketmanagement.rag`, following the same `controller/service/config/dto` layering as
`com.ticketmanagement.ticket`, per `rules/java-springboot.md`. No new deployable service is introduced — Elasticsearch
and Ollama are added as infrastructure containers in `docker-compose.yml`, not as application code.

## Complexity Tracking

*No constitution violations — table omitted.*
