import { FormEvent, useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { useAdminContentDetail, useCreateContent, useGenres, useUpdateContent } from "../lib/hooks/useAdminContent";
import { ErrorBanner } from "../components/ErrorBanner";
import { Spinner } from "../components/Spinner";
import type { AccessTier, ContentStatus, ContentType } from "../lib/types/api";

const CONTENT_TYPES: ContentType[] = ["MOVIE", "SERIES", "SEASON", "EPISODE", "TRAILER", "SHORT"];
const ACCESS_TIERS: AccessTier[] = ["FREE", "PREMIUM"];
const STATUSES: ContentStatus[] = ["DRAFT", "PUBLISHED", "ARCHIVED"];

export function ContentEditorPage() {
  const { contentId } = useParams<{ contentId: string }>();
  const isEditing = Boolean(contentId);
  const navigate = useNavigate();
  const { data: existing, isLoading: isLoadingExisting } = useAdminContentDetail(contentId);
  const { data: genres } = useGenres();
  const createContent = useCreateContent();
  const updateContent = useUpdateContent();

  const [type, setType] = useState<ContentType>("MOVIE");
  const [title, setTitle] = useState("");
  const [synopsis, setSynopsis] = useState("");
  const [releaseYear, setReleaseYear] = useState("");
  const [durationSeconds, setDurationSeconds] = useState("");
  const [accessTier, setAccessTier] = useState<AccessTier>("FREE");
  const [status, setStatus] = useState<ContentStatus>("DRAFT");
  const [isFeatured, setIsFeatured] = useState(false);
  const [genreNames, setGenreNames] = useState("");
  const [error, setError] = useState<unknown>(null);

  useEffect(() => {
    if (!existing) return;
    setType(existing.summary.type);
    setTitle(existing.summary.title);
    setSynopsis(existing.summary.synopsis ?? "");
    setReleaseYear(existing.summary.releaseYear?.toString() ?? "");
    setDurationSeconds(existing.summary.durationSeconds?.toString() ?? "");
    setAccessTier(existing.summary.accessTier);
    setStatus(existing.status);
  }, [existing]);

  if (isEditing && isLoadingExisting) return <Spinner label="Loading title" />;

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);
    const genreList = genreNames
      .split(",")
      .map((name) => name.trim())
      .filter(Boolean);
    const base = {
      title,
      synopsis: synopsis.trim() || null,
      releaseYear: releaseYear ? Number(releaseYear) : null,
      durationSeconds: durationSeconds ? Number(durationSeconds) : null,
      accessTier,
      status,
      isFeatured,
      genreNames: genreList,
    };
    try {
      if (isEditing && contentId) {
        const result = await updateContent.mutateAsync({ contentId, command: base });
        navigate(`/content/${result.summary.id}`);
      } else {
        const result = await createContent.mutateAsync({ ...base, type });
        navigate(`/content/${result.summary.id}`);
      }
    } catch (err) {
      setError(err);
    }
  };

  const isSubmitting = createContent.isPending || updateContent.isPending;

  return (
    <div className="mx-auto max-w-2xl px-6 py-6">
      <h1 className="mb-6 text-2xl font-bold">{isEditing ? "Edit title" : "New title"}</h1>
      <form onSubmit={handleSubmit} className="space-y-4">
        <ErrorBanner error={error} />
        <div>
          <label className="mb-1 block text-sm text-white/80">Title</label>
          <input
            required
            value={title}
            onChange={(event) => setTitle(event.target.value)}
            className="w-full rounded-md bg-surface-raised px-3 py-2 text-white focus:outline-none focus:ring-2 focus:ring-brand"
          />
        </div>
        <div>
          <label className="mb-1 block text-sm text-white/80">Synopsis</label>
          <textarea
            value={synopsis}
            onChange={(event) => setSynopsis(event.target.value)}
            rows={4}
            className="w-full rounded-md bg-surface-raised px-3 py-2 text-white focus:outline-none focus:ring-2 focus:ring-brand"
          />
        </div>
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="mb-1 block text-sm text-white/80">Type</label>
            <select
              disabled={isEditing}
              value={type}
              onChange={(event) => setType(event.target.value as ContentType)}
              className="w-full rounded-md bg-surface-raised px-3 py-2 text-white disabled:opacity-50"
            >
              {CONTENT_TYPES.map((option) => (
                <option key={option} value={option}>
                  {option}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className="mb-1 block text-sm text-white/80">Status</label>
            <select
              value={status}
              onChange={(event) => setStatus(event.target.value as ContentStatus)}
              className="w-full rounded-md bg-surface-raised px-3 py-2 text-white"
            >
              {STATUSES.map((option) => (
                <option key={option} value={option}>
                  {option}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className="mb-1 block text-sm text-white/80">Release year</label>
            <input
              type="number"
              value={releaseYear}
              onChange={(event) => setReleaseYear(event.target.value)}
              className="w-full rounded-md bg-surface-raised px-3 py-2 text-white"
            />
          </div>
          <div>
            <label className="mb-1 block text-sm text-white/80">Duration (seconds)</label>
            <input
              type="number"
              value={durationSeconds}
              onChange={(event) => setDurationSeconds(event.target.value)}
              className="w-full rounded-md bg-surface-raised px-3 py-2 text-white"
            />
          </div>
          <div>
            <label className="mb-1 block text-sm text-white/80">Access tier</label>
            <select
              value={accessTier}
              onChange={(event) => setAccessTier(event.target.value as AccessTier)}
              className="w-full rounded-md bg-surface-raised px-3 py-2 text-white"
            >
              {ACCESS_TIERS.map((option) => (
                <option key={option} value={option}>
                  {option}
                </option>
              ))}
            </select>
          </div>
          <div className="flex items-end gap-2">
            <input
              id="isFeatured"
              type="checkbox"
              checked={isFeatured}
              onChange={(event) => setIsFeatured(event.target.checked)}
              className="h-4 w-4"
            />
            <label htmlFor="isFeatured" className="text-sm text-white/80">
              Featured
            </label>
          </div>
        </div>
        <div>
          <label className="mb-1 block text-sm text-white/80">Genres (comma-separated)</label>
          <input
            value={genreNames}
            onChange={(event) => setGenreNames(event.target.value)}
            placeholder="Drama, Sci-Fi"
            className="w-full rounded-md bg-surface-raised px-3 py-2 text-white placeholder:text-white/40"
          />
          {genres && genres.length > 0 && (
            <p className="mt-1 text-xs text-white/40">Existing: {genres.map((genre) => genre.name).join(", ")}</p>
          )}
        </div>
        <button
          type="submit"
          disabled={isSubmitting}
          className="rounded-md bg-brand px-6 py-2 font-semibold text-white hover:bg-brand-dark disabled:opacity-60"
        >
          {isSubmitting ? "Saving..." : isEditing ? "Save changes" : "Create title"}
        </button>
      </form>
    </div>
  );
}
