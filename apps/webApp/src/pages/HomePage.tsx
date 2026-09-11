import { useHomeFeed } from "../lib/hooks/useCatalog";
import { useContinueWatching } from "../lib/hooks/usePlayback";
import { ContentRow } from "../components/ContentRow";
import { Spinner } from "../components/Spinner";
import { ErrorBanner } from "../components/ErrorBanner";

export function HomePage() {
  const { data: feed, isLoading, error } = useHomeFeed();
  const { data: continueWatching } = useContinueWatching();

  if (isLoading) return <Spinner label="Loading home feed" />;

  return (
    <div className="px-6 py-6">
      <ErrorBanner error={error} />
      {continueWatching && continueWatching.length > 0 && (
        <ContentRow title="Continue watching" items={continueWatching.map((item) => item.content)} />
      )}
      {feed?.sections.map((section) => (
        <ContentRow key={section.key} title={section.title} items={section.items} />
      ))}
      {feed && feed.sections.length === 0 && (!continueWatching || continueWatching.length === 0) && (
        <p className="text-white/60">No content is available yet. Check back soon.</p>
      )}
    </div>
  );
}
