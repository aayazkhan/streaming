import { Navigate, Route, Routes } from "react-router-dom";
import { NavBar } from "./components/NavBar";
import { ProtectedRoute } from "./components/ProtectedRoute";
import { RequireProfile } from "./components/RequireProfile";
import { LoginPage } from "./pages/LoginPage";
import { RegisterPage } from "./pages/RegisterPage";
import { ProfileSelectPage } from "./pages/ProfileSelectPage";
import { HomePage } from "./pages/HomePage";
import { BrowsePage } from "./pages/BrowsePage";
import { ContentDetailPage } from "./pages/ContentDetailPage";
import { SearchPage } from "./pages/SearchPage";
import { WatchlistPage } from "./pages/WatchlistPage";
import { PlayerPage } from "./pages/PlayerPage";

export function AppRouter() {
  return (
    <>
      <NavBar />
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route
          path="/profiles"
          element={
            <ProtectedRoute>
              <ProfileSelectPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/"
          element={
            <ProtectedRoute>
              <RequireProfile>
                <HomePage />
              </RequireProfile>
            </ProtectedRoute>
          }
        />
        <Route
          path="/browse"
          element={
            <ProtectedRoute>
              <RequireProfile>
                <BrowsePage />
              </RequireProfile>
            </ProtectedRoute>
          }
        />
        <Route
          path="/search"
          element={
            <ProtectedRoute>
              <RequireProfile>
                <SearchPage />
              </RequireProfile>
            </ProtectedRoute>
          }
        />
        <Route
          path="/watchlist"
          element={
            <ProtectedRoute>
              <RequireProfile>
                <WatchlistPage />
              </RequireProfile>
            </ProtectedRoute>
          }
        />
        <Route
          path="/content/:contentId"
          element={
            <ProtectedRoute>
              <RequireProfile>
                <ContentDetailPage />
              </RequireProfile>
            </ProtectedRoute>
          }
        />
        <Route
          path="/watch/:contentId"
          element={
            <ProtectedRoute>
              <RequireProfile>
                <PlayerPage />
              </RequireProfile>
            </ProtectedRoute>
          }
        />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </>
  );
}
