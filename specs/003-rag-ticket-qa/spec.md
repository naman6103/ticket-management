# Feature Specification: Ticket Knowledge Q&A (RAG)

**Feature Branch**: `003-rag-ticket-qa`

**Created**: 2026-09-25

**Status**: Draft

**Input**: User description: "Build a natural-language question-answering feature over the existing ticket history from Feature 1, grounded strictly in real ticket data. Backend-only — the UI for this comes later in Feature 4. Ingestion converts ticket description/comments/resolution notes into searchable knowledge documents with metadata (ticketId, status, priority, assignee, category), re-ingested on ticket update/close. API: POST /api/ai/ask, request has a question, response has answer text and cited ticket ID(s). Chunking strategy, top-K/similarity threshold configurability, and embedding model choice must be documented and justified. Assistant must only answer from retrieved ticket context, must say so explicitly when no tickets are relevant, and must be a single retrieval->generate flow (no autonomous agent actions). Depends on Feature 1; does not depend on Feature 2."

## Clarifications

### Session 2026-09-25

- Q: Should the embedding model run locally (via Ollama) or call a cloud embedding API? → A: Local via Ollama — keeps the whole stack self-contained per constitution Principle II (single `docker compose up`, no external API key required), at the cost of lower embedding quality/higher latency than top cloud models.
- Q: When a ticket is created, updated, or closed, should re-ingestion happen synchronously in the same request, or asynchronously in the background? → A: Asynchronously in the background — ticket create/update/close requests are not slowed down by re-embedding; there is a brief window after a change where the knowledge base may still reflect the prior version.
- Q: What is the maximum allowed length for a submitted question? → A: 1000 characters.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Ask about past incidents and get a grounded answer (Priority: P1)

A support agent or manager asks a plain-language question about ticket history (e.g. "Have we seen payment failures before?") and receives an answer built only from real tickets, with the specific ticket ID(s) that back the answer.

**Why this priority**: This is the core value of the feature — without a grounded, cited answer, there is no product. Everything else (ingestion, refresh, guardrails) exists to make this story trustworthy.

**Independent Test**: Seed the ticket store with a handful of tickets covering a known topic (e.g. payment failures), call `POST /api/ai/ask` with a question about that topic, and verify the response contains an answer plus the ticket ID(s) of the seeded tickets that discuss it.

**Acceptance Scenarios**:

1. **Given** tickets exist describing past payment failures, **When** the user asks "Have we seen payment failures before?", **Then** the response includes an answer summarizing the failures and cites the specific ticket ID(s) it drew from.
2. **Given** a ticket (identified by its ticket ID) with a documented resolution, **When** the user asks "What was the resolution for ticket <ticket-id>?", **Then** the response states that resolution and cites that ticket's ID.
3. **Given** several resolved tickets share a common root cause, **When** the user asks "What are the common causes of shipment tracking issues?", **Then** the response summarizes the shared cause(s) and cites the relevant ticket IDs.

---

### User Story 2 - Honest "no answer" when nothing relevant exists (Priority: P1)

A user asks a question that has no matching ticket history (or is out of scope), and the system tells them plainly that it found nothing relevant instead of inventing a plausible-sounding answer.

**Why this priority**: A fabricated answer is more damaging than no answer in a support tool — it erodes trust and can send agents down the wrong path. This guardrail is as critical as the happy path.

**Independent Test**: Call `POST /api/ai/ask` with a question about a topic absent from the ticket store, and verify the response explicitly states no relevant tickets were found, with no fabricated ticket citations and no fabricated answer content.

**Acceptance Scenarios**:

1. **Given** no ticket in the system relates to "satellite launch delays", **When** the user asks about satellite launch delays, **Then** the response explicitly says no relevant tickets were found and cites no ticket IDs.
2. **Given** the retrieval step returns results below the configured similarity threshold, **When** the user asks any question, **Then** the system treats this the same as no match and returns the honest "no relevant tickets found" response rather than a low-confidence guess.

---

