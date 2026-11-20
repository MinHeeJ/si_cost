import { test, expect, type Page } from "@playwright/test";
import { writeFile } from "node:fs/promises";
import { getDocument } from "pdfjs-dist/legacy/build/pdf.mjs";
import type { DashboardData } from "../../frontend/src/pages/dashboardops/dashboardApi";
import {
  formatAmount,
  formatRate,
  type AmountUnit,
} from "../../frontend/src/shared/format";

// Actual authentication boundary and API-backed application. No route interception.
// Credentials stay in memory; disable all automatic credential-bearing artifacts.
test.use({
  trace: "off",
  screenshot: "off",
  video: "off",
  ...(process.env.SICOST_E2E_CHROMIUM_PATH
    ? {
        launchOptions: { executablePath: process.env.SICOST_E2E_CHROMIUM_PATH },
      }
    : {}),
});
const internal =
  /Receivables|CorporateTax|CashFlow|WorkforceForecast|SalesPipeline|OperatingProfit|MonthClose|\/api\/|\b(?:ALL|BILL|SETL|BILL_MISS|MM_OVER|SOURCE_UNKNOWN)\b/;
const cardIds = [
  "sales",
  "operating-profit",
  "ytd-operating-profit",
  "utilization",
  "receivable",
];
const compactText = (text: string) => text.replace(/\s+/g, "");
async function login(page: Page, restricted = false) {
  const user =
    process.env[
      restricted ? "SICOST_VERIFY_RESTRICTED_USER" : "SICOST_VERIFY_USER"
    ];
  const password =
    process.env[
      restricted
        ? "SICOST_VERIFY_RESTRICTED_PASSWORD"
        : "SICOST_VERIFY_PASSWORD"
    ];
  if (!user || !password)
    throw new Error(
      "실제 검증 계정 환경변수가 필요합니다. 계정값은 기록하지 않습니다.",
    );
  // Establish a real server session without putting credentials in UI traces.
  // Navigation below still runs the production AuthenticationBoundary, session
  // query, capabilities query and menu projection (never injected grants).
  const csrfResponse = await page.request.get("/api/auth/csrf");
  expect(csrfResponse.status()).toBe(200);
  const csrf = (await csrfResponse.json()).data;
  const response = await page.request.post("/api/auth/login", {
    data: { userId: user, password },
    headers: { [csrf.headerName]: csrf.token },
  });
  const result = await response.json();
  expect({
    status: response.status(),
    step: result.data?.nextStep,
    code: result.code,
  }).toEqual({ status: 200, step: "AUTHENTICATED", code: undefined });
  await page.goto("/UI-16");
  await expect(page.getByTestId("authentication-content")).toBeVisible({
    timeout: 20000,
  });
  const later = page.getByTestId(
    "authentication-password-recommendation-later-button",
  );
  if (await later.isVisible()) await later.click();
}
const host = (page: Page) => page.locator(".sicost-screen-host:not([hidden])");
async function dashboard(page: Page) {
  const response = page.waitForResponse(
    (r) => new URL(r.url()).pathname === "/api/dashboard" && r.status() === 200,
  );
  await page.goto("/UI-16");
  const { data } = (await (await response).json()) as { data: DashboardData };
  await expect(page.getByTestId("dashboard-screen")).toHaveAttribute(
    "aria-busy",
    "false",
  );
  return data;
}
async function checkLabels(page: Page) {
  const screen = page.getByTestId("dashboard-screen");
  const text = await screen.textContent();
  expect(text).not.toMatch(internal);
  expect(text).not.toMatch(/·\s*(?:·|$)/);
  for (const title of await screen
    .locator("[title]")
    .evaluateAll((nodes) => nodes.map((n) => n.getAttribute("title"))))
    expect(title).not.toMatch(internal);
  for (const panel of await screen.locator(".dashboard-panel").all())
    await panel.scrollIntoViewIfNeeded();
  await page.getByTestId("dashboard-basis-help").click();
  await expect(page.getByRole("tooltip")).toBeVisible();
  expect(await page.getByRole("tooltip").textContent()).not.toMatch(internal);
  await page.keyboard.press("Escape");
  for (const id of ["dashboard-trend", "dashboard-supply"]) {
    const chart = page.getByTestId(id).locator(".sicost-chart-host").first();
    await chart.scrollIntoViewIfNeeded();
    const box = await chart.boundingBox();
    expect(box).not.toBeNull();
    await page.mouse.move(box!.x + box!.width / 2, box!.y + box!.height / 2);
    await expect(chart.locator("strong")).toBeVisible();
    expect(
      await chart.locator("strong").locator("..").textContent(),
    ).not.toMatch(internal);
    await page.mouse.move(0, 0);
    await expect(chart.locator("strong")).toBeHidden();
  }
  await expect(page.getByTestId("dashboard-trend")).toContainText(
    "회사 전체 매출목표",
  );
  expect(await page.getByTestId("dashboard-trend").textContent()).not.toMatch(
    internal,
  );
}
async function printSnapshot(
  page: Page,
  data: DashboardData,
  output: (name: string) => string,
) {
  const unitText = await page.locator(".dashboard-toolbar small").textContent();
  const unit = (["백만원", "천원", "원"] as AmountUnit[]).find((u) =>
    unitText?.includes(`단위: ${u}`),
  );
  expect(unit).toBeTruthy();
  expect(data.metrics).toHaveLength(5);
  expect(
    data.metrics.some((metric) => metric.value !== null),
    "값이 있는 실제 조회 결과가 필요합니다",
  ).toBe(true);
  for (const metric of data.metrics) {
    const card = page.getByTestId(
      `dashboard-metric-${metric.metricCode.toLowerCase().replaceAll("_", "-")}`,
    );
    await expect(card.locator("strong")).toHaveText(
      metric.unit === "PERCENT"
        ? formatRate(metric.value)
        : `${formatAmount(metric.value, unit!)} ${unit}`,
    );
  }
  const before = await page.locator(".dashboard-metric").allTextContents();
  let reads = 0;
  const onRequest = (request: { url(): string }) => {
    if (new URL(request.url()).pathname === "/api/dashboard") reads++;
  };
  page.on("request", onRequest);
  await page.emulateMedia({ media: "print" });
  for (const selector of [
    ".sicost-app-body",
    ".sicost-main",
    ".sicost-screen-host:not([hidden])",
  ]) {
    await expect(page.locator(selector)).toHaveCSS("overflow", "visible");
    await expect(page.locator(selector)).toHaveCSS("display", "block");
  }
  await expect(host(page).locator(".sicost-screen")).toHaveCSS(
    "container-type",
    "normal",
  );
  for (const id of cardIds) {
    const card = page.getByTestId(`dashboard-metric-${id}`);
    await expect(card).toBeVisible();
    await expect(card.locator("strong")).toBeVisible();
    await expect(card).toHaveCSS("break-inside", "avoid");
  }
  await expect(page.locator(".dashboard-print-period")).toContainText(
    data.selectedMonth,
  );
  await expect(page.locator(".dashboard-print-period")).toContainText(
    data.today,
  );
  await expect(page.getByTestId("dashboard-export")).toBeHidden();
  await expect(page.locator(".sicost-header")).toBeHidden();
  await expect(host(page).locator("select")).toBeHidden();
  await expect(
    page
      .getByTestId("dashboard-metric-receivable")
      .locator(".dashboard-compact-basis"),
  ).toHaveText(
    `기준일 ${data.metrics.find((m) => m.metricCode === "RECEIVABLE")!.basis.basisDate}`,
  );
  expect(await page.locator(".dashboard-metric").allTextContents()).toEqual(
    before,
  );
  const cards = await page.locator(".dashboard-metric").evaluateAll((nodes) =>
    nodes.map((node) => ({
      title: node.querySelector("span")!.textContent!,
      value: node.querySelector("strong")!.textContent!,
      supplements: [...node.querySelectorAll("small")].map(
        (n) => n.textContent!,
      ),
      basis: (node.querySelector(".dashboard-basis") as HTMLElement).innerText,
    })),
  );
  await writeFile(
    output(`dashboard-${data.selectedMonth}-expected.json`),
    JSON.stringify(
      { month: data.selectedMonth, today: data.today, unit, cards },
      null,
      2,
    ),
  );
  await page
    .locator(".dashboard-metrics")
    .screenshot({ path: output(`dashboard-${data.selectedMonth}-print.png`) });
  const pdfBytes = await page.pdf({
    path: output(`dashboard-${data.selectedMonth}.pdf`),
    format: "A4",
    printBackground: true,
    margin: { top: "10mm", bottom: "10mm", left: "10mm", right: "10mm" },
  });
  // Visibility in print media is insufficient: a scrolling/contained ancestor
  // can still clip the actual PDF. Inspect the emitted pages, not just the DOM.
  const pdf = await getDocument({
    data: new Uint8Array(pdfBytes),
    useSystemFonts: false,
    isEvalSupported: false,
  }).promise;
  try {
    const pages: string[] = [];
    for (let number = 1; number <= pdf.numPages; number++) {
      const content = await (await pdf.getPage(number)).getTextContent();
      pages.push(
        compactText(
          content.items
            .map((item) => ("str" in item ? item.str : ""))
            .join(" "),
        ),
      );
    }
    const summaryPage = pages.find((text) =>
      cards.every((card) => text.includes(compactText(card.title))),
    );
    expect(
      summaryPage,
      "실제 PDF의 같은 페이지에 5개 요약 카드 제목이 모두 있어야 합니다",
    ).toBeTruthy();
    for (const card of cards) {
      for (const fragment of [card.value, ...card.supplements, card.basis]) {
        expect(
          summaryPage,
          `${card.title}: 화면의 값·보조 지표·기준을 카드와 같은 페이지에 출력해야 합니다`,
        ).toContain(compactText(fragment));
      }
    }
    expect(pages.join(" ")).not.toMatch(internal);
  } finally {
    await pdf.destroy();
  }
  expect(reads, "printing must not trigger another dashboard request").toBe(0);
  page.off("request", onRequest);
  await page.emulateMedia({ media: "screen" });
  console.log(
    `PRINT PASS UI-16 width=1440 month=${data.selectedMonth} cards=5 unit=${unit} basis=${data.today} status=${data.provisional ? "잠정" : data.selectedMonthCloseStatus}`,
  );
}

