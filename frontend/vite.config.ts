/// <reference types="vitest/config" />
import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { readFileSync } from "node:fs";
import { defineConfig } from "vite";

// Version affichée en bas des Réglages (tenue à jour par release-please)
const version = JSON.parse(readFileSync(new URL("./package.json", import.meta.url), "utf8")).version;

export default defineConfig({
  plugins: [react(), tailwindcss()],
  define: { __APP_VERSION__: JSON.stringify(version) },
  server: {
    proxy: {
      // API_URL permet de viser un autre backend (ex. staging) en développement
      "/v1": process.env.API_URL ?? "http://localhost:8080",
    },
  },
  test: {
    // Logique pure de src/lib : pas de DOM. Fuseau fixé par le script npm
    // (TZ=Europe/Paris) pour que les dates locales soient reproductibles.
    include: ["src/**/*.test.ts"],
    environment: "node",
  },
});
