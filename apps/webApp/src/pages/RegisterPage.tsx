import { FormEvent, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../lib/auth/AuthContext";
import { ApiError } from "../lib/apiClient";
import { ErrorBanner } from "../components/ErrorBanner";

export function RegisterPage() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const [displayName, setDisplayName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<unknown>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);
    setIsSubmitting(true);
    try {
      await register(email, password, displayName);
      navigate("/", { replace: true });
    } catch (err) {
      setError(err instanceof ApiError ? err : new Error("Unable to create your account. Please try again."));
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="flex min-h-screen items-center justify-center px-4">
      <form onSubmit={handleSubmit} className="w-full max-w-sm space-y-4 rounded-lg bg-surface-raised p-8">
        <h1 className="text-2xl font-bold text-brand">Create your account</h1>
        <ErrorBanner error={error} />
        <div>
          <label htmlFor="displayName" className="mb-1 block text-sm text-white/80">
            Display name
          </label>
          <input
            id="displayName"
            required
            value={displayName}
            onChange={(event) => setDisplayName(event.target.value)}
            className="w-full rounded-md bg-surface px-3 py-2 text-white focus:outline-none focus:ring-2 focus:ring-brand"
          />
        </div>
        <div>
          <label htmlFor="email" className="mb-1 block text-sm text-white/80">
            Email
          </label>
          <input
            id="email"
            type="email"
            required
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            className="w-full rounded-md bg-surface px-3 py-2 text-white focus:outline-none focus:ring-2 focus:ring-brand"
          />
        </div>
        <div>
          <label htmlFor="password" className="mb-1 block text-sm text-white/80">
            Password (min. 12 characters)
          </label>
          <input
            id="password"
            type="password"
            required
            minLength={12}
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            className="w-full rounded-md bg-surface px-3 py-2 text-white focus:outline-none focus:ring-2 focus:ring-brand"
          />
        </div>
        <button
          type="submit"
          disabled={isSubmitting}
          className="w-full rounded-md bg-brand py-2 font-semibold text-white hover:bg-brand-dark disabled:opacity-60"
        >
          {isSubmitting ? "Creating account..." : "Create account"}
        </button>
        <p className="text-sm text-white/60">
          Already have an account?{" "}
          <Link to="/login" className="text-white hover:underline">
            Sign in
          </Link>
        </p>
      </form>
    </div>
  );
}
