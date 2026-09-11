import type { ContentSummary } from "../lib/types/api";
import { ContentCard } from "./ContentCard";

export function ContentRow({ title, items }: { title: string; items: ContentSummary[] }) {
  if (items.length === 0) return null;
  return (
    <section className="mb-8">
      <h2 className="mb-3 text-lg font-semibold text-white">{title}</h2>
      <div className="flex gap-4 overflow-x-auto pb-2">
        {items.map((item) => (
          <ContentCard key={item.id} content={item} />
        ))}
      </div>
    </section>
  );
}
