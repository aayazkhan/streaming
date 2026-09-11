# Phase 2 VOD architecture

```text
Application API
      │
      ▼
Playback Service
      │
      ├── authenticated user/profile ownership
      ├── catalog publication and media readiness
      ├── entitlement policy boundary
      └── short-lived signed CDN token
      │
      ▼
CDN / Media Origin
      ├── HLS manifest
      ├── DASH manifest
      └── video segments
```

The Ktor gateway and domain services never proxy media bytes. `PlaybackService` validates the authenticated profile, published catalog state, media readiness, and access tier, then creates a short-lived playback session and signs CDN URLs. Premium content is rejected with `ENTITLEMENT_REQUIRED` until the subscription/entitlement phase supplies a real entitlement checker; it is not treated as free in the interim.

## Content and discovery

`contents` is a hierarchical catalog: a series owns seasons, seasons own episodes, and any published item can have genres, people, audio tracks, subtitles, and one media asset record. Home sections are database-backed views over featured, trending, release, recently-added, and popularity signals. Personalization is deliberately not inferred from static data.

## Search

The Phase 2 development backend uses PostgreSQL full-text search so the service is runnable with the local stack. `SearchRepository` is the client-facing boundary; production deployment can swap the implementation for OpenSearch without changing the API or shared models. Search history is persisted per user and never used as a cross-user query source.

When `OPENSEARCH_ENDPOINT` is configured, the gateway uses the concrete `OpenSearchSearchIndex` adapter with multi-match fuzzy search, filters, and `search_after` cursors. The PostgreSQL implementation remains the deterministic local fallback and continues to own search history.

## DRM-ready media model

Media assets store manifest/object keys, not permanent client URLs. The playback grant has no DRM secret. Native clients consume `PlaybackGrant` through `NativePlayerAdapter`; Widevine, FairPlay, PlayReady, and EME license flows remain platform/provider implementations behind that boundary.
