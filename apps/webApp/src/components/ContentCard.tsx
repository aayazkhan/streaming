import { Link } from "react-router-dom";
import type { ContentSummary } from "../lib/types/api";

export function ContentCard({ content }: { content: ContentSummary }) {
  return (
    <Link
      to={`/content/${content.id}`}
      className="group block w-40 shrink-0 sm:w-48"
      aria-label={content.title}
    >
      <div className="aspect-[2/3] overflow-hidden rounded-md bg-surface-raised">
        {content.posterUrl ? (
          <img
            src={content.posterUrl}
            alt={content.title}
            loading="lazy"
            className="h-full w-full object-cover transition-transform group-hover:scale-105"
          />
        ) : (
          <div className="flex h-full w-full items-center justify-center px-2 text-center text-sm text-white/60">
            {content.title}
          </div>
        )}
      </div>
      <p className="mt-2 truncate text-sm text-white/90">{content.title}</p>
    </Link>
  );
}
