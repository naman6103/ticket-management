# Data Model: Ticket Knowledge Q&A (RAG)

**Feature**: 003-rag-ticket-qa | **Status**: Design

This feature adds no new MySQL/H2 tables — it reads Feature 1's existing `Ticket` and `Comment` entities and writes
derived data into Elasticsearch only, with one exception: a single new column on the existing `tickets` table (§1a
below). The entities below are conceptual (spec-level) and map to the Elasticsearch document shape in
architecture.md §6.1.

## Ticket (extended — Feature 1 entity, MySQL/H2)

One new field on Feature 1's existing `Ticket` entity/`tickets` table (FR-018):

| Field | Type | Column | Default | Notes |
|---|---|---|---|---|
| `knowledgeIndexed` | boolean | `knowledge_indexed` | `false` | `true` once this ticket's knowledge documents have been successfully written at least once (by either the create-time listener or the one-time backfill job); never reset to `false` afterward — later re-ingestion on update/transition/comment does not touch this flag, since it tracks "indexed at least once," not "currently fresh" |

This is a MySQL/H2 column, not an Elasticsearch field — it lives with the ticket record itself so it survives
independently of Elasticsearch's state, and so the backfill job (FR-017) can query it directly via
`TicketRepository` without touching the vector store.

## Ticket Knowledge Document

A retrievable, embedded chunk derived from one ticket's description or one comment.

| Field | Type | Source | Notes |
|---|---|---|---|
| `chunkId` | string | derived | `{ticketId}:description` or `{ticketId}:comment:{commentId}`; Elasticsearch document `_id`; deterministic → re-ingestion is an upsert |
| `ticketId` | string (UUID) | `Ticket.id` | used for citation and for scoped delete-then-write on re-ingestion |
| `content` | string | `Ticket.description` or `Comment.body` | the embedded text |
| `status` | enum string | `Ticket.status` | snapshot at ingestion time |
| `priority` | enum string | `Ticket.priority` | snapshot at ingestion time |
| `assignee` | string | `Ticket.assignee` | snapshot at ingestion time |
| `category` | string, nullable | `Ticket.category` | snapshot at ingestion time |
| `embedding` | float vector (768-dim) | computed | from `nomic-embed-text` over `content` |

**Lifecycle**: created/overwritten whenever `TicketIngestionService.reingest(ticketId)` runs (on ticket
create/update/transition, or comment add). All of a ticket's chunks are deleted and rewritten together (architecture.md
§6.1), so there is no partial/stale chunk state for a given ticket.

**Validation rules**: `content` must be non-blank (blank comments/descriptions, if Feature 1 ever allowed them, are
skipped rather than ingested as empty chunks — defensive only, since Feature 1 already validates non-blank content).

## Question

The input to `POST /api/ai/ask`.

| Field | Type | Rules |
|---|---|---|
| `question` | string | non-blank after trim; max 1000 characters (spec Clarifications, FR-004) |

Not persisted — request-scoped only.

## Answer

The output of `POST /api/ai/ask`.

| Field | Type | Rules |
|---|---|---|
| `answer` | string | generated text (grounded case) or the fixed "no relevant tickets found" string (guardrail case) |
| `ticketIds` | list of string (UUID) | distinct ticket IDs whose chunks were included in the prompt; empty iff `noRelevantTicketsFound` is `true` |
| `noRelevantTicketsFound` | boolean | see rag-api-contract.md §3 |

Not persisted — request-scoped only (no chat/answer history is stored by this feature).

## Relationships

```text
Ticket (Feature 1, MySQL/H2)  1 ──< Comment (Feature 1, MySQL/H2)
        │                                    │
        └──────────────┬─────────────────────┘
                        ▼
          Ticket Knowledge Document (Elasticsearch, this feature)
                        │
                        ▼ (retrieved subset, per question)
                     Answer.ticketIds
```

One `Ticket` produces N `Ticket Knowledge Document`s (1 for its description, 1 per comment). A single `Answer` may
cite documents from multiple tickets.