### User Story 3 - Knowledge base stays current as tickets change (Priority: P2)

As tickets are created, updated, or closed, the underlying knowledge base is refreshed so that answers reflect the latest ticket content (e.g., updated resolution notes) rather than stale data.

**Why this priority**: Without refresh, answers would drift out of sync with ticket reality, undermining the grounding guarantee from User Story 1. It is P2 because the initial ingestion and query flow can be demonstrated first with a one-time load.

**Independent Test**: Create a ticket, ask a question that would only match after an update, update the ticket to add the missing detail (e.g., a resolution note), then ask the question again and verify the answer now reflects the update and cites the ticket.

**Acceptance Scenarios**:

1. **Given** a new ticket is created, **When** ingestion completes, **Then** a question matching that ticket's content returns it as a citation.
2. **Given** an existing ticket's resolution notes are edited, **When** ingestion completes for that update, **Then** a question about the new resolution content returns the updated information, and the previous (stale) version of that ticket's content is no longer surfaced.
3. **Given** a ticket is closed, **When** ingestion completes, **Then** questions about that ticket's resolution include it in the answer with its current (closed) status metadata.

---

### Edge Cases

- What happens when the question is empty, whitespace-only, or exceeds a reasonable length limit? System MUST reject with a validation error rather than invoking retrieval/generation.
- What happens when a ticket is deleted (if deletion exists) after having been ingested? Its content MUST no longer be retrievable or cited in future answers.
- What happens when multiple tickets are relevant but conflict (e.g., different resolutions for similar symptoms)? The answer MUST cite all ticket IDs it drew from and MUST NOT silently pick one as if it were the only cause.
- What happens when the generation step itself fails or times out after a successful retrieval? System MUST return a clear error response rather than a partial or fabricated answer, and MUST NOT cite tickets that weren't actually used in a returned answer.
- What happens when a question asks the system to take an action (e.g., "create a ticket for this" or "notify the assignee")? System MUST NOT perform the action; it MUST only answer using retrieved context, or state that it cannot perform actions.
- What happens to tickets that were created before this feature was deployed (i.e., before the ingestion pipeline existed to process them)? A one-time backfill job MUST ingest them so they are retrievable and citable the same as any ticket created afterward — they MUST NOT be permanently invisible to `POST /api/ai/ask` just because they predate the feature.
- What happens when retrieval finds ticket content above the similarity threshold, but that content does not actually answer the specific question asked (a topical match, not a real answer)? System MUST state that the retrieved tickets do not answer the question, rather than stretching the retrieved content into an answer it doesn't support. This is a distinct outcome from "no relevant tickets found" (FR-006), which applies only when nothing meets the threshold at all — this case is a retrieved-but-insufficient outcome.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST convert each ticket's description, comments, and resolution notes into one or more searchable knowledge documents tagged with metadata: ticket ID, status, priority, assignee, and category.
- **FR-002**: System MUST store the resulting knowledge documents (and their vector representations) in a vector store so they can be retrieved by semantic similarity to a question.
- **FR-003**: System MUST expose `POST /api/ai/ask`, accepting a natural-language question and returning an answer plus the specific ticket ID(s) used to produce it.
- **FR-004**: System MUST reject requests with an empty, whitespace-only, or over-length question (maximum 1000 characters) with a validation error, without invoking retrieval or generation.
- **FR-005**: System MUST answer using only content retrieved from the ticket knowledge base for that request — it MUST NOT supplement or override retrieved context with general knowledge not grounded in a ticket.
- **FR-006**: System MUST return an explicit "no relevant tickets found" response (not a fabricated answer, and with no ticket citations) whenever retrieval finds no results at or above the configured similarity threshold.
- **FR-007**: Every non-empty answer MUST include the ticket ID(s) actually used to produce it, and MUST NOT include ticket IDs that were not part of the retrieved context used for that answer.
- **FR-008**: System MUST re-generate (refresh) the knowledge documents and their vector representations for a ticket whenever that ticket is created, updated, or transitioned to any status (including but not limited to closed), so retrieval never serves stale content. This refresh runs asynchronously in the background and MUST NOT delay the response of the ticket create/update/transition operation that triggered it.
- **FR-009**: When a ticket's content changes, the system MUST ensure the previous version of that ticket's knowledge documents is no longer served by retrieval (superseded, not duplicated alongside the new version).
- **FR-010**: The number of retrieved results considered (top-K) and the minimum similarity threshold for relevance MUST be adjustable through configuration, without requiring a code change.
- **FR-011**: The chunking approach used to split ticket content into knowledge documents MUST be documented, along with the reasoning for choosing it over alternatives, in the project's architecture documentation.
- **FR-012**: The embedding model MUST run locally via Ollama (not a cloud embedding API), consistent with the project's single-command, no-external-dependency deployment requirement. This choice, along with its cost/latency/quality trade-offs versus a cloud alternative, MUST be documented in the project's architecture documentation.
- **FR-013**: The question-answering flow MUST perform exactly one retrieval call and exactly one generation call per request — no repeated/retried retrieval, no multi-step or multi-hop lookups, and no chaining of additional retrieval or generation calls based on an intermediate result. The system MUST have no access, at any point in this flow, to any capability that creates, modifies, or deletes a ticket, sends a notification, or otherwise changes state outside of the request itself — this MUST be true structurally (no such capability is reachable from the code path that handles a question), not merely enforced by instructing the model not to use it, so that it holds even if a question asks the system to take an action.
- **FR-014**: System MUST handle questions that reference a specific ticket's identifier (e.g., "What was the resolution for ticket <ticket-id>?") by retrieving and prioritizing that ticket's content in the answer when it exists in the knowledge base. Because retrieval is similarity-based (FR-002) and a ticket identifier alone carries no semantic meaning, this requirement is satisfied on a best-effort basis by semantic search over the question text; it is evaluated via the "specific-ticket" category of the labeled golden set (evaluation-strategy.md) rather than guaranteed by a hard-coded ID lookup. If evaluation shows this best-effort behavior misses the target ticket, an exact-ID pre-filter (rag-api-contract.md §6) MUST be added.
- **FR-015**: System MUST handle failures in the generation step (e.g., the underlying model call fails or times out) by returning a clear error response rather than a partial, fabricated, or miscited answer.
- **FR-016**: When retrieval returns one or more results at or above the configured similarity threshold, but that content does not answer the specific question asked, the system MUST state that the retrieved tickets do not answer the question rather than generating an answer the retrieved content does not support. This is distinct from FR-006 (which applies only when nothing meets the threshold).
- **FR-017**: System MUST provide a one-time backfill job that ingests every ticket that already existed before this feature's ingestion pipeline first started running (i.e., tickets created prior to this feature's deployment/application-start date), so that pre-existing ticket history is retrievable and citable through `POST /api/ai/ask` — not just tickets created after this feature went live. This job runs independently of the per-ticket event-driven refresh (FR-008) and produces the same knowledge documents that event-driven ingestion would have produced had it existed at the time each ticket was created.
- **FR-018**: Each ticket MUST carry a persistent flag recording whether its initial knowledge-document embedding/chunking has ever completed successfully. The flag MUST be set only after ingestion for that ticket has actually succeeded (never before, and never on failure), and MUST remain set once true — it reflects "has this ticket been indexed at least once," not "is the index currently fresh" (freshness after later edits is governed separately by FR-008/FR-009). The backfill job (FR-017) MUST use this flag to select only tickets not yet indexed, so that: (a) newly created tickets — which are indexed automatically and immediately at creation, per FR-008 — are correctly recognized as already done and are not reprocessed, and (b) the backfill job can be safely interrupted and re-run, picking up only tickets still pending.

