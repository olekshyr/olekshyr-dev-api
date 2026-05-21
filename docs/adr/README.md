# Architecture Decision Records

This directory contains Architecture Decision Records (ADRs) for the project. An ADR documents a significant architectural decision: the context that motivated it, the decision itself, and its consequences.

## Index

| ADR | Title | Status |
|-----|-------|--------|
| [ADR-001](ADR-001-api-key-authentication.md) | API Key Authentication Strategy | Accepted |

## Format

Each ADR follows this structure:

- **Context** — the problem and constraints that drove the decision
- **Decision** — what was decided and why
- **Consequences** — benefits, tradeoffs, and things this decision rules out

## Workflow

- New ADRs are numbered sequentially (`ADR-NNN`).
- Once accepted, an ADR is not edited — superseded decisions get a new ADR that references the old one and marks it as **Superseded**.
- Status values: `Proposed` → `Accepted` | `Rejected` | `Superseded`.
