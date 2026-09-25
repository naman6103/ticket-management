---
description: Review a file or diff against project coding standards
argument-hint: [file-or-diff]
---

Review $ARGUMENTS (default to the output of `git diff` if no argument is given)
against `rules/java-springboot.md` and `rules/api-standards.md`.

For each violation:

- Cite the exact `file:line` reference.
- Quote the offending line(s).
- Name the specific rule violated (e.g. "REST naming — plural nouns",
  "boundary validation", "package layering").
- Propose a concrete fix: the exact replacement code or change, not vague
  feedback like "improve error handling" or "follow conventions better".

Skip formatting nits that don't change meaning (whitespace, import order)
unless they violate a documented rule.

If no violations are found, say so explicitly — do not invent issues to
have something to report.
