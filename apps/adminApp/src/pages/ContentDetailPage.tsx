import { useRef, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { useAdminContentDetail, useArchiveContent } from "../lib/hooks/useAdminContent";
import { useCompleteMediaUpload, useCreateMediaUpload, useMediaJob, uploadFileToGrant } from "../lib/hooks/useMediaUpload";
import { ErrorBanner } from "../components/ErrorBanner";
import { Spinner } from "../components/Spinner";

export function ContentDetailPage() {
  const { contentId } = useParams<{ contentId: string }>();
  const navigate = useNavigate();
  const { data: detail, isLoading, error } = useAdminContentDetail(contentId);
  const archiveContent = useArchiveContent();
  const createUpload = useCreateMediaUpload();
  const completeUpload = useCompleteMediaUpload();
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [uploadProgress, setUploadProgress] = useState<number | null>(null);
  const [uploadError, setUploadError] = useState<unknown>(null);
  const [jobId, setJobId] = useState<string | undefined>(undefined);
  const { data: job } = useMediaJob(jobId, true);

  if (isLoading) return <Spinner label="Loading title" />;
  if (error || !detail) return <ErrorBanner error={error ?? new Error("Title not found")} />;

  const handleFileSelected = async (file: File) => {
    if (!contentId) return;
    setUploadError(null);
    setUploadProgress(0);
    try {
      const { upload, grant } = await createUpload.mutateAsync({
        contentId,
        fileName: file.name,
        contentType: file.type || "video/mp4",
        sizeBytes: file.size,
      });
      await uploadFileToGrant(file, grant, setUploadProgress);
      const completed = await completeUpload.mutateAsync(upload.id);
      setJobId(completed.upload.id);
      setUploadProgress(null);
    } catch (err) {
      setUploadError(err);
      setUploadProgress(null);
    }
  };

  const handleArchive = async () => {
    if (!contentId) return;
    await archiveContent.mutateAsync(contentId);
    navigate("/");
  };

  return (
    <div className="mx-auto max-w-2xl px-6 py-6">
      <div className="mb-4 flex items-center justify-between">
        <h1 className="text-2xl font-bold">{detail.summary.title}</h1>
        <div className="flex gap-2">
          <Link to={`/content/${contentId}/edit`} className="rounded-md border border-white/30 px-4 py-2 text-sm text-white/80 hover:border-white">
            Edit
          </Link>
          <button
            onClick={handleArchive}
            disabled={detail.status === "ARCHIVED"}
            className="rounded-md border border-red-500/50 px-4 py-2 text-sm text-red-300 hover:border-red-400 disabled:opacity-40"
          >
            Archive
          </button>
        </div>
      </div>
      <p className="mb-1 text-sm text-white/60">
        {detail.summary.type} · {detail.status} · {detail.summary.accessTier}
        {detail.summary.releaseYear ? ` · ${detail.summary.releaseYear}` : ""}
      </p>
      <p className="mb-6 text-white/90">{detail.summary.synopsis}</p>

      <section className="rounded-md border border-white/10 p-4">
        <h2 className="mb-3 text-lg font-semibold">Media</h2>
        <ErrorBanner error={uploadError} />
        <input
          ref={fileInputRef}
          type="file"
          accept="video/*"
          className="hidden"
          onChange={(event) => {
            const file = event.target.files?.[0];
            if (file) void handleFileSelected(file);
          }}
        />
        <button
          onClick={() => fileInputRef.current?.click()}
          disabled={uploadProgress !== null}
          className="rounded-md bg-brand px-4 py-2 text-sm font-semibold text-white hover:bg-brand-dark disabled:opacity-60"
        >
          {uploadProgress !== null ? `Uploading... ${uploadProgress}%` : "Upload video file"}
        </button>
        {job && (
          <p className="mt-3 text-sm text-white/70">
            Processing job <code>{job.id}</code>: <span className="font-semibold">{job.state}</span>
            {job.failureCode ? ` (${job.failureCode})` : ""}
          </p>
        )}
      </section>
    </div>
  );
}
