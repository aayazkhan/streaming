import { useSearchParams } from "react-router-dom";
import { useSearch } from "../lib/hooks/useSearch";
import { ContentCard } from "../components/ContentCard";
import { Spinner } from "../components/Spinner";
import { ErrorBanner } from "../components/ErrorBanner";

export function SearchPage() {
  const [searchParams] = useSearchParams();
  const query = searchParams.get("q") ?? "";
  const { data, isLoading, error } = useSearch(query);

  return (
    <div className="px-6 py-6">
      <h1 className="mb-4 text-2xl font-bold">Results for "{query}"</h1>
      <ErrorBanner error={error} />
      {isLoading ? (
        <Spinner label="Searching" />
      ) : data && data.items.length > 0 ? (
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-4 md:grid-cols-6">
          {data.items.map((item) => (
            <ContentCard key={item.id} content={item} />
          ))}
        </div>
      ) : (
        <p className="text-white/60">No titles matched your search.</p>
      )}
    </div>
  );
}
