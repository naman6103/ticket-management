# Feature Specification: Ticket Management Web UI

**Feature Branch**: `002-ticket-management-ui`

**Created**: 2026-09-25

**Status**: Draft

**Input**: User description: "Build the web UI for ticket management, consuming the Feature 1 Ticket Management API. No new backend logic — this feature is purely the frontend, and it does NOT include the AI assistant (that's Feature 4, built after Feature 3's API exists). Requirements: ticket list view with status filter and keyword search; ticket creation form (title, description, priority, assignee); ticket detail view showing all fields, status, and comment history; ability to edit title, description, priority, and assignee; ability to change assignee; ability to add comments; status transition controls that only offer valid next states and surface backend rejection reasons; meaningful, specific error messages for backend validation failures. Out of scope: any new backend logic — only calls the API specified in Feature 1's api-contract.md. Build the layout so an AI Q&A panel can be added later without a rewrite (shared navigation shell, shared API-client pattern, shared error-display component), but do not build the panel itself here."

## Clarifications

### Session 2026-09-25

- Q: When ticket list has more results than fit on one page, how should the UI let the user move through them? → A: Infinite scroll — next page auto-loads as user scrolls near bottom.
- Q: Should the assignee field (on create and edit) be free-text entry, or a picker limited to a known set of people? → A: A picker backed by a real list of known assignees, sourced from the backend. Since Feature 1's API has no endpoint for this today, this feature specifies one small new read-only backend endpoint (list of distinct known assignees) as an explicit, narrow exception to the "no new backend logic" constraint — everything else about ticket management still comes from Feature 1's existing API untouched.
- Q: Should the new assignee-list endpoint return distinct assignee values already seen on existing tickets, or a separately maintained roster of people? → A: Just a list of all the assignees already in the system — derived from distinct assignee values on existing tickets, not a separately managed roster. A brand-new deployment with zero tickets has no assignees to pick from until at least one ticket exists with an assignee value.

### Session 2026-09-25 (post-implementation)

- Q: The initial implementation shipped with unstyled semantic HTML (no visual design was specified in the original request). What visual design should the UI use? → A: A generic Jira-inspired look (blue top nav, card-style ticket rows, colored status pills, clean forms), built with plain CSS — no specific external design file/reference was provided; this is a reasonable-default interpretation, not a pixel-exact clone of Jira.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Browse and find tickets (Priority: P1)

A support agent opens the ticket UI to see what's open, narrow the list down by status, and search by keyword to find a specific ticket without scrolling through everything.

**Why this priority**: Without a working list/search/filter view, no other screen is reachable in practice — this is the entry point to the whole application and the first thing any user sees.

**Independent Test**: Can be fully tested by loading the ticket list, applying a status filter, and typing a search keyword, and confirming the displayed tickets match the filter/search criteria — delivers value even before creation or editing exists, since it can be tested against tickets seeded via the API directly.

**Acceptance Scenarios**:

1. **Given** tickets exist with different statuses, **When** the agent opens the list view, **Then** all tickets are shown with their title, status, priority, and assignee visible without opening each one.
2. **Given** the agent selects a status filter (e.g. "OPEN"), **When** the filter is applied, **Then** only tickets with that status are shown.
3. **Given** the agent types a keyword into the search box, **When** the search is submitted, **Then** only tickets whose title or description contain that keyword (case-insensitive) are shown.
4. **Given** a status filter and a search keyword are both active, **When** the list is refreshed, **Then** results satisfy both conditions together.
5. **Given** no tickets match the current filter/search combination, **When** the list loads, **Then** the UI shows a clear "no tickets found" state rather than an empty blank area.
6. **Given** more tickets match the current filter/search than fit in one page, **When** the agent scrolls near the bottom of the list, **Then** the next page of results loads automatically and appends to the list without a manual "next page" action.

---

### User Story 2 - Create a new ticket (Priority: P1)

A support agent receives a new issue and needs to log it as a ticket with a title, description, priority, and assignee.

**Why this priority**: Ticket creation is the second foundational capability — without it the list view has nothing new to show, and it's independently testable and valuable on its own (a working intake form is useful even before editing/detail views exist).

**Independent Test**: Can be fully tested by filling out the creation form with valid values, submitting it, and confirming a new ticket appears in the list with status "OPEN" and the entered field values.

**Acceptance Scenarios**:

