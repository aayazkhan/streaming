# Android application boundary

`Media3PlaybackAdapter.kt` is the first native playback implementation. It connects Media3 listener callbacks to shared state and telemetry, and accepts a deterministic manifest failure injector for E2E/failure tests. Add the Android app's pinned Media3 dependency and complete the remaining Widevine, background playback, PiP, and WorkManager download wiring in this target.

The Android app consumes the Phase 2 shared playback contracts and will provide native adapters for Jetpack Compose UI, Media3/ExoPlayer playback, Widevine DRM, Android Keystore-backed `SecureTokenStore`, and WorkManager synchronization jobs. `NativePlayerAdapter` receives only a short-lived `PlaybackGrant`; it must never receive origin credentials.

Media player and DRM objects must not be passed into `commonMain`.
