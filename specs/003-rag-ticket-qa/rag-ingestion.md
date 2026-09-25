# Ingestion Pipeline: Ticket → Knowledge Document

**Feature**: 003-rag-ticket-qa | **Status**: Design

## 1. Source Data

For a given ticket, ingestion reads (via Feature 1's existing repositories, in-process — no new API call):

- `Ticket.description`
- All `Comment`s for that ticket (ordered as Feature 1 already orders them), which is where resolution detail lives
  (Feature 1 has no separate resolution-notes field — see architecture.md §3)
- Metadata: `ticketId` (UUID string), `status`, `priority`, `assignee`, `category`

## 2. Chunking → Embedding → Storage Flow

```text
TicketChangedEvent(ticketId)
        │
        ▼
1. Load ticket + comments (TicketRepository, CommentRepository)
        │
        ▼
2. Build candidate chunks:
     - one chunk: { chunkId: "{ticketId}:description", text: ticket.description }
     - one chunk per comment: { chunkId: "{ticketId}:comment:{commentId}", text: comment.body }
     - (rare) sub-split any single chunk > ~2000 chars on blank-line boundaries → "{chunkId}:part{N}"
        │
        ▼
3. Delete all existing ticket-knowledge documents where ticketId == this ticket's ID
   (query-by-ticketId delete; ensures chunks removed since the last ingestion, e.g. a deleted
   comment, are not left behind — FR-009)
        │
        ▼
4. Embed each candidate chunk's text (Ollama, nomic-embed-text) via Spring AI's EmbeddingModel
        │
        ▼
5. Upsert each chunk as a document into the `ticket-knowledge` Elasticsearch index, keyed by chunkId,
   with the embedding vector + text + full metadata snapshot (status/priority/assignee/category at
   this moment)
        │
        ▼
6. If the upsert in step 5 succeeded and Ticket.knowledgeIndexed == false: set it to true and save
   the ticket (MySQL/H2) — this is the *only* place this flag is ever set (FR-018)
```

Step 3 (delete-then-write, scoped to a single ticket) is what guarantees FR-009 ("previous version ... no longer
served") without needing per-field diffing — it is simple, and correct because chunking is deterministic from
current ticket state, so re-deriving all of a ticket's chunks from scratch on every change is cheap and cannot drift.

Step 6 is intentionally the last step and conditioned on step 5's success: `knowledgeIndexed` must never read `true`
for a ticket whose knowledge documents don't actually exist in Elasticsearch yet. If step 4 or 5 throws (Ollama or
Elasticsearch unreachable), step 6 never runs and the flag stays `false` (or unchanged, if it was already `true` from
a prior successful run) — see §5's failure-handling note for why this matters for the backfill job specifically. On
every run after the first, step 6 is a no-op if the flag is already `true` (avoids an unnecessary write on every
routine update/transition/comment re-ingestion).

## 3. Re-ingestion Trigger: Event Listener (not a scheduled job)

**Decision: an in-process, asynchronous Spring event listener on Feature 1's write paths — not a scheduled
reconciliation job.**

`TicketServiceImpl` (create, update, transition) and `CommentServiceImpl` (add comment) each publish a
`TicketChangedEvent(ticketId)` via Spring's `ApplicationEventPublisher` immediately after the successful
`repository.save(...)` call that changes the ticket or adds a comment. A single `@Async @EventListener` in the `rag`
package (`TicketChangeListener`) consumes this event and calls `TicketIngestionService.reingest(ticketId)`.

| Option | Why not chosen / chosen |
|---|---|
| **Event listener on Feature 1's write paths (chosen)** | Ingestion happens exactly when content actually changes — no polling delay, no wasted work re-scanning unchanged tickets. `@Async` (backed by Spring's default `SimpleAsyncTaskExecutor` or a dedicated bounded executor bean) ensures the ticket-write HTTP response is not delayed by embedding/Elasticsearch calls, satisfying the spec's Clarifications decision (async refresh) and FR-008's "MUST NOT delay" requirement. |
| Scheduled reconciliation job (e.g. poll `updatedAt` every N seconds) | Introduces an unavoidable staleness window bounded by the poll interval even when nothing is happening, adds a `lastIngestedAt`-style bookkeeping column/field purely for the scheduler's own bookkeeping, and does needless repeated work re-scanning tickets that haven't changed. It's a reasonable fallback if event delivery reliability ever becomes a concern, but is not justified as the primary mechanism when Feature 1's write paths are in the same process and can publish events directly. |

