import type { ReactNode } from "react";
import { Navigate } from "react-router-dom";
import { useAuth } from "../lib/auth/AuthContext";
import { Spinner } from "./Spinner";

export function ProtectedRoute({ children }: { children: ReactNode }) {
  const { isAuthenticated, isInitializing } = useAuth();

  if (isInitializing) return <Spinner label="Restoring session" />;
  if (!isAuthenticated) return <Navigate to="/login" replace />;
  return <>{children}</>;
}
