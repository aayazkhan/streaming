import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../lib/auth/AuthContext";

export function NavBar() {
  const { isAuthenticated, logout } = useAuth();
  const navigate = useNavigate();

  if (!isAuthenticated) return null;

  return (
    <header className="sticky top-0 z-10 flex items-center gap-6 bg-surface/95 px-6 py-3 backdrop-blur">
      <Link to="/" className="text-xl font-bold text-brand">
        Streaming Admin
      </Link>
      <nav className="flex gap-4 text-sm text-white/80">
        <Link to="/">Content</Link>
        <Link to="/content/new">New title</Link>
      </nav>
      <button
        onClick={() => {
          void logout();
          navigate("/login");
        }}
        className="ml-auto text-sm text-white/70 hover:text-white"
      >
        Sign out
      </button>
    </header>
  );
}
