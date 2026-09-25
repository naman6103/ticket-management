# Architecture: Ticket Knowledge Q&A (RAG)

**Feature**: 003-rag-ticket-qa | **Status**: Design

## 1. Component Diagram

```text
                         ┌───────────────────────────────────────────┐
                         │            Spring Boot Application         │
                         │                                             │
  Ticket CRUD/state ───▶│  ticket.service.TicketServiceImpl           │
  change (Feature 1)     │    └─ publishes TicketChangedEvent ────┐    │
                         │                                        │    │
                         │  rag.service.TicketChangeListener      │    │
                         │    (@Async @EventListener) ◀───────────┘    │
                         │        │                                    │
                         │        ▼                                    │
                         │  rag.service.TicketIngestionServiceImpl     │
                         │    ├─ loads ticket + comments (Feature 1)   │
                         │    ├─ chunks content (per §3)               │
                         │    ├─ embeds chunks ─────────────┐          │
                         │    └─ upserts + deletes stale ───┼───┐      │
                         │                                  │   │      │
  POST /api/ai/ask  ───▶│  rag.controller.AskController      │   │      │
                         │        │                          │   │      │
                         │        ▼                          │   │      │
                         │  rag.service.AskServiceImpl        │   │      │
                         │    ├─ validates question (§5)      │   │      │
                         │    ├─ embeds question ─────────────┤   │      │
                         │    ├─ similarity search (top-K, ───┼───┼──┐   │
                         │    │   threshold) ─────────────────┘   │  │   │
                         │    ├─ builds grounded prompt from      │  │   │
                         │    │   retrieved chunks only            │  │   │
                         │    ├─ calls chat model ─────────────┐   │  │   │
                         │    └─ maps result → answer + ticket │   │  │   │
                         │        ID citations                 │   │  │   │
                         └──────────────────────────────────────┼───┼──┼───┘
                                                                  │   │  │
                              ┌───────────────────────────────────▼───▼──▼─┐
                              │            Ollama (local)                   │
                              │  - embedding model: nomic-embed-text        │
                              │  - chat model: llama3.1:8b (generation)     │
                              └──────────────────────────────────────────────┘
                                                                  │
                              ┌───────────────────────────────────▼─────────┐
                              │   Elasticsearch (single-node, local/dev)     │
                              │   Index: ticket-knowledge                    │
                              │   (dense_vector + keyword/text metadata)     │
                              └────────────────────────────────────────────┘
```

Both the ingestion path (triggered by Feature 1 writes) and the query path (`POST /api/ai/ask`) live in the same
Spring Boot process. No new deployable service is introduced — Elasticsearch and Ollama are added as infrastructure
containers.

## 2. Request Flow: `POST /api/ai/ask`

1. `AskController` receives `{ "question": "..." }`, delegates to `AskServiceImpl` after `@Valid` DTO validation
   (non-blank, ≤1000 chars — see rag-api-contract.md).
2. `AskServiceImpl` embeds the question using the same Ollama embedding model used at ingestion time (embedding
   spaces must match).
3. `AskServiceImpl` runs a similarity search against the `ticket-knowledge` Elasticsearch index, applying the
   configured `top-k` and `similarity-threshold` (see `RagRetrievalProperties`, §6).
4. If no chunk meets the threshold: return the "no relevant tickets found" response immediately (§5) — the chat
   model is never called. This is the primary anti-hallucination guardrail: an empty context window cannot produce a
   grounded answer, so we short-circuit rather than let generation "fill the gap."
5. If chunks are found: build a prompt that includes only the retrieved chunk text + their ticket ID metadata, with
   an explicit system instruction to answer only from the supplied context and to say so if the context does not
   answer the question (defense-in-depth guardrail — retrieval matched something, but it may still not answer the
   specific question asked).
6. Call the Ollama chat model with that prompt (Spring AI `ChatClient`, single call — no tool calling, no agentic
   loop, per FR-013).
