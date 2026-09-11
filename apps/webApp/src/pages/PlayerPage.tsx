import { useEffect, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { useStartPlayback, useUpdatePlaybackPosition } from "../lib/hooks/usePlayback";
import { useActiveProfile } from "../lib/profile/ActiveProfileContext";
import { HtmlMediaPlaybackAdapter } from "../playback/HtmlMediaPlaybackAdapter";
import { ErrorBanner } from "../components/ErrorBanner";
import { Spinner } from "../components/Spinner";
import type { PlaybackGrant } from "../lib/types/api";

const POSITION_REPORT_INTERVAL_MS = 15_000;

export function PlayerPage() {
  const { contentId } = useParams<{ contentId: string }>();
  const navigate = useNavigate();
  const { activeProfileId } = useActiveProfile();
  const videoRef = useRef<HTMLVideoElement>(null);
  const adapterRef = useRef<HtmlMediaPlaybackAdapter | null>(null);
  const startPlayback = useStartPlayback();
  const updatePosition = useUpdatePlaybackPosition();
  const [grant, setGrant] = useState<PlaybackGrant | null>(null);
  const [playbackError, setPlaybackError] = useState<unknown>(null);

  useEffect(() => {
    if (!contentId || !activeProfileId) return;
    let cancelled = false;
    startPlayback
      .mutateAsync({ contentId, profileId: activeProfileId })
      .then((response) => {
        if (!cancelled) setGrant(response);
      })
      .catch((error) => {
        if (!cancelled) setPlaybackError(error);
      });
    return () => {
      cancelled = true;
    };
    // startPlayback is a stable mutation object from useMutation; re-running on every render
    // would restart the session, so only contentId/activeProfileId should re-trigger this.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [contentId, activeProfileId]);

  useEffect(() => {
    if (!grant || !videoRef.current) return;
    const adapter = new HtmlMediaPlaybackAdapter(videoRef.current);
    adapterRef.current = adapter;
    let cancelled = false;
    adapter
      .prepare(grant)
      .then(() => {
        if (!cancelled) void adapter.play();
      })
      .catch((error) => {
        if (!cancelled) setPlaybackError(error);
      });
    return () => {
      cancelled = true;
      adapter.release();
      adapterRef.current = null;
    };
  }, [grant]);

  useEffect(() => {
    if (!grant) return;
    const intervalId = window.setInterval(() => {
      const adapter = adapterRef.current;
      if (!adapter) return;
      const positionSeconds = Math.floor(adapter.currentPositionSeconds);
      const durationSeconds = adapter.durationSeconds ? Math.floor(adapter.durationSeconds) : undefined;
      updatePosition.mutate({
        sessionId: grant.session.id,
        command: { positionSeconds, durationSeconds },
      });
    }, POSITION_REPORT_INTERVAL_MS);
    return () => window.clearInterval(intervalId);
    // updatePosition is a stable mutation object; only the session identity should reset the timer.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [grant?.session.id]);

  const handleEnded = () => {
    if (!grant || !adapterRef.current) return;
    const durationSeconds = adapterRef.current.durationSeconds ? Math.floor(adapterRef.current.durationSeconds) : undefined;
    updatePosition.mutate({
      sessionId: grant.session.id,
      command: { positionSeconds: durationSeconds ?? 0, durationSeconds, completed: true },
    });
  };

  if (!activeProfileId) {
    navigate("/profiles", { replace: true });
    return null;
  }

  return (
    <div className="flex min-h-screen flex-col bg-black">
      <button onClick={() => navigate(-1)} className="absolute left-4 top-4 z-10 text-white/80 hover:text-white">
        ← Back
      </button>
      {playbackError ? (
        <div className="flex flex-1 items-center justify-center p-6">
          <ErrorBanner error={playbackError} />
        </div>
      ) : !grant ? (
        <Spinner label="Starting playback" />
      ) : (
        <video ref={videoRef} controls autoPlay className="h-screen w-full" onEnded={handleEnded} />
      )}
    </div>
  );
}