### Key Entities

- **Ticket Knowledge Document**: A retrievable, embedded chunk derived from a ticket's description, a comment, or its resolution notes. Carries metadata (ticket ID, status, priority, assignee, category) and a reference back to the source ticket so it can be superseded on re-ingestion and cited in answers.
- **Ticket (extended)**: Feature 1's existing ticket record gains one new attribute for this feature: a boolean flag indicating whether its content has ever been successfully embedded/chunked into the knowledge base (FR-018). This flag lives with the ticket itself, not with the knowledge documents, since it answers a question about the ticket's indexing history rather than about retrievable content.
- **Question**: The natural-language input submitted to `POST /api/ai/ask`; must be non-empty (after trimming whitespace) and no longer than 1000 characters.
- **Answer**: The response returned for a question; contains generated answer text and the list of ticket ID(s) that grounded it (empty list paired with an explicit "no relevant tickets found" message when nothing relevant was retrieved).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: For questions about topics present in ticket history, users receive an answer with correct supporting ticket ID citations in at least 90% of evaluated cases against a labeled question/ticket test set.
- **SC-002**: For questions about topics absent from ticket history, the system responds with an explicit "no relevant tickets found" message (zero fabricated answers) in 100% of evaluated cases against a labeled test set.
- **SC-003**: An answer never cites a ticket ID that was not actually part of the context used to generate that answer — verified across 100% of evaluated cases.
- **SC-004**: After a ticket is updated, a question targeting the new content returns an answer reflecting that update once the background refresh for that ticket completes, with no evaluated case still surfacing the pre-update content after that point.
- **SC-005**: Retrieval tuning (top-K, similarity threshold) can be changed and take effect without any code change or redeployment of application logic — verified by changing configuration and observing different answer/citation behavior for the same question.
- **SC-006**: A user submitting a request asking the system to perform an action (e.g., "create a ticket") never results in a side-effecting action being performed — verified across 100% of evaluated cases, and verifiable by inspecting the code path rather than only by observing model behavior (no reachable mutating/notification capability exists in that path).
- **SC-007**: For questions where retrieved content is topically related but does not answer the specific question, the system states that the retrieved tickets do not answer the question, rather than generating an unsupported answer — verified across 100% of evaluated cases against a labeled test set containing this scenario.
- **SC-008**: After the one-time backfill job runs, 100% of tickets that existed prior to this feature's deployment are retrievable and citable through `POST /api/ai/ask` — verified by asking about a pre-existing (pre-deployment) ticket's content and confirming it is cited, the same as a ticket created after deployment.
- **SC-009**: Re-running the backfill job after it has already completed reprocesses 0 already-indexed tickets — verified by confirming only tickets never previously indexed are affected on a second run, using the per-ticket indexing flag (FR-018) as the check.

