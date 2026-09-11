# Streaming Platform

Kotlin Multiplatform video platform delivered by roadmap phase. The repository now includes the Phase 1 foundation, Phase 2 VOD control plane, and the Phase 3 storage/media/native-playback seams.

## Repository layout

```text
apps/                 Platform integration boundaries and UI ownership
backend/              API gateway and independently deployable domain services
docs/                 Architecture, API, security, and operations contracts
infrastructure/       Local Docker, Kubernetes, and CI configuration
shared/               Platform-neutral KMP domain/data contracts and use cases
streaming/            Reserved for ingestion, packaging, DRM, and media workers
```

## Run the foundation locally

1. Start PostgreSQL and MinIO with `docker compose -f infrastructure/docker/docker-compose.yml up -d postgres minio`.
2. Export the variables documented in `docs/deployment/local-development.md`.
3. Run `gradle :backend:api-gateway:run`.
4. Check `GET /health` and `GET /ready`.

The API surface is documented in [docs/api/openapi.yaml](docs/api/openapi.yaml). The Android, iOS, web, and admin directories contain integration boundaries; platform players remain native and DRM is intentionally not faked.

## Phase 2 VOD slice

The current branch contains the Phase 2 backend/API slice: catalog hierarchy and discovery, PostgreSQL/OpenSearch search routing, signed CDN playback grants with session/position/telemetry persistence, continue watching, watchlist, and a media-job orchestration boundary. Phase 3 adds presigned S3-compatible uploads, upload lifecycle validation, FFmpeg providers, grant validation, and native adapter scaffolds. See [docs/architecture/phase-3-media.md](docs/architecture/phase-3-media.md) and the [playback capability matrix](docs/architecture/playback-capability-matrix.md).

## Phase 4 production playback

Phase 4 adds durable media-job state/retry/cancellation contracts, real S3-compatible multipart upload sessions, resource-bound CDN grants, shared DRM/offline contracts, and a portable playback state store. Phase 5 adds a deployable JVM media worker, lease heartbeats, bounded concurrency, pipeline progress, provider-backed DRM HTTP adapters, and segment-aware grant scopes. See [docs/architecture/phase-4-production-playback.md](docs/architecture/phase-4-production-playback.md) and [docs/architecture/phase-5-real-playback.md](docs/architecture/phase-5-real-playback.md). A real CDN edge/origin deployment, vendor DRM credentials, and native device/download implementations remain integration work.

## Phase 6 production validation

Phase 6 adds shared QoE metrics, deterministic playback fault injection, and Android-first telemetry boundaries. See [docs/architecture/phase-6-production-validation.md](docs/architecture/phase-6-production-validation.md). Docker smoke execution, real CDN/DRM, native device playback, and offline DRM remain explicit environment gates.

## Phase 7 E2E validation

Phase 7 freezes the streaming architecture and adds the reusable [golden media E2E harness](docs/architecture/phase-7-e2e-validation.md): controlled fixture generation, Docker health/readiness checks, and seeded-content multipart upload/job validation. Real CDN, DRM, native-client, offline, load, and security gates remain pending until their environments are available.

## Phase 8 certification

Phase 8 adds worker certification metrics, machine-readable golden-path traces, CDN negative-case tooling, and the formal production release gate. See [docs/architecture/phase-8-production-certification.md](docs/architecture/phase-8-production-certification.md). It is not a production-ready claim until real infrastructure and devices pass the external gates.

## Phase 9 certification execution

Phase 9 adds the release-facing certification runner and immutable JSON/Markdown report. Run `./infrastructure/e2e/run-certification.sh` to collect repository, Docker, golden-path, CDN, worker, provider, playback, offline, load, and security evidence under `certification/`. Missing external evidence is recorded as `NOT_EXECUTED`; use `CERTIFICATION_MODE=strict` for release certification. See [docs/architecture/phase-9-certification-execution.md](docs/architecture/phase-9-certification-execution.md).

## Phase 10 production certification

Phase 10 enforces the certification evidence policy and makes [certification.json](certification/certification.json) and `certification.md` the authoritative release artifacts. Any failed or unexecuted hard gate produces `NO-GO`; the manual [certification workflow](.github/workflows/certification.yml) always runs in strict mode and exits nonzero.

## Phase 11 operational certification transition

Phase 11 freezes the certification semantics and release governance. `FAIL` and `NOT_EXECUTED` are never promoted to `PASS`.

## Phase 12 real production certification

Phase 12 executes the frozen VOD stack against real infrastructure, CDN/origin, DRM providers, native clients, offline licenses, load targets, and security tests. See [docs/architecture/phase-12-real-infrastructure-certification.md](docs/architecture/phase-12-real-infrastructure-certification.md). No new foundation architecture is planned until certification exposes a concrete defect.