1. **Given** the agent fills in title, description, priority, and assignee with valid values, **When** the form is submitted, **Then** a new ticket is created with status "OPEN" and the agent is shown the created ticket (e.g. navigated to its detail view).
2. **Given** the agent opens the creation form, **When** the assignee field is shown, **Then** it presents a searchable picker of known assignees sourced from the backend, and also accepts a typed name not yet in that list.
3. **Given** the agent leaves a required field (title, description, priority, or assignee) blank, **When** the form is submitted, **Then** the UI shows a specific message identifying which field is invalid and why, and no ticket is created.
4. **Given** the backend rejects the submission for a reason not caught by client-side checks, **When** the response returns, **Then** the UI displays the backend's field-level validation message(s) rather than a generic failure message.

---

### User Story 3 - View full ticket detail and history (Priority: P1)

A support agent opens a specific ticket to see its full description, current status, and the complete history of comments left on it.

**Why this priority**: Detail view is where editing, commenting, and status transitions all happen — it's the hub screen referenced by both the list view and (later) the AI assistant, so it must exist as its own independently valuable, testable screen.

**Independent Test**: Can be fully tested by opening a ticket (seeded via the API, with comments already attached) and confirming every field, the current status, and every comment in order are visible.

**Acceptance Scenarios**:

1. **Given** a ticket with a title, description, priority, assignee, category, and status, **When** its detail view is opened, **Then** all of those fields are displayed.
2. **Given** a ticket has one or more comments, **When** its detail view is opened, **Then** every comment is listed in chronological order with its content and timestamp.
3. **Given** a ticket has zero comments, **When** its detail view is opened, **Then** the UI shows a clear "no comments yet" state rather than an empty blank area.
4. **Given** an id for a ticket that does not exist, **When** its detail view is requested, **Then** the UI shows a clear "ticket not found" message instead of a blank or broken page.

---

### User Story 4 - Edit ticket fields and reassign (Priority: P2)

A support agent needs to correct or update a ticket's title, description, priority, or assignee as the situation changes (e.g. escalating priority, or reassigning to a different person).

**Why this priority**: Builds directly on the detail view (P1) and is needed for day-to-day ticket upkeep, but the system is already useful for browsing and creating without it, so it ranks below the P1 stories.

**Independent Test**: Can be fully tested by opening a ticket's detail view, changing one or more editable fields (including assignee), saving, and confirming the detail view reflects the new values.

**Acceptance Scenarios**:

1. **Given** a ticket is open in edit mode, **When** the agent changes the title, description, priority, and/or assignee and saves, **Then** the detail view reflects the updated values and only the changed fields are affected (fields not touched keep their prior values).
2. **Given** the agent clears a required field (title, description, or assignee) and saves, **When** the backend rejects the change, **Then** the UI shows the specific field-level reason and the ticket's stored values remain unchanged.
3. **Given** the agent changes only the assignee, **When** the change is saved, **Then** the ticket's assignee updates and no other field is affected.
4. **Given** a ticket is open in edit mode, **When** the assignee field is shown, **Then** it presents the same backend-sourced searchable picker used on creation, also accepting a typed name not yet in that list.

---

### User Story 5 - Add a comment (Priority: P2)

A support agent adds a note to a ticket to record progress, findings, or context for whoever looks at it next.

**Why this priority**: Adding comments is a frequent, valuable action but depends on the detail view (P1) already existing; it doesn't block browsing, creating, or viewing tickets, so it's ranked P2 alongside editing.

**Independent Test**: Can be fully tested by opening a ticket, submitting a comment, and confirming it appears at the end of that ticket's comment history without a full page reload losing context.

**Acceptance Scenarios**:

1. **Given** a ticket's detail view is open, **When** the agent types a comment and submits it, **Then** the comment appears in the ticket's comment history with its content and a timestamp.
2. **Given** the agent submits an empty or blank comment, **When** the submission is attempted, **Then** the UI shows a specific message that comment content is required and no comment is added.

---

### User Story 6 - Transition ticket status safely (Priority: P2)

A support agent moves a ticket through its lifecycle (e.g. from "OPEN" to "IN_PROGRESS", or "RESOLVED" to "CLOSED") without needing to know or guess which transitions are allowed.

**Why this priority**: Status transitions are central to ticket workflow but, like editing and commenting, build on the detail view and don't block the more foundational browse/create/view stories.

