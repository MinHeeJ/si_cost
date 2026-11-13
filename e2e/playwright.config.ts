import { defineConfig } from "@playwright/test";

if (!process.env.SICOST_E2E_BASE_URL)
  throw new Error(
    "SICOST_E2E_BASE_URL: runner가 시작한 독립 DB 앱 주소가 필요합니다.",
  );

export default defineConfig({
  testDir: "./tests",
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL: process.env.SICOST_E2E_BASE_URL,
    browserName: "chromium",
    headless: true,
    viewport: { width: 1440, height: 1000 },
    screenshot: "only-on-failure",
    trace: "retain-on-failure",
    acceptDownloads: true,
  },
});
