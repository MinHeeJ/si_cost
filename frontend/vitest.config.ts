import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["./src/shared/testSetup.ts"],
    restoreMocks: true,
    // Bound concurrent jsdom workers so container CPU limits do not cause UI timeouts.
    maxWorkers: 2,
    minWorkers: 1,
  },
});
