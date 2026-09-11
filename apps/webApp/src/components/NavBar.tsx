import { FormEvent, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../lib/auth/AuthContext";

export function NavBar() {
  const { isAuthenticated, logout } = useAuth();
  const navigate = useNavigate();
  const [query, setQuery] = useState("");

  const handleSearch = (event: FormEvent) => {
    event.preventDefault();
    if (query.trim()) navigate(`/search?q=${encodeURIComponent(query.trim())}`);
  };

  if (!isAuthenticated) return null;

  return (
    <header className="sticky top-0 z-10 flex items-center gap-6 bg-surface/95 px-6 py-3 backdrop-blur">
      <Link to="/" className="text-xl font-bold text-brand">
        Streaming
      </Link>
      <nav className="flex gap-4 text-sm text-white/80">
        <Link to="/">Home</Link>
        <Link to="/browse">Browse</Link>
        <Link to="/watchlist">My List</Link>
      </nav>
      <form onSubmit={handleSearch} className="ml-auto flex items-center">
        <input
          type="search"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="Search titles..."
          aria-label="Search titles"
          className="w-56 rounded-md bg-surface-raised px-3 py-1.5 text-sm text-white placeholder:text-white/40 focus:outline-none focus:ring-2 focus:ring-brand"
        />
      </form>
      <button
        onClick={() => {
          void logout();
          navigate("/login");
        }}
        className="text-sm text-white/70 hover:text-white"
      >
        Sign out
      </button>
    </header>
  );
}
