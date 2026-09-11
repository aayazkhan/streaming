import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
export default defineConfig({
    plugins: [react()],
    server: {
        port: 3000,
        proxy: {
            "/v1": {
                target: process.env.VITE_API_PROXY_TARGET ?? "http://localhost:8080",
                changeOrigin: true,
            },
        },
    },
    build: {
        // hls.js is lazy-loaded only on the player route (see HtmlMediaPlaybackAdapter) and is
        // inherently large; it never lands in the initial bundle, so the default 500kB warning
        // for that isolated chunk is a false positive here.
        chunkSizeWarningLimit: 700,
        rollupOptions: {
            output: {
                manualChunks: {
                    "react-vendor": ["react", "react-dom", "react-router-dom"],
                },
            },
        },
    },
});
