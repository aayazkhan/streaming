# Phase 8 production streaming certification

Phase 8 is the release-certification phase for the frozen VOD architecture. It does not introduce subscriptions or new streaming abstractions.

## Repository evidence

- `MediaWorkerMetricsObserver` records claims, completion/failure/cancellation, retry scheduling, processing duration, heartbeat renewals, and lease losses.
- The golden path emits a machine-readable trace containing correlation ID, elapsed time, worker ownership, attempts, heartbeat, progress, and generated artifact keys.
- `cdn-negative-cases.sh` checks valid manifest/segment access and denial for missing grants, invalid signatures, expired grants, wrong resources, and direct-origin access when configured.
- The multipart path signs and sends per-part SHA-256 checksums before job completion.

## Certification sequence

1. Run Docker/Compose smoke and capture service health, readiness, image versions, and logs.
2. Run the seeded golden path and archive its JSON trace, worker logs, job state transitions, retry count, and artifacts.
3. Kill a worker during processing; verify lease expiry, reclaim by another worker, and correct retry/idempotency behavior.
4. Exercise checksum mismatch, invalid media, storage outage, timeout, cancellation, duplicate submission, and duplicate completion.
5. Run CDN positive and negative cases, including direct-origin denial, expiry, resource binding, replay, caching, and range requests.
6. Run real Widevine, FairPlay, and applicable Web EME license flows with entitlement/device/revocation negatives.
7. Certify Android first, then iOS and browser matrices, including QoE and complete player lifecycle.
8. Certify offline encrypted downloads after online DRM passes, including restart, reboot, airplane mode, expiry, and deletion.

## Performance evidence

Record, rather than assume, the following baselines:

| Area | Measurements |
|---|---|
| API | p50/p95/p99 latency, error rate, saturation |
| Playback | startup time, first-frame failure, rebuffer ratio, playback failures, bitrate, completion |
| Media pipeline | upload, queue wait, transcode, package, thumbnail, total processing duration |
| Worker | jobs/minute, CPU/memory, retries, lease losses, failed-job rate |

Initial API targets are p50 < 100 ms, p95 < 300 ms, and p99 < 500 ms for playback/session control APIs. Playback and media-worker targets should be set from the first real-infrastructure baseline and then enforced as regression thresholds.

## Release gate

Production VOD certification requires PASS for Docker, golden media, worker recovery, multipart integrity, CDN authorization, origin protection, DRM systems, Android/iOS/Web playback, offline playback, QoE, load, and security testing. JVM tests, mocked DRM transport, or a generated artifact alone cannot satisfy those gates.
