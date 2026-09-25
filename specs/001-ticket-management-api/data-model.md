# Data Model: Ticket Management REST API

Derived from spec.md Key Entities, Functional Requirements, and the plan's forward-compatible RAG metadata request.

## Ticket

Represents a support issue tracked through resolution.

| Field | Type | Required | Notes |
|---|---|---|---|
| `id` | UUID | generated | Primary key, assigned at creation |
| `title` | String | yes | Non-blank (FR-001, spec Assumptions: no max length enforced) |
| `description` | String | yes | Non-blank (spec Assumptions: no max length enforced) |
| `priority` | enum `Priority` | yes | One of `LOW`, `MEDIUM`, `HIGH` (per Clarifications) |
| `assignee` | String | yes | Simple identifying value (name or existing user identifier); no roster validation (spec Assumptions) |
| `status` | enum `TicketStatus` | system-managed | One of `OPEN`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`, `CANCELLED`; defaults to `OPEN` at creation (FR-001); mutated only via the transition endpoint, never via the general field-update endpoint |
| `category` | String | no | Nullable, unvalidated. Added for Feature 3 (RAG) metadata filtering; not set or required by any user story in this feature |
| `createdAt` | Instant/timestamp | system-managed | Set at creation, immutable |
| `updatedAt` | Instant/timestamp | system-managed | Updated on any field change, comment addition, or status transition |

**Relationships**: One Ticket has many Comments (1:N, Ticket owns the relationship; Comment is deleted if its Ticket is deleted — no cascade-delete endpoint exists in this feature, so this is a schema-level constraint only).

**Validation rules** (enforced at the DTO/controller boundary per FR-010 and constitution Principle IV):
- `title`: not blank
- `description`: not blank
- `priority`: must be one of the three enum values; unrecognized values rejected with `400 VALIDATION_FAILED`
- `assignee`: not blank
- `category`: no validation (optional, freeform, may be absent/null)
- `status`: never accepted on create or on the general update endpoint — always `OPEN` on create; changed only through `POST /tickets/{id}/transitions` (see state-machine.md)

## Comment

Represents a note attached to a ticket.

| Field | Type | Required | Notes |
|---|---|---|---|
| `id` | UUID | generated | Primary key |
| `ticketId` | UUID | yes | Foreign key to Ticket; must reference an existing ticket (FR-012 not-found otherwise) |
| `content` | String | yes | Non-blank (spec User Story 5, Acceptance Scenario 2) |
| `createdAt` | Instant/timestamp | system-managed | Set at creation, immutable |

**Validation rules**:
- `content`: not blank
- `ticketId` (path parameter): must resolve to an existing Ticket, else `404 TICKET_NOT_FOUND`

No authorship/user-identity field, per spec Assumptions (auth is out of scope for this feature).

## State field lifecycle (summary — full table in state-machine.md)

`status` transitions are the only mutation path that is graph-constrained. All other Ticket fields (`title`, `description`, `priority`, `assignee`, `category`) can be changed independently of `status` via partial update, at any point in the ticket's lifecycle (the spec does not restrict field edits to particular statuses).
