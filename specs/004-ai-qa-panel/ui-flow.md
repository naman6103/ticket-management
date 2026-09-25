# UI Flow: AI Q&A Panel

Extends `specs/002-ticket-management-ui/ui-flow.md` — adds one new screen and fills the "reserved-but-empty slot" that document's Navigation-shell section flagged for this feature. No existing screen's flow changes.

## Navigation shell (update to Feature 2's shared shell)

- The nav link slot Feature 2 reserved (its `ui-flow.md` §Navigation shell: "reserved-but-empty slot for the future AI Q&A panel entry point... rendered as nothing today") is now filled: a persistent "Ask Assistant" link, visible from every screen, → `/assistant` (FR-009, SC-004).

---

## Screen 4: AI Q&A Panel (`/assistant`) — User Stories 1–4

**Entry point**: nav link, reachable from any screen. No link from ticket detail back to the panel — the reverse direction isn't required by spec.md.

**Layout**:
- Question textarea + submit button. Both disabled while a request is pending (spec Clarification #3).
- Result region, showing exactly one of three states at a time:
  a. **Grounded answer**: answer text, then a *separate* citation list — one clickable ticket-ID link per cited ticket, each opening `/tickets/{id}` in a new tab (spec Clarification #1, #2).
  b. **No relevant tickets found**: a dedicated, visually distinct message block — no citations (FR-006, FR-007).
  c. **Nothing yet**: initial state before the first submission.
- Shared error-display region (`ErrorDisplay`, same component/behavior as every other screen) for request/network/backend failures — rendered separately from, and never combined with, states (a)/(b) above.

**Flow**:

1. Agent types a question and submits (blocked if blank/whitespace-only — FR-002). Input/submit disable immediately; a loading indicator shows (FR-011).
2. `POST /api/ai/ask` (via this feature's own Route Handler, `contracts/consumed-api.md`) resolves:
   - `noRelevantTicketsFound: false` → render (a); any previous state is fully replaced (FR-010).
   - `noRelevantTicketsFound: true` → render (b); any previous state is fully replaced.
   - non-2xx → `ErrorDisplay` renders the mapped message (`data-model.md`'s `mapApiError.ts` extension); any previous answer/no-match state is cleared first, per FR-010, so a stale answer never sits behind an error.
3. Agent clicks a citation in (a) → a new tab opens `/tickets/{id}` (Feature 2's existing detail view, including its own "ticket not found" handling if the ticket was since deleted — Edge Cases); the `/assistant` tab is untouched, input re-enabled, ready for the next question.
4. Agent submits a new question at any point after a previous one resolved → step 1 repeats, replacing whatever was shown.

---

## Cross-cutting: Error and empty states (addition to Feature 2's table)

| Situation | Presentation |
|---|---|
| `noRelevantTicketsFound: true` (`200`, not an error) | Dedicated no-match block, distinct from every row below (FR-007) |
| `400 VALIDATION_FAILED` (blank/too-long question) | Field-level message under the question textarea when `details[]` present, else shared error banner |
| `502 AI_GENERATION_FAILED` | Shared error banner: "assistant couldn't generate an answer" |
| `503 AI_RETRIEVAL_UNAVAILABLE` | Shared error banner: "assistant temporarily unavailable" |
| Network/timeout failure | Same existing "couldn't reach the server" banner used everywhere else |

All error rows route through the single shared error-display component (architecture.md), same as every screen in Feature 2 — never a bespoke per-screen error UI. The no-match row deliberately does not.
