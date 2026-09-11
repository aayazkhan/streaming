# Phase 1 foundation architecture

## Dependency direction

```text
platform UI / native media adapters
                ↓
shared authentication, user, profile use cases
                ↓
shared network + security + database contracts
                ↓
shared core + common primitives

API gateway → identity service → PostgreSQL
            → profile service → PostgreSQL
```

The shared modules expose interfaces and immutable serializable models. The gateway composes service implementations and owns cross-cutting concerns: JSON negotiation, CORS, correlation IDs, JWT verification, error envelopes, readiness, and database migration startup. Services do not expose their database connections to clients.

## Authentication flow

1. The client sends credentials and a device identifier to `/v1/auth/register` or `/v1/auth/login`.
2. The identity service hashes passwords with Argon2id and returns a short-lived signed JWT plus a random refresh token.
3. Only a SHA-256 digest of the refresh token is stored in PostgreSQL.
4. Refresh requests require the original device identifier. Rotation revokes the previous token before persisting the replacement.
5. API routes accept only JWTs whose signature, issuer, audience, expiry, and subject validate.

Access tokens are intentionally not persisted. Client-side token persistence is represented by `SecureTokenStore`, whose Android/iOS implementations must use platform secure storage.

## Boundaries reserved for Phase 2+

Playback, DRM, download managers, HLS/DASH manifests, CDN authorization, and telemetry are not placed in shared UI code. Shared playback will own state and intent contracts; native adapters will own Media3, AVFoundation, and EME/HTML5 integration. The backend will issue short-lived playback grants only after entitlement checks are introduced in the monetization phase.
