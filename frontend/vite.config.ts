import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    proxy: {
      // API_URL permet de viser un autre backend (ex. staging) en développement
      "/v1": process.env.API_URL ?? "http://localhost:8080",
    },
  },
});
