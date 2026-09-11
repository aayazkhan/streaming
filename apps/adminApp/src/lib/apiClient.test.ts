import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiClient, ApiError, onSessionExpired } from "./apiClient";
import { clearTokens, getAccessToken, setRefreshToken } from "./auth/tokenStore";

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

describe("apiClient", () => {
  beforeEach(() => {
    clearTokens();
    vi.restoreAllMocks();
  });

  it("returns parsed JSON on success", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(jsonResponse(200, { hello: "world" })));
    const result = await apiClient.get<{ hello: string }>("/ping", { auth: false });
    expect(result).toEqual({ hello: "world" });
  });

  it("throws ApiError with the server's error fields on failure", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(jsonResponse(403, { code: "CONTENT_ADMIN_REQUIRED", message: "forbidden", correlationId: "abc" })),
    );
    await expect(apiClient.get("/admin/content", { auth: false })).rejects.toMatchObject({
      code: "CONTENT_ADMIN_REQUIRED",
      status: 403,
    });
  });

  it("refreshes the access token once and retries after a 401, then succeeds", async () => {
    setRefreshToken("old-refresh-token");
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(401, { code: "UNAUTHORIZED", message: "expired", correlationId: "1" }))
      .mockResolvedValueOnce(
        jsonResponse(200, { accessToken: "new-access", refreshToken: "new-refresh", accessTokenExpiresAt: "2030-01-01T00:00:00Z" }),
      )
      .mockResolvedValueOnce(jsonResponse(200, { ok: true }));
    vi.stubGlobal("fetch", fetchMock);

    const result = await apiClient.get<{ ok: boolean }>("/admin/content");

    expect(result).toEqual({ ok: true });
    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(getAccessToken()).toBe("new-access");
  });

  it("notifies session-expired listeners when refresh itself fails", async () => {
    setRefreshToken("old-refresh-token");
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(401, { code: "UNAUTHORIZED", message: "expired", correlationId: "1" }))
      .mockResolvedValueOnce(jsonResponse(401, { code: "UNAUTHORIZED", message: "refresh failed", correlationId: "2" }));
    vi.stubGlobal("fetch", fetchMock);

    const listener = vi.fn();
    const unsubscribe = onSessionExpired(listener);

    await expect(apiClient.get("/admin/content")).rejects.toBeInstanceOf(ApiError);
    expect(listener).toHaveBeenCalledTimes(1);
    expect(getAccessToken()).toBeNull();

    unsubscribe();
  });
});
