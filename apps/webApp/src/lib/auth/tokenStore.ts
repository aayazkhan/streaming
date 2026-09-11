// Token storage tradeoff (documented in README): the backend returns the refresh token as plain
// JSON, not an httpOnly cookie, so the browser cannot get pure XSS-proof storage without a backend
// change. We keep the access token in memory only (lost on reload) and the refresh token in
// sessionStorage (cleared when the tab closes, not shared across origins like localStorage).

const REFRESH_TOKEN_KEY = "streaming.refreshToken";
const DEVICE_ID_KEY = "streaming.deviceId";

let accessToken: string | null = null;

export function getAccessToken(): string | null {
  return accessToken;
}

export function setAccessToken(token: string | null): void {
  accessToken = token;
}

export function getRefreshToken(): string | null {
  try {
    return sessionStorage.getItem(REFRESH_TOKEN_KEY);
  } catch {
    return null;
  }
}

export function setRefreshToken(token: string | null): void {
  try {
    if (token) sessionStorage.setItem(REFRESH_TOKEN_KEY, token);
    else sessionStorage.removeItem(REFRESH_TOKEN_KEY);
  } catch {
    // sessionStorage unavailable (private browsing, etc.) — session simply won't survive a refresh.
  }
}

export function clearTokens(): void {
  setAccessToken(null);
  setRefreshToken(null);
}

export function getOrCreateDeviceId(): string {
  try {
    const existing = localStorage.getItem(DEVICE_ID_KEY);
    if (existing) return existing;
    const created = crypto.randomUUID();
    localStorage.setItem(DEVICE_ID_KEY, created);
    return created;
  } catch {
    // localStorage unavailable — fall back to a per-session id, which is fine since deviceId is
    // only used to scope refresh-token rotation, not to identify the user.
    return crypto.randomUUID();
  }
}
