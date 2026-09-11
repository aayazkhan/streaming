# Phase 7 real E2E streaming validation

Phase 7 freezes the streaming architecture and exercises the existing contracts against infrastructure, real media, real providers, and native clients. It must not be marked complete from JVM compilation or mocked DRM transport alone.

## Repository harness

Generate the controlled ten-second MP4 fixture:

```bash
bash infrastructure/e2e/generate-golden-fixture.sh
```

Run the Docker health/readiness smoke gate:

```bash
bash infrastructure/e2e/run-infrastructure-smoke.sh
```

The smoke runner starts PostgreSQL, MinIO, the API gateway, and the media worker, then checks `/health` and `/ready`. Redis, Kafka, OpenSearch, CDN, and DRM services are not enabled in the current local Compose profile and must be added only when their real environments are available.

Run the seeded-content golden media path inside the Compose network:

```bash
export ADMIN_TOKEN='...'
export CONTENT_ID='...'
docker compose -f infrastructure/docker/docker-compose.yml --profile e2e run --rm e2e
```

The E2E image generates the fixture internally when it is absent. The standalone generator is available when FFmpeg is installed locally. The golden path performs multipart upload, checksum declaration, part upload, completion, durable job polling, and asserts that the job reaches `READY` with HLS/DASH and thumbnail artifact keys. It requires an existing published content row and an administrator token because content-authoring/admin provisioning is not currently an API operation.

## Security and provider gates

| Gate | Required negative cases |
|---|---|
| CDN/origin | Expired grant, wrong resource, invalid signature, direct origin, expired segment, unauthorized user, replay, cache and range behavior |
| DRM | Invalid/expired entitlement, invalid token, license failure, renewal, revocation, device limits, offline license expiration |
| Android | HLS/DASH, Widevine, ABR, tracks, seek/resume, PiP/background, MediaSession, QoE and failure injection |
| iOS | HLS, FairPlay SPC/CKC, certificate handling, background/PiP/AirPlay, offline FairPlay |
| Web | HLS/DASH, EME browser matrix, browser DRM support, token expiry and network transitions |
| Offline | Entitlement, encrypted download, offline license, airplane mode, expiration, deletion, interrupted download recovery |

The critical CDN assertion is that a segment request without a valid grant cannot retrieve bytes from either the CDN or the origin.

For a configured CDN/origin environment, run the negative-case runner with `CDN_MANIFEST_URL`, `CDN_SEGMENT_URL`, and `VALID_TOKEN`; optionally provide `EXPIRED_TOKEN`, `ORIGIN_MANIFEST_URL`, and `PLAYBACK_VALIDATION_URL`:

```bash
bash infrastructure/e2e/cdn-negative-cases.sh
```

## Golden paths

Online:

```text
Seed content → upload fixture → process job → READY → private origin/CDN
→ playback grant → DRM license → native playback → QoE → position → continue watching
```

Offline:

```text
entitlement → download grant → encrypted media → offline DRM license
→ disable network → playback → verify expiration → delete download
```

## Release matrix

| Gate | Status |
|---|---|
| Repository build, unit tests, QoE, fault injection | PASS |
| Docker smoke | PENDING / not executable when Docker is unavailable |
| Real media pipeline | PENDING |
| CDN authorization and origin protection | PENDING |
| Widevine/FairPlay/PlayReady | PENDING |
| Android/iOS/Web E2E | PENDING |
| Offline DRM | PENDING |
| Load and security testing | PENDING |
