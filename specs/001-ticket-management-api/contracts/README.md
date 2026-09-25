# Contracts

The full REST contract (endpoints, request/response schemas, status codes, error shape) lives in [`../api-contract.md`](../api-contract.md) to avoid duplicating it in two places. This directory is the conventional Spec Kit location for machine-readable contracts; add an OpenAPI/JSON Schema export here during implementation if tooling needs it (e.g. `openapi.yaml` generated from the controllers), keeping `api-contract.md` as the human-readable source of truth it is derived from.
