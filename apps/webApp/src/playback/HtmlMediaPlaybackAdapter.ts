import type Hls from "hls.js";

export type WebPlaybackGrant = {
  hlsUrl?: string | null;
  dashUrl?: string | null;
};

/**
 * DRM extension point. No browser EME/CDM wiring is implemented yet — there are no vendor
 * credentials for Widevine/PlayReady in this environment (see docs/architecture/playback-capability-matrix.md).
 * A future DRM-enabled grant would carry a `licenseUrl`/key system here; `configureEme` is the
 * seam where `navigator.requestMediaKeySystemAccess` + `MediaKeys` setup belongs.
 */
export interface DrmConfig {
  licenseUrl: string;
  keySystem: "com.widevine.alpha" | "com.microsoft.playready";
}

/** HTMLMediaElement boundary. MSE/EME and DRM remain browser/provider integrations. */
export class HtmlMediaPlaybackAdapter {
  private hls: Hls | null = null;

  public constructor(private readonly element: HTMLVideoElement) {}

  public async prepare(grant: WebPlaybackGrant, _drm?: DrmConfig): Promise<void> {
    const manifest = grant.hlsUrl ?? grant.dashUrl;
    if (!manifest) throw new Error("PLAYBACK_MANIFEST_UNAVAILABLE");

    if (grant.dashUrl && !grant.hlsUrl) {
      throw new Error("DASH_PLAYBACK_NOT_SUPPORTED_IN_BROWSER");
    }

    if (this.element.canPlayType("application/vnd.apple.mpegurl")) {
      // Safari plays HLS natively; hls.js would conflict with the native pipeline.
      this.element.src = manifest;
      this.element.load();
      return;
    }

    // Loaded lazily so the ~600KB hls.js bundle isn't part of every route's initial download.
    const { default: Hls } = await import("hls.js");
    if (!Hls.isSupported()) throw new Error("HLS_PLAYBACK_UNSUPPORTED");

    this.hls = new Hls();
    this.hls.loadSource(manifest);
    this.hls.attachMedia(this.element);
  }

  public play(): Promise<void> {
    return this.element.play();
  }

  public pause(): void {
    this.element.pause();
  }

  public seekTo(positionSeconds: number): void {
    this.element.currentTime = positionSeconds;
  }

  public get currentPositionSeconds(): number {
    return this.element.currentTime;
  }

  public get durationSeconds(): number | null {
    return Number.isFinite(this.element.duration) ? this.element.duration : null;
  }

  public release(): void {
    this.hls?.destroy();
    this.hls = null;
    this.element.removeAttribute("src");
    this.element.load();
  }
}
