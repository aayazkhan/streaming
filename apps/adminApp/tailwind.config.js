/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        brand: {
          DEFAULT: "#2563eb",
          dark: "#1d4ed8",
        },
        surface: {
          DEFAULT: "#0f1115",
          raised: "#181c24",
        },
      },
    },
  },
  plugins: [],
};
