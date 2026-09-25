# Feature Specification: Ticket Management REST API

**Feature Branch**: `001-ticket-management-api`

**Created**: 2026-09-25

**Status**: Draft

**Input**: User description: "Build the ticket management REST API for a support ticket system: create, list, view, update, comment, search, filter, and an enforced status lifecycle. This is a backend-only feature — no UI."

## Clarifications

### Session 2026-09-25

- Q: What priority levels should tickets support? → A: LOW, MEDIUM, HIGH (3 levels)
- Q: When updating a ticket, must the client send every field or only the fields they want to change? → A: Partial update — client sends only fields to change; omitted fields stay as-is
- Q: Should title and description have maximum length limits enforced by validation? → A: No enforced max length (limited only by storage)

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Create and track a support ticket (Priority: P1)

A support agent opens a new ticket for an incoming issue, recording a title, description, priority, and the person assigned to work it. Later, anyone with API access can look the ticket up to see its current details.

**Why this priority**: Without creating and retrieving tickets, no other capability (commenting, searching, status changes) has anything to operate on. This is the foundation of the whole system.

**Independent Test**: Can be fully tested by calling the create endpoint with valid data, then calling the get-by-id endpoint and confirming the returned ticket matches what was submitted, defaults to status OPEN, and has a generated ID and creation timestamp.

**Acceptance Scenarios**:

1. **Given** no prior tickets exist, **When** a client submits a new ticket with title, description, priority, and assignee, **Then** the system creates the ticket, assigns it status OPEN, and returns the full ticket record including a unique ID.
2. **Given** a ticket was created, **When** a client requests that ticket by ID, **Then** the system returns its current title, description, priority, assignee, status, and timestamps.
3. **Given** a client submits a ticket with a blank title, **When** the create request is processed, **Then** the system rejects it with a structured validation error identifying the title field.

---

### User Story 2 - List, search, and filter tickets (Priority: P1)

A support agent or team lead wants to see all open tickets, find tickets mentioning a particular keyword, or narrow a list down to a specific status, so they can triage work without knowing individual ticket IDs.

**Why this priority**: Ticket volume makes lookups by ID alone impractical. Listing, search, and filtering are what make the system usable day-to-day, so they ship alongside creation as core P1 value.

**Independent Test**: Can be fully tested by creating several tickets with varying statuses and text content, then confirming the list endpoint returns all of them, the search endpoint returns only keyword matches, and the filter endpoint returns only tickets with the requested status.

**Acceptance Scenarios**:

1. **Given** multiple tickets exist, **When** a client requests the ticket list, **Then** the system returns all tickets in a paginated response.
2. **Given** tickets with different titles/descriptions exist, **When** a client searches using a keyword, **Then** the system returns only tickets whose title or description contains that keyword.
3. **Given** tickets exist in multiple statuses, **When** a client filters by a specific status, **Then** the system returns only tickets currently in that status.
4. **Given** a client filters by a status value that does not exist in the lifecycle, **When** the filter request is processed, **Then** the system rejects it with a structured error rather than silently returning an empty or unfiltered list.

---

### User Story 3 - Update ticket fields and reassign (Priority: P2)

As work progresses, a support agent needs to correct or refine a ticket's title/description, change its priority as urgency becomes clearer, or hand it off to a different assignee.

**Why this priority**: Tickets are rarely static after creation. Editing and reassignment are necessary for day-to-day operation, but the system is still usable in a limited fashion (create/read/list) without them, so they rank below the P1 stories.

**Independent Test**: Can be fully tested by creating a ticket, submitting an update with new field values (including a new assignee), and confirming a subsequent get-by-id reflects the changes.

**Acceptance Scenarios**:

1. **Given** an existing ticket, **When** a client submits a partial update changing only its title, description, or priority, **Then** the system persists the new values for the submitted fields, leaves all other fields unchanged, and returns the updated ticket.
2. **Given** an existing ticket, **When** a client updates its assignee, **Then** the system persists the new assignee and returns the updated ticket.
3. **Given** an update request contains an invalid value (e.g. blank title or unrecognized priority), **When** the update is processed, **Then** the system rejects it with a structured validation error and leaves the stored ticket unchanged.
4. **Given** a client requests an update for a ticket ID that does not exist, **When** the update is processed, **Then** the system returns a not-found error.

---

### User Story 4 - Enforce the ticket status lifecycle (Priority: P1)

A support agent moves a ticket through its lifecycle (starting work, resolving it, closing it, or cancelling it), and the system only allows the moves that make sense for a support workflow, rejecting anything else with a clear reason.

**Why this priority**: The enforced state machine is a explicit, named requirement of this feature and a core trust guarantee — invalid transitions (e.g. reopening a closed ticket) must never be allowed to silently succeed. This is core, not optional, so it is P1.

