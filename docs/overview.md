# Project overview (non-technical)

## What this is

A video streaming platform, similar in shape to services like Netflix or Disney+:
users browse a catalog, search for titles, build a watchlist, and play video content
that resumes where they left off across devices. There is also an admin console for
managing the catalog (adding/editing/removing titles, uploading media).

## Who it's for

- **Viewers** — browse, search, watch, and track video content via web, Android, or
  iOS.
- **Content administrators** — manage the catalog and upload/process media through
  the admin console.
- **Developers/operators** — deploy and run the platform (see
  [docs/deployment](deployment/local-development.md)).

## What it does today (implemented)

- **Accounts** — registration, login, session refresh/logout.
- **Profiles** — multiple viewer profiles per account (like household profiles on
  commercial streaming apps), each with its own watch history and watchlist.
- **Browse & discover** — a home feed, genre/catalog browsing, and content detail
  pages with "similar titles" recommendations.
- **Search** — full-text search with autocomplete and search history.
- **Watchlist** — add/remove titles to a personal "watch later" list.
- **Playback** — starting a playback session, resuming from last position
  ("continue watching"), and playback telemetry.
- **Content administration** — an admin web app for creating, editing, and removing
  catalog entries and genres.
- **Media pipeline** — uploading source video (including large multipart uploads),
  and a worker that processes it into streamable renditions.
- **DRM & licensing groundwork** — the contracts and provider integration points
  needed for protected playback (see limits below).

## What's intentionally not real yet

This is a from-scratch build, not a production deployment, so some pieces are
built as contracts/integration points rather than live third-party integrations:

- No live CDN edge/origin, vendor DRM license servers, or payment processor are
  connected.
- No monetization (plans, subscriptions, payments) — see phases 13–14 in
  [docs/roadmap.md](roadmap.md).
- No live streaming/channels, social features, or recommendation ML — see phases
  16–18 in the roadmap.

The [roadmap](roadmap.md) tracks this phase by phase, and each phase's
architecture doc states exactly what's implemented vs. pending external
validation.

## Where to go next

- New to the codebase? Start with [technical-overview.md](technical-overview.md).
- Want to know exactly what each feature does and doesn't do?
  See [functional-requirements.md](functional-requirements.md).
- Curious about performance/security/reliability expectations?
  See [non-functional-requirements.md](non-functional-requirements.md).
- Unfamiliar terms? See [glossary.md](glossary.md).
- Want to contribute? See [../CONTRIBUTING.md](../CONTRIBUTING.md).
