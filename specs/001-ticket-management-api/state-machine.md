# Ticket Status State Machine

Source of truth: spec.md FR-009 and User Story 4. This table is what `TicketServiceImpl` implements literally as its transition map, and what the integration test suite (test-strategy.md) must cover exhaustively — every row below is one test case.

## States

`OPEN`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`, `CANCELLED`

## Valid transitions

| From | Event / Target | To | HTTP outcome |
|---|---|---|---|
| OPEN | start work | IN_PROGRESS | `200 OK`, status updated |
| IN_PROGRESS | resolve | RESOLVED | `200 OK`, status updated |
| RESOLVED | close | CLOSED | `200 OK`, status updated |
| OPEN | cancel | CANCELLED | `200 OK`, status updated |
| IN_PROGRESS | cancel | CANCELLED | `200 OK`, status updated |

## Rejected transitions (exhaustive)

Every pair not listed above is invalid. Enumerated explicitly so the test matrix has no ambiguity:

| From | To | Result |
|---|---|---|
| OPEN | RESOLVED | `409 INVALID_TRANSITION` |
| OPEN | CLOSED | `409 INVALID_TRANSITION` |
| OPEN | OPEN | `409 INVALID_TRANSITION` (no-op transitions are not allowed) |
| IN_PROGRESS | OPEN | `409 INVALID_TRANSITION` |
| IN_PROGRESS | CLOSED | `409 INVALID_TRANSITION` |
| IN_PROGRESS | IN_PROGRESS | `409 INVALID_TRANSITION` |
| RESOLVED | OPEN | `409 INVALID_TRANSITION` |
| RESOLVED | IN_PROGRESS | `409 INVALID_TRANSITION` |
| RESOLVED | CANCELLED | `409 INVALID_TRANSITION` |
| RESOLVED | RESOLVED | `409 INVALID_TRANSITION` |
| CLOSED | OPEN | `409 INVALID_TRANSITION` |
| CLOSED | IN_PROGRESS | `409 INVALID_TRANSITION` |
| CLOSED | RESOLVED | `409 INVALID_TRANSITION` |
| CLOSED | CANCELLED | `409 INVALID_TRANSITION` |
| CLOSED | CLOSED | `409 INVALID_TRANSITION` |
| CANCELLED | OPEN | `409 INVALID_TRANSITION` |
| CANCELLED | IN_PROGRESS | `409 INVALID_TRANSITION` |
| CANCELLED | RESOLVED | `409 INVALID_TRANSITION` |
| CANCELLED | CLOSED | `409 INVALID_TRANSITION` |
| CANCELLED | CANCELLED | `409 INVALID_TRANSITION` |
| any status | value not in {OPEN, IN_PROGRESS, RESOLVED, CLOSED, CANCELLED} | `400 VALIDATION_FAILED` (unrecognized enum value, not a transition error) |

**Rule**: `CLOSED` and `CANCELLED` are terminal — no transition out of either status is ever valid.

## Enforcement contract

- The transition check runs in `TicketServiceImpl`, before any persistence write, using a static `from → allowed-to` lookup (see research.md "State machine enforcement location").
- On rejection, the ticket's stored `status` is left completely unchanged (SC-003) — the service must fail fast before calling the repository save.
- Rejection uses the shared structured error shape (api-contract.md) with `code: "INVALID_TRANSITION"` and HTTP `409`, distinct from `400 VALIDATION_FAILED` used for unrecognized status values or malformed request bodies.
