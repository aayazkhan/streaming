# iOS application boundary

`AVPlayerPlaybackAdapter.swift` is the first AVFoundation implementation. The Xcode target must supply the generated KMP grant bridge and observe AVPlayer status/time-control events. FairPlay, background playback, PiP, and offline downloads remain native.

The iOS app consumes the Phase 2 shared playback contracts and will provide native adapters for SwiftUI/UIKit, AVPlayer/AVFoundation playback, FairPlay Streaming, Keychain-backed `SecureTokenStore`, and background download/synchronization jobs. `NativePlayerAdapter` receives only a short-lived `PlaybackGrant`; FairPlay license handling remains native.

The shared layer exposes state and intents; AVFoundation owns media lifecycle and DRM callbacks.
