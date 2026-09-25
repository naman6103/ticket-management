# Review RAG Output

Ground-truth an assistant answer against the **cited tickets**, not against model memory. Every factual claim gets its own pass/fail verdict.

Source of truth: constitution Principle V (RAG Guardrails) — answer only from retrieved ticket context, cite ticket IDs, and say `no relevant tickets found` when nothing relevant was retrieved. Never treat fluent prose as evidence.

## Invocation

```text
/review-rag-output
```

Text after the command name is `$ARGUMENTS`. Parse a **question**, a **generated answer**, and **cited ticket IDs**.

Accepted shapes (use the first that fits):

1. Explicit blocks in `$ARGUMENTS` or the user message:

```text
Question: {user question}
Answer: {assistant answer}
Citations: {id, id, ...}
```

2. A pasted Q/A where citations appear in the answer (e.g. `[TCK-1024]`, `(ticket 1024)`, `` `uuid` ``). Extract IDs from the answer; if the user also lists IDs, take the **union**.
3. If the question or answer is missing, stop and ask. Do not invent either. If citations are omitted, extract from the answer; if none exist, treat the citation set as empty.

## Preconditions (do these first)

1. Read constitution Principle V and `rules/testing.md` § "RAG evaluation vs deterministic testing" so abstain wording and eval vs unit-test rules stay aligned.
2. Normalize the citation set: unique IDs, original surface form preserved (for the report).
3. **Fetch every cited ticket** before judging any claim. Do not use training knowledge, prior chat tickets, or "typical" ticket content.

### How to fetch tickets

Try in order; record which source you used per ID:

1. **HTTP:** `GET /api/v1/tickets/{id}` (compose/local API). Follow redirects; treat `404` / `410` as **not found**.
2. **Repo fixtures:** labeled eval pairs or ticket JSON under the project (e.g. eval datasets, seed data) if the API is down.
3. If both fail, mark the ID **unfetched**. An unfetched ID cannot support any claim.

Fetch the **full ticket body** (title, description, status, comments, assignee, timestamps, custom fields — whatever the API/fixture returns). Judging from IDs or titles alone is not allowed.

If the user provides ticket payloads inline, use those as the fetch result and still note the source as `user-supplied`.

## Scope

**In scope**

- Atomic factual claims in the answer (status, owner, dates, root cause, workaround, linked IDs, counts, “this ticket says …”).
- Mapping each claim to cited ticket field/text that allegedly supports it.
- Empty or irrelevant retrieval → required abstain phrase.

**Out of scope**

- Style, tone, or completeness of the answer beyond faithfulness.
- Retrieval quality metrics (Recall@K) unless needed to decide the abstain case.
- Java/API code review (`commands/review-code.md`).
- Asserting free-form RAG text in unit/integration tests (`rules/testing.md`).

## Workflow

Copy and complete:

```text
Review progress:
- [ ] Parse question, answer, citation IDs
- [ ] Fetch every cited ticket (or record not-found / unfetched)
- [ ] Split the answer into atomic claims
- [ ] Verdict per claim (traceable + supported)
- [ ] Abstain check (should have said "no relevant tickets found")
- [ ] Emit the mandatory report
```

### 1. Split claims

Break the answer into the smallest statements that can be true or false on their own. Skip pure hedging with no fact (“hope this helps”). Keep claims that imply ticket state even if hedged (“it might be assigned to Alex” is still a claim about assignment).

Number claims `C1`, `C2`, … in answer order.

For each claim record:

- **Text** — the claim as stated (short quote).
- **Attached citations** — IDs the answer ties to this claim (inline citation, same sentence, or immediately preceding citation list). If the answer cites tickets globally but not per sentence, attach **all** cited IDs to every claim (then still fail any ID that does not support that claim — see flag b).

### 2. Per-claim checks (mandatory flags)

For **each** claim, evaluate independently:

**(a) Untraceable** — the claim is not grounded in any **cited** ticket you fetched. Fail if:

- no citation is attached and the fact is not a restatement of the user question, or
- attached IDs were not in the citation set, or
- no fetched ticket contains the fact (including unfetched / not-found IDs).

**(b) Citation does not support** — an ID is attached to the claim but that ticket’s actual content does not entail the claim. Fail if the ticket:

- contradicts the claim, or
- is silent on the claimed field, or
- is about a different issue than the claim asserts, or
- was not found / not fetched.

A claim **passes** only if **at least one** attached, successfully fetched ticket **entails** the claim (direct statement or unambiguous field value). Paraphrase is OK; invented numbers, names, statuses, or causes are not.

Do **not** pass a claim because a *different*, uncited ticket in the database would have supported it. Uncited sources do not count.

