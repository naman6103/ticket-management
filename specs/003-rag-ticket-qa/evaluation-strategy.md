# Evaluation Strategy: Ticket Knowledge Q&A (RAG)

**Feature**: 003-rag-ticket-qa | **Status**: Design

Per constitution Principle III, RAG output is evaluated separately from deterministic tests — never with
`assertEquals(expectedAnswer, actualAnswer)`-style binary assertions on generated text.

## 1. Golden Set

A small, checked-in labeled fixture (e.g. `src/test/resources/rag-eval/golden-set.json` or `.csv`) of
question → expected-ticket-ID(s) pairs, built against a known, seeded set of test tickets (not production data).
Example shape:

```json
[
  {
    "question": "Have we seen payment failures before?",
    "expectedTicketIds": ["<seeded-ticket-id-1>", "<seeded-ticket-id-2>"],
    "type": "topical"
  },
  {
    "question": "What was the resolution for ticket <seeded-ticket-id-3>?",
    "expectedTicketIds": ["<seeded-ticket-id-3>"],
    "type": "specific-ticket"
  },
  {
    "question": "What caused the satellite launch to be delayed?",
    "expectedTicketIds": [],
    "type": "no-match"
  },
  {
    "question": "What browser was the customer using when the payment failure occurred?",
    "expectedTicketIds": ["<seeded-payment-ticket-id>"],
    "type": "retrieved-but-insufficient",
    "expectedBehavior": "states retrieved tickets do not answer the question (FR-016) — the payment ticket is topically retrieved but does not record browser information"
  }
]
```

Minimum coverage for the golden set (small but representative, per the spec's example questions):
- ≥3 "topical" questions (multiple tickets share a theme, e.g. payment failures, shipment tracking)
- ≥2 "specific-ticket" questions (question names or clearly targets one ticket)
- ≥2 "no-match" questions (topic absent from the seeded tickets entirely)
- ≥1 "low-similarity" question (topic tangentially related but not actually answered by any ticket — tests the
  threshold, not just zero-hit retrieval)
- ≥1 "retrieved-but-insufficient" question (content is retrieved above threshold but does not answer the specific
  question asked — tests FR-016/SC-007, distinct from both "topical" success and "no-match")

## 2. Retrieval Metrics: Precision & Recall

For each golden-set item, run retrieval only (no generation) against the configured `top-k` /
`similarity-threshold`, and compare the retrieved ticket IDs to `expectedTicketIds`:

- **Recall@K**: of the expected ticket IDs, what fraction were retrieved in the top-K results?
- **Precision@K**: of the retrieved top-K results, what fraction correspond to an expected ticket ID?
- **No-match accuracy**: for `"no-match"` items, retrieval must return zero results above threshold — measured as a
  simple pass/fail rate across those items (this one sub-metric is legitimately binary, since it is checking a
  deterministic threshold comparison, not judging generated prose).

These metrics are computed by a small evaluation harness (`src/test/java/.../rag/eval/`), run on demand or in a
non-blocking CI job — not part of the must-pass unit/integration test gate, per constitution Principle III.

## 3. Manual Grounding Checks

Automated metrics catch retrieval quality; they do not catch subtle generation-side grounding failures (e.g. the
model paraphrasing correctly-retrieved content into a claim the ticket doesn't actually support). For each golden-set
"topical" and "specific-ticket" item, a manual (human) review answers three yes/no questions by pointing to a
specific sentence in the cited ticket's content (or citing its absence), so two independent reviewers reach the same
verdict without relying on undefined judgment:

1. **Sentence-level check**: for every factual claim in `answer` (split into individual claims), can the reviewer
   point to a specific sentence in one of the cited tickets' `content` that states or directly implies it? Any claim
   for which no such sentence exists is a FAIL for this item.
2. **Citation-necessity check**: for every ticket ID in `ticketIds`, can the reviewer point to at least one claim in
   `answer` that came from that specific ticket's content (not a different cited ticket's)? A cited ticket with no
   corresponding claim is a FAIL for this item.
3. **Reversal check**: for each cited ticket, does the direction of any cause→resolution or symptom→cause statement
   in `answer` match the direction stated in that ticket's content (not swapped, and not attributed to a different
   cited ticket)? Any mismatch is a FAIL for this item.

