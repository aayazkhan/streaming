# Architecture decisions

## ADR-001: KMP owns contracts, not native media

Kotlin Multiplatform is used for domain models, state, repository contracts, networking, security abstractions, and synchronization. Native media and DRM remain behind platform interfaces because Widevine, FairPlay, PlayReady, and EME have different lifecycle and secure-key requirements.

## ADR-002: Opaque rotating refresh tokens

Refresh tokens are random, device-bound, rotated on use, and stored as hashes. This limits the impact of database disclosure and makes replay detectable at the token record boundary. JWT access tokens are short-lived and stateless.

## ADR-003: PostgreSQL is the source of truth for identity/profile state

Redis, Kafka, search, and object storage will be introduced for their respective workloads. They are not used as the authoritative store for accounts, sessions, or profiles.

## ADR-004: Versioned API envelopes

All client-visible failures use `ApiErrorResponse` with a stable code and correlation ID. This keeps UI behavior independent from backend exception text and supports support-ticket tracing.
