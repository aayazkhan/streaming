import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { apiClient, onSessionExpired } from "../apiClient";
import { clearTokens, decodeRoleClaim, getOrCreateDeviceId, getRefreshToken, setAccessToken, setRefreshToken } from "./tokenStore";
import type { AuthenticationResponse, TokenPair, UserSummary } from "../types/api";

interface AuthContextValue {
  user: UserSummary | null;
  isAuthenticated: boolean;
  isInitializing: boolean;
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export class NotAnAdminError extends Error {
  constructor() {
    super("This account does not have administrator access.");
    this.name = "NotAnAdminError";
  }
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

  useEffect(() => {
    const refreshToken = getRefreshToken();
    if (!refreshToken) {
      setIsInitializing(false);
      return;
    }
    apiClient
      .post<TokenPair>("/auth/refresh", { refreshToken, deviceId: getOrCreateDeviceId() }, { auth: false })
      .then((tokens) => {
        if (decodeRoleClaim(tokens.accessToken) !== "ADMIN") {
          clearSession();
          return;
        }
        setAccessToken(tokens.accessToken);
        setRefreshToken(tokens.refreshToken);
        setIsAuthenticated(true);
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
    if (decodeRoleClaim(response.session.tokens.accessToken) !== "ADMIN") {
      throw new NotAnAdminError();
    }
    setAccessToken(response.session.tokens.accessToken);
    setRefreshToken(response.session.tokens.refreshToken);
    setUser(response.user);
    setIsAuthenticated(true);
  }, []);

  const logout = useCallback(async () => {
    const refreshToken = getRefreshToken();
    clearSession();
    if (refreshToken) {
      await apiClient.post("/auth/logout", { refreshToken }, { auth: false }).catch(() => {});
    }
  }, [clearSession]);

  const value = useMemo<AuthContextValue>(
    () => ({ user, isAuthenticated, isInitializing, login, logout }),
    [user, isAuthenticated, isInitializing, login, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error("useAuth must be used within an AuthProvider");
  return context;
}
