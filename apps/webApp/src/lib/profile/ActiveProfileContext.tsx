import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { useAuth } from "../auth/AuthContext";

const ACTIVE_PROFILE_KEY = "streaming.activeProfileId";

interface ActiveProfileContextValue {
  activeProfileId: string | null;
  setActiveProfileId: (profileId: string | null) => void;
}

const ActiveProfileContext = createContext<ActiveProfileContextValue | undefined>(undefined);

export function ActiveProfileProvider({ children }: { children: ReactNode }) {
  const { isAuthenticated, isInitializing } = useAuth();
  const [activeProfileId, setActiveProfileIdState] = useState<string | null>(() => {
    try {
      return sessionStorage.getItem(ACTIVE_PROFILE_KEY);
    } catch {
      return null;
    }
  });

  useEffect(() => {
    // Only clear once we know for certain the user is logged out (isInitializing has settled) —
    // isAuthenticated is transiently false while the silent refresh-token restore is in flight on
    // a fresh page load, and clearing here would wipe a still-valid persisted profile selection.
    if (!isInitializing && !isAuthenticated) setActiveProfileIdState(null);
  }, [isAuthenticated, isInitializing]);

  const setActiveProfileId = (profileId: string | null) => {
    setActiveProfileIdState(profileId);
    try {
      if (profileId) sessionStorage.setItem(ACTIVE_PROFILE_KEY, profileId);
      else sessionStorage.removeItem(ACTIVE_PROFILE_KEY);
    } catch {
      // non-fatal: profile selection just won't survive a reload in this browsing context
    }
  };

  const value = useMemo(() => ({ activeProfileId, setActiveProfileId }), [activeProfileId]);

  return <ActiveProfileContext.Provider value={value}>{children}</ActiveProfileContext.Provider>;
}

export function useActiveProfile(): ActiveProfileContextValue {
  const context = useContext(ActiveProfileContext);
  if (!context) throw new Error("useActiveProfile must be used within an ActiveProfileProvider");
  return context;
}
