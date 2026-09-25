# REST API Standards

Source of truth: constitution Principle IV (REST API Contract). Apply to all backend HTTP APIs.

## Endpoint naming

- Use plural nouns for resources: `/api/v1/tickets`, not `/api/v1/ticket` or `/api/v1/getTickets`.
- Nest sub-resources only when ownership is clear: `/api/v1/tickets/{ticketId}/comments`.
- Prefer nouns over verbs. Actions that are not CRUD map to sub-resources or controlled verbs sparingly (e.g. `POST /api/v1/tickets/{id}/transitions`).
- Path segments are lowercase, kebab-case if multi-word: `/api/v1/support-agents`.
- Version via URL prefix: `/api/v1/...`. Breaking changes require a new version.
- Path parameters identify resources (`{id}`); query parameters filter, sort, and paginate.

```text
✅ GET    /api/v1/tickets
✅ GET    /api/v1/tickets/{id}
✅ POST   /api/v1/tickets
✅ PATCH  /api/v1/tickets/{id}
✅ POST   /api/v1/tickets/{id}/transitions

❌ GET    /api/v1/getTicket?id=1
❌ POST   /api/v1/ticket/create
❌ GET    /api/v1/Tickets
```

## HTTP methods and status codes

| Method | Use for | Typical success |
|--------|---------|-----------------|
| `GET` | Read one or list | `200` |
| `POST` | Create or non-idempotent action | `201` (create) or `200`/`202` (action) |
| `PUT` | Full replace | `200` or `204` |
| `PATCH` | Partial update | `200` or `204` |
| `DELETE` | Remove | `204` |

| Status | When |
|--------|------|
| `200 OK` | Successful read or update with body |
| `201 Created` | Resource created; include `Location` when practical |
| `204 No Content` | Success with empty body |
| `400 Bad Request` | Malformed JSON, type mismatch, or failed validation |
| `401 Unauthorized` | Missing or invalid authentication |
| `403 Forbidden` | Authenticated but not allowed |
| `404 Not Found` | Resource id does not exist |
| `409 Conflict` | State conflict (e.g. invalid ticket transition) |
| `422 Unprocessable Entity` | Optional: semantically invalid but well-formed body; prefer `400` unless the team standardizes on `422` |
| `500 Internal Server Error` | Unexpected failure; never leak stack traces to clients |

Do not return `200` with an error payload. Do not use `500` for client mistakes.

## Structured error response

All error responses MUST use the same JSON shape (no ad-hoc `{ "message": "..." }` only).

```json
{
  "timestamp": "2026-09-25T02:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "path": "/api/v1/tickets",
  "details": [
    {
      "field": "title",
      "rejectedValue": "",
      "message": "must not be blank"
    }
  ]
}
```

| Field | Required | Notes |
|-------|----------|-------|
| `timestamp` | yes | ISO-8601 UTC |
| `status` | yes | HTTP status as number |
| `error` | yes | Standard reason phrase |
| `code` | yes | Stable machine-readable code (`SCREAMING_SNAKE`) |
| `message` | yes | Human-readable summary |
| `path` | yes | Request path |
| `details` | no | Field-level or multi-cause list; empty array or omit when unused |

Validation failures at the API boundary MUST populate `details` per field. Domain conflicts (e.g. illegal transition) SHOULD set a specific `code` such as `INVALID_TRANSITION` and use `409`.

## Pagination

List endpoints that can grow MUST paginate. Use offset/limit query params unless a spec requires cursors.

```text
GET /api/v1/tickets?page=0&size=20&sort=createdAt,desc
```

| Param | Default | Rules |
|-------|---------|-------|
| `page` | `0` | Zero-based page index |
| `size` | `20` | Max page size enforced server-side (document the cap) |
| `sort` | resource-specific | `property,asc\|desc`; reject unknown properties with `400` |

Success body for lists:

```json
{
  "content": [ ],
  "page": 0,
  "size": 20,
  "totalElements": 100,
  "totalPages": 5
}
```

Do not return unbounded arrays from list endpoints.

## Filtering and search

- Filters are query parameters named after resource fields: `?status=OPEN&assigneeId=42`.
- Multiple values for the same field: repeat the param or use a documented comma-separated form; pick one and stick to it per API version.
- Free-text search uses `q` (or a documented alias): `?q=login+timeout`.
- Unknown filter names MUST yield `400` with `code` such as `UNKNOWN_FILTER` (do not silently ignore).
- Filtering MUST NOT change path structure; keep filters on the collection resource.

```text
✅ GET /api/v1/tickets?status=OPEN&page=0&size=20
✅ GET /api/v1/tickets?q=elasticsearch&status=IN_PROGRESS

❌ GET /api/v1/tickets/status/OPEN
❌ GET /api/v1/openTickets
```

## Input validation

- Validate at the controller/DTO boundary before business logic.
- Fail fast with `400` + structured `details`; do not partially apply invalid writes.
- Path and query params are validated with the same error shape as body fields (`field` may be the param name).

## Quick checklist

- [ ] Plural, versioned resource paths; no verb-in-path CRUD
- [ ] Correct status codes; errors never disguised as `200`
- [ ] Error body matches the shared shape (`code`, `message`, `details`, …)
- [ ] Lists are paginated with `content` + page metadata
- [ ] Filters via query params; unknown filters rejected
- [ ] Boundary validation before domain logic
