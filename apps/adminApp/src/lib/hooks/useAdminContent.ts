import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "../apiClient";
import { toQueryString } from "../queryString";
import type {
  AdminContentSummary,
  ContentStatus,
  ContentType,
  CreateContentCommand,
  Genre,
  Page,
  UpdateContentCommand,
} from "../types/api";

export function useAdminContentList(status?: ContentStatus, type?: ContentType) {
  return useQuery({
    queryKey: ["admin-content", status, type],
    queryFn: () => apiClient.get<Page<AdminContentSummary>>(`/admin/content${toQueryString({ status, type })}`),
  });
}

export function useAdminContentDetail(contentId: string | undefined) {
  return useQuery({
    queryKey: ["admin-content-detail", contentId],
    queryFn: () => apiClient.get<AdminContentSummary>(`/admin/content/${contentId}`),
    enabled: Boolean(contentId),
  });
}

export function useGenres() {
  return useQuery({
    queryKey: ["admin-genres"],
    queryFn: () => apiClient.get<Genre[]>("/admin/genres"),
  });
}

export function useCreateContent() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (command: CreateContentCommand) => apiClient.post<AdminContentSummary>("/admin/content", command),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["admin-content"] }),
  });
}

export function useUpdateContent() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ contentId, command }: { contentId: string; command: UpdateContentCommand }) =>
      apiClient.put<AdminContentSummary>(`/admin/content/${contentId}`, command),
    onSuccess: (_, { contentId }) => {
      queryClient.invalidateQueries({ queryKey: ["admin-content"] });
      queryClient.invalidateQueries({ queryKey: ["admin-content-detail", contentId] });
    },
  });
}

export function useArchiveContent() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (contentId: string) => apiClient.delete<void>(`/admin/content/${contentId}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["admin-content"] }),
  });
}