## Assumptions

- Feature 1 (Ticket Management API) is deployed and queryable, providing ticket description, comments, resolution notes, status, priority, assignee, and category for ingestion.
- "Ticket ID" throughout this spec refers to Feature 1's actual identifier type (a UUID), not a human-readable code like "TKT-1001" — earlier drafts used that style purely as an illustrative example; citations and ID-specific questions (FR-014) operate on the real UUID values.
- This feature is backend-only; no user interface is built here. A UI to submit questions and display answers is planned for a later feature.
- "Re-ingestion on update/close" means the affected ticket's knowledge documents are refreshed; it does not require a full knowledge-base rebuild.
- Ingestion refresh runs asynchronously in the background after a ticket change is saved, so there is a short, practical delay before the update is reflected in answers; this is acceptable for the "never stale" guarantee since the requirement is about correctness over time, not real-time synchronization, and it avoids coupling ticket-write latency to embedding/vector-store performance.
- Embeddings are generated by a locally hosted model (Ollama) rather than a cloud embedding API, to keep the system deployable via a single `docker compose up` with no external API key, per constitution Principle II.
- A single vector-store-backed knowledge base is sufficient (no per-tenant or per-team partitioning) since Feature 1 does not describe multi-tenancy.
- Evaluation of answer/citation quality uses a labeled question-to-ticket test set (a golden set) rather than exact-string assertions on generated text, consistent with the probabilistic nature of generated answers.
