# Contributing

This is a Kotlin Multiplatform streaming platform delivered by roadmap phase (see
[docs/roadmap.md](docs/roadmap.md) for the phase-level plan). This document lists
concrete, self-contained work items that don't require phase-level design first —
pick one, open a PR against `main`.

## Open work items

### Backend test coverage

These services have no `src/test` yet, while `drm-service`, `identity-service`, and
`playback-service` do — use those as the pattern to follow (test framework, fixture
style, mocking approach):

- [ ] `backend/api-gateway`
- [ ] `backend/content-service`
- [ ] `backend/media-service`
- [ ] `backend/media-worker`
- [ ] `backend/profile-service`
- [ ] `backend/search-service`
- [ ] `backend/watchlist-service`

Start with `profile-service` or `watchlist-service` — their domain logic is the most
self-contained. `media-service`/`media-worker` will need S3/FFmpeg provider mocking,
so tackle those after the pattern is established.

### Client test coverage

- [ ] `apps/androidApp` — no tests yet
- [ ] `apps/iosApp` — no tests yet
- [ ] `apps/adminApp` — only 2 test files (`apiClient.test.ts`, `tokenStore.test.ts`)
- [ ] `apps/webApp` — only 2 test files

### CI (`.github/workflows/ci.yml`)

- [ ] Add a job that runs the JS app test suites (`npm test` in `apps/adminApp` and
      `apps/webApp`) — currently only the Gradle/JVM side is tested.
- [ ] Add a lint/static-analysis step: ktlint or detekt for the Kotlin modules,
      eslint for the JS apps.

## Guidelines

- Match the existing test framework/style already used in `drm-service`,
  `identity-service`, or `playback-service` for backend work.
- Keep PRs scoped to one service or one CI job at a time — don't bundle unrelated
  changes.
- Don't mark roadmap phases in [docs/roadmap.md](docs/roadmap.md) as complete based
  on local runs alone; several phases explicitly gate on real infrastructure/device
  validation (see that file's phase notes).