Failure handling: if ingestion for a given ticket fails (e.g. Elasticsearch or Ollama is temporarily unreachable),
the listener logs the failure; it does not fail or roll back the original ticket write (the event listener runs
after the transaction that saved the ticket has already committed). A future enhancement could add a retry queue or
a periodic "catch-up" reconciliation pass as a safety net for missed events — noted as an explicit non-goal for v1,
not hidden scope.

## 4. Idempotency

Re-running ingestion for the same ticket state twice is safe: chunk IDs are deterministic (`{ticketId}:description`,
`{ticketId}:comment:{commentId}`), so a repeated upsert simply overwrites the same Elasticsearch document with the
same content and embedding — no duplicates, no accumulation.

## 5. Initial Backfill (FR-017) and the `knowledgeIndexed` Flag (FR-018)

The event listener (§3) only fires for tickets created/changed *after* this feature is deployed. Every ticket that
already existed at that point — i.e., created before this feature's ingestion pipeline first ran — would otherwise
be permanently invisible to `/api/ai/ask`, since no `TicketChangedEvent` was ever published for it. FR-017 makes
closing this gap a hard requirement, not an optional cleanup step.

**The problem a plain `findAll()` backfill would have**: without a persistent marker, the backfill job has no way to
tell "a ticket created before deployment, never indexed" apart from "a ticket created after deployment, already
indexed automatically by the event listener" — both just look like rows in the `tickets` table. A naive backfill over
`findAll()` would re-embed *everything*, including tickets the event listener already handled, wasting Ollama/
Elasticsearch work, and would have no way to resume cleanly if interrupted partway through a large table.

**Mechanism**: `Ticket.knowledgeIndexed` (data-model.md §"Ticket (extended)") is the marker that solves this. It is
set to `true` in exactly one place — step 6 of the pipeline in §2 — regardless of whether that pipeline run was
triggered by the create-time event listener or by this backfill job. The backfill job pages through
`TicketRepository.findByKnowledgeIndexedFalse(pageable)` (not `findAll()`) and calls
`TicketIngestionService.reingest(ticketId)` for each result — the exact same chunk → embed → delete-then-upsert flow
(§2) the event listener uses, so there is no separate ingestion code path to keep in sync, and no separate flag-
setting logic to keep in sync either (§2 step 6 handles both callers identically).

**Why this makes the backfill safe to interrupt and re-run**: each ticket's `knowledgeIndexed` only flips to `true`
after its chunks are actually confirmed written (§2 step 6 runs only after a successful upsert). If the backfill job
is killed halfway through, the tickets it hadn't reached yet — and any it was processing when it died, if that
ticket's step 5 hadn't completed — are still `knowledgeIndexed = false` and are picked up again by
`findByKnowledgeIndexedFalse` on the next run. Tickets it already finished are `true` and are skipped, so re-running
the job costs nothing beyond a single indexed query.

**Trigger**: an explicit, opt-in action (e.g. a `CommandLineRunner`/`ApplicationRunner` gated behind a Spring
profile or a dedicated admin-only endpoint) — not something that silently re-runs on every application startup. With
the flag-based query, an accidental extra run is cheap (it will find zero or few pending tickets), but the job still
should not be part of ordinary startup, to keep application boot time predictable.

**Scope boundary**: the backfill's query (`knowledgeIndexed = false`) naturally covers exactly the tickets that
existed *before* this feature's deployment/application-start date — new tickets created after deployment are set to
`knowledgeIndexed = true` by the event listener within moments of creation and so are not selected by this query at
all. The two mechanisms (event listener, backfill) are complementary, not overlapping in responsibility, and the
flag is what keeps them from stepping on each other.

**Completion signal**: the job logs (or otherwise reports) the count of tickets processed, so an operator can
confirm it ran against the full pending ticket set (spec SC-008); a second run reporting a near-zero count confirms
the flag-based skip logic is working (spec SC-009).
