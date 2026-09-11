# Glossary

Plain-language definitions for terms used across the docs, for readers without a
streaming/media or Kotlin background.

- **VOD (Video on Demand)** — pre-recorded video a viewer can start anytime, as
  opposed to a live broadcast.
- **CDN (Content Delivery Network)** — a network of servers that caches and
  serves video close to the viewer, instead of every viewer hitting one origin
  server.
- **DRM (Digital Rights Management)** — technology that restricts playback of
  protected content to authorized devices/sessions, preventing copying/piracy.
- **Rendition** — a specific encoded version of a video (e.g. 1080p at a given
  bitrate). A title typically has multiple renditions so playback can adapt to
  the viewer's bandwidth.
- **Transcoding** — converting an uploaded source video into one or more
  streamable renditions (done by FFmpeg here).
- **Multipart upload** — uploading a large file in chunks ("parts") instead of
  one request, so an interrupted upload can resume instead of restarting.
- **Playback grant** — a short-lived, signed authorization that proves a
  specific viewer/session is allowed to fetch a specific piece of content,
  checked before video is served.
- **Continue watching** — the "resume from where you left off" feature, tracked
  per profile.
- **KMP (Kotlin Multiplatform)** — writing business logic once in Kotlin and
  sharing it across backend, Android, and (via a compiled framework) iOS,
  instead of rewriting the same logic per platform.
- **XCFramework** — Apple's packaging format for a compiled library (here, the
  compiled KMP shared code) that an iOS app can link against.
- **Ktor** — the Kotlin HTTP framework used to build each backend service.
- **Certification (in this repo)** — an automated, machine-readable report
  (`certification/certification.json`) stating whether the platform passes a
  defined set of release gates (infra, playback, security, load, etc.), with
  `NOT_EXECUTED` used for gates that need infrastructure not available locally.
- **Hard gate** — a certification check that must pass (or be marked
  not-executed, never silently skipped) before a release can be called "GO."
- **QoE (Quality of Experience)** — playback-side metrics (buffering, startup
  time, bitrate switches) used to judge how good playback actually felt to a
  viewer, as opposed to pure server-side metrics.
