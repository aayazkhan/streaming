# Phase 3 media infrastructure

Phase 3 connects the VOD control plane to provider boundaries without making the API gateway a media origin.

```text
Admin API
   │ presigned PUT
   ▼
S3-compatible object storage
   │ source object
   ▼
Media worker + FFmpeg
   │ HLS/DASH, thumbnails, subtitles
   ▼
Object storage / origin ── CDN ── native player
```

`POST /v1/media/uploads` creates a database lifecycle record and returns a short-lived presigned PUT URL. The client uploads directly to storage, then calls the completion endpoint. Completion verifies the object exists and its declared size before marking the asset `PROCESSING`. A worker can then run `MediaPipelineOrchestrator` with `FfmpegVideoTranscoder`, `FfmpegAdaptiveBitratePackager`, `SidecarSubtitleProcessor`, and `FfmpegThumbnailGenerator`.

The media service requires an `ADMIN` JWT role. Normal user access tokens cannot create source assets. The checksum is retained as upload metadata; a production worker must compute and compare SHA-256 before transitioning an asset to `READY`.

Playback grants are JWTs scoped to user, profile, content, playback session, exact manifest resource keys, and the corresponding asset prefixes for segment requests. `GET /v1/playback/grants/validate` verifies issuer, audience, signature, expiry, resource scope, and the still-active database session. A CDN edge/origin authorization function should pass every requested manifest or segment resource to this contract; the gateway never serves manifests or segments itself. The storage bucket/origin must remain private and accept traffic only from the CDN identity.
