import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter } from "react-router-dom";
import { AuthProvider } from "./lib/auth/AuthContext";
import { ActiveProfileProvider } from "./lib/profile/ActiveProfileContext";
import { AppRouter } from "./router";

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: 1, refetchOnWindowFocus: false },
  },
});

export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <ActiveProfileProvider>
          <BrowserRouter>
            <AppRouter />
          </BrowserRouter>
        </ActiveProfileProvider>
      </AuthProvider>
    </QueryClientProvider>
  );
}
