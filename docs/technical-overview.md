# Technical overview

For phase-by-phase implementation detail and rationale, see
[docs/architecture](architecture/decisions.md). This page is the map that ties the
codebase layout to the services and modules it contains.

## Stack

- **Backend**: Kotlin, Ktor (HTTP routing), Gradle multi-module build, PostgreSQL,
  OpenSearch (search routing), S3-compatible object storage (MinIO locally),
  FFmpeg (rendition/packaging).
- **Shared domain logic**: Kotlin Multiplatform (KMP) modules under `shared/`,
  consumed by the backend and (via a Kotlin/Native XCFramework) by the iOS app.
- **Web/Admin**: TypeScript + React (Vite), served via nginx in Docker.
- **Android**: native Android app consuming the shared KMP modules directly.
- **iOS**: native Swift app consuming shared KMP business logic through a
  Kotlin/Native XCFramework (`shared/iosKit`).
- **CI**: GitHub Actions (`.github/workflows/ci.yml`, `certification.yml`).
- **Infra-as-code**: Docker Compose (local), Kubernetes manifests, Terraform
  environments, under `infrastructure/`.

## Repository layout

```text
apps/                 Client applications (integration boundaries + UI)
  adminApp/            React admin console (catalog management)
  androidApp/          Native Android client
  iosApp/              Native iOS client
  webApp/              React viewer-facing web client
backend/              Independently deployable Ktor services
  api-gateway/          Routes/aggregates requests to downstream services
  identity-service/     Registration, login, token refresh/logout
  profile-service/      Viewer profiles per account
  content-service/      Catalog browse/detail + admin content CRUD
  search-service/        Full-text search, autocomplete, search history
  playback-service/      Playback sessions, position, continue-watching, grants
  watchlist-service/     Per-profile watchlist
  media-service/         Upload orchestration (incl. multipart) + job status
  media-worker/          Background worker: processes uploaded media into renditions
  drm-service/           DRM provider integration contracts (library, not its own API)
docs/                 Architecture, API, security, and operations documentation
infrastructure/       Docker, Kubernetes, Terraform, CI, monitoring, E2E harness
shared/               Platform-neutral KMP domain/data contracts and use cases
streaming/            Media processing and storage building blocks used by services
certification/        Machine-generated release certification evidence
```

## Service responsibilities (backend)

| Service | Responsibility | HTTP surface (representative) |
|---|---|---|
| `api-gateway` | Entry point; routes to downstream services | — (routing/config, see `GatewayConfig.kt`) |
| `identity-service` | Account auth | `POST /auth/register`, `/login`, `/refresh`, `/logout`, `/auth/admin/bootstrap` |
| `profile-service` | Per-account viewer profiles | `PUT/DELETE /profiles/{profileId}` |
| `content-service` | Catalog browse/detail + admin CRUD | `GET /home`, `/content/{id}`, `/content/{id}/similar`; `PUT/DELETE /admin/content/{id}`, `GET /admin/genres` |
| `search-service` | Search | `GET /search`, `/search/autocomplete`, `/search/history` |
| `playback-service` | Playback session lifecycle | `POST /playback/sessions`, `PATCH /playback/sessions/{id}/position`, `POST /playback/sessions/{id}/events`, `GET /playback/continue-watching`, `GET /playback/grants/validate` |
| `watchlist-service` | Per-profile watchlist | `PUT/DELETE /watchlist/{contentId}` |
| `media-service` | Upload orchestration | `POST /media/uploads`, `/uploads/multipart`, part upload/complete/abort, `GET /media/jobs/{jobId}`, `POST /media/jobs/{jobId}/cancel` |
| `media-worker` | Background job processor (no HTTP surface) | consumes jobs created by `media-service` |
| `drm-service` | DRM provider adapters, consumed as a library by `playback-service` | — |

The exhaustive request/response contract lives in
[docs/api/openapi.yaml](api/openapi.yaml); the table above is a map, not the
source of truth.

## Shared (`shared/`) modules

Platform-neutral Kotlin used by backend services and, via KMP, by the mobile
clients: `authentication`, `common`, `content`, `core`, `database`, `network`,
`playback`, `profile`, `search`, `security`, `user`, `watchlist`, `testing`
(shared test utilities), and `iosKit` (the iOS-facing XCFramework boundary).

## Local development

See [docs/deployment/local-development.md](deployment/local-development.md) for
the full setup. Summary: bring up PostgreSQL + MinIO via Docker Compose, export
the documented environment variables, run `gradle :backend:api-gateway:run`.