**Independent Test**: Can be fully tested by driving a ticket through every valid transition in sequence and confirming each succeeds, then attempting every invalid transition from each status and confirming each is rejected with a clear error and no status change.

**Acceptance Scenarios**:

1. **Given** a ticket is OPEN, **When** it is transitioned to IN_PROGRESS, **Then** the transition succeeds and the ticket's status becomes IN_PROGRESS.
2. **Given** a ticket is IN_PROGRESS, **When** it is transitioned to RESOLVED, **Then** the transition succeeds and the ticket's status becomes RESOLVED.
3. **Given** a ticket is RESOLVED, **When** it is transitioned to CLOSED, **Then** the transition succeeds and the ticket's status becomes CLOSED.
4. **Given** a ticket is OPEN, **When** it is transitioned to CANCELLED, **Then** the transition succeeds and the ticket's status becomes CANCELLED.
5. **Given** a ticket is IN_PROGRESS, **When** it is transitioned to CANCELLED, **Then** the transition succeeds and the ticket's status becomes CANCELLED.
6. **Given** a ticket is CLOSED, **When** a transition to OPEN (or any other status) is requested, **Then** the system rejects it with a structured error and the ticket's status remains CLOSED.
7. **Given** a ticket is RESOLVED, **When** a transition to OPEN is requested, **Then** the system rejects it with a structured error and the ticket's status remains RESOLVED.
8. **Given** a ticket is CANCELLED, **When** any transition is requested, **Then** the system rejects it with a structured error and the ticket's status remains CANCELLED.

---

### User Story 5 - Add comments to a ticket (Priority: P2)

A support agent records notes, updates, or communication history directly on a ticket so anyone reviewing it later has full context.

**Why this priority**: Comments enrich the ticket record and support collaboration, but the system delivers value (tracking, searching, lifecycle) without them, placing this below the P1 stories.

**Independent Test**: Can be fully tested by creating a ticket, submitting a comment, and confirming the ticket's detail view includes that comment with its content and timestamp.

**Acceptance Scenarios**:

1. **Given** an existing ticket, **When** a client adds a comment with text content, **Then** the system stores the comment against that ticket and it appears when the ticket is retrieved.
2. **Given** a client submits a comment with blank content, **When** the request is processed, **Then** the system rejects it with a structured validation error.
3. **Given** a client submits a comment for a ticket ID that does not exist, **When** the request is processed, **Then** the system returns a not-found error.

---

### Edge Cases

- What happens when a client requests a ticket, filters, or comments against a ticket ID that does not exist? → System returns a structured not-found error, never a silent empty success.
- What happens when a client submits a malformed request body (wrong types, missing required fields, unknown fields)? → System rejects with a structured 400-style error identifying the offending field(s).
- What happens when the application restarts? → All previously created tickets, their field values, status, and comments MUST still be present afterward (data survives restart).
- What happens when a search keyword matches nothing? → System returns an empty result list, not an error.
- What happens when list/search results are large? → Results MUST be paginated rather than returned as one unbounded set.
- What happens when a client attempts a transition not in the defined lifecycle graph (including any attempt to move a ticket back to OPEN from any other status)? → Rejected with a clear, structured error; ticket status is unchanged.
- What happens when a client requests a transition to the ticket's own current status (e.g. OPEN → OPEN)? → Treated as an invalid transition and rejected with a structured error, same as any other transition not explicitly listed as valid; status is unchanged.
- What happens when a client submits a partial update with no fields at all? → Treated as a no-op: the system returns 200 with the ticket's current, unchanged state rather than an error.
- What happens when two clients attempt to transition or update the same ticket at the same time? → The system MUST NOT corrupt data (each write fully succeeds or fully fails); the last write to complete wins. Detecting and rejecting concurrent conflicts (optimistic locking) is not required for this feature.
- What happens when a search keyword (`q`) is supplied but empty? → Treated the same as no keyword filter: no keyword-based narrowing is applied.
- What happens when `page` or `size` is negative or non-numeric? → Rejected with a structured validation error (400), the same as any other malformed input.
- What happens when a ticket ID in the URL path is not a validly formatted identifier (as opposed to a validly formatted identifier that does not exist)? → Rejected with a structured validation error (400), distinct from the not-found error (404) used when the identifier is well-formed but no matching ticket exists.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST allow creation of a ticket with a title, description, priority, and assignee, and MUST assign it status OPEN and a unique identifier at creation time.
- **FR-002**: System MUST allow retrieval of the full list of tickets, returned in a paginated form.
- **FR-003**: System MUST allow retrieval of a single ticket's full details by its unique identifier, including its comments.
- **FR-004**: System MUST allow partial updates to a ticket's title, description, priority, and assignee — a client MAY submit only the fields it wants to change, and fields omitted from the request MUST remain unchanged. Field updates are independent of changing status.
- **FR-005**: System MUST allow adding a comment (text content) to an existing ticket, recorded with a timestamp.
- **FR-006**: System MUST allow searching tickets by a free-text keyword that matches against ticket title and/or description.
- **FR-007**: System MUST allow filtering the ticket list by status, returning only tickets currently in the requested status.
- **FR-008**: System MUST persist all ticket, and comment data such that it remains available after an application restart.
- **FR-009**: System MUST enforce the following ticket status lifecycle and reject any transition not explicitly listed: OPEN → IN_PROGRESS, IN_PROGRESS → RESOLVED, RESOLVED → CLOSED, OPEN → CANCELLED, IN_PROGRESS → CANCELLED. All other transitions (including any transition back to OPEN from IN_PROGRESS, RESOLVED, CLOSED, or CANCELLED) MUST be rejected.
- **FR-010**: System MUST validate all incoming request data (required fields, field formats, allowed values such as priority/status enums) before acting on it, and MUST reject invalid input without partially applying the change.
- **FR-011**: System MUST return a structured, consistent error response for every rejected request (validation failure, not-found, invalid status transition), including a machine-readable error code, a human-readable message, and enough detail to identify the offending field(s) or reason when applicable.
- **FR-012**: System MUST return a not-found error when an operation references a ticket ID that does not exist.
- **FR-013**: System MUST reject filter requests that specify a status value outside the defined lifecycle, rather than ignoring the filter.
- **FR-014**: System MUST reject a requested status transition whose target equals the ticket's current status, using the same structured error as any other invalid transition.
- **FR-015**: System MUST treat a partial update request containing no fields as a no-op and return the ticket's current state unchanged, rather than an error.
- **FR-016**: System MUST reject requests where a path identifier (e.g. ticket ID) is not a validly formatted identifier, using a structured validation error distinct from the not-found error used for a well-formed but non-existent identifier.
- **FR-017**: System MUST reject list/search requests where `page` or `size` is negative or non-numeric, using a structured validation error.
- **FR-018**: System MUST treat an empty search keyword the same as no keyword filter (no keyword-based narrowing applied).

