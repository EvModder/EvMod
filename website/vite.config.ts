import { defineConfig } from "vite";
import react from "@vitejs/plugin-react-swc";
import path from "path";

const basePath = process.env.BASE_PATH ?? "/EvMod/";

export default defineConfig({
  base: basePath.endsWith("/") ? basePath : `${basePath}/`,
  server: { port: 8080 },
  plugins: [react()],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
});
