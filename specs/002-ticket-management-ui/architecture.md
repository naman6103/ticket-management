# Architecture: Ticket Management Web UI

## Component structure

```text
frontend/src/
├── app/
│   ├── layout.tsx                 # Nav shell + shared error boundary + React Query provider
│   ├── api/                        # Route Handlers: server-side proxy to the backend (see below)
│   │   ├── tickets/route.ts, tickets/[id]/route.ts, tickets/[id]/transitions/route.ts,
│   │   │   tickets/[id]/comments/route.ts, assignees/route.ts
│   ├── tickets/
│   │   ├── page.tsx               # Screen 1: list (uses TicketList, SearchFilterBar)
│   │   ├── new/page.tsx           # Screen 3: create (uses TicketForm in "create" mode)
│   │   └── [id]/page.tsx          # Screen 2: detail (uses TicketDetail, CommentList, CommentForm)
├── components/
│   ├── layout/
│   │   ├── NavShell.tsx           # persistent nav, reserves future AI-panel slot (empty today)
│   │   └── PageErrorBoundary.tsx  # catches render-time errors, renders ErrorDisplay
│   ├── tickets/
│   │   ├── TicketList.tsx         # infinite-scroll list (React Query useInfiniteQuery)
│   │   ├── SearchFilterBar.tsx    # q + status controls, debounced
│   │   ├── TicketRow.tsx          # single row (title/status/priority/assignee)
│   │   ├── TicketDetail.tsx       # read-only field block + edit-mode toggle
│   │   ├── TicketForm.tsx         # shared create/edit form (title/description/priority/assignee)
│   │   ├── AssigneeCombobox.tsx   # backed by GET /assignees, accepts typed values (FR-005a/c)
│   │   ├── StatusBadge.tsx        # renders current status
│   │   └── TransitionControl.tsx  # offers only valid next statuses (data-model.md lookup)
│   ├── comments/
│   │   ├── CommentList.tsx        # chronological, "no comments yet" empty state
│   │   └── CommentForm.tsx        # add-comment box
│   └── errors/
│       └── ErrorDisplay.tsx       # the ONE shared component every error/empty state routes through
├── lib/
│   ├── api/
│   │   ├── client.ts              # fetch wrapper: base URL from env, JSON parsing, ApiError construction
│   │   ├── tickets.ts             # createTicket, listTickets, getTicket, updateTicket, transitionTicket
│   │   ├── comments.ts            # addComment
│   │   └── assignees.ts           # listAssignees
│   └── errors/
│       └── mapApiError.ts         # ErrorResponse -> UI-facing message model (see below)
└── types/
    ├── ticket.ts, comment.ts, page.ts, apiError.ts, assignee.ts   # mirror data-model.md
```

## Visual design (FR-017)

A single global stylesheet (`app/globals.css`, imported once in `app/layout.tsx`) drives the entire UI's look — no per-component CSS Modules, no styling library dependency. Decided post-implementation (see spec.md Clarifications, "post-implementation" session) once it became clear the initial semantic-HTML-only build needed an actual visual design, and the user asked for a generic Jira-inspired look with no specific reference file provided.

- **Design tokens**: CSS custom properties in `:root` — an Atlassian-blue primary color (`--color-primary: #0052cc`), a light grey app background, a white "surface" color for cards, and five status-specific color pairs (background + text) for `OPEN`/`IN_PROGRESS`/`RESOLVED`/`CLOSED`/`CANCELLED`.
- **Layout primitives**: `.page` (centered, max-width content column), `.navShell` (persistent colored top bar), `.card` (white bordered/shadowed surface — used for the ticket list container, detail view, and both forms).
- **Status pills**: `StatusBadge` maps each `TicketStatus` to a dedicated CSS class (`.statusOpen`, `.statusInProgress`, etc.) — colors are never computed inline, so the status→color mapping lives in exactly one place (`globals.css`) and one lookup table (`StatusBadge.tsx`).
- **Forms**: `.form` / `.formField` classes give every form (create, edit, add-comment) the same label/input/spacing treatment.
- **Error/empty states**: `ErrorDisplay`'s three message kinds get distinct tones — `.errorFullpage` and `.errorField` use the danger (red) palette for genuine failures, but `.errorBanner` deliberately uses a neutral blue "info" tone, not red, because the same `banner` kind is also used for benign empty states (e.g. "No tickets found") where a red/danger color would misleadingly read as an error.

This is a from-scratch interpretation of "Jira-style," not a pixel-accurate clone — no Jira design file, screenshot, or CSS was provided as a reference.

## State management

