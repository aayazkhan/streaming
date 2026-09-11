# Phase 1 security controls

- Passwords use Argon2id with memory and iteration cost configured in code; raw passwords are wiped from temporary character arrays.
- Refresh tokens are generated with `SecureRandom`, are device-bound, rotated, and persisted only as SHA-256 digests.
- JWT validation checks HMAC signature, issuer, audience, expiration, and a non-empty subject.
- Database credentials and JWT secrets are environment-provided and rejected when missing; JWT secrets shorter than 32 characters fail startup.
- SQL uses prepared statements throughout the identity/profile repositories.
- Error responses do not expose exception details; each response carries a correlation ID.
- CORS origins are allowlisted and credentials are enabled only for those origins.
- Profile queries always constrain by the authenticated user ID, preventing cross-account access by ID guessing.

Before production, complete a formal threat model and independent review covering key rotation, rate limiting, email/OTP flows, account recovery, device attestation, audit retention, abuse prevention, and secret-manager integration. Those controls are intentionally not represented as complete until their supporting services are implemented.
