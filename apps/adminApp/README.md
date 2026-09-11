# Admin app

A production React + TypeScript + Vite content-management portal — a **fully separate deployment** from `apps/webApp`, with its own token storage, own login flow, and own Docker image, per the constraint that it must never reuse end-user profile authorization.

## Features

- Admin-only sign-in (rejects tokens whose JWT `role` claim isn't `ADMIN` — this is a UX gate only; every write route is independently re-checked server-side).
- Content list with status filter (DRAFT/PUBLISHED/ARCHIVED), create/edit form, archive action.
- Media upload: pick a video file, it's uploaded directly to object storage via a presigned grant, then polls the transcode job's status.

## Getting started

```bash
npm install
cp .env.example .env
npm run dev   # http://localhost:3001, proxies /v1/* to http://localhost:8080
```

```bash
npm run build
npm run test
```

## Creating the first admin account

There is no admin registration flow — this is intentional, so a JWT can never be forged into an admin role by anything other than this one explicit, server-secret-gated action:

1. Register a normal account (via this app's login page will reject it, so register via `apps/webApp` or `curl`):
   ```bash
   curl -X POST http://localhost:8080/v1/auth/register \
     -H 'Content-Type: application/json' \
     -d '{"email":"admin@example.com","password":"a-strong-password-12+","displayName":"Admin","deviceId":"bootstrap"}'
   ```
2. Promote it, using the `ADMIN_BOOTSTRAP_SECRET` the api-gateway was started with (`infrastructure/docker/docker-compose.yml` sets a local-only default):
   ```bash
   curl -X POST http://localhost:8080/v1/admin/bootstrap \
     -H 'Content-Type: application/json' \
     -d '{"email":"admin@example.com","secret":"local-only-admin-bootstrap-secret"}'
   ```
3. Sign in here with that account.

In a real deployment, rotate or unset `ADMIN_BOOTSTRAP_SECRET` after creating the accounts you need — the endpoint fails closed (503) if it's unset, so removing it afterward disables further bootstrapping rather than leaving a standing hole.

## Backend API this app depends on

`apps/webApp` only ever reads catalog data; this app is what makes that possible — `POST/PUT/DELETE /v1/admin/content`, `GET /v1/admin/content`, `GET /v1/admin/genres` (all admin-JWT-gated, added alongside this app — see `backend/content-service/.../AdminContentRoutes.kt`), plus the pre-existing (already admin-gated) `/v1/media/*` upload/job routes.

## Docker

```bash
docker compose -f ../../infrastructure/docker/docker-compose.yml up -d --build admin
```

Same pattern as `apps/webApp`: multi-stage build, nginx reverse-proxies `/v1/*` to `api-gateway`, served on port 3001.

## Known limitations

Same token-storage tradeoff as `apps/webApp` (access token in memory, refresh token in `sessionStorage` — see that app's README for the full rationale). Explicitly out of scope for this pass, matching this file's own prior framing: moderation queues, audit-log views, and credits/audio-track/subtitle-track editing.
