# Phase 0 Research: Ticket Management Web UI

All Technical Context items were resolvable directly from the user's stack instructions and the spec — no unresolved `NEEDS CLARIFICATION` markers remain. This file documents the concrete choice, rationale, and alternatives considered for each open technical decision within that stack.

## 1. Framework: Next.js (App Router) vs. plain React (Vite/CRA)

- **Decision**: Next.js 14, App Router, deployed as a static/SSR hybrid served by its built-in Node server behind the Dockerfile's lightweight web server.
- **Rationale**: User's instruction allowed "React or Next.js"; Next.js gives file-system routing that maps 1:1 onto the spec's screens (list, detail, create, edit-in-place), built-in code-splitting so the future AI Q&A panel can be added as an independent route/section without restructuring (FR-015), and a conventional place (`app/layout.tsx`) for the shared navigation shell.
- **Alternatives considered**: Plain React + Vite + React Router — simpler build, but requires hand-rolling routing/code-splitting conventions that Next.js already standardizes; rejected as unnecessary extra decision-making for no spec benefit.

## 2. HTTP client / API typing

- **Decision**: Hand-written, narrowly-typed `fetch` wrapper (`frontend/src/lib/api/`) with one module per backend resource (`tickets.ts`, `comments.ts`, `assignees.ts`), typed directly from the shapes documented in `specs/001-ticket-management-api/api-contract.md`. Request/response types live in `frontend/src/types/` and are hand-maintained against that contract (no code-gen step, since Feature 1 does not publish an OpenAPI/JSON-Schema artifact to generate from).
- **Rationale**: "An HTTP client typed against Feature 1's api-contract.md" (user instruction) is satisfied without introducing a codegen toolchain dependency; a thin wrapper is also the natural single place to centralize error-shape parsing (architecture.md) and the environment-driven base URL.
- **Alternatives considered**: Axios — no material benefit over `fetch` for this scope, adds a dependency; OpenAPI-generated client — would require Feature 1 to publish a machine-readable spec, which it currently doesn't (only `api-contract.md`, a Markdown doc); rejected as out of scope (would require backend-side changes beyond the one narrow assignees endpoint already agreed).

## 3. Server-state / caching / infinite scroll

- **Decision**: TanStack Query (React Query) v5, using its `useInfiniteQuery` for the ticket list (matches the FR-004a infinite-scroll clarification) and standard `useQuery`/`useMutation` for detail, create, edit, comment, and transition operations.
- **Rationale**: Backend already returns page/size/totalPages (`PageResponse<T>`); React Query's infinite-query primitive maps directly onto that shape with minimal glue code, and its built-in cache invalidation (e.g. refetch ticket detail after a successful transition) satisfies FR-009's "immediately reflected" and SC-006's "without a full page reload" requirements without hand-rolled state management.
- **Alternatives considered**: Redux Toolkit + RTK Query — more ceremony than this scope needs (no complex client-only state beyond server-mirrored data); plain `useState`/`useEffect` — would require hand-rolling cache invalidation and infinite-scroll pagination logic that React Query provides out of the box; rejected as reinventing a solved problem.

## 4. Assignee combobox component

- **Decision**: A small hand-built combobox component (native `<input>` + `<datalist>`-style filtered dropdown, ARIA `combobox` pattern) rather than a third-party component library, backed by `GET /api/v1/assignees` and accepting free-typed values (FR-005a/FR-005c).
- **Rationale**: Requirement is narrow (filter a short list, or accept a new typed value) and doesn't justify a full component library dependency; a hand-built component keeps bundle size minimal and gives full control over the "no assignees yet — type a name to add one" empty state (FR-005c).
- **Alternatives considered**: A component library (e.g. Headless UI, Radix combobox) — reasonable alternative if design system needs grow later; deferred rather than rejected outright, since the current requirement doesn't need it yet (YAGNI).

## 5. New backend endpoint: `GET /api/v1/assignees`

- **Decision**: One new read-only Spring Boot endpoint, `GET /api/v1/assignees`, returning `{"assignees": ["jane.doe", "john.smith", ...]}` — the distinct, non-null `assignee` values across all tickets, sorted alphabetically. No pagination (assignee count is expected to be small; if it grows, pagination can be added later without breaking this contract's shape, since the array can always be wrapped in a page envelope in a compatible way).
- **Rationale**: Matches the spec's clarification exactly (FR-005b): derived from existing ticket data, not a separately maintained roster; matches `api-standards.md` naming (plural noun, no verb) and error-shape conventions (though this endpoint has no meaningful error case beyond generic 5xx, since it takes no input).
- **Alternatives considered**: Embedding assignees as metadata on the existing list endpoint response — rejected, conflates two different resources and would bloat every list response; a dedicated `/api/v1/tickets/assignees` sub-path — rejected, assignees are not a sub-resource of a specific ticket, they're a distinct (if minimal) resource of their own.

## 6. Frontend web server (Docker runtime)

- **Decision**: Next.js's own production server (`next start`) run under Node in the final Docker stage — not a separate static file server (e.g. nginx) — because the App Router setup benefits from Next's built-in routing/asset serving and this avoids maintaining a second reverse-proxy config file.
- **Rationale**: "Lightweight web server" is satisfied by Next's minimal production server; using nginx in front would add a second process/config surface for no functional benefit at this scale (single frontend service, no static-export-only requirement).
- **Alternatives considered**: `next export` (fully static) + nginx — would work since this feature has no server-only Next.js features, but adds a second Docker-stage/tooling surface for no benefit given Next's own server is already lightweight and single-process; deferred, not required by the spec.

## 7. Testing tools

- **Decision**: Vitest + React Testing Library for component tests (forms/validation-display per test-strategy.md); Playwright for the single required E2E flow (create → edit → transition), run against a docker-composed stack (frontend + backend + MySQL) in CI/local validation.
- **Rationale**: Standard, well-supported pairing for Next.js/React component + E2E testing; Playwright can drive a real browser against the real Docker Compose stack, which matches the constitution's "single-command deployment" spirit (test what actually gets deployed).
- **Alternatives considered**: Jest — Vitest chosen instead for faster Vite-native execution and simpler ESM/TS handling in a Next.js project; Cypress — Playwright chosen instead for better multi-browser support and lower flake in headless CI, though either would satisfy the requirement.

## Resolved Technical Context

All items in `plan.md`'s Technical Context section reflect the decisions above. No `NEEDS CLARIFICATION` markers remain.
