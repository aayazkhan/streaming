import { useNavigate, useParams } from "react-router-dom";
import { useContentDetail, useSimilarContent } from "../lib/hooks/useCatalog";
import { useAddToWatchlist, useRemoveFromWatchlist, useWatchlist } from "../lib/hooks/useWatchlist";
import { ContentRow } from "../components/ContentRow";
import { Spinner } from "../components/Spinner";
import { ErrorBanner } from "../components/ErrorBanner";

export function ContentDetailPage() {
  const { contentId } = useParams<{ contentId: string }>();
  const navigate = useNavigate();
  const { data: detail, isLoading, error } = useContentDetail(contentId);
  const { data: similar } = useSimilarContent(contentId);
  const { data: watchlist } = useWatchlist();
  const addToWatchlist = useAddToWatchlist();
  const removeFromWatchlist = useRemoveFromWatchlist();

  if (isLoading) return <Spinner label="Loading title" />;
  if (error || !detail) return <ErrorBanner error={error ?? new Error("Title not found")} />;

  const { summary } = detail;
  const isInWatchlist = watchlist?.items.some((entry) => entry.content.id === summary.id) ?? false;

  return (
    <div>
      <div
        className="relative flex h-[50vh] items-end bg-cover bg-center px-6 py-8"
        style={{ backgroundImage: summary.backdropUrl ? `url(${summary.backdropUrl})` : undefined }}
      >
        <div className="absolute inset-0 bg-gradient-to-t from-surface via-surface/60 to-transparent" />
        <div className="relative z-10 max-w-2xl">
          <h1 className="text-4xl font-bold">{summary.title}</h1>
          <p className="mt-2 text-sm text-white/70">
            {summary.releaseYear} · {detail.genres.join(", ")}
            {summary.rating ? ` · ★ ${summary.rating.toFixed(1)}` : ""}
          </p>
          <p className="mt-4 text-white/90">{summary.synopsis}</p>
          <div className="mt-6 flex gap-3">
            <button
              disabled={!detail.playable}
              onClick={() => navigate(`/watch/${summary.id}`)}
              className="rounded-md bg-white px-6 py-2 font-semibold text-surface hover:bg-white/90 disabled:cursor-not-allowed disabled:opacity-50"
            >
              {detail.playable ? "Play" : "Unavailable"}
            </button>
            <button
              onClick={() =>
                isInWatchlist ? removeFromWatchlist.mutate(summary.id) : addToWatchlist.mutate(summary.id)
              }
              className="rounded-md border border-white/40 px-6 py-2 font-semibold text-white hover:border-white"
            >
              {isInWatchlist ? "Remove from My List" : "Add to My List"}
            </button>
          </div>
        </div>
      </div>

      <div className="px-6 py-6">
        {detail.credits.length > 0 && (
          <p className="mb-6 text-sm text-white/70">
            Cast: {detail.credits.map((credit) => credit.name).join(", ")}
          </p>
        )}
        {detail.children.length > 0 && <ContentRow title="Episodes" items={detail.children} />}
        {similar && similar.length > 0 && <ContentRow title="More like this" items={similar} />}
      </div>
    </div>
  );
}