**Independent Test**: Can be fully tested by opening tickets in different statuses and confirming the offered transition options match what the state machine allows for each status, then performing a transition and confirming the new status is reflected.

**Acceptance Scenarios**:

1. **Given** a ticket in a given status, **When** its detail view is opened, **Then** only the statuses reachable from the current status are offered as transition options (e.g. a "CLOSED" or "CANCELLED" ticket offers no further transitions).
2. **Given** the agent selects a valid next status and confirms, **When** the transition is submitted, **Then** the ticket's status updates and is reflected immediately in the detail view.
3. **Given** the UI is out of sync with the backend and an offered transition is rejected as invalid, **When** the rejection is returned, **Then** the UI surfaces the backend's specific rejection reason (naming the current and attempted status) rather than a generic error.

---

### Edge Cases

- What happens when the backend is unreachable or times out while loading the list, a ticket detail, or submitting a form? The UI must show a clear, specific "couldn't reach the server" message and allow the user to retry, not a silent failure or blank screen.
- How does the system handle a search/filter combination that returns zero results? Shown as an explicit empty state (see User Story 1, Scenario 5), not an error.
- How does the system handle opening a ticket that was deleted or never existed? Shown as an explicit "not found" state (see User Story 3, Scenario 4), not a blank or broken page.
- How does the system handle an unrecognized/unknown filter or status value being sent (e.g. a stale UI build vs. a newer backend enum)? The UI must surface the backend's specific rejection message rather than silently dropping the filter.
- What happens if two agents edit or transition the same ticket at nearly the same time? The second save is expected to either succeed against the latest state or fail with the backend's specific error (e.g. an invalid-transition or not-found response); the UI must show whichever specific message the backend returns.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The UI MUST display a list of tickets showing, at minimum, each ticket's title, status, priority, and assignee, without requiring the user to open each ticket individually.
- **FR-002**: The UI MUST let users filter the ticket list by status.
- **FR-003**: The UI MUST let users search the ticket list by keyword, matching against ticket title and/or description.
- **FR-004**: The UI MUST let users combine a status filter and a keyword search at the same time.
- **FR-004a**: The UI MUST load additional pages of ticket-list results automatically (infinite scroll) as the user scrolls near the bottom of the list, rather than requiring a manual page-number or "load more" action.
- **FR-005**: The UI MUST provide a ticket creation form capturing title, description, priority, and assignee, and MUST create the ticket via the ticket management API on submission.
- **FR-005a**: The assignee field on both the creation form and the edit form MUST present a searchable picker (combobox) of known assignees, sourced from a backend list rather than hardcoded in the UI, and MUST also let the user type a name not yet in that list (e.g. the very first assignee ever used, or a genuinely new person) rather than blocking the action.
- **FR-005b**: A small, read-only backend endpoint MUST be added to support FR-005a, returning the distinct assignee values already present across existing tickets (not a separately maintained roster). This is a narrow, explicit exception to this feature's "no new backend logic" boundary; no other new backend endpoints or business rules are introduced.
- **FR-005c**: When the known-assignees list is empty (e.g. a brand-new deployment with no tickets yet), the assignee picker MUST show an explicit "no assignees yet — type a name to add one" state and MUST still accept a typed value, rather than blocking ticket creation or showing an empty/broken dropdown.
- **FR-006**: The UI MUST provide a ticket detail view showing all ticket fields (title, description, priority, assignee, category, status, created/updated timestamps) and the ticket's full comment history.
- **FR-007**: The UI MUST let users edit a ticket's title, description, priority, and assignee, and save only the fields that were changed.
- **FR-008**: The UI MUST let users change a ticket's assignee independently of other field edits, using the same assignee picker as FR-005a.
- **FR-009**: The UI MUST let users add a comment to a ticket and display it in that ticket's comment history immediately after it is successfully added.
- **FR-010**: The UI MUST offer status transition controls that show only the next statuses that are valid from the ticket's current status, per the ticket state machine.
- **FR-011**: The UI MUST NOT invent, perform, or simulate any ticket business logic itself (e.g. validation of what fields are required, or which status transitions are legal); it MUST rely on the backend API for all such decisions and reflect the backend's authoritative response.
- **FR-012**: When the backend rejects a request (creation, edit, comment, or transition) with a validation or conflict error, the UI MUST display the backend's specific error message and, where present, its field-level detail — not a generic failure message.
- **FR-013**: When a status transition is rejected as invalid, the UI MUST display the backend's rejection reason naming both the current status and the attempted target status.
- **FR-014**: The UI MUST show explicit empty/not-found states for: an empty ticket list (or empty filtered/search result), a ticket detail requested for a non-existent ticket, and a ticket with zero comments.
- **FR-015**: The UI MUST be structured around a shared navigation shell, a shared API-client pattern for talking to the backend, and a shared error-display component, so that a future AI Q&A panel (a separate feature) can be added without restructuring the existing screens.
- **FR-016**: The UI MUST NOT implement or expose any AI assistant / Q&A functionality; that capability is explicitly out of scope for this feature.
- **FR-017**: The UI MUST use a consistent, Jira-inspired visual design across every screen: a persistent colored top navigation bar, card-style surfaces for lists/detail/forms, and color-coded status pills distinguishing each `TicketStatus` value. Implemented via a single global stylesheet (no per-feature ad-hoc styling), so visual consistency doesn't depend on each new screen re-implementing its own look.

