# Phase 6 production validation

Phase 6 closes the gap between repository implementation and a secure real-device streaming path. It is a validation milestone, not a new business-feature phase.

## Repository-verified slice

- `PlaybackQoeAggregator` calculates startup time, total rebuffering, rebuffer ratio, watch duration, average bitrate, quality/audio/subtitle changes, playback errors, failure rate, and completion from persisted telemetry events.
- Shared telemetry includes first frame, buffer start/end, audio changes, and subtitle changes in addition to the original playback events.
- `ScriptedPlaybackFailureInjector` provides deterministic fault points for CDN manifests/segments, origin, playback tokens, DRM, offline/network transitions, database, downloads, and worker crashes.
- The Android Media3 boundary emits shared start, first-frame, buffering, play/pause, and error telemetry and accepts an injectable manifest failure point.

## Validation matrix

| Gate | Status | Evidence or prerequisite |
|---|---|---|
| Build validation | PASS | `./gradlew build --no-daemon` |
| Shared playback/QoE tests | PASS | `./gradlew :shared:playback:jvmTest --no-daemon` |
| Media/DRM/playback service tests | PASS | Existing targeted Gradle test suites |
| Static validation | PASS | Kotlin compilation and migration verification in Gradle build |
| Docker smoke | NOT EXECUTED | Docker runtime unavailable in the validation environment |
| Real S3/MinIO worker run | PENDING | Requires Docker or deployed object storage and FFmpeg |
| Real CDN/origin enforcement | PENDING | Requires CDN edge configuration and private-origin credentials |
| Real Widevine/FairPlay/PlayReady | PENDING | Requires provider sandbox credentials and certificates |
| Android Media3/Widevine E2E | PENDING | Requires Android project/device and license configuration |
| iOS FairPlay E2E | PENDING | Requires Apple platform target, certificate, and device |
| Web EME matrix | PENDING | Requires browser test targets and DRM configuration |
| Offline DRM | PENDING | Requires native download managers and offline licenses |

## Acceptance gate

The phase is complete only when real MP4 media passes the online path through private storage, worker processing, HLS/DASH packaging, DRM, CDN authorization, native playback, telemetry, and watch history; then an entitled Android and iOS client must download encrypted media, lose network access, and play with a valid offline license.

The repository intentionally does not mark those provider/device gates complete based on mocks or compilation alone.
