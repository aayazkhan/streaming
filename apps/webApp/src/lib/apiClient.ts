import type { ApiErrorResponse, TokenPair } from "./types/api";
import { clearTokens, getAccessToken, getOrCreateDeviceId, getRefreshToken, setAccessToken, setRefreshToken } from "./auth/tokenStore";

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "/v1";

export class ApiError extends Error {
  public readonly code: string;
  public readonly status: number;
  public readonly correlationId: string;
  public readonly details: Record<string, string>;

  constructor(status: number, body: ApiErrorResponse) {
    super(body.message);
    this.name = "ApiError";
    this.status = status;
    this.code = body.code;
    this.correlationId = body.correlationId;
    this.details = body.details ?? {};
  }
}

type SessionExpiredListener = () => void;
const sessionExpiredListeners = new Set<SessionExpiredListener>();

/** AuthContext subscribes so it can clear user state and redirect when a refresh attempt fails. */
export function onSessionExpired(listener: SessionExpiredListener): () => void {
  sessionExpiredListeners.add(listener);
  return () => sessionExpiredListeners.delete(listener);
}

function notifySessionExpired(): void {
  clearTokens();
  sessionExpiredListeners.forEach((listener) => listener());
}

interface RequestOptions {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  body?: unknown;
  auth?: boolean; // defaults to true; set false for public endpoints called before login
  signal?: AbortSignal;
}

let refreshInFlight: Promise<TokenPair> | null = null;

async function refreshAccessToken(): Promise<TokenPair> {
  if (refreshInFlight) return refreshInFlight;

  const refreshToken = getRefreshToken();
  if (!refreshToken) throw new Error("No refresh token available");

  refreshInFlight = fetch(`${BASE_URL}/auth/refresh`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ refreshToken, deviceId: getOrCreateDeviceId() }),
  })
    .then(async (response) => {
      if (!response.ok) throw new Error("Refresh failed");
      const tokens = (await response.json()) as TokenPair;
      setAccessToken(tokens.accessToken);
      setRefreshToken(tokens.refreshToken);
      return tokens;
    })
    .finally(() => {
      refreshInFlight = null;
    });

  return refreshInFlight;
}

async function parseErrorBody(response: Response): Promise<ApiErrorResponse> {
  try {
    return (await response.json()) as ApiErrorResponse;
  } catch {
    return { code: "UNKNOWN_ERROR", message: response.statusText || "Request failed", correlationId: "unknown" };
  }
}

async function performRequest<T>(path: string, options: RequestOptions): Promise<T> {
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  const requiresAuth = options.auth ?? true;
  if (requiresAuth) {
    const token = getAccessToken();
    if (token) headers.Authorization = `Bearer ${token}`;
  }

  const response = await fetch(`${BASE_URL}${path}`, {
    method: options.method ?? "GET",
    headers,
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
    signal: options.signal,
  });

  if (response.status === 204) return undefined as T;

  if (!response.ok) {
    const body = await parseErrorBody(response);
    throw new ApiError(response.status, body);
  }

  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const requiresAuth = options.auth ?? true;
  try {
    return await performRequest<T>(path, options);
  } catch (error) {
    if (error instanceof ApiError && error.status === 401 && requiresAuth) {
      try {
        await refreshAccessToken();
      } catch {
        notifySessionExpired();
        throw error;
      }
      return performRequest<T>(path, options);
    }
    throw error;
  }
}

export const apiClient = {
  get: <T>(path: string, options?: Omit<RequestOptions, "method" | "body">) => apiRequest<T>(path, { ...options, method: "GET" }),
  post: <T>(path: string, body?: unknown, options?: Omit<RequestOptions, "method" | "body">) =>
    apiRequest<T>(path, { ...options, method: "POST", body }),
  put: <T>(path: string, body?: unknown, options?: Omit<RequestOptions, "method" | "body">) =>
    apiRequest<T>(path, { ...options, method: "PUT", body }),
  patch: <T>(path: string, body?: unknown, options?: Omit<RequestOptions, "method" | "body">) =>
    apiRequest<T>(path, { ...options, method: "PATCH", body }),
  delete: <T>(path: string, options?: Omit<RequestOptions, "method" | "body">) => apiRequest<T>(path, { ...options, method: "DELETE" }),
};