### 3. Abstain check (flag c)

The answer **must** be (or contain as the substantive reply) exactly:

```text
no relevant tickets found
```

when **any** of these is true:

- citation set is empty, and the answer still asserts ticket facts, or
- every cited ticket is not found / unfetched, or
- every successfully fetched cited ticket is **irrelevant to the question** (no overlapping issue, entity, or error with what was asked), even if the tickets exist.

If the abstain condition holds and the answer did **not** use that phrase as the outcome (instead answering from general knowledge or stretching unrelated tickets), fail flag **(c)**. Then **fail every factual claim** as well (they should not have been made).

If the answer correctly abstains, there are no factual claims to pass. Report one row for the abstain outcome (`PASS`) and do not invent claims from the question.

If retrieval clearly had relevant cited tickets and the model abstained anyway, note it under **Notes** (over-abstain). That is not flags a–c; do not fail a–c for over-abstain unless the user asked to score completeness.

## Verdict vocabulary (per claim)

Use exactly one of:

| Verdict | Meaning |
|---------|---------|
| `PASS` | Fetched cited ticket(s) entail the claim; no a/b failure |
| `FAIL (a)` | Not traceable to any cited ticket |
| `FAIL (b)` | Attached citation(s) do not support the claim |
| `FAIL (a+b)` | Both: nothing cited that traces, and named IDs don’t support it |
| `FAIL (c)` | Claim exists only because the model should have abstained |

Overall line is a **summary only**. It does not replace per-claim rows.

| Overall | When |
|---------|------|
| `PASS` | Every claim `PASS` and abstain check OK |
| `FAIL` | Any claim failed or flag (c) fired |

## Output format (mandatory)

```markdown
## RAG faithfulness review

**Overall:** {PASS|FAIL} — {n} claims, {n} passed, {n} failed. Abstain check: {PASS|FAIL (c)}.

**Question:** {one-line restatement}

**Citations fetched:**
| ID | Source | Result |
|---|---|---|
| {id} | {GET /api/v1/tickets/{id} \| fixture \| user-supplied} | {ok — one-line gist \| 404 \| unfetched: reason} |

### Claims

#### C1 · {PASS|FAIL (a)|FAIL (b)|FAIL (a+b)|FAIL (c)}
- **Claim:** {quote}
- **Attached IDs:** {ids or none}
- **Evidence:** {ticket id → field or quoted span that supports **or** why it does not}
- **Flags:** {none | (a) untraceable | (b) citation does not support | (c) should have abstained}

#### C2 · …
```

Rules:

1. **One row (heading) per claim.** Never collapse multiple claims into a single verdict.
2. **Evidence must quote or paraphrase a fetched field**, with ticket ID. “Seems consistent” is not evidence.
3. If flag (c) fires, add a short **Abstain** subsection: why retrieval was empty/irrelevant, and that the required phrase was missing.
4. List **cited IDs that were never used** to support any passing claim (orphan citations) under **Notes** — informational, not a substitute for (a)/(b).
5. Do not modify application code or tickets unless the user asks.

## Behavior rules

- Fetch before judge. No skipped IDs.
- Prefer the ticket record over the model’s wording when they conflict.
- The required abstain string is case-insensitive but must be that sentence (extra apology around it is OK if the outcome is clearly abstain and **no** extra ticket facts are added).
- If a claim mixes a supported fact and an unsupported one, split into two claims.
- This command is an **eval/review** of a single answer. Do not add `assertEquals` on RAG prose to unit tests.

## Example (illustrative)

```markdown
## RAG faithfulness review

**Overall:** FAIL — 3 claims, 1 passed, 2 failed. Abstain check: PASS.

**Question:** What is the status of the login timeout ticket?

**Citations fetched:**
| ID | Source | Result |
|---|---|---|
| TCK-12 | GET /api/v1/tickets/TCK-12 | ok — OPEN, "session expires after 5 min on /login" |
| TCK-99 | GET /api/v1/tickets/TCK-99 | ok — CLOSED, printer driver |

### Claims

#### C1 · PASS
- **Claim:** "TCK-12 is OPEN"
- **Attached IDs:** TCK-12
- **Evidence:** TCK-12.status = OPEN
- **Flags:** none

#### C2 · FAIL (b)
- **Claim:** "TCK-12 was caused by a bad Redis cluster"
- **Attached IDs:** TCK-12
- **Evidence:** TCK-12 description mentions login timeout only; no Redis
- **Flags:** (b) citation does not support

#### C3 · FAIL (a)
- **Claim:** "Average resolution time is 2 days"
- **Attached IDs:** none
- **Evidence:** not present on TCK-12 or TCK-99
- **Flags:** (a) untraceable
```
