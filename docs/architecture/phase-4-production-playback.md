# Phase 4 production playback

Phase 4 moves media processing off the request path and formalizes platform-owned DRM/download execution.

```text
Upload API → media_jobs → queue claim → worker heartbeat
                                  │
                                  ├─ retry with exponential backoff
                                  ├─ cancellation / dead-letter state
                                  └─ FFmpeg + storage artifact updates
```

`media_jobs` carries an idempotency key, attempt count, retry time, progress, worker ownership, heartbeat, cancellation flag, and provider artifact keys. `MediaJobWorker` owns retry policy; `JdbcMediaJobQueue` uses row locking with `SKIP LOCKED` so multiple workers do not claim the same job.

Multipart uploads persist the provider upload ID. Parts are independently presigned, completion requires unique part numbers and ETags, the provider completes the upload, and the API verifies the final object size before enqueuing processing. Abandoned uploads can be aborted explicitly and should also be cleaned by a scheduled retention job.

CDN grants contain exact manifest resource keys plus scoped asset prefixes for HLS/DASH segment requests. The edge authorization call must provide the requested resource for every manifest and segment, and the origin must reject public access. DRM and offline contracts are shared only as serializable state/request boundaries; Widevine, FairPlay, PlayReady, secure key storage, and download managers remain native.

## Cross-platform playback matrix

| Test | Android / Media3 | iOS / AVPlayer | Web / HTML5-MSE-EME |
|---|---:|---:|---:|
| Signed HLS manifest | Required | Required | Required |
| Signed DASH manifest | Required | Platform-dependent | Required |
| ABR and manual quality | Required | Required | Required |
| Audio/subtitle selection | Required | Required | Required |
| Seek/resume/position sync | Required | Required | Required |
| Buffering/error telemetry | Required | Required | Required |
| Widevine / FairPlay / PlayReady | Widevine | FairPlay | Browser-supported EME |
| Background/PiP | Required | Required | Browser-dependent |
| Offline encrypted playback | Later | Later | N/A/limited |
