import { useQuery } from "@tanstack/react-query";
import { apiClient } from "../apiClient";
import { toQueryString } from "../queryString";
import type { AutocompleteSuggestion, SearchResult } from "../types/api";

export function useSearch(query: string) {
  return useQuery({
    queryKey: ["search", query],
    queryFn: () => apiClient.get<SearchResult>(`/search${toQueryString({ q: query })}`, { auth: false }),
    enabled: query.trim().length > 0,
  });
}

export function useAutocomplete(query: string) {
  return useQuery({
    queryKey: ["search-autocomplete", query],
    queryFn: () =>
      apiClient.get<AutocompleteSuggestion[]>(`/search/autocomplete${toQueryString({ q: query })}`, { auth: false }),
    enabled: query.trim().length > 0,
  });
}
