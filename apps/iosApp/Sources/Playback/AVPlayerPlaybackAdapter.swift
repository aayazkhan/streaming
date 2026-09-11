import AVFoundation
import SharedKit

/// Swift-side boundary for the shared KMP PlaybackGrant. AVFoundation owns the player lifecycle;
/// FairPlay license handling remains native and is out of scope for this pass (no DRM vendor
/// credentials exist in this environment), matching the same tradeoff Android's phase documented.
final class AVPlayerPlaybackAdapter {
    let player = AVPlayer()

    func prepare(grant: PlaybackGrant) {
        guard let urlString = grant.hlsUrl ?? grant.dashUrl, let url = URL(string: urlString) else { return }
        player.replaceCurrentItem(with: AVPlayerItem(url: url))
    }

    func play() { player.play() }
    func pause() { player.pause() }
    func seek(to positionSeconds: Int64) {
        player.seek(to: CMTime(seconds: Double(positionSeconds), preferredTimescale: 600))
    }
    func release() { player.replaceCurrentItem(with: nil) }

    var currentPositionSeconds: Int64 {
        let seconds = player.currentTime().seconds
        return seconds.isFinite ? Int64(seconds) : 0
    }

    var durationSeconds: Int64? {
        guard let duration = player.currentItem?.duration.seconds, duration.isFinite else { return nil }
        return Int64(duration)
    }
}
