# Feature Specification: AI Q&A Panel

**Feature Branch**: `004-ai-qa-panel`

**Created**: 2026-09-25

**Status**: Draft

**Input**: User description: "Extend the existing ticket management UI (Feature 2) with an AI Q&A panel that consumes the Feature 3 AI Ticket Assistant API. This is an incremental addition to the existing frontend, not a rebuild. Requirements: An AI Q&A panel: a question input, the grounded answer, the cited ticket ID(s) rendered as links to those tickets, and a clear 'no relevant tickets found' state when the assistant has nothing to cite. The panel is reachable from Feature 2's existing navigation shell and reuses Feature 2's shared error-display component for request/network failures, kept visually distinct from the assistant's own 'no relevant tickets found' response. Clicking a cited ticket ID navigates to that ticket's detail view (built in Feature 2). Acceptance criteria: AI Q&A panel returns and displays grounded, ticket-cited answers for in-scope questions. AI Q&A panel clearly shows 'no relevant tickets found' for out-of-scope/no-match questions, distinct from an error state. Citations link through to the corresponding ticket detail view. Out of scope: any new backend logic, any changes to Feature 1 or Feature 3's APIs — only calls POST /api/ai/ask as specified in Feature 3's rag-api-contract.md."

## Clarifications

### Session 2026-09-25

- Q: When assistant cites ticket IDs, do those show as a separate list of links below answer text, or as inline links woven into the answer prose itself? → A: Separate list of citation links below/after the answer text.
- Q: When agent clicks a cited ticket ID, does it open ticket detail in the same view (leaving the Q&A panel), or in a new tab/window (keeping panel open)? → A: New tab/window — panel stays open in original tab.
- Q: While one question is still being answered, does the panel block a new submission, or allow another submission with only the latest result kept? → A: Disable question input/submit until current request resolves.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Ask a question and get a grounded, cited answer (Priority: P1)

A support agent, while working in the ticket system, wants to ask a plain-language question ("have we seen payment failures before?") and get back an answer grounded in real tickets, with those tickets identified so the agent can go verify or read more.

**Why this priority**: This is the entire value of the feature — without a working ask-and-receive-grounded-answer flow, nothing else in this feature matters. It is also the simplest end-to-end slice that proves the panel is wired to the assistant correctly.

**Independent Test**: Can be fully tested by opening the panel, typing a question known to match existing ticket content, submitting it, and confirming the answer text and at least one cited ticket ID appear.

**Acceptance Scenarios**:

1. **Given** the AI Q&A panel is open, **When** the agent types a question that matches existing ticket content and submits it, **Then** the panel displays the generated answer text and the cited ticket ID(s) associated with that answer.
2. **Given** an answer with multiple cited ticket IDs is displayed, **When** the agent looks at the citations, **Then** each cited ticket ID is shown as its own clickable element in a separate citation list below the answer text (not woven into the prose).
3. **Given** the agent has already received one answer, **When** the agent submits a new question, **Then** the panel replaces the previous answer and citations with the new question's results (no stale answer left on screen).

---

### User Story 2 - Follow a citation to the ticket it came from (Priority: P1)

Having gotten a grounded answer, the agent wants to open one of the cited tickets directly to read its full detail and history, without hunting for it in the ticket list.

**Why this priority**: Citations that cannot be followed are not meaningfully "grounded" from the agent's point of view — the ability to verify a citation against the real ticket is what makes the answer trustworthy and useful, so this is as core as receiving the answer itself.

**Independent Test**: Can be fully tested by receiving an answer with at least one citation, clicking a cited ticket ID, and confirming the ticket detail view (built in Feature 2) opens for that exact ticket.

**Acceptance Scenarios**:

1. **Given** an answer with a cited ticket ID is displayed, **When** the agent clicks that ticket ID, **Then** that ticket's existing detail view opens in a new tab/window, and the AI Q&A panel remains open and unchanged in the original tab.
2. **Given** an answer with multiple cited ticket IDs, **When** the agent clicks a specific one, **Then** the detail view opened in the new tab corresponds to that specific ticket, not another cited ticket.

---

### User Story 3 - Clearly see when nothing relevant was found (Priority: P2)

