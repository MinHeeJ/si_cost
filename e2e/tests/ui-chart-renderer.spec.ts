import { test, expect } from "@playwright/test";

// Component-level real SVG renderer check against the frontend dev server. This is not an authenticated API test.
test("ECharts renders safely, resizes after a hidden tab is revealed and retains the original fallback", async ({
  page,
}) => {
  await page.emulateMedia({ reducedMotion: "reduce" });
  await page.goto("/UI-22");
  await expect(
    page.getByRole("heading", { name: "로그인", exact: true }),
  ).toBeVisible();
  await page.evaluate(async () => {
    const paths = [
      "/node_modules/.vite/deps/react.js",
      "/node_modules/.vite/deps/react-dom_client.js",
      "/src/shared/charts/ChartHost.tsx",
      "/src/shared/charts/chartAdapters.ts",
    ];
    const [
      { default: React },
      {
        default: { createRoot },
      },
      { ChartHost },
      { seriesOption },
    ] = await Promise.all(paths.map((path) => import(path)));
    const container = document.createElement("div");
    container.id = "chart-renderer-probe";
    container.style.cssText =
      "position:fixed;inset:0;z-index:10000;background:white;padding:24px";
    document.body.appendChild(container);
    createRoot(container).render(
      React.createElement(
        ChartHost,
        {
          testId: "renderer-probe",
          label: "렌더러 검증",
          option: seriesOption(
            ["음수", "영", "미확인", "큰 금액"],
            [
              {
                name: "<img src=x onerror=alert(1)>",
                values: ["-1000000", "0", null, "9007199254740993"],
              },
            ],
            "원",
          ),
        },
        React.createElement(
          "p",
          { "data-testid": "renderer-fallback" },
          "원본 숫자표 대체",
        ),
      ),
    );
  });
  const host = page.getByTestId("renderer-probe");
  await expect(
    host.getByRole("img", { name: "렌더러 검증", exact: true }),
  ).toBeVisible();
  await expect(
    host.locator(
      ".sicost-chart-canvas > div > svg, .sicost-chart-canvas > svg",
    ),
  ).toBeVisible();
  await expect(page.getByTestId("renderer-fallback")).toBeHidden();
  expect(await host.locator("img").count()).toBe(0);
  await page.locator("#chart-renderer-probe").evaluate((element) => {
    (element as HTMLElement).style.display = "none";
  });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.locator("#chart-renderer-probe").evaluate((element) => {
    (element as HTMLElement).style.display = "block";
  });
  await expect
    .poll(() =>
      host
        .locator(".sicost-chart-canvas svg")
        .evaluate((element) => element.getBoundingClientRect().width),
    )
    .toBeLessThanOrEqual(342);
  await expect(
    host.getByRole("img", { name: "렌더러 검증", exact: true }),
  ).toBeVisible();
});
