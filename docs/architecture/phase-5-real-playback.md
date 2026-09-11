# Phase 5 real playback integration

Phase 5 turns the Phase 4 contracts into deployable integration boundaries without pretending that CDN, DRM, or device vendors are available in the repository.

## Implemented foundation

- `backend/media-worker` is a dedicated JVM application with FFmpeg, S3-compatible artifact publication, graceful shutdown, bounded concurrent claims, lease heartbeats, retry/backoff, and structured worker lifecycle hooks.
- `infrastructure/docker/media-worker.Dockerfile` and `infrastructure/kubernetes/media-worker.yaml` provide local and cluster deployment shapes. The worker uses `SKIP LOCKED`, persistent job progress, stale-lease recovery, and terminal failure state.
- `MediaAssetStateMachine` models asset lifecycle states and pipeline stages from upload through CDN publication.
- Playback grants contain exact manifest keys and scoped asset prefixes so CDN edge authorization can validate both manifests and HLS/DASH segments. The origin must remain private.
- `backend/drm-service` provides HTTP transport adapters for Widevine, FairPlay, and PlayReady license endpoints. Vendor policy, credentials, key storage, and license server deployment stay outside the shared domain.
- Shared playback state, player events, DRM requests, and offline download policies remain platform-neutral. Native adapters own Media3, AVPlayer, EME, secure storage, and download engines.

## Required external validation

The Phase 5 end-to-end gate still requires real infrastructure:

1. Upload an MP4 to private S3/MinIO and enqueue a durable job.
2. Run the worker with FFmpeg and publish generated artifacts to a private origin.
3. Put a CDN in front of the origin and reject direct origin access.
4. Validate the grant for the manifest and every requested segment, including expiry, wrong-resource, replay, and cache behavior.
5. Configure a real DRM vendor and validate license acquisition, renewal, expiration, revocation, and device/session policy.
6. Run the signed playback path on Android Media3 first, then iOS AVPlayer and Web EME.
7. Add native offline download/license execution only after online DRM playback is stable.

The repository build verifies the contracts and adapters; it does not claim vendor/device completion without those external systems.
