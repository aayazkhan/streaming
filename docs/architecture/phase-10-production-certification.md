# Phase 10 production certification

Phase 10 is the final VOD certification and hardening phase before monetization. It executes Phase 9 against real infrastructure and does not add product features.

## Certification flow

```text
Real infrastructure
  ↓
Certification runner (strict)
  ↓
Golden media path
  ↓
CDN and origin authorization
  ↓
Real DRM and native/web playback
  ↓
Offline playback
  ↓
API and media load tests
  ↓
Security tests
  ↓
certification.json / certification.md
  ↓
GO or NO-GO
```

Run strict certification with:

```bash
CERTIFICATION_MODE=strict \
CERTIFICATION_ENVIRONMENT=production-candidate \
STOP_COMPOSE=true \
./infrastructure/e2e/run-certification.sh
```

The manual CI workflow is strict by default and uploads the complete `certification/` directory even when a gate fails.

## Release artifacts

The authoritative artifacts are:

```text
certification/
├── certification.json
├── certification.md
├── golden-path.json
├── worker-metrics.json
├── cdn-results.json
├── drm-results.json
├── playback-results.json
├── offline-results.json
├── load-results.json
└── security-results.json
```

Every hard gate must be `PASS`. Missing Docker, credentials, CDN, devices, browsers, or offline-license infrastructure is `NOT_EXECUTED`, never `PASS`.

## Certification matrix

The release decision requires repository tests, Docker/Compose, golden path, multipart integrity, worker recovery, CDN/origin protection, Widevine, FairPlay, applicable PlayReady/EME coverage, Android Media3, iOS AVPlayer, Web EME, offline DRM, API load, CDN/media performance, and security testing.

Any `FAIL` or `NOT_EXECUTED` gate produces a `NO-GO` certification decision. Only after this matrix produces an all-`PASS` `certification.json` should the platform status change from `Certification PENDING` to `CERTIFIED`. Subscription and entitlement work follows that decision.
