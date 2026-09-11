# Phase 11 certification transition reference

Phase 11 freezes the certification semantics and preserves the operational gate checklist. Real execution is Phase 12; see [phase-12-real-infrastructure-certification.md](phase-12-real-infrastructure-certification.md). It introduces no new foundation abstractions.

## Required gates

### Gate A — Infrastructure

Exercise Docker or Kubernetes with PostgreSQL, Redis, MinIO/S3, API Gateway, Media Service, Media Worker, and DRM Service. Capture health/readiness, versions, logs, correlation IDs, and service connectivity.

### Gate B — Real media

Prove MP4 multipart upload, per-part checksum verification, worker claim, FFmpeg processing, HLS/DASH packaging, thumbnail/subtitle generation, and `READY` publication. Verify worker crash, lease expiry, reclaim, retry, cancellation, duplicate submission, and storage failure behavior.

### Gate C — CDN and origin

Validate valid grants, expiration, wrong resource, wrong scope, invalid signature, segment authorization, cache/range behavior, replay behavior, and direct-origin denial. A media segment must never be reachable without a valid grant.

### Gate D — DRM

Use real sandbox/vendor credentials for Android Widevine, iOS FairPlay, and applicable Web EME/PlayReady. Test acquisition, renewal, expiration, revocation, entitlement, device limits, and concurrent playback limits.

### Gate E — Real clients

Run Android Media3, iOS AVPlayer, and supported browser matrices against real devices/browsers. Capture startup, first frame, ABR, seek, audio/subtitles, lifecycle, DRM, errors, and QoE telemetry.

### Gate F — Offline

Authorize download, obtain an offline license, store encrypted media, disable networking, restart/reboot, play successfully, and verify expiration, cancellation, interruption/resume, storage exhaustion, and deletion behavior.

### Gate G — Performance

Load authentication, content, search, playback sessions, watch-position, watchlist, and continue-watching APIs separately from CDN/media traffic. Record latency percentiles, RPS, error rate, CPU/memory, DB/Redis saturation, cache hit ratio, origin traffic, bandwidth, and QoE.

### Gate H — Security

Run authentication bypass, authorization/IDOR, token manipulation, refresh-token replay, device-limit bypass, playback-grant tampering, CDN/origin bypass, rate limiting, admin authorization, upload abuse, media-processing security, dependency scanning, and secret scanning.

## Release decision

Run the strict runner:

```bash
CERTIFICATION_MODE=strict \
CERTIFICATION_ENVIRONMENT=production-candidate \
STOP_COMPOSE=true \
./infrastructure/e2e/run-certification.sh
```

The authoritative decision is simple:

- Any `FAIL` → `NO-GO`.
- Any `NOT_EXECUTED` → `NO-GO`.
- All required gates `PASS` → `GO` and `productionReady: true`.

Only the all-`PASS` report can advance the roadmap to subscriptions and monetization.