test("두 조회월의 5개 요약 카드·기준·단위·문구를 실제 Chromium 인쇄 PDF에 유지한다", async ({
  page,
}, info) => {
  test.setTimeout(120000);
  await login(page);
  const first = await dashboard(page);
  await checkLabels(page);
  await page.locator(".dashboard-metrics").screenshot({
    path: info.outputPath(`dashboard-${first.selectedMonth}.png`),
  });
  await printSnapshot(page, first, (name) => info.outputPath(name));
  const period = host(page).getByRole("combobox", {
    name: "기간",
    exact: true,
  });
  const options = await period
    .locator("option")
    .evaluateAll((nodes) => nodes.map((n) => (n as HTMLOptionElement).value));
  const other = options.filter((month) => month !== first.selectedMonth).at(-1);
  expect(other, "두 번째 검증 가능한 조회월이 필요합니다").toBeTruthy();
  const response = page.waitForResponse(
    (r) =>
      new URL(r.url()).pathname === "/api/dashboard" &&
      new URL(r.url()).searchParams.get("month") === other &&
      r.status() === 200,
  );
  await period.selectOption(other!);
  const { data: second } = (await (await response).json()) as {
    data: DashboardData;
  };
  await expect(page.getByTestId("dashboard-screen")).toHaveAttribute(
    "aria-busy",
    "false",
  );
  expect(second.selectedMonth).toBe(other);
  await checkLabels(page);
  await printSnapshot(page, second, (name) => info.outputPath(name));
  // Restore the actual user's period preference through the same UI.
  await period.selectOption(first.selectedMonth);
  await expect(page.getByTestId("dashboard-metric-sales")).toContainText(
    `${first.selectedMonth.slice(5)}월 매출`,
  );
  const restored = await page.request.get("/api/me/preferences");
  expect(restored.status()).toBe(200);
  const saved = (await restored.json()).data.items.find(
    (item: { type: string; key: string }) =>
      item.type === "PERIOD" && item.key === "UI-16",
  );
  expect(saved?.value).toEqual({
    type: "YEAR_MONTH",
    value: first.selectedMonth,
  });
});

