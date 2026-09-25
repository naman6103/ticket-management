# RAG Vector Store Conventions

Source of truth for the ticket knowledge Q&A feature (`POST /api/ai/ask`). Details are decided and justified in
`specs/003-rag-ticket-qa/architecture.md` and `specs/003-rag-ticket-qa/rag-ingestion.md` — this file is the
quick-reference steering doc for any future AI-assisted change that touches ingestion, retrieval, or the vector
store. Aligned with constitution Principle V (RAG Grounding & Guardrails).

## Chunking convention

**Paragraph/unit-based**: one knowledge-document chunk per ticket description, and one chunk per comment. No
fixed-size sliding window, no semantic (embedding-similarity-based) splitting.

Why: ticket descriptions and comments are already short, self-contained units written by one person at one point in
time. Chunking along these natural boundaries keeps each chunk coherent and keeps a chunk's ticket-ID citation
trustworthy — a chunk's content genuinely belongs to that ticket at that moment. Fixed-size windows would split a
comment's reasoning across chunks or pad in unrelated adjacent content; semantic splitting adds an extra embedding
pass that isn't justified for content this short. Full trade-off table: `architecture.md` §3.

Fallback only: a single description/comment over ~2000 characters is further split on blank-line boundaries, not
mid-sentence. This is the exception path, not the default.

"Resolution notes" are not a separate Feature 1 field — they live in regular comments (typically the comment(s) at
or after the `RESOLVED` transition). All comments are ingested, so resolution content is covered without a special
case.

## Embedding model

**Local via Ollama — `nomic-embed-text`**. Not a cloud embedding API.

Why: fits constitution Principle II (single `docker compose up`, no external API key, works offline) — a reviewer or
grader must be able to run the whole system with zero external dependencies. Embedding quality is sufficient for
short, templated ticket text where the goal is topical/semantic clustering, not nuanced long-document understanding.
Trade-off vs. a cloud embedding provider (cost, latency, quality, deployment, data privacy): full table in
`architecture.md` §4.

The chat/generation model used for answer synthesis (`llama3.1:8b`, also via Ollama) is a separate model from the
embedding model — do not conflate the two when changing configuration.

## Retrieval-tuning defaults

Read from `@ConfigurationProperties(prefix = "ai.rag.retrieval")`, bound to `RagRetrievalProperties`:

```yaml
# application.yml
ai:
  rag:
    retrieval:
      top-k: 5                     # number of chunks considered per question
      similarity-threshold: 0.60   # 0.0–1.0; hits below this are treated as "not relevant"
    embedding-model: nomic-embed-text
    chat-model: llama3.1:8b
```

| Property | Config key | Default | Meaning |
|---|---|---|---|
| Top-K | `ai.rag.retrieval.top-k` | `5` | max number of retrieved chunks considered per question |
| Similarity threshold | `ai.rag.retrieval.similarity-threshold` | `0.60` | minimum similarity score for a hit to count as relevant; below this, treated as "no relevant tickets found" |

These defaults are a starting point, tuned against the labeled golden set via `RagRetrievalEvaluationRunner`
(`specs/003-rag-ticket-qa/evaluation-strategy.md` §5) — they are not claimed optimal independent of that evaluation,
and may be retuned there without needing this file to change (only the table above needs updating if the *default*
value changes, not this rule itself). The similarity threshold was moved from an initial 0.65 to 0.60 after measured
evaluation showed 0.60 improves recall while keeping no-match guardrail accuracy at 100%.

Semantic search alone cannot find a ticket by its ID (an identifier has no semantic content an embedding model can
match on) — measured recall for ID-specific questions was 0% before this was addressed. `TicketRetrieval`
(`com.ticketmanagement.rag.service`) therefore also runs an exact-match pre-filter whenever a question literally
contains a ticket ID (UUID pattern), merging those results with the semantic search rather than relying on
similarity alone for that case (FR-014).

## Initial indexing tracking

Each ticket carries a `knowledgeIndexed` boolean (`tickets.knowledge_indexed`, default `false`). It is set to `true`
in exactly one place — after ingestion successfully writes that ticket's chunks to Elasticsearch — regardless of
whether that ingestion run was triggered by ticket creation (event listener) or by the one-time backfill job. It is
never reset to `false` and is not touched by later update/transition/comment re-ingestion (it tracks "indexed at
least once," not "currently fresh"). The backfill job queries `knowledgeIndexed = false` (not `findAll()`) so it only
processes tickets that predate this feature and skips tickets the event listener already handled — this is what
makes the backfill safe to interrupt and re-run. Full design: `specs/003-rag-ticket-qa/rag-ingestion.md` §2/§5.

## Hard rules

- **Top-K and similarity threshold MUST always be read from `RagRetrievalProperties` / the `ai.rag.retrieval.*`
  configuration keys above — never hardcoded inline in service, controller, or advisor code.** This is a direct
  requirement of constitution Principle V and spec FR-010. A code review that finds a literal numeric top-K or
  threshold value in application logic (outside of `application.yml`/env and the properties class itself) MUST
  reject the change.
- **Any change to the chunking strategy or the embedding model MUST update this file and
  `specs/003-rag-ticket-qa/architecture.md` together, in the same change.** These two documents must never
  disagree about what chunking approach or embedding model is actually in use — a PR that changes one without the
  other is incomplete. If the embedding model changes, re-embedding the existing vector store (a full re-ingestion,
  not an incremental one) is required, since old and new embeddings are not comparable in the same similarity space.
