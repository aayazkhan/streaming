# Functional requirements

Each requirement reflects what is actually implemented in the codebase today
(traced to the route/page that implements it), not aspirational scope. Planned-but-
not-built functionality lives in [roadmap.md](roadmap.md) instead.

Status legend: **Implemented** = backend route + at least one client consumes it.
**Backend only** = API exists, no client screen wired to it yet, or vice versa.

## Accounts (identity-service)

| ID | Requirement | Status |
|---|---|---|
| FR-1.1 | A visitor can register a new account. | Implemented — `POST /auth/register`, `webApp/RegisterPage` |
| FR-1.2 | A registered user can log in and receive a session. | Implemented — `POST /auth/login`, `webApp/LoginPage`, `adminApp/LoginPage` |
| FR-1.3 | A logged-in user's session can be refreshed without re-entering credentials. | Implemented — `POST /auth/refresh` |
| FR-1.4 | A user can log out, invalidating their session. | Implemented — `POST /auth/logout` |
| FR-1.5 | The first admin account can be bootstrapped on a fresh deployment. | Implemented — `POST /auth/admin/bootstrap` |

## Profiles (profile-service)

| ID | Requirement | Status |
|---|---|---|
| FR-2.1 | An account can have multiple viewer profiles. | Implemented — `webApp/ProfileSelectPage` |
| FR-2.2 | A profile can be edited or deleted. | Implemented — `PUT/DELETE /profiles/{profileId}` |

## Catalog & discovery (content-service)

| ID | Requirement | Status |
|---|---|---|
| FR-3.1 | A viewer sees a personalized/curated home feed. | Implemented — `GET /home`, `webApp/HomePage` |
| FR-3.2 | A viewer can browse the catalog by genre/category. | Implemented — `webApp/BrowsePage` |
| FR-3.3 | A viewer can view details for a specific title. | Implemented — `GET /content/{contentId}`, `webApp/ContentDetailPage` |
| FR-3.4 | A viewer sees similar/related titles on a detail page. | Implemented — `GET /content/{contentId}/similar` |
| FR-3.5 | An admin can create, edit, and delete catalog entries. | Implemented — `PUT/DELETE /admin/content/{contentId}`, `adminApp/ContentEditorPage` |
| FR-3.6 | An admin can list/manage genres. | Implemented — `GET /admin/genres` |

## Search (search-service)

| ID | Requirement | Status |
|---|---|---|
| FR-4.1 | A viewer can search the catalog by keyword. | Implemented — `GET /search`, `webApp/SearchPage` |
| FR-4.2 | A viewer sees autocomplete suggestions while typing a search query. | Implemented — `GET /search/autocomplete` |
| FR-4.3 | A viewer's past searches are retained as search history. | Implemented — `GET /search/history` |

## Watchlist (watchlist-service)

| ID | Requirement | Status |
|---|---|---|
| FR-5.1 | A viewer can add a title to their watchlist. | Implemented — `PUT /watchlist/{contentId}` |
| FR-5.2 | A viewer can remove a title from their watchlist. | Implemented — `DELETE /watchlist/{contentId}` |
| FR-5.3 | A viewer can view their watchlist. | Implemented — `webApp/WatchlistPage` |

## Playback (playback-service, drm-service)

| ID | Requirement | Status |
|---|---|---|
| FR-6.1 | A viewer can start a playback session for a title. | Implemented — `POST /playback/sessions`, `webApp/PlayerPage` |
| FR-6.2 | Playback position is saved as the viewer watches. | Implemented — `PATCH /playback/sessions/{sessionId}/position` |
| FR-6.3 | A viewer resumes a title from where they left off ("continue watching"). | Implemented — `GET /playback/continue-watching` |
| FR-6.4 | Playback telemetry/events are recorded during a session. | Implemented — `POST /playback/sessions/{sessionId}/events` |
| FR-6.5 | Playback access is authorized via a signed grant before content is served. | Implemented — `GET /playback/grants/validate` |
| FR-6.6 | Protected content can be played back under DRM. | Contracts/adapters implemented; real vendor DRM license servers not connected — see [phase-5](architecture/phase-5-real-playback.md) |

## Media upload & processing (media-service, media-worker)

| ID | Requirement | Status |
|---|---|---|
| FR-7.1 | An admin can upload a source media file. | Implemented — `POST /media/uploads` |
| FR-7.2 | Large source files can be uploaded via multipart upload. | Implemented — `POST /media/uploads/multipart`, part upload, `complete-multipart`, `abort` |
| FR-7.3 | An admin can check the status of a processing job. | Implemented — `GET /media/jobs/{jobId}` |
| FR-7.4 | An admin can cancel an in-progress processing job. | Implemented — `POST /media/jobs/{jobId}/cancel` |
| FR-7.5 | Uploaded media is transcoded into streamable renditions by a background worker. | Implemented — `media-worker`, FFmpeg-based providers, see [phase-3](architecture/phase-3-media.md) |

## Out of scope today

Monetization/payments, live streaming, social features, and recommendation ML are
tracked as planned phases (13, 14, 16, 17, 18) in [roadmap.md](roadmap.md) and are
not yet implemented anywhere in the codebase.