test("실제 인증 경계: 사용자·공통코드 직접주소 390/767 안내 유지와 버튼 이동, 768 정상 접근", async ({
  page,
}, info) => {
  test.setTimeout(120000);
  await login(page);
  for (const [id, name] of [
    ["UI-23", "사용자"],
    ["UI-27", "공통코드"],
  ]) {
    for (const width of [1440, 390, 767, 768]) {
      await page.setViewportSize({ width, height: 900 });
      await page.goto(`/${id}`);
      if (width < 768) {
        const notice = page.getByTestId("pc-only-notice");
        await expect(notice).toBeVisible();
        await expect(notice).toContainText(`「${name}」`);
        await expect(page).toHaveURL(new RegExp(`/${id}$`));
        await expect(page.locator(`[data-screen-id="SCR-${id}"]`)).toHaveCount(
          0,
        );
        await expect(
          page.locator(`.sicost-sidebar a[href="/${id}"]`),
        ).toHaveCount(0);
        await notice.screenshot({
          path: info.outputPath(`${id}-${width}.png`),
        });
        await page
          .getByRole("button", { name: "대시보드로", exact: true })
          .click();
        await expect(page).toHaveURL(/\/UI-16$/);
        await expect(page.getByTestId("dashboard-screen")).toBeVisible();
      } else {
        await expect(
          host(page).getByRole("heading", { name, exact: true, level: 1 }),
        ).toBeVisible();
        await expect(page.getByTestId("pc-only-notice")).toHaveCount(0);
      }
      console.log(
        `MOBILE PASS /${id} width=${width} ${width < 768 ? "named notice remains until click, then UI-16" : "normal screen"}`,
      );
    }
  }
});