- **Server state** (tickets, comments, assignees — anything backend-owned): TanStack Query exclusively. No Redux/Context duplication of server data. Query keys are structured as `["tickets", { q, status }]` (list), `["ticket", id]` (detail), `["assignees"]`. Mutations (`create`, `update`, `transition`, `addComment`) invalidate/refetch the relevant query key on success so the UI reflects the backend's authoritative state immediately (FR-011, SC-006).
- **Local/UI-only state** (edit-mode toggle, form draft values, combobox input text, filter-bar draft before debounce fires): plain React `useState`/`useReducer` inside the owning component. Never lifted to a global store — no cross-cutting UI state exists at this scope.
- **No client-side business logic**: the only "logic" living client-side is the display-only status-transition lookup table (data-model.md) used purely to decide which buttons to *render*; it never decides whether a transition is *actually* allowed — that answer only ever comes from the backend's response (FR-011).

## Error mapping: backend `ErrorResponse` → UI message

`lib/errors/mapApiError.ts` is the single translation point. Every API call in `lib/api/*` funnels non-2xx responses through it before the calling component ever sees an error.

```text
ErrorResponse.code            → UI treatment
─────────────────────────────────────────────────────────────
VALIDATION_FAILED             → if details[] present: render each {field, message} as an inline
                                 error next to that field's input (TicketForm, CommentForm).
                                 If details[] empty: render `message` as a single inline banner
                                 above the form.
TICKET_NOT_FOUND              → render full-page/section "not found" state (message shown verbatim
                                 as supporting text, e.g. "No ticket found for this ID").
INVALID_TRANSITION            → render `message` (already names current + attempted status per
                                 FR-013) inline near TransitionControl; control resets to unselected.
UNKNOWN_FILTER                → render `message` in the shared error banner at the list screen;
                                 should not occur under normal use (contracts/consumed-api.md).
(network/timeout — no code)   → synthesized local ApiError with a fixed "couldn't reach the
                                 server" message + retry action; never shown as a raw stack trace
                                 or generic "something went wrong" (FR-012).
```

`ErrorDisplay` is the only component that renders any of the above — it accepts a mapped message model (`{ kind: "field" | "banner" | "fullpage", text, fieldName? }`) and is used identically whether the source was a list-load failure, a form submission rejection, or a transition rejection. This is what keeps error presentation consistent across every screen (ux checklist CHK029) and is the second of the three "shared" pieces required by FR-015.

## How the layout leaves room for the future AI Q&A panel (FR-015, without a rewrite)

Three specific seams are built now, deliberately unused by anything AI-related today:

1. **Shared navigation shell** (`NavShell.tsx` in `app/layout.tsx`): already the single place every screen mounts through. Adding an AI panel later means adding one more nav entry/route under the same `layout.tsx` — no other screen's markup changes.
2. **Shared API-client pattern** (`lib/api/*.ts` + `client.ts`): a future `lib/api/assistant.ts` module follows the exact same shape (typed functions wrapping `client.ts`) as `tickets.ts`/`comments.ts`/`assignees.ts` today. The base-URL/env-var/error-parsing plumbing in `client.ts` is already generic across resources, not ticket-specific.
3. **Shared error-display component** (`ErrorDisplay.tsx` + `mapApiError.ts`): an AI panel's own failures (e.g. "assistant unavailable") route through the same component by producing the same mapped-message shape — no new error-UI component needed.

Nothing about the AI panel itself (layout slot content, its own routes, its own data model) is built in this feature — only that these three seams exist and are already the pattern every other screen uses, so adding it later is additive, not a refactor.

## Deployment architecture

- `frontend/Dockerfile`: multi-stage — `node:20-alpine` build stage (`npm ci && npm run build`), runtime stage also `node:20-alpine` (lightweight, no OS package bloat) running `next start` as a non-root user, `EXPOSE 3000`.
- `docker-compose.yml` (repo root, extended not replaced): new `frontend` service, `depends_on: app` (Feature 1's backend service name), reaching the backend via `http://app:8080` — the Docker Compose network's internal DNS name, never `localhost` (compose services resolve each other by service name on the shared default network).
- **Correction from initial design**: `client.ts` and all `lib/api/*.ts` calls run **server-side only** (Next.js Route Handlers under `app/api/*`, called by client components via same-origin relative paths like `/api/tickets`), not directly from browser code. This is required because the backend URL uses the Compose service name (`http://app:8080`), which only resolves inside the Docker network — a browser on the host cannot resolve it. The Next.js server acts as a thin backend-for-frontend proxy: browser → same-origin `/api/*` → Next.js server (inside the container) → `http://app:8080`.
- API base URL is injected via a single **server-side-only** environment variable, `API_BASE_URL` (deliberately **not** prefixed `NEXT_PUBLIC_`, since that prefix would bundle the value into client-side JS and expose the internal Compose hostname to the browser for no benefit) — read by `lib/api/client.ts` at request time, never hardcoded, never committed with a real value; `.env.example` carries this entry alongside `FRONTEND_PORT`, consistent with the existing `MYSQL_*`/`APP_PORT` pattern.
- No secrets: the frontend has no credentials of its own (it talks to a public-within-the-compose-network backend URL, not a database or third-party API key).