7. Map the model's response to `AskResponse`: `answer` text plus the **distinct set of ticket IDs whose chunks were
   actually included in the prompt** (not IDs the model happens to mention) — this is what FR-007 requires ("MUST
   NOT include ticket IDs that were not part of the retrieved context").
8. On a chat-model failure/timeout: return a structured `502`/`504`-class error (via the shared error shape, see
   `rules/api-standards.md`) — never a partial or fabricated answer (FR-015).

## 3. Chunking Strategy

**Decision: paragraph/unit-based chunking — one chunk per ticket description, and one chunk per comment.**

| Approach | Why not chosen |
|---|---|
| Fixed-size (e.g. 500-token sliding window) | Ticket content (description, individual comments) is already short and semantically self-contained; fixed windows would either split a single comment's reasoning across two chunks (losing coherence) or pad chunks with unrelated adjacent comments, both of which weaken retrieval precision and dilute the ticket-ID citation to something that may not fully back the answer. |
| Semantic splitting (embedding-similarity-based segmentation within a document) | Adds meaningful complexity (an extra embedding pass just to decide split points) for content that rarely exceeds a few hundred words per unit. The complexity is not justified until ticket descriptions/comments are observed to be long-form documents, which is not the case here. |
| **Paragraph/unit-based (chosen)** | Each ticket description and each comment is already a natural, bounded unit of meaning written by one person at one point in time. Chunking along these existing boundaries keeps each chunk coherent, keeps the ticket-ID citation trustworthy (a chunk's content genuinely belongs to that ticket at that moment), and needs no extra tokenization/splitting logic. |

Refinement: if a single description or comment exceeds a practical embedding input size (e.g. > ~2000 characters),
it is further split into paragraph-boundary sub-chunks (split on blank lines, not mid-sentence) — this is a fallback
for the rare long entry, not the default path.

Each chunk becomes one **Ticket Knowledge Document** (see data-model.md) carrying the full ticket metadata (ticket
ID, status, priority, assignee, category) regardless of which part of the ticket it came from, so retrieval can
filter/cite by ticket ID and by metadata without a join back to MySQL at query time.

"Resolution notes" mapping: Feature 1's schema has no dedicated resolution-notes field — resolution detail is
recorded as regular comments (typically the comment(s) added at or after the `RESOLVED` transition). This feature
does not require a Feature 1 schema change; all comments are ingested as knowledge documents, so resolution
information is covered without a special case.

## 4. Embedding Model Choice

**Decision: local embedding model via Ollama (`nomic-embed-text`), not a cloud embedding API.**

| Criterion | Local (Ollama, `nomic-embed-text`) | Cloud (e.g. a hosted embeddings API) |
|---|---|---|
| Cost | Zero marginal cost per embedding call; one-time model download | Per-token cost on every ingestion and every question; scales with ticket volume |
| Latency | No network round-trip; runs on the same host/Compose network | Adds external network latency per call; subject to third-party rate limits |
| Quality | Adequate for short, domain-specific ticket text (support tickets are not long-form prose); somewhat behind top cloud embedding models on broad semantic benchmarks | Generally higher-quality embeddings on diverse/long text, better multilingual support |
| Deployment / reproducibility | Fits constitution Principle II directly: `docker compose up` with an added Ollama container, no API key, works fully offline | Requires a secret (API key) via environment variable, requires outbound internet access — breaks the "zero hidden setup, no external dependency" reproducibility goal for reviewers/graders unless explicitly justified |
| Data privacy | Ticket content never leaves the local/deployed environment | Ticket content (potentially including customer data) is sent to a third party |

**Justification**: for this project, reproducibility and zero-external-dependency deployment (constitution Principle
II) dominate the trade-off — a grader or reviewer must be able to run the whole system with `docker compose up` and
no API key. Local embedding quality is sufficient for short, templated ticket text where the goal is topical/semantic
similarity (e.g. "payment failure" tickets clustering together), not nuanced long-document understanding. If ticket
volume or quality requirements grow significantly, a cloud embedding provider can be swapped in behind the same
Spring AI `EmbeddingModel` abstraction without changing ingestion/retrieval code — this is an explicit non-goal for
v1, not a closed door.

The chat/generation model (used only for the final answer synthesis step, not for embeddings) is also served locally
via Ollama (`llama3.1:8b`, configurable) for the same reproducibility reason; this is a separate model from the
embedding model and both are named explicitly in configuration (never hardcoded — see rag-api-contract.md §Config).

## 5. Grounding & Guardrails (implementation view)

- **No general knowledge fallback**: the system prompt sent to the chat model explicitly restricts it to the
  supplied ticket context and instructs it to say the context is insufficient if it cannot answer from it. This is
  belt-and-suspenders with step 4 above (empty retrieval never reaches the model at all).
- **Retrieved-but-insufficient (FR-016)**: distinct from the empty-retrieval case — hits exist above threshold, but
  don't answer the question. Handled by the same system-prompt instruction above ("say so if the context does not
  answer the question"), and evaluated explicitly via a dedicated golden-set category (evaluation-strategy.md §1) so
  this path has its own pass/fail signal instead of being folded into the general "topical" case.
- **"No relevant tickets found"**: triggered by (a) zero search hits, or (b) all hits below
  `similarity-threshold`. Both cases short-circuit before any model call — see rag-api-contract.md for the exact
  response shape.
- **Citation integrity**: ticket IDs returned in `AskResponse.ticketIds` are computed from the retrieved chunk
  metadata that was actually included in the prompt, not parsed out of the model's free-text answer. This guarantees
  FR-007 even if the model's prose mentions a ticket ID incorrectly or not at all.
- **No side effects (structural, not prompt-based)**: `AskServiceImpl` and its Spring AI wiring have no constructor
  or field dependency on `TicketService`, `CommentService`, or any notification component, and register no `@Tool`
  functions with the `ChatClient` — so there is no code path a question can reach that mutates a ticket or sends a
  notification, regardless of what the question asks. This is enforced by the class's dependency graph, not by
  instructing the model to refrain (FR-013); test-strategy.md verifies this by asserting on injected collaborators,
  not by prompting the live model and hoping it complies. There is exactly one retrieval call and one generation
  call per request — no retries, no multi-hop follow-up retrieval based on an intermediate result.

## 6. Elasticsearch Specifics

### 6.1 Index mapping (`ticket-knowledge`)

| Field | Type | Notes |
|---|---|---|
| `embedding` | `dense_vector` | dimension matches the embedding model output (768 for `nomic-embed-text`); similarity `cosine` |
| `content` | `text` | the chunk's raw text (description or comment), used for prompt construction and optional debugging/keyword fallback |
| `ticketId` | `keyword` | source ticket's UUID (string form); used for citation and for deleting/superseding stale chunks on re-ingestion |
| `chunkId` | `keyword` | stable identifier for this specific chunk (e.g. `ticketId:description` or `ticketId:comment:{commentId}`), used as the document `_id` so re-ingestion is an upsert, not a blind insert |
| `status` | `keyword` | ticket status at ingestion time (`OPEN`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`, `CANCELLED`) |
| `priority` | `keyword` | ticket priority at ingestion time |
| `assignee` | `keyword` | ticket assignee at ingestion time |
| `category` | `keyword` | ticket category at ingestion time (nullable, per Feature 1) |
| `updatedAt` | `date` | ticket's `updatedAt` at ingestion time, for diagnostics/debugging staleness, not used in retrieval filtering by default |

Using the ticket's UUID + a stable per-chunk suffix as the Elasticsearch document `_id` means re-ingestion after an
update is a plain upsert of the still-relevant chunks; chunks that no longer exist after an edit (e.g. a comment was
part of re-ingestion logic that determines old chunk IDs no longer produced) are explicitly deleted by ticket ID
before the new chunks are written, so retrieval never serves a superseded chunk (FR-009). See rag-ingestion.md for
the exact delete-then-write sequencing.

### 6.2 Should Elasticsearch also power Feature 1's keyword search?

**Decision: no — Elasticsearch stays dedicated to the RAG vector store; Feature 1's `TicketService.search(...)`
keeps its existing JPA `Specification`-based keyword search against MySQL/H2.**

Rationale:
- Feature 1's search is already implemented, working, and tested against MySQL/H2 (`TicketSearchAndFilterIntegrationTest`).
  Migrating it to Elasticsearch is a non-trivial change to a shipped, unrelated feature and is out of this feature's
  scope (the spec's Input explicitly states "Does not depend on Feature 2" and says nothing about replacing Feature
  1's search).
- The two search needs are different in kind: Feature 1's search is exact/substring keyword matching over
  structured ticket fields; this feature's retrieval is semantic (embedding similarity) over unstructured ticket
  text. Conflating them into one index would force one schema to serve two different query patterns and couple two
  independently-shippable features.
- Keeping them separate respects constitution Principle I (no dependency sprawl) by scoping the new Elasticsearch
  dependency to exactly the capability that needs it, and avoids a data-migration/backfill task with no user-facing
  requirement behind it.

If a future feature wants unified search, that is a new, explicitly-scoped change — not a side effect of this one.

## 7. Deployment

`docker-compose.yml` gains two services:

- `elasticsearch`: single-node, `xpack.security.enabled: false` (local/dev only, matches "no manual setup step"),
  bounded heap (e.g. `ES_JAVA_OPTS=-Xms512m -Xmx512m`) so it does not starve the other containers on a dev machine,
  a named volume for index persistence across restarts, and a healthcheck gating the `app` service's startup
  (mirroring the existing `mysql` healthcheck pattern).
- `ollama`: pulls/serves the embedding (`nomic-embed-text`) and chat (`llama3.1:8b`) models; a named volume for the
  model cache so models are not re-downloaded on every `docker compose up`.

No secrets are introduced: Elasticsearch runs without auth in local/dev (documented as a local/dev-only posture,
consistent with the constitution's Security & Configuration section which governs *credentials*, not TLS/auth
hardening scope for this feature); Ollama requires no API key. Elasticsearch host/port and Ollama base URL are
supplied via environment variables (`.env` / `.env.example`), following the same pattern as `MYSQL_*` variables
today — never hardcoded in `application.yml`.