test("5개 카드와 업무 이동 버튼이 기존 대상 화면을 연다", async ({ page }) => {
  test.setTimeout(120000);
  await login(page);
  const data = await dashboard(page);
  for (const metric of data.metrics) {
    await page
      .getByTestId(
        `dashboard-metric-${metric.metricCode.toLowerCase().replaceAll("_", "-")}`,
      )
      .click();
    await expect(page).toHaveURL(
      new RegExp(`/${metric.link.screenId}(?:\\?|$)`),
    );
    await expect(host(page).locator("h1")).toBeVisible();
    await page.goto("/UI-16");
    await expect(page.getByTestId("dashboard-screen")).toHaveAttribute(
      "aria-busy",
      "false",
    );
  }
  for (const [button, target] of [
    ["dashboard-close-open", "UI-14"],
    ["dashboard-pipeline-open", "UI-29"],
  ]) {
    await page.getByTestId(button).click();
    await expect(page).toHaveURL(new RegExp(`/${target}$`));
    await expect(host(page).locator("h1")).toBeVisible();
    await page.goto("/UI-16");
    await expect(page.getByTestId("dashboard-screen")).toHaveAttribute(
      "aria-busy",
      "false",
    );
  }
});

test("모바일 무권한 계정의 직접주소는 기존 차단 흐름을 유지한다", async ({
  page,
}) => {
  test.skip(
    !process.env.SICOST_VERIFY_RESTRICTED_USER ||
      !process.env.SICOST_VERIFY_RESTRICTED_PASSWORD,
    "별도 무권한 검증 계정 환경변수 미제공 — 실제 브라우저 미검증",
  );
  await login(page, true);
  await page.setViewportSize({ width: 390, height: 900 });
  for (const id of ["UI-23", "UI-27"]) {
    await page.goto(`/${id}`);
    await expect(
      page.getByText("이 화면을 볼 권한이 없습니다.", { exact: true }),
    ).toBeVisible();
    await expect(page.getByTestId("pc-only-notice")).toHaveCount(0);
    await expect(page).not.toHaveURL(new RegExp(`/${id}$`));
  }
});

test("미인증 모바일 직접주소는 로그인 흐름을 유지한다", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 900 });
  await page.goto("/UI-27");
  await expect(page.getByTestId("authentication-login-form")).toBeVisible();
  await expect(page.getByTestId("pc-only-notice")).toHaveCount(0);
});
