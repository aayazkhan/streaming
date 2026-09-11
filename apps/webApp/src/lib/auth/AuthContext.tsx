import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { apiClient, onSessionExpired } from "../apiClient";
import { clearTokens, getOrCreateDeviceId, getRefreshToken, setAccessToken, setRefreshToken } from "./tokenStore";
import type { AuthenticationResponse, TokenPair, UserSummary } from "../types/api";

interface AuthContextValue {
  user: UserSummary | null;
  isAuthenticated: boolean;
  isInitializing: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string, displayName: string) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

function applySession(session: AuthenticationResponse["session"]): void {
  setAccessToken(session.tokens.accessToken);
  setRefreshToken(session.tokens.refreshToken);
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserSummary | null>(null);
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [isInitializing, setIsInitializing] = useState(true);

  const clearSession = useCallback(() => {
    clearTokens();
    setUser(null);
    setIsAuthenticated(false);
  }, []);

  useEffect(() => onSessionExpired(clearSession), [clearSession]);

  // On load, there's no access token (memory-only) but there may be a refresh token in
  // sessionStorage from earlier in this tab's life — use it to silently restore the session.
  useEffect(() => {
    const refreshToken = getRefreshToken();
    if (!refreshToken) {
      setIsInitializing(false);
      return;
    }
    apiClient
      .post<TokenPair>("/auth/refresh", { refreshToken, deviceId: getOrCreateDeviceId() }, { auth: false })
      .then((tokens) => {
        setAccessToken(tokens.accessToken);
        setRefreshToken(tokens.refreshToken);
        setIsAuthenticated(true);
        // We don't have a "whoami" endpoint; the login/register response carried the user summary,
        // but a bare refresh does not. Re-deriving it isn't available from the API today, so `user`
        // stays null until the next explicit login — see README "Known limitations".
      })
      .catch(() => clearSession())
      .finally(() => setIsInitializing(false));
  }, [clearSession]);

  const login = useCallback(async (email: string, password: string) => {
    const response = await apiClient.post<AuthenticationResponse>(
      "/auth/login",
      { email, password, deviceId: getOrCreateDeviceId() },
      { auth: false },
    );
    applySession(response.session);
    setUser(response.user);
    setIsAuthenticated(true);
  }, []);

  const register = useCallback(async (email: string, password: string, displayName: string) => {
    const response = await apiClient.post<AuthenticationResponse>(
      "/auth/register",
      { email, password, displayName, deviceId: getOrCreateDeviceId() },
      { auth: false },
    );
    applySession(response.session);
    setUser(response.user);
    setIsAuthenticated(true);
  }, []);

  const logout = useCallback(async () => {
    const refreshToken = getRefreshToken();
    clearSession();
    if (refreshToken) {
      await apiClient.post("/auth/logout", { refreshToken }, { auth: false }).catch(() => {
        // best-effort: the local session is already cleared regardless of server-side outcome
      });
    }
  }, [clearSession]);

  const value = useMemo<AuthContextValue>(
    () => ({ user, isAuthenticated, isInitializing, login, register, logout }),
    [user, isAuthenticated, isInitializing, login, register, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error("useAuth must be used within an AuthProvider");
  return context;
}
