<!--
Sync Impact Report
Version change: [TEMPLATE] → 1.0.0 (initial ratification)
Modified principles: n/a (first fill of template placeholders)
Added sections:
  - Core Principles I–V (Tech Stack Discipline, Single-Command Deployment,
    Test-First & State Machine Coverage, API Consistency & Boundary Validation,
    RAG Grounding & Guardrails)
  - Security & Configuration (Section 2)
  - Spec-Driven Change Workflow (Section 3)
  - Governance
Removed sections: none (placeholders only)
Deferred / TODO placeholders: none — RATIFICATION_DATE set to today per initial adoption.
Templates requiring follow-up: none checked in this run (constitution-only scope).
-->

# Ticket Management System Constitution

## Core Principles

### I. Tech Stack Discipline
Backend MUST be Java 21 + Spring Boot + Spring AI. Persistence MUST use MySQL in
production and H2 for local development and tests — no other database engine may be
introduced without a constitution amendment. Vector storage MUST use Elasticsearch via
Spring AI's `VectorStore` abstraction; no other vector store may be added silently.
Frontend MUST be React or Next.js. All external interfaces MUST be exposed as REST APIs.
Rationale: a fixed, narrow stack keeps the system runnable by anyone with Docker and
prevents silent dependency sprawl across an AI-assisted codebase.

### II. Single-Command Deployment
The entire system (backend, frontend, MySQL, Elasticsearch) MUST start with a single
`docker compose up`, each service defined by its own Dockerfile and compose entry. No
manual setup step (seeding, manual migrations, manual index creation, etc.) may be required
beyond providing an `.env` file. Any new service added to the system MUST ship with its own
Dockerfile/compose entry in the same change. Rationale: reproducibility for reviewers,
graders, and future contributors depends on zero hidden setup steps.

### III. Test-First & State Machine Coverage
Services and validation logic MUST have unit tests. The ticket state machine MUST have
integration tests covering every valid transition AND every invalid transition (rejected
transitions must assert the rejection, not just skip it). RAG output MUST NOT be tested with
binary pass/fail assertions; it MUST be evaluated separately using labeled question/ticket
pairs (a golden set) with an explicit, documented scoring approach (e.g., relevance/citation
accuracy), because LLM output is probabilistic and pass/fail assertions produce false
failures. Rationale: deterministic logic (state machine, validation) deserves deterministic
tests; probabilistic logic (RAG) needs a fundamentally different evaluation method.

### IV. API Consistency & Boundary Validation
REST endpoints MUST follow REST resource naming conventions (plural nouns, HTTP verbs for
actions, no verbs in paths). All error responses MUST share one structured shape (e.g.
consistent fields for code, message, details) across every endpoint. Input validation MUST
happen at the API boundary (controller/DTO layer) before it reaches service logic — services
MUST NOT re-implement boundary validation as their primary defense. Rationale: a predictable
API contract is required for both the frontend and any future integrations, and boundary
validation stops bad data before it can pollute business logic or storage.

### V. RAG Grounding & Guardrails
Chunking convention, embedding model choice, and retrieval-tuning defaults (top-K,
similarity threshold) MUST be documented in the repository and MUST be configurable
(e.g. via application properties/environment variables) — they MUST NOT be hardcoded
inline in application logic. The assistant MUST answer only from retrieved ticket context,
MUST cite the specific ticket ID(s) it used, and MUST explicitly respond "no relevant
tickets found" when retrieval returns nothing usable, rather than generating an answer from
general knowledge. Rationale: an unreferenced or fabricated answer in a ticket-management
assistant is worse than no answer — it erodes trust and hides the true absence of data.

## Security & Configuration

No secrets (API keys, database passwords, tokens) may be committed to the repository in any
form — not in code, not in `docker-compose.yml`, not in checked-in config files. All
credentials MUST be supplied via environment variables, sourced from an `.env` file that is
git-ignored. A `.env.example` (or equivalent) listing required variable names without values
MUST be kept up to date whenever a new credential is introduced.

## Spec-Driven Change Workflow

Every AI-assisted change MUST be traceable to a spec produced through the Spec Kit workflow
(e.g. `/speckit-specify`, `/speckit-plan`, `/speckit-tasks`) before implementation begins.
"Build the whole feature" prompts that skip spec/plan/task decomposition are prohibited.
Each implementation task MUST reference the spec or plan item it fulfills, so reviewers can
verify scope without re-deriving intent from the diff alone.

## Governance

This constitution supersedes conflicting ad-hoc practices. Amendments require: (1) a
documented rationale for the change, (2) a version bump per semantic versioning (MAJOR for
incompatible principle removal/redefinition, MINOR for new/materially expanded principles or
sections, PATCH for clarifications/wording), and (3) an updated Sync Impact Report at the
top of this file for the review that introduces the change. All pull requests and AI-assisted
changes MUST be checked against these principles before merge; any deviation MUST be
justified in the PR description or rejected. Complexity that violates Principle I (stack
discipline) or Principle II (single-command deployment) MUST be justified in writing or
removed.

**Version**: 1.0.0 | **Ratified**: 2026-09-25 | **Last Amended**: 2026-09-25
