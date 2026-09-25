# API Contract: Ticket Management REST API

Base path: `/api/v1`. Follows `rules/api-standards.md` (naming, status codes, pagination, error shape) throughout.

## Shared types

### Enums

- `Priority`: `LOW` | `MEDIUM` | `HIGH`
- `TicketStatus`: `OPEN` | `IN_PROGRESS` | `RESOLVED` | `CLOSED` | `CANCELLED`

### TicketResponse

```json
{
  "id": "3f2a1e10-...-uuid",
  "title": "Login page returns 500",
  "description": "Users see a 500 error after submitting the login form.",
  "priority": "HIGH",
  "assignee": "jane.doe",
  "status": "OPEN",
  "category": null,
  "createdAt": "2026-09-25T09:00:00Z",
  "updatedAt": "2026-09-25T09:00:00Z",
  "comments": [
    {
      "id": "9c1b...-uuid",
      "content": "Reproduced on staging.",
      "createdAt": "2026-09-25T09:15:00Z"
    }
  ]
}
```

`comments` is included on the single-ticket detail response only (`GET /tickets/{id}`); list/search/filter responses return tickets without the `comments` array to keep list payloads light (`comments` omitted, not null, on those endpoints).

### CommentResponse

```json
{
  "id": "9c1b...-uuid",
  "ticketId": "3f2a1e10-...-uuid",
  "content": "Reproduced on staging.",
  "createdAt": "2026-09-25T09:15:00Z"
}
```

### PageResponse<T> (list/search/filter results)

```json
{
  "content": [ /* array of TicketResponse, comments omitted */ ],
  "page": 0,
  "size": 20,
  "totalElements": 42,
  "totalPages": 3
}
```

### ErrorResponse (all failures, every endpoint)

```json
{
  "timestamp": "2026-09-25T09:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "path": "/api/v1/tickets",
  "details": [
    { "field": "title", "rejectedValue": "", "message": "must not be blank" }
  ]
}
```

`details` is omitted (or `[]`) when not applicable (e.g. not-found, invalid-transition errors carry no field-level details).

Error codes used by this feature: `VALIDATION_FAILED` (400), `TICKET_NOT_FOUND` (404), `INVALID_TRANSITION` (409), `UNKNOWN_FILTER` (400, unrecognized query parameter/status value).

---

## Endpoints

### `POST /api/v1/tickets` — Create a ticket

**Request body**:

```json
{
  "title": "Login page returns 500",
  "description": "Users see a 500 error after submitting the login form.",
  "priority": "HIGH",
  "assignee": "jane.doe",
  "category": null
}
```

- `title`, `description`, `assignee`: required, not blank
- `priority`: required, one of `Priority`
- `category`: optional, freeform string or omitted

**Responses**:
| Status | Condition | Body |
|---|---|---|
| `201 Created` | Success | `TicketResponse` (status `OPEN`, generated `id`); `Location: /api/v1/tickets/{id}` header set |
| `400 Bad Request` | Missing/blank required field, invalid `priority` value, malformed JSON | `ErrorResponse` with `code: VALIDATION_FAILED` and `details[]` per field |

---

### `GET /api/v1/tickets` — List / search / filter tickets

**Query parameters** (all optional, combinable):

| Param | Type | Behavior |
|---|---|---|
| `q` | string | Free-text keyword; case-insensitive substring match against `title` OR `description` |
| `status` | `TicketStatus` | Exact match; unrecognized value → `400 UNKNOWN_FILTER` |
| `page` | int, default `0` | Zero-based page index |
| `size` | int, default `20` | Page size (server-enforced max, e.g. 100) |
| `sort` | string | `property,asc\|desc`; unknown property → `400` |

**Responses**:
| Status | Condition | Body |
|---|---|---|
| `200 OK` | Always (including zero matches) | `PageResponse<TicketResponse>` |
| `400 Bad Request` | `status` value not in the `TicketStatus` enum | `ErrorResponse` with `code: UNKNOWN_FILTER` |

---

### `GET /api/v1/tickets/{id}` — Get ticket detail

**Responses**:
| Status | Condition | Body |
|---|---|---|
| `200 OK` | Ticket exists | `TicketResponse` including `comments[]` |
| `404 Not Found` | No ticket with that `id` | `ErrorResponse` with `code: TICKET_NOT_FOUND` |

---

### `PATCH /api/v1/tickets/{id}` — Partial update

**Request body** (all fields optional; only present fields are changed — see research.md "Partial update semantics"):

```json
{
  "title": "Updated title",
  "priority": "MEDIUM",
  "assignee": "john.smith"
}
```

- Any field present MUST pass the same validation as on create (not blank; `priority` must be a valid enum value if present)
- `status` is NOT accepted on this endpoint — use the transitions endpoint below. If present in the body, it is rejected as an unknown/unsupported field (`400 VALIDATION_FAILED`).
- Fields omitted from the body are left unchanged.

**Responses**:
| Status | Condition | Body |
|---|---|---|
| `200 OK` | Success | Updated `TicketResponse` |
| `400 Bad Request` | A present field fails validation, or `status` is included | `ErrorResponse` with `code: VALIDATION_FAILED` |
| `404 Not Found` | No ticket with that `id` | `ErrorResponse` with `code: TICKET_NOT_FOUND` |

---

### `POST /api/v1/tickets/{id}/transitions` — Change status

**Request body**:

```json
{ "targetStatus": "IN_PROGRESS" }
```

**Responses**:
| Status | Condition | Body |
|---|---|---|
| `200 OK` | Transition is valid from the ticket's current status (state-machine.md) | Updated `TicketResponse` with new `status` |
| `400 Bad Request` | `targetStatus` missing or not a valid `TicketStatus` value | `ErrorResponse` with `code: VALIDATION_FAILED` |
| `404 Not Found` | No ticket with that `id` | `ErrorResponse` with `code: TICKET_NOT_FOUND` |
| `409 Conflict` | `targetStatus` is not reachable from the ticket's current status | `ErrorResponse` with `code: INVALID_TRANSITION`, `message` naming both the current status and the rejected target |

---

### `POST /api/v1/tickets/{id}/comments` — Add a comment

**Request body**:

```json
{ "content": "Reproduced on staging." }
```

- `content`: required, not blank

**Responses**:
| Status | Condition | Body |
|---|---|---|
| `201 Created` | Success | `CommentResponse` |
| `400 Bad Request` | `content` missing/blank | `ErrorResponse` with `code: VALIDATION_FAILED` |
| `404 Not Found` | No ticket with that `id` | `ErrorResponse` with `code: TICKET_NOT_FOUND` |

---

## Endpoint summary

| Method | Path | Purpose | Success |
|---|---|---|---|
| POST | `/api/v1/tickets` | Create ticket | 201 |
| GET | `/api/v1/tickets` | List / search / filter | 200 |
| GET | `/api/v1/tickets/{id}` | Get ticket detail | 200 |
| PATCH | `/api/v1/tickets/{id}` | Partial update fields | 200 |
| POST | `/api/v1/tickets/{id}/transitions` | Change status | 200 |
| POST | `/api/v1/tickets/{id}/comments` | Add comment | 201 |