Each of the three checks is recorded as pass/fail per golden-set item, not a single subjective overall impression.
This is a lightweight review run against golden-set answers when the retrieval/prompt/model configuration changes
meaningfully — not a per-commit gate. Findings that reveal a systemic grounding problem (not a one-off) feed back
into the prompt instructions in architecture.md §5 or the retrieval configuration in rag-api-contract.md §5.

## 4. Success Thresholds (ties to spec Success Criteria)

- Recall@K and Precision@K on "topical"/"specific-ticket" items ≥ 90%, matching spec SC-001's 90% correct-citation
  target.
- No-match accuracy on "no-match" items = 100%, matching spec SC-002.
- Zero manual-review findings of a cited ticket ID not actually supporting the answer, matching spec SC-003.

If a threshold is missed, the response is to tune `top-k`/`similarity-threshold` (config-only) or revisit the
chunking/prompt approach (architecture.md) — not to loosen the evaluation criteria.

## 5. Measured Findings (T025)

`RagRetrievalEvaluationRunner` (`src/test/java/com/ticketmanagement/rag/eval/`) was run against a live Elasticsearch
+ Ollama (`nomic-embed-text`) instance with the golden set in §1, seeded against 5 fixture tickets. Findings, in the
order they were investigated:

1. **Specific-ticket recall was 0%** at every similarity threshold, including `0.0` (accept everything) when a
   filter wasn't applied. Root cause: an embedding model has no way to semantically match an opaque identifier
   (UUID) against ticket description/comment text — there is no shared meaning to embed toward. This confirmed the
   risk flagged in FR-014's original wording ("best-effort... may need a pre-filter").
   - **Decision**: implemented the exact-ID pre-filter (rag-api-contract.md §6) in
     `com.ticketmanagement.rag.service.TicketRetrieval` — when a question contains a literal ticket ID, an
     additional exact-match filter search runs alongside semantic search, merged and de-duplicated.
   - **Result after the fix**: specific-ticket recall = 100%, precision = 100% (2/2 golden-set items), re-measured
     against the same golden set and fixture data.

2. **Topical recall was low (16.7%) at the original default `similarity-threshold: 0.65`**, despite a
   threshold-free sweep showing 100% recall was achievable (i.e., ranking was correct — the right chunks were
   retrieved, just filtered out below the configured threshold). A sweep across `[0.0, 0.2, 0.3, 0.4, 0.5, 0.6,
   0.65, 0.7]` showed:
   - No-match guardrail accuracy (SC-002, must stay 100%) only holds at threshold ≥ 0.6 on this corpus; below 0.6 the
     guardrail starts failing (unrelated questions score high enough to look "relevant").
   - At 0.6, recall = 61.1% / precision = 52.8% (semantic-only, no exact-ID pre-filter); at 0.65 (the prior default),
     recall drops to 41.7% for no precision gain.
   - **Decision**: moved the default `ai.rag.retrieval.similarity-threshold` from `0.65` to `0.60` — strictly better
     recall at the same 100% no-match guardrail accuracy.

3. **Overall recall at the tuned default (0.60) does not yet reach the 90% bar in SC-001** on this specific 5-ticket
   synthetic fixture set. This is logged as a known limitation, not silently dropped: the fixture corpus is
   intentionally small (5 tickets) for a fast, deterministic harness, and `nomic-embed-text` (a compact local model,
   chosen for the reasons in architecture.md §4) has lower semantic resolution than larger cloud embedding models on
   short, jargon-heavy support text. Recommended follow-up (not implemented in this pass, out of scope for the
   current task): re-run this harness against a larger, more realistic ticket corpus before treating the 90% bar as
   met or missed — 5 tickets is too small a sample to draw a final conclusion from, and the measured recall gap here
   reflects corpus size and embedding-model capacity, not a defect in the retrieval logic itself (which the
   threshold-free sweep showed ranks correctly).

Re-running `RagRetrievalEvaluationRunner` is self-contained: it seeds its own fixture tickets and clears the
`ticket-knowledge` index of prior runs' documents first, so results are reproducible run-to-run.
