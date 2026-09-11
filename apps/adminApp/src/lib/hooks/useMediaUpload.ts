import { useMutation, useQuery } from "@tanstack/react-query";
import { apiClient } from "../apiClient";
import type { CompletedMediaUpload, CreateMediaUploadRequest, MediaJob, MediaUploadGrant } from "../types/api";

export function useCreateMediaUpload() {
  return useMutation({
    mutationFn: (request: CreateMediaUploadRequest) => apiClient.post<MediaUploadGrant>("/media/uploads", request),
  });
}

/** PUTs the raw file bytes directly to the presigned storage URL — bypasses the api-gateway. */
export async function uploadFileToGrant(file: File, grant: MediaUploadGrant["grant"], onProgress?: (percent: number) => void): Promise<void> {
  await new Promise<void>((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open(grant.method, grant.url);
    Object.entries(grant.requiredHeaders).forEach(([key, value]) => xhr.setRequestHeader(key, value));
    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable && onProgress) onProgress(Math.round((event.loaded / event.total) * 100));
    };
    xhr.onload = () => (xhr.status >= 200 && xhr.status < 300 ? resolve() : reject(new Error(`Upload failed: ${xhr.status}`)));
    xhr.onerror = () => reject(new Error("Upload failed"));
    xhr.send(file);
  });
}

export function useCompleteMediaUpload() {
  return useMutation({
    mutationFn: (uploadId: string) => apiClient.post<CompletedMediaUpload>(`/media/uploads/${uploadId}/complete`),
  });
}

export function useMediaJob(jobId: string | undefined, enabled: boolean) {
  return useQuery({
    queryKey: ["media-job", jobId],
    queryFn: () => apiClient.get<MediaJob>(`/media/jobs/${jobId}`),
    enabled: Boolean(jobId) && enabled,
    refetchInterval: (query) => (query.state.data?.state === "READY" || query.state.data?.state === "FAILED" ? false : 3000),
  });
}
