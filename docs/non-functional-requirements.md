# Non-functional requirements

Status reflects what's verifiable in the codebase today, not target aspirations.
Where a control is designed but not independently verified, that's stated
explicitly — see the linked source doc for detail.

## Security

| Requirement | Status |
|---|---|
| Passwords hashed with a memory-hard algorithm (Argon2id); raw passwords not retained in memory longer than necessary | Implemented — [phase-1 controls](security/phase-1-controls.md) |
| Refresh tokens are device-bound, rotated, and stored only as digests (never plaintext) | Implemented — same doc |
| JWTs are validated for signature, issuer, audience, expiry, and subject | Implemented — same doc |
| Required secrets (DB credentials, JWT secret) fail startup if missing or too short, rather than defaulting silently | Implemented |
| SQL access uses prepared statements (no string-concatenated queries) | Implemented — identity/profile repositories |
| Error responses never leak exception internals; each carries a correlation ID for support/debugging | Implemented |
| CORS is allowlist-based; credentials only enabled for allowlisted origins | Implemented |
| A profile cannot read/modify another account's data by guessing IDs | Implemented — queries always scope by authenticated user ID |
| Formal threat model, key rotation, rate limiting, OTP/email verification, account recovery, device attestation, audit retention, secret-manager integration | **Not done** — explicitly called out as pre-production gaps in [phase-1 controls](security/phase-1-controls.md) |
| DRM license enforcement against real vendor providers | **Not connected** — contracts/adapters exist, no live vendor integration ([phase-5](architecture/phase-5-real-playback.md)) |

## Observability

| Requirement | Status |
|---|---|
| HTTP request rate, and p50/p95/p99 latency per route, are collected and dashboarded | Implemented and verified end-to-end against real traffic — [monitoring README](../infrastructure/monitoring/README.md) |
| 4xx/5xx error rate by status code is dashboarded | Implemented |
| Database connection pool utilization / wait time / readiness failures are exposed | **Not wired** — HikariCP exposes these natively but they aren't registered yet |
| Alerting on auth failures, refresh-token replay, device mismatch, registration conflicts | **Not implemented** — no rules exist yet |
| Pod restarts / CPU / memory saturation dashboards | Covered for free by the bundled `kube-prometheus-stack`, but no custom alert rules on top of it yet |
| Credentials/tokens are never written to logs | Stated policy; not independently re-verified in the current pass |

## Reliability / resilience

| Requirement | Status |
|---|---|
| Media processing jobs run with durable state, retry, and cancellation | Implemented — [phase-4](architecture/phase-4-production-playback.md) |
| Worker leases use heartbeats and bounded concurrency (so a crashed worker's job can be picked up) | Implemented — [phase-5](architecture/phase-5-real-playback.md) |
| Deterministic playback fault injection exists for testing failure paths | Implemented — [phase-6](architecture/phase-6-production-validation.md) |
| Real CDN edge/origin failover and multi-region resilience | **Not implemented** — planned phase 20 in [roadmap.md](roadmap.md) |

## Performance / scalability

| Requirement | Status |
|---|---|
| Search is routed through OpenSearch rather than ad hoc DB queries for scale | Implemented — [phase-2](architecture/phase-2-vod.md) |
| Playback grants are signed and resource-scoped (segment-aware) rather than broad session tokens | Implemented — [phase-5](architecture/phase-5-real-playback.md) |
| Load testing against real infrastructure/CDN | **Pending** — gated on external infrastructure per [phase-9](architecture/phase-9-certification-execution.md)/[phase-12](architecture/phase-12-real-infrastructure-certification.md) |
| Documented capacity targets (RPS, concurrent streams, latency SLOs) | **Not yet defined** — no target numbers exist in the repo; this should be filled in once real load-test evidence exists |

## Compliance / release governance

| Requirement | Status |
|---|---|
| Release readiness is captured as a machine-readable, immutable certification report | Implemented — `certification/certification.json`/`.md`, [phase-10](architecture/phase-10-production-certification.md) |
| A failed or unexecuted hard gate can never be silently promoted to a pass | Implemented — [phase-11](architecture/phase-11-real-production-certification.md) |
| CI runs the certification workflow in strict mode on demand | Implemented — `.github/workflows/certification.yml` |
| Regulatory compliance (e.g. content licensing region rules, payment compliance, data-privacy regulations like GDPR/CCPA) | **Not addressed** — no monetization or region-gating exists yet ([roadmap.md](roadmap.md) phases 13–14, 20) |

## Known gaps summary

The single most useful thing to internalize: this repo is honest about the
difference between "the contract/interface is implemented" and "it's been
proven against real infrastructure." Anything under "Pending"/"Not implemented"/
"Not wired" above is a legitimate open task, several of which are also listed in
[CONTRIBUTING.md](../CONTRIBUTING.md).