### Key Entities

- **Ticket**: A support issue tracked through its lifecycle. Represented in the UI with title, description, priority, assignee, category, status, creation/update timestamps, and its associated comments. Sourced entirely from the backend ticket management API; the UI does not define or duplicate this data model, only displays and edits it through the API.
- **Comment**: A timestamped note attached to a single ticket, containing free-text content. Displayed in chronological order as part of a ticket's detail view.
- **Status transition**: A request to move a ticket from its current status to a specific target status. The set of valid target statuses for a given current status is defined by the backend state machine, not the UI; the UI only reflects and requests transitions the backend has already defined as reachable.
- **Assignee**: A known person tickets can be assigned to. Sourced from a new small read-only backend list (FR-005b) of distinct assignee values already used on existing tickets, so the create/edit pickers reflect real, current assignees; a new/first assignee name can still be typed directly when it isn't yet in that list.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user can go from opening the application to viewing a specific ticket's full detail (fields, status, and comments) in under 30 seconds without external help.
- **SC-002**: A user can create a new ticket and see it appear in the ticket list in under 1 minute.
- **SC-003**: 100% of backend validation, not-found, and conflict responses result in a UI message that names the specific field or reason involved, with zero generic "something went wrong" messages shown for these cases.
- **SC-004**: 100% of status transition controls shown to a user are transitions the backend actually allows from that ticket's current status (no offered transition is ever rejected by the backend as invalid under normal, single-user conditions).
- **SC-005**: A user can filter by status, search by keyword, or combine both, and see correctly matching results in a single action (one filter selection and/or one search submission), without needing to reload the page manually.
- **SC-006**: A user can add a comment to a ticket and see it reflected in that ticket's history without a full page reload.
- **SC-007**: A user can always complete ticket creation or reassignment by either picking an existing assignee from the backend-sourced list or typing a new one, with zero cases where an empty/unavailable assignee list blocks the action.

## Assumptions

- The backend Ticket Management API (Feature 1, `specs/001-ticket-management-api/api-contract.md`) is complete, stable, and reachable, including its structured error response shape and its ticket state machine; this feature consumes it as-is and introduces no new ticket business rules. The single exception is one new small, read-only endpoint for listing known assignees (FR-005b), added because Feature 1's API has no such endpoint and the assignee picker (FR-005a) needs a real backend-driven source. That list is derived from distinct assignee values already on existing tickets — there is no separate assignee roster/entity to maintain, and the picker still accepts a typed value for names not yet in the list (FR-005c).
- The set of valid status transitions is authoritative on the backend; the UI is expected to mirror the state machine described in Feature 1 to decide which transition controls to display, but always treats the backend's response as the final word (an offered transition can still be rejected if backend state has changed since the UI last loaded it).
- Users are internal support agents (not end customers) operating from a desktop browser during business use; no distinct customer-facing or mobile-specific experience is in scope.
- No new authentication/authorization system is introduced by this feature; access control, if any, is out of scope and assumed to be handled the same way it is for the existing API (or left open, matching Feature 1's current scope).
- "Category" is displayed and editable wherever other ticket fields are, consistent with it already being a field on the ticket in Feature 1's API contract, even though it wasn't separately called out in the requirements list.
- The AI Q&A panel referenced as future work (Feature 4) is not designed or scaffolded beyond ensuring the navigation shell, API-client pattern, and error-display component are shared/reusable; no placeholder UI for it is built in this feature.
