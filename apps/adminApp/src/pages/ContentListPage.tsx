import { useState } from "react";
import { Link } from "react-router-dom";
import { useAdminContentList } from "../lib/hooks/useAdminContent";
import { Spinner } from "../components/Spinner";
import { ErrorBanner } from "../components/ErrorBanner";
import type { ContentStatus } from "../lib/types/api";

const STATUSES: ContentStatus[] = ["DRAFT", "PUBLISHED", "ARCHIVED"];

export function ContentListPage() {
  const [status, setStatus] = useState<ContentStatus | undefined>(undefined);
  const { data, isLoading, error } = useAdminContentList(status);

  return (
    <div className="px-6 py-6">
      <div className="mb-4 flex items-center justify-between">
        <h1 className="text-2xl font-bold">Content</h1>
        <Link to="/content/new" className="rounded-md bg-brand px-4 py-2 text-sm font-semibold text-white hover:bg-brand-dark">
          New title
        </Link>
      </div>
      <div className="mb-6 flex gap-2">
        <button
          onClick={() => setStatus(undefined)}
          className={`rounded-full px-4 py-1.5 text-sm ${!status ? "bg-brand text-white" : "bg-surface-raised text-white/70"}`}
        >
          All
        </button>
        {STATUSES.map((option) => (
          <button
            key={option}
            onClick={() => setStatus(option)}
            className={`rounded-full px-4 py-1.5 text-sm ${status === option ? "bg-brand text-white" : "bg-surface-raised text-white/70"}`}
          >
            {option}
          </button>
        ))}
      </div>
      <ErrorBanner error={error} />
      {isLoading ? (
        <Spinner label="Loading content" />
      ) : (
        <div className="overflow-x-auto rounded-md border border-white/10">
          <table className="w-full min-w-[640px] text-left text-sm">
            <thead className="bg-surface-raised text-white/60">
              <tr>
                <th className="px-4 py-2">Title</th>
                <th className="px-4 py-2">Type</th>
                <th className="px-4 py-2">Status</th>
                <th className="px-4 py-2">Access</th>
                <th className="px-4 py-2">Year</th>
              </tr>
            </thead>
            <tbody>
              {data?.items.map((item) => (
                <tr key={item.summary.id} className="border-t border-white/10 hover:bg-surface-raised">
                  <td className="px-4 py-2">
                    <Link to={`/content/${item.summary.id}`} className="text-white hover:underline">
                      {item.summary.title}
                    </Link>
                  </td>
                  <td className="px-4 py-2 text-white/70">{item.summary.type}</td>
                  <td className="px-4 py-2 text-white/70">{item.status}</td>
                  <td className="px-4 py-2 text-white/70">{item.summary.accessTier}</td>
                  <td className="px-4 py-2 text-white/70">{item.summary.releaseYear ?? "-"}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {data?.items.length === 0 && <p className="p-6 text-center text-white/60">No titles yet.</p>}
        </div>
      )}
    </div>
  );
}
