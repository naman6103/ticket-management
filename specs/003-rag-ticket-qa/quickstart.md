# Quickstart: Ticket Knowledge Q&A (RAG)

**Feature**: 003-rag-ticket-qa | **Status**: Design

End-to-end validation guide for this feature once implemented. See architecture.md for component details,
rag-api-contract.md for the request/response schema, and data-model.md for entities.

## Prerequisites

- `.env` populated per `.env.example` (MySQL vars, plus any new Elasticsearch/Ollama vars this feature adds — no
  secrets required for local/dev since Elasticsearch security is disabled and Ollama needs no API key)
- Docker + Docker Compose

## Setup

```bash
docker compose up
```

This brings up: `mysql`, `elasticsearch`, `ollama`, `app`, `frontend` — a single command, per constitution
Principle II. On first run, Ollama pulls the embedding (`nomic-embed-text`) and chat (`llama3.1:8b`) models, which
may take a few minutes; subsequent runs reuse the model cache volume.

## Validate: seed data and ingestion

1. Create a few tickets via Feature 1's API (`POST /api/v1/tickets`) covering a couple of distinct topics (e.g. two
   payment-failure tickets, one shipment-tracking ticket), including comments that describe a resolution, and
   transition at least one to `RESOLVED`/`CLOSED`.
2. Allow a short delay for the async re-ingestion listener to process each change (see rag-ingestion.md §3).

## Validate: grounded answer (User Story 1)

```bash
curl -X POST http://localhost:8080/api/ai/ask \
  -H "Content-Type: application/json" \
  -d '{"question": "Have we seen payment failures before?"}'
```

**Expected**: `200 OK`, `noRelevantTicketsFound: false`, `answer` summarizing the seeded payment-failure tickets,
`ticketIds` containing exactly those tickets' IDs.

## Validate: honest no-match (User Story 2)

```bash
curl -X POST http://localhost:8080/api/ai/ask \
  -H "Content-Type: application/json" \
  -d '{"question": "What caused the satellite launch delay?"}'
```

**Expected**: `200 OK`, `noRelevantTicketsFound: true`, `ticketIds: []`, fixed "no relevant tickets found" `answer`.

## Validate: freshness after update (User Story 3)

1. Update one of the seeded tickets' resolution comment to mention a new, distinctive detail (e.g. a made-up root
   cause keyword not used elsewhere).
2. Allow the async refresh delay, then ask a question targeting that new detail.

**Expected**: the answer reflects the new detail and cites that ticket; asking about the pre-update wording (if it
was fully replaced, not merely appended to) no longer surfaces it as the top match.

## Validate: guardrails

```bash
curl -X POST http://localhost:8080/api/ai/ask \
  -H "Content-Type: application/json" \
  -d '{"question": "Create a new ticket for this payment issue."}'
```

**Expected**: no ticket is actually created (verify via `GET /api/v1/tickets` count is unchanged); the response
either answers from retrieved context only or states it cannot perform actions — never a side effect.

## Validate: configuration

Change `ai.rag.retrieval.top-k` or `similarity-threshold` in `application.yml` (or the corresponding env override),
restart `app`, and re-run the grounded-answer question — observe that the returned `ticketIds` set can change (e.g.
tightening the threshold reduces citations) without any code change (FR-010, SC-005).
