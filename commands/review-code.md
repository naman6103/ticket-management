# Review Code

Review a diff or file against project Java/Spring Boot and REST API standards.
Propose a concrete fix for every violation.

## Invocation

```text
/review-code [path | --diff | --staged]
```

| Argument | Meaning |
|----------|---------|
| `path` | Review that file (or directory of `*.java` sources) |
| `--diff` | Review unstaged + uncommitted changes (`git diff` + untracked Java) |
| `--staged` | Review staged changes only (`git diff --cached`) |
| _(empty)_ | If the user @-mentioned files or pasted a diff, use that; otherwise ask which scope to review |

Text after the command name is `$ARGUMENTS`. Treat it as the review scope.

## Preconditions (do these first)

1. Read `rules/java-springboot.md` in full.
2. Read `rules/api-standards.md` in full.
3. Resolve the review target:
   - **File/dir:** read the named sources (and nearby related types if needed for context).
   - **Diff:** run the appropriate `git diff` (and list untracked `*.java` when using `--diff`); review only changed hunks plus enough surrounding context to judge layering/DI/API shape.
4. If the target is missing or empty, stop and ask for a path or diff scope. Do not invent findings.

## Scope of review

**In scope**

- Layering, packages, DI, exceptions, naming, style notes from `rules/java-springboot.md`
- REST paths, methods/status codes, error JSON shape, pagination/filtering, boundary validation from `rules/api-standards.md`
- Controllers, services, repositories, DTOs, entities, `@ControllerAdvice`, `@ConfigurationProperties`, API mappings

**Out of scope (do not expand the review)**

- Product requirements / product UX
- RAG eval quality (see `rules/testing.md` only if the diff itself violates API/Java rules)
- Formatting-only nits that do not contradict Google style or the named rules
- Suggesting stack changes forbidden by the constitution

## Checklist

Walk every applicable item. Skip items that cannot apply to the target (e.g. no HTTP mapping → skip REST path checks).

### From `rules/java-springboot.md`

- [ ] Package matches role (`controller` / `service` / `repository` / `entity` / `dto` / `config` / `exception` / …)
- [ ] Controller has no business logic; no repository calls from controller
- [ ] Service owns domain rules; does not leak HTTP types inappropriately
- [ ] Entities not used as request/response bodies
- [ ] Constructor injection; `private final` deps; no field `@Autowired`
- [ ] Service injected by interface when an interface exists
- [ ] Custom `*Exception` + `ErrorCode`; mapped via `@RestControllerAdvice` to the shared error shape
- [ ] Config via `@ConfigurationProperties` / env — no hardcoded secrets or RAG tuning constants
- [ ] Naming: `*Controller`, `*Service`/`*ServiceImpl`, `*Repository`, `*Request`/`*Response`, `*Exception`, `*Properties`
- [ ] Methods ≤ 50 lines; no wildcard imports; Java 21–only features
- [ ] Public controller/service/repository methods have adequate Javadoc when newly added or materially changed

### From `rules/api-standards.md`

- [ ] Paths: `/api/v1/` + plural nouns; no verb-CRUD paths; kebab-case segments
- [ ] Correct HTTP method and success status (`201` + body/Location for creates, etc.)
- [ ] Errors never returned as `200`; client mistakes not mapped to `500`
- [ ] Error body fields present and consistent: `timestamp`, `status`, `error`, `code`, `message`, `path`, optional `details[]`
- [ ] Validation at boundary (`@Valid` / constraints) with field `details` on failure
- [ ] Lists paginated (`page`/`size`/`sort`) with `content` + page metadata — no unbounded arrays
- [ ] Filters via query params; unknown filters rejected with `400`
- [ ] State conflicts (e.g. illegal transition) use `409` and a specific `code`

## Finding severity

| Severity | Use when |
|----------|----------|
| `blocker` | Breaks layering, security (secrets), wrong stack/hardcoded RAG/secrets, or ships a non-compliant public API error/status contract |
| `major` | Clear rule violation that will cause inconsistency or review rejection |
| `minor` | Naming, Javadoc gaps, style that the rules call out but does not break runtime contract |

## Output format (mandatory)

Lead with a one-line summary: pass / N findings (by severity).

Then list **every** violation. Use this exact shape per finding — no prose-only bullets:

```markdown
### F1 · {blocker|major|minor} · {short title}
- **Where:** `path/to/File.java:123`
- **Rule:** `rules/{java-springboot|api-standards}.md` → {section or checklist item}
- **Violation:** {what is wrong, in one or two sentences}
- **Fix:** {concrete change — rename, move package, inject via constructor, replace status code, reshape error JSON, add @Valid, etc.}
- **Suggested patch:**

\`\`\`java
// replacement or minimal corrected snippet
\`\`\`
```

Rules for findings:

1. **`Where` must be `file:line`** (start line of the offending construct). For diff-only context without a stable line, use `file:line` from the working tree after reading the file; if impossible, use `file` + hunk header and say line is approximate.
2. **One finding per distinct violation.** Do not bundle unrelated issues.
3. **Every finding must include a concrete Fix and a Suggested patch** (code or config). “Consider refactoring” is not enough.
4. If the same pattern repeats, file one finding on the first site and note “also at `a:1`, `b:2`” in **Violation**; still give one canonical patch.
5. If nothing violates the two rule files, output:

```markdown
## Result
No violations of `rules/java-springboot.md` or `rules/api-standards.md` in the reviewed scope.
```

## Behavior rules

- Read the rule files every run; do not rely on memory alone.
- Do not modify source files unless the user explicitly asks to apply fixes after the review.
- Do not re-litigate constitution topics outside these two rule files.
- Prefer quoting the smallest corrected snippet that would pass the checklist.
- When reviewing a diff, judge **new/changed** code; only mention pre-existing issues if they are touched by the hunk or block merging the change.

## Example (illustrative)

```markdown
## Result
3 findings (1 blocker, 1 major, 1 minor).

### F1 · blocker · Business logic in controller
- **Where:** `src/main/java/com/ticketmanagement/ticket/controller/TicketController.java:48`
- **Rule:** `rules/java-springboot.md` → Layering (controller must not contain business rules)
- **Violation:** Transition validity is checked in the controller before calling the repository.
- **Fix:** Move transition rules into `TicketService.applyTransition`; controller only validates the DTO and returns the service result.
- **Suggested patch:**

\`\`\`java
@PostMapping("/{id}/transitions")
public ResponseEntity<TicketResponse> transition(
    @PathVariable UUID id, @Valid @RequestBody TransitionRequest request) {
  return ResponseEntity.ok(ticketService.applyTransition(id, request));
}
\`\`\`
```
