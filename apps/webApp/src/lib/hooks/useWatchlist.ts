import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "../apiClient";
import type { Page, WatchlistEntry } from "../types/api";

export function useWatchlist() {
  return useQuery({
    queryKey: ["watchlist"],
    queryFn: () => apiClient.get<Page<WatchlistEntry>>("/watchlist"),
  });
}

export function useAddToWatchlist() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (contentId: string) => apiClient.put<WatchlistEntry>(`/watchlist/${contentId}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["watchlist"] }),
  });
}

export function useRemoveFromWatchlist() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (contentId: string) => apiClient.delete<void>(`/watchlist/${contentId}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["watchlist"] }),
  });
}
