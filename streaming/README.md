# Media pipeline boundary

Reserved services:

- ingestion: validate uploads and live input;
- transcoding: create ABR renditions;
- packaging: produce HLS/DASH manifests and subtitles;
- DRM: license policy and key-provider integration;
- thumbnails: poster, sprite, and preview generation;
- media-processing: durable workflow orchestration.

Media assets must be private at origin and delivered through short-lived CDN grants. Do not put permanent media URLs in shared client models.

Phase 3–5 adds concrete S3-compatible single and multipart storage adapters, FFmpeg providers, durable job metadata, a deployable JVM worker with lease heartbeats and bounded concurrency, resource-bound manifest/segment grants, provider-backed DRM HTTP adapters, and shared DRM/offline contracts. A real CDN edge function, vendor DRM credentials, and native DRM/download implementations still require provider-specific integration; these are not represented by fake implementations.
