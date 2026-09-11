# Playback capability matrix

The shared playback domain carries portable session and intent models. Platform adapters own capabilities that cannot be made consistent across all clients.

| Capability | Android | iOS | Web |
|---|---:|---:|---:|
| HLS | ✅ | ✅ | ✅ |
| DASH | ✅ | Platform-dependent | ✅ |
| Adaptive bitrate | ✅ | ✅ | ✅ |
| Subtitles | ✅ | ✅ | ✅ |
| Multiple audio | ✅ | ✅ | ✅ |
| Offline playback | Later | Later | N/A/limited |
| Widevine | Later | N/A | Browser-dependent |
| FairPlay | N/A | Later | Safari |
| PlayReady | Optional | N/A | Browser-dependent |
| Chromecast | Later | Later | Later |
| AirPlay | N/A | Later | Later |
| Picture-in-picture | Later | Later | Later |

DRM-ready interfaces exist in the playback and media pipeline contracts, but no fake DRM implementation is counted as complete.
