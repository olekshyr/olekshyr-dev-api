# ADR-001: API Key Authentication Strategy

**Status:** Accepted
**Date:** 2026-05-21

## Context

The API is a backend service consumed by controlled clients — internal systems and trusted partners — not end users in a browser. This shapes the authentication requirements significantly:

- Clients are machines, not humans; session-based flows and browser-oriented protocols add complexity without benefit.
- The caller set is small and known at deployment time.
- The service must be stateless to support horizontal scaling without shared session state.
- Two access tiers are needed: a privileged tier for administrative operations that mutate system state or expose sensitive data, and a general tier for broader query access.

## Decision

Use static API keys delivered in a custom HTTP header. Two distinct keys are issued — one per access tier — enforced by two separate Spring Security filter chains registered at explicit priorities.

The chains are ordered so that the administrative chain is evaluated ahead of the general chain, covering only the administrative route namespace. The general chain covers all remaining routes. A request that presents a valid key for the wrong tier is rejected by the chain that owns that route — key reuse across tiers is not permitted. A separate, higher-priority chain leaves a small set of operational actuator endpoints (health, info) unauthenticated; access to those is expected to be restricted at the infrastructure layer rather than by key.

Each keyed filter chain:

- Is stateless. No session is created or consulted at any point.
- Applies its filter before Spring's default authentication processing.
- Uses a constant-time key comparison to eliminate timing side-channels during key validation.
- Short-circuits the filter chain on rejection, returning the tier's status code (403 for the administrative tier, 401 for the general tier) with an empty response body, and clearing any partial security context.

Spring Security's CSRF protection, HTTP Basic authentication, and form login are all disabled — none are applicable to machine-to-machine API key flows. `UserDetailsService` autoconfiguration is excluded from the application context entirely, since this service has no concept of user identity.

## Consequences

**Benefits:**

- Simple to implement, audit, and reason about. The auth path is a thin filter with no external dependencies at request time.
- No token expiry, refresh logic, or clock-synchronization concerns.
- Strict tier separation ensures that a key valid for the general tier cannot be used to reach administrative routes, and vice versa.
- Constant-time comparison removes a class of timing-based key enumeration attacks.
- Testable in isolation at the web layer without a running database or full application context.

**Tradeoffs:**

- Keys are static and long-lived. Rotation requires updating environment configuration and redeploying (or restarting) the service.
- A compromised key must be rotated immediately; there is no built-in expiry or revocation mechanism.
- All callers sharing a key are indistinguishable at the application layer. There is no per-client identity or per-client audit trail.
- This model is not appropriate for user-facing authentication or for flows that require identity-linked authorization (e.g., row-level access control).

## Alternatives Considered

**JWT (signed stateless tokens):**
Adds token expiry and signing infrastructure, which is useful when individual client identity or short-lived credentials are needed. For this service's threat model — small set of known, infrastructure-level callers — the added key management complexity is not justified.

**OAuth 2.0 client credentials flow:**
Appropriate when a third-party identity provider is already in the infrastructure, or when clients must be individually registered and tracked. Introduces an external runtime dependency and significantly more operational surface. Not warranted for the current caller set.

**Spring Security `UserDetailsService` with in-memory users:**
Designed for user identity models and does not map cleanly onto machine API key patterns. Retaining the autoconfiguration would produce confusing default behaviour (e.g., generated passwords logged on startup) with no practical benefit.

**mTLS (mutual TLS):**
Strong option for service-to-service auth that pushes identity to the transport layer. Requires a PKI, certificate rotation tooling, and infrastructure support. A reasonable future upgrade path if the caller set grows or compliance requirements change.