### Key Entities

- **Ticket**: A support issue tracked through resolution. Key attributes: unique identifier, title, description, priority (one of LOW, MEDIUM, HIGH), assignee, status (one of OPEN, IN_PROGRESS, RESOLVED, CLOSED, CANCELLED), creation timestamp, last-updated timestamp. Has many Comments.
- **Comment**: A note attached to a ticket. Key attributes: unique identifier, parent ticket reference, text content, creation timestamp.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A new ticket can be created and then retrieved by ID, with all submitted fields matching, in a single round trip each (2 API calls total).
- **SC-002**: 100% of the six lifecycle transitions defined as valid (OPEN→IN_PROGRESS, IN_PROGRESS→RESOLVED, RESOLVED→CLOSED, OPEN→CANCELLED, IN_PROGRESS→CANCELLED) succeed when attempted from the correct starting status.
- **SC-003**: 100% of attempted invalid transitions — including every attempt to move any non-OPEN status back to OPEN — are rejected with a structured error and leave the ticket's status unchanged.
- **SC-004**: 100% of malformed or invalid create/update/comment requests receive a structured error response identifying the problem, with zero partial writes to storage.
- **SC-005**: Tickets and comments created before an application restart are still retrievable, unchanged, after restart.
- **SC-006**: A keyword search returns only tickets whose title or description contains that keyword, with zero unrelated tickets in the result set.
- **SC-007**: A status filter returns only tickets in the requested status, with zero tickets of other statuses in the result set.

## Assumptions

- Priority is a fixed set of three levels: LOW, MEDIUM, HIGH.
- Title and description have no enforced maximum length beyond what the underlying storage supports; validation checks presence/non-blank, not length caps.
- Assignee is recorded as a simple identifying value (e.g. a name or existing user identifier) with no separate user-management feature — this spec does not create or validate a roster of assignable users, since authentication/authorization and multi-tenancy are explicitly out of scope.
- Comments are plain text with no authorship/user-identity field required, since auth is out of scope for this feature.
- "Keyword search" means a case-insensitive substring/contains match against title and description; no fuzzy or ranked relevance search is required.
- List, search, and filter results are paginated using reasonable default page size behavior; exact page size is an implementation default, not a fixed business rule.
- No feature in this spec requires authentication, authorization, or multi-tenant data isolation, per explicit scope exclusion in the input.
- The AI assistant (RAG) and any UI are separate features and are not built or wired up here; this feature only provides the REST API and persistence they will later depend on.
- Concurrent writes to the same ticket use last-write-wins semantics; optimistic locking / conflict detection is not required, since this feature has no multi-user auth model to make "conflicting user" a meaningful concept.
- Malformed ticket ID format (not a well-formed identifier) is a validation error (400), separate from a well-formed identifier that simply doesn't exist (404).
