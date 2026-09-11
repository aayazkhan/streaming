import { useWatchlist } from "../lib/hooks/useWatchlist";
import { ContentCard } from "../components/ContentCard";
import { Spinner } from "../components/Spinner";
import { ErrorBanner } from "../components/ErrorBanner";

export function WatchlistPage() {
  const { data, isLoading, error } = useWatchlist();

  return (
    <div className="px-6 py-6">
      <h1 className="mb-4 text-2xl font-bold">My List</h1>
      <ErrorBanner error={error} />
      {isLoading ? (
        <Spinner label="Loading your list" />
      ) : data && data.items.length > 0 ? (
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-4 md:grid-cols-6">
          {data.items.map((entry) => (
            <ContentCard key={entry.content.id} content={entry.content} />
          ))}
        </div>
      ) : (
        <p className="text-white/60">You haven't added any titles yet.</p>
      )}
    </div>
  );
}
