import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "../apiClient";
import type { ContinueWatchingItem, PlaybackGrant, StartPlaybackCommand, UpdatePositionCommand } from "../types/api";

export function useStartPlayback() {
  return useMutation({
    mutationFn: (command: StartPlaybackCommand) => apiClient.post<PlaybackGrant>("/playback/sessions", command),
  });
}

export function useUpdatePlaybackPosition() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ sessionId, command }: { sessionId: string; command: UpdatePositionCommand }) =>
      apiClient.patch(`/playback/sessions/${sessionId}/position`, command),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["continue-watching"] }),
  });
}

export function useContinueWatching() {
  return useQuery({
    queryKey: ["continue-watching"],
    queryFn: () => apiClient.get<ContinueWatchingItem[]>("/continue-watching"),
  });
}
