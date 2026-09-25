---
name: documentation
description: Generates Javadoc/JSDoc and a short usage example in this project's documentation style. Use when writing or updating documentation for a class, endpoint, or module.
---

# Documentation

Produce Javadoc (Java/Spring Boot backend) or JSDoc (React/Next.js frontend)
plus one short usage example, matching this project's conventions
(`rules/java-springboot.md`, `rules/api-standards.md`).

## Before writing

1. Identify the target: class, method, REST endpoint, or frontend
   component/hook/module.
2. Read the target file fully, plus one or two sibling files in the same
   package/folder, to match existing doc tone and format if any already
   exist. Do not invent a new style — follow what's there.
3. Check `rules/api-standards.md` when documenting a controller/endpoint,
   so documented status codes, error shape, and pagination match the
   actual contract.

## Javadoc (backend: controller/service/repository/entity/dto)

- Class-level: one-sentence summary of responsibility (what it does, not
  how). Add `@since` only if the project already uses it elsewhere.
- Method-level: summary line, then `@param` for each parameter, `@return`
  (omit for `void`), `@throws` for checked/documented runtime exceptions
  the caller must handle (e.g. domain exceptions like
  `InvalidTransitionException`).
- For `@RestController` methods, document the HTTP contract in prose
  (method + path, success status, error statuses/codes it can return per
  `rules/api-standards.md`), not just the Java signature.
- No comments restating the method name. No speculative `@author`/`@version`
  unless the file already has that pattern.

```java
/**
 * Applies a status transition to a ticket, validating it against the
 * ticket state machine before persisting.
 *
 * @param ticketId  id of the ticket to transition
 * @param event     transition event to apply
 * @return the ticket's updated status after the transition
 * @throws InvalidTransitionException if {@code event} is not valid from
 *         the ticket's current status
 */
public TicketStatus applyTransition(Long ticketId, TicketEvent event) { ... }
```

Usage example (place directly below the Javadoc block or in a linked test):

```java
// Example: reopen a resolved ticket
TicketStatus status = ticketService.applyTransition(42L, TicketEvent.REOPEN);
// status == TicketStatus.OPEN
```

## JSDoc (frontend: components/hooks/utils)

- One-line summary above the function/component signature.
- `@param {Type} name - description` for each param; `@returns {Type}`
  for non-void returns.
- For React components, document required props via the summary or a
  `@param {Props} props` block, not inline prop-by-prop unless props are
  non-obvious (e.g. callbacks, discriminated unions).

```jsx
/**
 * Renders a ticket status badge with the color mapped to TicketStatus.
 *
 * @param {{ status: TicketStatus }} props
 * @returns {JSX.Element}
 */
function TicketStatusBadge({ status }) { ... }
```

Usage example:

```jsx
<TicketStatusBadge status="OPEN" />
```

## Output rules

- Only document the WHY when it's non-obvious (hidden constraint, subtle
  invariant); never restate the WHAT if the name/signature already says it.
- Keep the usage example minimal — one call/render, one expected outcome.
- Do not add documentation blocks to code that doesn't need them (trivial
  getters/setters, one-line pure functions with self-evident names).
- Match existing indentation and comment-block style exactly; don't
  reformat surrounding code.
