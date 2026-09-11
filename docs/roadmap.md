# Delivery roadmap

The repository is delivered incrementally. Each phase must meet the architecture → implementation → tests → contract → security → performance → documentation → CI definition of done before being marked complete.

| Phase | Scope | Status |
|---|---|---|
| 1 | Repository, KMP contracts, networking, local database schema, security abstractions, auth/profile services, gateway, CI, deployment docs | Implemented |
| 2 | Catalog/home/search, content details, playback authorization/state, signed CDN grants, watch history/watchlist, media/native-player boundaries | Backend/API slice implemented |
| 3 | S3-compatible storage, presigned uploads, FFmpeg rendition/packaging providers, CDN grant validation, native playback adapters | Baseline implemented |
| 4 | Durable workers, multipart uploads, production playback, CDN resource authorization, DRM contracts, offline download contracts | Foundation implemented |
| 5 | Real worker deployment, CDN edge/origin enforcement, vendor DRM, native playback, offline execution | Implementation baseline; provider/device validation remains |
| 6 | Real CDN/DRM deployment, Android-first E2E playback, offline validation, QoE, failure injection | Validation framework implemented |
| 7 | Docker smoke, golden media pipeline, CDN security, real DRM, Android/iOS/Web E2E, offline golden path | Harness implemented; external execution pending |
| 8 | Production streaming certification framework, worker metrics, CDN/origin security tooling | Implementation GO; external validation pending |
| 9 | Certification execution, worker recovery, real DRM/device playback, offline, load, and security gates | Runner implemented; certification pending |
| 10 | Production certification hardening, evidence policy, strict release gate, final VOD hardening | Implementation GO; real-environment execution pending |
| 11 | Operational certification transition and strict release governance | Implemented |
| 12 | Real production certification across infrastructure, media, CDN, DRM, clients, offline, load, and security | Executed locally; NO-GO with external gates pending |
| 13 | Plans, subscriptions, entitlements, coupons, ad policy | Planned |
| 14 | Payments and monetization | Planned |
| 15 | Production offline streaming | Planned |
| 16 | Recommendations and personalization | Planned |
| 17 | Live ingestion, channels, events, low-latency playback, DVR | Planned |
| 18 | Social graph, comments, sharing, watch party, moderation | Planned |
| 19 | Advanced analytics | Planned |
| 20 | Multi-region and multi-CDN | Planned |
| 21 | Scale and cost optimization | Planned |

The Phase 1 status refers only to the foundation scope. It does not claim that playback, DRM, monetization, offline downloads, live streaming, social, or recommendation behavior has been implemented.