An agent asks a question that the assistant has no grounded basis to answer (e.g. it's about something never logged as a ticket, or is out of scope). The agent needs to immediately understand that this is an honest "no match," not a broken feature.

**Why this priority**: Distinguishing "no relevant tickets" from a technical failure is called out explicitly in the requirements and is essential for trust — an agent who can't tell the two apart will either distrust every empty result or waste time retrying a request that was never going to fail. It's ranked below the core ask/cite flow because it's a state of that same flow, not a separate capability.

**Independent Test**: Can be fully tested by submitting a question designed to have no relevant tickets and confirming the panel shows a distinct "no relevant tickets found" message with no citations and no error styling.

**Acceptance Scenarios**:

1. **Given** the agent submits a question with no matching ticket content, **When** the assistant responds that no relevant tickets were found, **Then** the panel shows a clear "no relevant tickets found" message instead of an answer, with no citation links shown.
2. **Given** the "no relevant tickets found" state is displayed, **When** the agent compares it to what a request/network failure looks like, **Then** the two states are visually distinguishable from one another (different styling/treatment, not the same generic message box).

---

### User Story 4 - Reach the panel from normal navigation, and see failures handled consistently (Priority: P2)

An agent browsing the existing ticket system wants to get to the AI Q&A panel the same way they get to any other part of the app, and if the request to the assistant fails (network issue, backend error), they want to see the same kind of error handling they already know from the rest of the app.

**Why this priority**: This makes the feature feel like a native part of the existing product rather than a bolted-on demo, and reusing the existing error-display component keeps failure handling consistent — but the panel already delivers its core value (Stories 1-3) even before this polish is confirmed, so it ranks after the core flow.

**Independent Test**: Can be fully tested by navigating to the panel from the existing navigation shell without a direct link/bookmark, and by simulating a request failure (e.g. backend down) and confirming the shared error-display component appears.

**Acceptance Scenarios**:

1. **Given** the agent is anywhere in the existing ticket management UI, **When** the agent uses the existing navigation shell, **Then** an entry point to the AI Q&A panel is visible and reachable.
2. **Given** the AI Q&A panel is open, **When** the request to the assistant fails due to a network or server error, **Then** the panel shows the same shared error-display component used elsewhere in the app, and this error state is visually distinct from the "no relevant tickets found" state.

---

### Edge Cases

- What happens when the agent submits an empty or whitespace-only question? The panel should prevent submission or show a validation message rather than sending an invalid request.
- What happens when the agent submits a question longer than the assistant's accepted length? The panel should surface the resulting validation error clearly (via the shared error-display component) rather than a generic failure.
- What happens when a cited ticket ID no longer resolves to an existing ticket (e.g. deleted)? Clicking it should lead to the existing "ticket not found" state already defined in Feature 2's detail view, not a broken navigation.
- What happens when the assistant call takes a long time to respond? The agent should see a clear waiting/in-progress indication rather than a panel that appears frozen or unresponsive.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The UI MUST provide an AI Q&A panel containing a text input where the agent can type a free-text question.
- **FR-002**: The UI MUST let the agent submit the question and send it to the existing AI assistant endpoint, and MUST prevent submission of an empty or whitespace-only question.
- **FR-003**: The UI MUST display the assistant's returned answer text when the response indicates relevant tickets were found.
- **FR-004**: The UI MUST display every cited ticket ID from the response as its own clickable element in a separate citation list shown below/after the answer text (not woven inline into the answer prose) when the response indicates relevant tickets were found.
- **FR-005**: The UI MUST open the corresponding ticket's existing detail view in a new browser tab/window when a cited ticket ID is clicked, leaving the AI Q&A panel open and unaffected in the original tab.
- **FR-006**: The UI MUST display a clear "no relevant tickets found" state — with no citation links — whenever the response indicates no relevant tickets were found, instead of treating that response as an answer or an error.
- **FR-007**: The UI MUST visually distinguish the "no relevant tickets found" state from the request/network failure (error) state; they MUST NOT share the same presentation.
- **FR-008**: The UI MUST reuse Feature 2's existing shared error-display component to present request/network/backend failures encountered while asking a question.
- **FR-009**: The UI MUST provide an entry point to the AI Q&A panel from Feature 2's existing navigation shell.
- **FR-010**: The UI MUST clear or replace the previously displayed answer, citations, or "no relevant tickets found" state when a new question is submitted, so results never mix across questions.
- **FR-011**: The UI MUST show a clear waiting/in-progress indication while a question is being answered, and MUST disable the question input and submit control for the duration of that request so a second question cannot be submitted until the current one resolves.
- **FR-012**: The UI MUST NOT introduce any new backend endpoints or modify Feature 1's or Feature 3's APIs; it MUST only call the existing `POST /api/ai/ask` endpoint as defined in Feature 3's `rag-api-contract.md`.
- **FR-013**: The UI MUST surface a validation-specific message (via the shared error-display component) when the assistant rejects a question for being blank or exceeding the accepted length, distinguishing it from a generic failure where feasible based on the information the backend response provides.

### Key Entities

- **Question**: The free-text input the agent submits to the assistant; transient, not persisted by this feature.
- **Answer**: The assistant's grounded response text tied to one submitted question; displayed until replaced by the next question's result.
- **Citation**: A reference from an answer to a specific existing ticket (by ticket ID); rendered as a link to that ticket's detail view. An answer has zero citations only when it is a "no relevant tickets found" response.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: An agent can ask a question and see either a grounded answer with citations or a "no relevant tickets found" state within the same panel, without leaving or reloading the page.
- **SC-002**: 100% of citations shown to the agent are clickable and open the correct corresponding ticket's detail view in a new tab, without disrupting the agent's current Q&A panel session.
- **SC-003**: Agents can visually tell apart, without reading fine print, the three distinct outcomes of asking a question: a grounded answer, "no relevant tickets found," and a request failure.
- **SC-004**: The AI Q&A panel is reachable from any screen in the existing ticket management UI within a single navigation action (e.g. one click from the navigation shell).
- **SC-005**: Agents encountering a request failure while asking a question see the same error presentation style they already recognize from elsewhere in the ticket management UI, with no unhandled/blank-screen failures.

## Assumptions

- The AI Q&A panel is a single screen/route within the existing frontend application; it does not require its own separate app shell or authentication model beyond what Feature 2 already provides.
- One question is answered at a time per panel instance; the panel does not need to maintain a running conversation history or multi-turn context across questions (each submission is independent, consistent with the stateless `POST /api/ai/ask` contract).
- The shared error-display component from Feature 2 is generic enough to present the assistant's failure responses (validation and server errors) without needing new component variants; only its supplied content/message differs per use.
- "Visually distinct" (FR-007, SC-003) means using existing UI patterns already available in the app (e.g. differing icon, color, or layout treatment) rather than introducing a new design system.
