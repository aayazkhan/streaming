import { FormEvent, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth, NotAnAdminError } from "../lib/auth/AuthContext";
import { ErrorBanner } from "../components/ErrorBanner";

export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<unknown>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);
    setIsSubmitting(true);
    try {
      await login(email, password);
      navigate("/", { replace: true });
    } catch (err) {
      setError(err instanceof NotAnAdminError ? err : new Error("Unable to sign in. Check your email and password."));
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="flex min-h-screen items-center justify-center px-4">
      <form onSubmit={handleSubmit} className="w-full max-w-sm space-y-4 rounded-lg bg-surface-raised p-8">
        <h1 className="text-2xl font-bold text-brand">Admin sign in</h1>
        <ErrorBanner error={error} />
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
            Password
          </label>
          <input
            id="password"
            type="password"
            required
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
          {isSubmitting ? "Signing in..." : "Sign in"}
        </button>
        <p className="text-xs text-white/50">
          Admin accounts are promoted via <code>POST /v1/admin/bootstrap</code> — see the README.
        </p>
      </form>
    </div>
  );
}
