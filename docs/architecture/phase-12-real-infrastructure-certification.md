# Phase 12 real infrastructure certification

Phase 12 executes the frozen streaming architecture against real infrastructure. It does not add new platform abstractions.

The strict execution command is:

```bash
CERTIFICATION_MODE=strict \
CERTIFICATION_PHASE=12 \
CERTIFICATION_ENVIRONMENT=production-certification \
STOP_COMPOSE=true \
./infrastructure/e2e/run-certification.sh
```

The run must exercise infrastructure, the golden media pipeline, CDN/origin authorization, real Widevine/FairPlay/browser DRM, Android/iOS/Web playback, offline licenses, API/media load, and security tests. Every required gate must produce a real evidence artifact.

The authoritative result is:

```text
executionStatus: COMPLETE
overallStatus: GO
productionReady: true
```

until then:

```text
executionStatus: INCOMPLETE
overallStatus: NO-GO
productionReady: false
```

Missing Docker, credentials, CDN/origin, devices, browsers, offline-license services, load infrastructure, or security tooling is `NOT_EXECUTED` and therefore `NO-GO`.
