import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AuthProvider, useAuth } from "./AuthContext";
import { clearTokens } from "./tokenStore";

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

function TestConsumer() {
  const { user, isAuthenticated, isInitializing, login, logout } = useAuth();
  return (
    <div>
      <span data-testid="state">
        {isInitializing ? "initializing" : isAuthenticated ? "authenticated" : "anonymous"}
      </span>
      <span data-testid="user">{user?.displayName ?? "none"}</span>
      <button onClick={() => login("user@example.com", "correct-password")}>Login</button>
      <button onClick={() => logout()}>Logout</button>
    </div>
  );
}

describe("AuthProvider", () => {
  beforeEach(() => {
    clearTokens();
    sessionStorage.clear();
    vi.restoreAllMocks();
  });

  it("starts anonymous when there is no stored refresh token", async () => {
    vi.stubGlobal("fetch", vi.fn());
    render(
      <AuthProvider>
        <TestConsumer />
      </AuthProvider>,
    );
    await waitFor(() => expect(screen.getByTestId("state")).toHaveTextContent("anonymous"));
  });

  it("becomes authenticated after a successful login", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        jsonResponse(200, {
          user: { id: "u1", email: "user@example.com", displayName: "Ada", emailVerified: true },
          session: {
            userId: "u1",
            deviceId: "d1",
            tokens: { accessToken: "access", refreshToken: "refresh", accessTokenExpiresAt: "2030-01-01T00:00:00Z" },
          },
        }),
      ),
    );

    render(
      <AuthProvider>
        <TestConsumer />
      </AuthProvider>,
    );
    await waitFor(() => expect(screen.getByTestId("state")).toHaveTextContent("anonymous"));

    await act(async () => {
      await userEvent.click(screen.getByText("Login"));
    });

    expect(screen.getByTestId("state")).toHaveTextContent("authenticated");
    expect(screen.getByTestId("user")).toHaveTextContent("Ada");
  });

  it("clears state on logout", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValueOnce(
          jsonResponse(200, {
            user: { id: "u1", email: "user@example.com", displayName: "Ada", emailVerified: true },
            session: {
              userId: "u1",
              deviceId: "d1",
              tokens: { accessToken: "access", refreshToken: "refresh", accessTokenExpiresAt: "2030-01-01T00:00:00Z" },
            },
          }),
        )
        .mockResolvedValueOnce(new Response(null, { status: 204 })),
    );

    render(
      <AuthProvider>
        <TestConsumer />
      </AuthProvider>,
    );
    await waitFor(() => expect(screen.getByTestId("state")).toHaveTextContent("anonymous"));
    await act(async () => {
      await userEvent.click(screen.getByText("Login"));
    });
    expect(screen.getByTestId("state")).toHaveTextContent("authenticated");

    await act(async () => {
      await userEvent.click(screen.getByText("Logout"));
    });
    expect(screen.getByTestId("state")).toHaveTextContent("anonymous");
    expect(screen.getByTestId("user")).toHaveTextContent("none");
  });
});
