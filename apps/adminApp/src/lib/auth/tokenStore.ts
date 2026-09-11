// Separate storage keys from apps/webApp's consumer app — this is a fully separate deployment
// and must never share session state with the end-user app, even if both happen to be open in
// the same browser. Same tradeoff as the consumer app: access token in memory only, refresh
// token in sessionStorage (no httpOnly cookie support on the backend yet).

const REFRESH_TOKEN_KEY = "admin.refreshToken";
const DEVICE_ID_KEY = "admin.deviceId";

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
    // sessionStorage unavailable — session simply won't survive a reload.
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
    return crypto.randomUUID();
  }
}

/** Decodes the JWT's `role` claim for UI gating only — never a security boundary. Every write
 * route re-checks the role server-side (requireAdmin() in AdminContentRoutes.kt). */
export function decodeRoleClaim(accessToken: string): string | null {
  try {
    const payload = accessToken.split(".")[1];
    if (!payload) return null;
    const decoded = JSON.parse(atob(payload.replace(/-/g, "+").replace(/_/g, "/")));
    return typeof decoded.role === "string" ? decoded.role : null;
  } catch {
    return null;
  }
}
