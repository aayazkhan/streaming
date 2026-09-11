/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        brand: {
          DEFAULT: "#e50914",
          dark: "#b0060f",
        },
        surface: {
          DEFAULT: "#0b0b0f",
          raised: "#181820",
        },
      },
    },
  },
  plugins: [],
};
