import type { ReactNode } from "react";
import { Navigate } from "react-router-dom";
import { useActiveProfile } from "../lib/profile/ActiveProfileContext";

export function RequireProfile({ children }: { children: ReactNode }) {
  const { activeProfileId } = useActiveProfile();
  if (!activeProfileId) return <Navigate to="/profiles" replace />;
  return <>{children}</>;
}
