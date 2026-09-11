import { ApiError } from "../lib/apiClient";

export function ErrorBanner({ error }: { error: unknown }) {
  if (!error) return null;
  const message = error instanceof ApiError ? error.message : error instanceof Error ? error.message : "Something went wrong.";
  return (
    <div role="alert" className="rounded-md border border-red-500/40 bg-red-950/50 px-4 py-3 text-sm text-red-200">
      {message}
    </div>
  );
}
