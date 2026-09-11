# Web application

A production React + TypeScript + Vite single-page app for the streaming platform's consumer web client. It talks to the `api-gateway` REST API (`/v1/...`) directly — it does not (yet) share code with the Kotlin Multiplatform `shared/*` modules, which currently target JVM only (see "Architecture notes" below).

## Features

- Email/password registration, login, logout, and silent session restore via refresh token.
- Netflix-style profile selection ("Who's watching?").
- Home feed, catalog browsing with type filters and cursor pagination, content detail pages, search with results, and a watchlist.
- Playback: starts a signed playback grant, plays HLS via `hls.js` (or natively on Safari), and periodically reports watch position for "Continue watching".

## Getting started

```bash
npm install
cp .env.example .env   # defaults to the Vite dev proxy; edit only if you're not using it
npm run dev            # http://localhost:3000, proxies /v1/* to http://localhost:8080
```

The dev server expects the backend stack to be running (see the repo root README: `docker compose -f infrastructure/docker/docker-compose.yml up -d postgres minio api-gateway media-worker`).

```bash
npm run build   # tsc -b && vite build -> dist/
npm run test    # vitest run
```

## Docker

```bash
docker compose -f ../../infrastructure/docker/docker-compose.yml up -d --build web
```

This builds `Dockerfile` (multi-stage: `npm run build` then nginx) and serves the SPA on port 3000, reverse-proxying `/v1/*` to the `api-gateway` service (see `nginx.conf.template`; `API_GATEWAY_HOST`/`API_GATEWAY_PORT` are substituted at container start).

## Architecture notes

- **Why plain TypeScript instead of Kotlin/JS or Compose for Web**: none of the `shared/*` KMP modules currently declare a `js()`/`wasmJs()` target (JVM-only), and there's no Compose Multiplatform dependency in `gradle/libs.versions.toml`. Retrofitting multiplatform targets across 7+ shared modules is a separate investment; this app ships against the REST contract directly instead of waiting on that.
- **DRM**: no browser EME/CDM wiring is implemented — there are no Widevine/PlayReady vendor credentials in this environment. `HtmlMediaPlaybackAdapter.prepare()` takes an optional `DrmConfig` as the documented extension point for when that becomes available; today it always plays unencrypted HLS.
- **hls.js is lazy-loaded** (dynamic `import()`) only when the player route mounts, so it never inflates the initial bundle.

## Known limitations

- **Token storage tradeoff**: the backend returns the refresh token as plain JSON, not an httpOnly cookie, so pure XSS-proof storage isn't achievable purely from the client. The access token is kept in memory only (lost on reload); the refresh token is kept in `sessionStorage` (cleared when the tab closes, not shared across origins). The ideal fix is a backend change to set the refresh token as an httpOnly cookie — tracked as future backend work, not silently worked around here.
- **No "whoami" endpoint**: after a silent session restore via refresh token (e.g. reopening the app in the same tab), the API returns new tokens but not the user's profile summary, so `user` in `AuthContext` stays `null` until the next explicit login. The app treats "authenticated" and "have a display name" as separate states (`isAuthenticated` vs `user`) to handle this correctly rather than papering over it.
- **No public content-authoring endpoint**: the consumer API has no way to create catalog rows, so local testing needs content seeded directly (e.g. via SQL) or through the admin/media-ingest pipeline once it exists.
- Telemetry event recording (`POST /v1/playback/sessions/{id}/events`) is not wired up yet — only position reporting is implemented.
