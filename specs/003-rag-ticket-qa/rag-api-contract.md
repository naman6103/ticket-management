# API Contract: `POST /api/ai/ask`

**Feature**: 003-rag-ticket-qa | **Status**: Design

Follows `rules/api-standards.md` (structured error shape, boundary validation) with one accepted deviation, noted
below.

## 1. Request

```http
POST /api/ai/ask
Content-Type: application/json
```

```json
{
  "question": "Have we seen payment failures before?"
}
```

| Field | Type | Rules |
|---|---|---|
| `question` | string | required; non-blank after trim; max 1000 characters (spec Clarifications) |

Naming deviation note: `POST /api/ai/ask` uses a verb-shaped path segment (`ask`) rather than a resource noun. This
is an accepted, explicit exception to `rules/api-standards.md`'s "prefer nouns over verbs" guidance: the endpoint
represents a single, non-CRUD, non-resource action (submit a question, get a grounded answer), the exact path was
specified in this feature's own requirements, and `rules/api-standards.md` itself allows "controlled verbs sparingly"
for actions that are not CRUD (e.g. its own `.../transitions` example). No sub-resource or noun-based alternative
(e.g. `/api/v1/answers`) was requested, and introducing one would contradict the given contract.

## 2. Response — grounded answer found (`200 OK`)

```json
{
  "answer": "Yes — payment failures have occurred before. TKT-... describes a gateway timeout during checkout that was resolved by retrying with exponential backoff, and TKT-... describes a declined-card rate spike traced to a stale fraud-rule config.",
  "ticketIds": ["b3f1c2a4-...-uuid", "9a7d0e11-...-uuid"],
  "noRelevantTicketsFound": false
}
```

| Field | Type | Notes |
|---|---|---|
| `answer` | string | generated answer text, grounded only in the cited tickets |
| `ticketIds` | array of string | ticket IDs (UUID string form, Feature 1's identifier type) whose content was actually included in the prompt used to produce `answer`; never empty when `noRelevantTicketsFound` is `false` |
| `noRelevantTicketsFound` | boolean | `false` here; included on every response (not just the no-match case) so clients can branch on one field rather than infer intent from an empty array |

## 3. Response — no relevant tickets found (`200 OK`)

```json
{
  "answer": "No relevant tickets found for this question.",
  "ticketIds": [],
  "noRelevantTicketsFound": true
}
```

This exact shape is returned whenever:
- retrieval returns zero hits, or
- every hit's similarity score is below the configured `similarity-threshold`.

It is a `200`, not a `404` — the request was well-formed and successfully processed; "no relevant tickets" is a
valid, honest answer to the question, not an error. `answer` is a fixed, non-generated string (the chat model is
never called in this path — see architecture.md §5) so this response can never itself be a fabrication.

## 4. Error responses

Use the shared error shape from `rules/api-standards.md`.

| Condition | Status | `code` |
|---|---|---|
| `question` blank/missing | `400` | `VALIDATION_FAILED` (with `details[]` per `rules/api-standards.md`) |
| `question` exceeds 1000 characters | `400` | `VALIDATION_FAILED` |
| Chat model call fails/times out after a successful retrieval | `502` | `AI_GENERATION_FAILED` |
| Embedding call fails (question cannot be embedded) or vector store is unreachable | `503` | `AI_RETRIEVAL_UNAVAILABLE` |

None of these error paths return a `200` with an error payload, and none fabricate a partial answer (FR-015).

## 5. Configuration (top-K and similarity threshold)

Bound via `@ConfigurationProperties` (constitution Principle V — "MUST NOT be hardcoded inline"), mirroring the
existing `PaginationProperties` pattern:

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

```java
@ConfigurationProperties(prefix = "ai.rag.retrieval")
public record RagRetrievalProperties(int topK, double similarityThreshold) { }
```

Changing `top-k` / `similarity-threshold` requires only a config/env change and application restart — no code
change (FR-010, SC-005). The similarity-threshold default was measured and tuned from an initial 0.65 to 0.60 via
`RagRetrievalEvaluationRunner` (see evaluation-strategy.md §5) — 0.60 improves recall while keeping no-match
guardrail accuracy at 100% on the golden set.

## 6. Ticket-ID-specific questions

Questions naming a specific ticket ID are **not** left to semantic search alone. Measured evaluation
(evaluation-strategy.md §5) showed pure semantic search finds 0% of ticket-ID-specific questions — an identifier
carries no semantic meaning an embedding model can match on. `TicketRetrieval`
(`com.ticketmanagement.rag.service.TicketRetrieval`) therefore also scans the question text for a literal ticket ID
(UUID pattern) and, when found, runs an additional exact-match filter search (`ticketId` keyword equality) against
Elasticsearch, merging its results with the semantic search (de-duplicated by chunk ID) rather than relying on
similarity ranking for this case. This raised measured "specific-ticket" recall from 0% to 100% on the golden set.
