# Phase 9 certification execution

Phase 9 executes the frozen streaming architecture. It does not add subscriptions, social features, or new playback abstractions.

## Certification runner

The repository now provides one release-facing command:

```bash
./infrastructure/e2e/run-certification.sh
```

It records evidence under `certification/`:

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
├── security-results.json
├── repository.json
├── docker.json
├── summary.json
├── certification-report.md
└── logs/
```

The authoritative report has two certification decisions:

- `GO`: every hard gate has a valid `PASS` artifact.
- `NO-GO`: at least one hard gate failed or was not executed. The report also exposes `executionStatus: INCOMPLETE` when external gates are unavailable.

Local pending mode may return zero so the report can be inspected. Strict certification fails on every non-`PASS` hard gate:

```bash
CERTIFICATION_MODE=strict ./infrastructure/e2e/run-certification.sh
```

## Execution order

1. Repository tests and build evidence.
2. Docker Compose health/readiness smoke.
3. Golden upload → worker → FFmpeg → `READY` path, including required HLS/DASH artifacts and an optional processing-time threshold.
4. Worker recovery evidence supplied by a real crash/reclaim test.
5. CDN positive and negative authorization cases.
6. Real DRM, native playback, offline, load, and security evidence.

The golden path can enforce a processing threshold with `MAX_PROCESSING_SECONDS`. It writes a failure trace when it exits before reaching `READY`, so a failed run is still auditable.

## External evidence hooks

Provider and environment-specific gates are intentionally injected rather than mocked. Each gate accepts either a command or a pre-generated JSON result artifact:

| Gate | Command variable | Result variable |
|---|---|---|
| Worker recovery | `WORKER_RECOVERY_COMMAND` | `WORKER_METRICS_RESULT_PATH` |
| DRM | `DRM_CERTIFICATION_COMMAND` | `DRM_RESULTS_PATH` |
| Native playback | `PLAYBACK_CERTIFICATION_COMMAND` | `PLAYBACK_RESULTS_PATH` |
| Offline | `OFFLINE_CERTIFICATION_COMMAND` | `OFFLINE_RESULTS_PATH` |
| Load | `LOAD_TEST_COMMAND` | `LOAD_TEST_RESULTS_PATH` |
| Security | `SECURITY_TEST_COMMAND` | `SECURITY_RESULTS_PATH` |

Commands must exit successfully and result artifacts must be valid JSON. The command log is retained under `certification/logs/`.

The CDN runner requires manifest, segment, and valid-grant variables. In certification mode it also requires expired-token, direct-origin, and wrong-resource validation inputs. A negative case that returns `ALLOW` fails the gate.

## Required real-infrastructure gates

The certification report must include evidence for:

- Worker crash, lease expiry, reclaim by another worker, retry/idempotency, cancellation, and storage/media failures.
- CDN token scope, expiration, resource binding, segment authorization, cache/range behavior, and public-origin denial.
- Widevine, FairPlay, and applicable browser DRM acquisition, renewal, expiration, revocation, entitlement, and device-limit behavior.
- Android, iOS, and browser playback lifecycle, QoE, and DRM behavior on real supported clients.
- Encrypted offline download, offline license validation, restart/reboot, interruption/resume, expiration, and deletion.
- Separate API and CDN/media load tests.
- Authentication, authorization, IDOR, token/grant tampering, origin bypass, upload abuse, SSRF, dependency, and secret-scanning tests.

JVM tests, transport mocks, or a generated report with `NOT_EXECUTED` gates do not certify production streaming.
