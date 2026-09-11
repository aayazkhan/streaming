import { Navigate, Route, Routes } from "react-router-dom";
import { NavBar } from "./components/NavBar";
import { ProtectedRoute } from "./components/ProtectedRoute";
import { LoginPage } from "./pages/LoginPage";
import { ContentListPage } from "./pages/ContentListPage";
import { ContentEditorPage } from "./pages/ContentEditorPage";
import { ContentDetailPage } from "./pages/ContentDetailPage";

export function AppRouter() {
  return (
    <>
      <NavBar />
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route
          path="/"
          element={
            <ProtectedRoute>
              <ContentListPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/content/new"
          element={
            <ProtectedRoute>
              <ContentEditorPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/content/:contentId"
          element={
            <ProtectedRoute>
              <ContentDetailPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/content/:contentId/edit"
          element={
            <ProtectedRoute>
              <ContentEditorPage />
            </ProtectedRoute>
          }
        />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </>
  );
}
