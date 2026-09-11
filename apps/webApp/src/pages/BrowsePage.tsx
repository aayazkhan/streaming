import { useState } from "react";
import { useContentList } from "../lib/hooks/useCatalog";
import { ContentCard } from "../components/ContentCard";
import { Spinner } from "../components/Spinner";
import { ErrorBanner } from "../components/ErrorBanner";
import type { ContentType } from "../lib/types/api";

const CONTENT_TYPES: ContentType[] = ["MOVIE", "SERIES"];

export function BrowsePage() {
  const [type, setType] = useState<ContentType | undefined>(undefined);
  const { data, isLoading, error, fetchNextPage, hasNextPage, isFetchingNextPage } = useContentList({ type });
  const items = data?.pages.flatMap((page) => page.items) ?? [];

  return (
    <div className="px-6 py-6">
      <h1 className="mb-4 text-2xl font-bold">Browse</h1>
      <div className="mb-6 flex gap-2">
        <button
          onClick={() => setType(undefined)}
          className={`rounded-full px-4 py-1.5 text-sm ${!type ? "bg-brand text-white" : "bg-surface-raised text-white/70"}`}
        >
          All
        </button>
        {CONTENT_TYPES.map((option) => (
          <button
            key={option}
            onClick={() => setType(option)}
            className={`rounded-full px-4 py-1.5 text-sm ${type === option ? "bg-brand text-white" : "bg-surface-raised text-white/70"}`}
          >
            {option}
          </button>
        ))}
      </div>
      <ErrorBanner error={error} />
      {isLoading ? (
        <Spinner label="Loading catalog" />
      ) : (
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-4 md:grid-cols-6">
          {items.map((item) => (
            <ContentCard key={item.id} content={item} />
          ))}
        </div>
      )}
      {hasNextPage && (
        <button
          onClick={() => fetchNextPage()}
          disabled={isFetchingNextPage}
          className="mt-6 rounded-md border border-white/30 px-4 py-2 text-sm text-white/80 hover:border-white hover:text-white disabled:opacity-60"
        >
          {isFetchingNextPage ? "Loading..." : "Load more"}
        </button>
      )}
    </div>
  );
}
