import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "../apiClient";
import type { CreateProfileCommand, Profile } from "../types/api";

export function useProfiles() {
  return useQuery({
    queryKey: ["profiles"],
    queryFn: () => apiClient.get<Profile[]>("/profiles"),
  });
}

export function useCreateProfile() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (command: CreateProfileCommand) => apiClient.post<Profile>("/profiles", command),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["profiles"] }),
  });
}
