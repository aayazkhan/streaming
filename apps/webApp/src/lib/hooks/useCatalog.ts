import { useInfiniteQuery, useQuery } from "@tanstack/react-query";
import { apiClient } from "../apiClient";
import { toQueryString } from "../queryString";
import type { ContentDetail, ContentListParams, ContentSummary, HomeFeed, Page } from "../types/api";

export function useHomeFeed() {
  return useQuery({
    queryKey: ["home"],
    queryFn: () => apiClient.get<HomeFeed>("/home", { auth: false }),
  });
}

export function useContentList(params: ContentListParams) {
  return useInfiniteQuery({
    queryKey: ["content", params],
    initialPageParam: undefined as string | undefined,
    queryFn: ({ pageParam }) =>
      apiClient.get<Page<ContentSummary>>(
        `/content${toQueryString({ ...params, cursor: pageParam })}`,
        { auth: false },
      ),
    getNextPageParam: (lastPage) => (lastPage.hasMore ? lastPage.nextCursor ?? undefined : undefined),
  });
}

export function useContentDetail(contentId: string | undefined) {
  return useQuery({
    queryKey: ["content-detail", contentId],
    queryFn: () => apiClient.get<ContentDetail>(`/content/${contentId}`, { auth: false }),
    enabled: Boolean(contentId),
  });
}

export function useSimilarContent(contentId: string | undefined) {
  return useQuery({
    queryKey: ["content-similar", contentId],
    queryFn: () => apiClient.get<ContentSummary[]>(`/content/${contentId}/similar`, { auth: false }),
    enabled: Boolean(contentId),
  });
}
