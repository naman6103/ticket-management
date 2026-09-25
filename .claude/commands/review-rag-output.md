---
description: Check an AI assistant answer for hallucination or ungrounded claims against its cited tickets
argument-hint: [question]
---

Send $ARGUMENTS as the question to `POST /api/ai/ask`. Take the returned
answer text and its cited ticket IDs.

For each cited ticket ID, fetch the ticket (its full content, not just the
title) so you have ground truth to check against.

Break the answer down into individual factual claims (one claim = one
checkable statement). For each claim, check it against the fetched tickets
and record a verdict:

| Verdict | Meaning |
|---------|---------|
| PASS | Claim is directly supported by a cited ticket |
| FAIL — ungrounded | Claim isn't traceable to any cited ticket |
| FAIL — miscited | Claim is attached to a cited ticket, but that ticket doesn't actually support it |
| FAIL — missing disclaimer | Answer should have said "no relevant tickets found" (no cited ticket actually supports the question) but gave a substantive answer instead |

Output format: one row per claim.

```text
Claim: "<claim text>"
Cited ticket(s): TICKET-123, TICKET-456
Verdict: PASS | FAIL — ungrounded | FAIL — miscited | FAIL — missing disclaimer
Evidence: <quote from the ticket that supports/contradicts it, or "none found">
```

After the per-claim table, give one summary line: total claims, pass count,
fail count by category. Do not collapse this into a single overall
pass/fail judgment — the per-claim verdicts are the deliverable.
