# Certification evidence policy

Production certification is evidence-based. A gate may be marked `PASS` only when its required external dependency was actually exercised and the result artifact is present.

## Status policy

| Status | Meaning | Can certify production? |
|---|---|---:|
| `PASS` | Required test executed and passed. | Yes |
| `FAIL` | Required test executed and failed. | No |
| `NOT_EXECUTED` | Dependency, credential, device, or environment was unavailable, or the gate was skipped. The certification decision is `NO-GO`. | No |
| `PENDING` | Legacy/local status; normalized to `NOT_EXECUTED` by the report builder. | No |

Examples:

- Docker unavailable → `NOT_EXECUTED`.
- DRM credentials missing → `NOT_EXECUTED`.
- No physical Android/iOS device → `NOT_EXECUTED`.
- No CDN/origin environment → `NOT_EXECUTED`.
- A transport mock passing does not make a real DRM gate `PASS`.

## Required evidence schema

Each gate artifact must provide or be normalized to:

```json
{
  "status": "PASS",
  "startedAt": "2026-08-23T00:00:00Z",
  "completedAt": "2026-08-23T00:01:00Z",
  "durationSeconds": 60,
  "environment": "android-device-lab",
  "testCount": 12,
  "passed": 12,
  "failed": 0,
  "skipped": 0,
  "artifacts": [],
  "failureReason": null
}
```

`run-certification.sh` produces `NOT_EXECUTED` artifacts for unavailable gates. The authoritative report is `NO-GO` whenever any hard gate is `FAIL` or `NOT_EXECUTED`; local pending mode may return zero so development can inspect the report. `CERTIFICATION_MODE=strict` or `CERTIFICATION_FAIL_ON_PENDING=true` makes any non-`PASS` hard gate exit with status 1. Only an all-`PASS` report produces `GO` and `productionReady: true`.
