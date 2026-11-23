import { test, expect, type Page } from "@playwright/test";
import { spawnSync } from "node:child_process";
import { formatAmount } from "../../frontend/src/shared/format";
import { mkdir } from "node:fs/promises";
import { createRequire } from "node:module";

const runtimeCheck = createRequire(import.meta.url)(
  "../../aiops.runtime-check.json",
);

// Real session/capabilities/menu APIs only. Never record credentials/cookies in artifacts.
test.use({ trace: "off", screenshot: "off", video: "off" });
const cards = [
  "sales",
  "operating-profit",
  "ytd-operating-profit",
  "utilization",
  "receivable",
];
const internalText =
  /Receivables|CorporateTax|CashFlow|WorkforceForecast|SalesPipeline|MonthClose|OperatingProfit|\/api\/|\b(?:BILL_MISS|SETL|BILL|ALL|REAL_PAY_AMT|VAT_RT)\b|·\s*·/;

async function login(page: Page, prefix = "SICOST_VERIFY") {
  // Capability/session refresh can re-display this existing advisory after a
  // full-page visit. Use its normal API-backed button, never force/hide overlays.
  const recommendation = page.getByTestId(
    "authentication-password-recommendation-dialog",
  );
  await page.addLocatorHandler(recommendation, async () => {
    await page
      .getByTestId("authentication-password-recommendation-later-button")
      .click();
    await expect(recommendation).toBeHidden();
  });
  const user = process.env[`${prefix}_USER`],
    password = process.env[`${prefix}_PASSWORD`];
  if (!user || !password)
    throw new Error(
      `${prefix} 계정 환경변수가 필요합니다. 값은 기록하지 않습니다.`,
    );
  await page.goto("/UI-22");
  await expect(page.getByTestId("authentication-login-form")).toBeVisible();
  await page.evaluate(
    ({ user, password }) => {
      const set = Object.getOwnPropertyDescriptor(
        HTMLInputElement.prototype,
        "value",
      )!.set!;
      for (const [name, value] of [
        ["userId", user],
        ["password", password],
      ]) {
        const input = document.querySelector<HTMLInputElement>(
          `input[name="${name}"]`,
        )!;
        set.call(input, value);
        input.dispatchEvent(new Event("input", { bubbles: true }));
      }
    },
    { user, password },
  );
  const result = page.waitForResponse(
    (r) =>
      new URL(r.url()).pathname === "/api/auth/login" &&
      r.request().method() === "POST",
    { timeout: 20_000 },
  );
  await page.getByRole("button", { name: "로그인", exact: true }).click();
  const response = await result;
  expect(response.status(), "실제 로그인 API 성공").toBe(200);
  // Use a verifier-provisioned account with initial password change/MFA complete.
  expect(
    (await response.json()).data.nextStep,
    "시험 계정의 추가 인증 준비 필요",
  ).toBe("AUTHENTICATED");
  await expect(page.getByTestId("authentication-content")).toBeVisible();

  const responseCaps = await page.request.get("/api/current-user/capabilities");
  expect(responseCaps.status()).toBe(200);
  return (await responseCaps.json()).data as {
    screens: Record<string, string[]>;
    pcOnlyScreens: string[];
  };
}
async function dashboard(page: Page) {
  const result = page.waitForResponse(
    (r) =>
      new URL(r.url()).pathname === "/api/dashboard" &&
      r.request().method() === "GET",
    { timeout: 20_000 },
  );
  await page.goto("/UI-16");
  const response = await result;
  expect(response.status()).toBe(200);
  const data = (await response.json()).data;
  await expect(page.getByTestId("dashboard-screen")).toHaveAttribute(
    "aria-busy",
    "false",
  );
  await expect(
    page.getByRole("combobox", { name: "기간", exact: true }),
  ).toBeEnabled();
  return data;
}
async function cleanDashboard(page: Page) {
  const root = page.getByTestId("dashboard-screen");
  // Names are business data, not system text. Exclude only registered-name nodes.
  const text = await root.evaluate((element) => {
    const copy = element.cloneNode(true) as HTMLElement;
    copy
      .querySelectorAll(
        '[data-testid^="dashboard-alert-"] strong, [data-testid^="dashboard-opportunity-"] strong, [data-testid^="dashboard-part-"] td:first-child',
      )
      .forEach((node) => node.remove());
    return copy.textContent ?? "";
  });
  expect(text).not.toMatch(internalText);
  expect(
    await root
      .locator("[title]")
      .evaluateAll((nodes) =>
        nodes.map((node) => node.getAttribute("title")).join("\n"),
      ),
  ).not.toMatch(internalText);
  await page.getByTestId("dashboard-cash").scrollIntoViewIfNeeded();
  await page.getByTestId("dashboard-basis-help").click();
  await expect(page.getByRole("tooltip")).not.toContainText(internalText);
  await page.keyboard.press("Escape");
  const chart = page.locator(
    '[data-testid="dashboard-trend"] .sicost-chart-host',
  );
  await chart.scrollIntoViewIfNeeded();
  const box = await chart.boundingBox();
  expect(box).not.toBeNull();
  await page.mouse.move(box!.x + box!.width * 0.45, box!.y + box!.height * 0.5);
  const chartTooltip = chart.locator("div:has(>strong)");
  await expect(chartTooltip).toBeVisible();
  await expect(chartTooltip).toContainText("회사 전체 매출목표");
  await expect(chartTooltip).not.toContainText(internalText);
}
function pdfPages(pdf: Buffer): string[] {
  const result = spawnSync(
    "python",
    [
      "-c",
      "import sys,json,io; from pypdf import PdfReader; print(json.dumps([p.extract_text() for p in PdfReader(io.BytesIO(sys.stdin.buffer.read())).pages]))",
    ],
    { input: pdf },
  );
  if (result.status !== 0)
    throw new Error(
      "출력물 검증에 Python pypdf가 필요합니다. PDF 생성만으로 통과 처리하지 않습니다.",
    );
  return JSON.parse(result.stdout.toString());
}

test("BASIC-156 런타임 계약: 헬스·실제 로그인·첫 메뉴 조회", async ({
  page,
}) => {
  test.setTimeout(60_000);
  const health = await page.request.get(runtimeCheck.backend.health.path);
  expect(health.status()).toBe(200);
  const healthData = await health.json();
  // Assert booleans/types, not payload objects that reporters could serialize.
  expect(healthData.success === true).toBe(true);
  expect(healthData.meta !== null && typeof healthData.meta === "object").toBe(
    true,
  );
  await login(page);
  const firstMenu = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname === runtimeCheck.first_menu.path &&
      response.request().method() === runtimeCheck.first_menu.method,
  );
  await page.goto(runtimeCheck.first_menu.route);
  const response = await firstMenu;
  expect(response.status()).toBe(200);
  const menuData = await response.json();
  expect(menuData.success === true).toBe(true);
  expect(Array.isArray(menuData.data?.items)).toBe(true);
  await expect(page.getByTestId("common-codes-screen")).toBeVisible();
});

test("BASIC-156 배치·공통 헤더·반응형·배지와 실데이터 확인할 일", async ({
  page,
}) => {
  test.setTimeout(120_000);
  await page.emulateMedia({ reducedMotion: "reduce" });
  await login(page);
  const data = await dashboard(page);
  for (const width of [1440, 1700, 1280, 390]) {
    await page.setViewportSize({ width, height: 1000 });
    const box = async (id: string) => {
      const result = await page.getByTestId(id).boundingBox();
      expect(result).not.toBeNull();
      return result!;
    };
    const trend = await box("dashboard-trend"),
      actions = await box("dashboard-actions");
    const supply = await box("dashboard-supply"),
      alerts = await box("dashboard-alerts");
    if (width >= 1440) {
      expect(
        Math.abs(trend.y + trend.height - actions.y - actions.height),
      ).toBeLessThan(2);
      expect(
        Math.abs(supply.y + supply.height - alerts.y - alerts.height),
      ).toBeLessThan(2);
      expect(Math.abs(trend.x - supply.x)).toBeLessThan(2);
      expect(Math.abs(actions.x - alerts.x)).toBeLessThan(2);
      expect(Math.abs(trend.width / actions.width - 1.5)).toBeLessThan(0.01);
      const bottom = await Promise.all(
        ["dashboard-parts", "dashboard-pipeline", "dashboard-cash"].map(box),
      );
      for (const item of bottom) {
        expect(Math.abs(item.y - bottom[0].y)).toBeLessThan(2);
        expect(Math.abs(item.height - bottom[0].height)).toBeLessThan(2);
        expect(Math.abs(item.width - bottom[0].width)).toBeLessThan(2);
      }
    }
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    ).toBe(true);
    const root = page.getByTestId("dashboard-screen");
    expect(
      await root.evaluate((node) => node.scrollWidth <= node.clientWidth),
    ).toBe(true);
    const badges = page.locator(
      '[data-testid="dashboard-alerts"] .dashboard-list-row > .sicost-badge, [data-testid="dashboard-cash"] .dashboard-list-row > .sicost-badge',
    );
    for (const badge of await badges.all()) {
      const bounds = await badge.boundingBox();
      expect(bounds!.width).toBe(88);
      expect(bounds!.height).toBe(24);
      expect(
        await badge.evaluate((node) => getComputedStyle(node).fontSize),
      ).toBe("12px");
    }
    const tableViewport = page
      .getByRole("table", { name: "영업 파트별 실적" })
      .locator("..");
    await tableViewport.evaluate((node) => {
      node.scrollLeft = node.scrollWidth;
    });
    const lastColumn = page
      .getByRole("table", { name: "영업 파트별 실적" })
      .locator("thead th")
      .last();
    const cell = await lastColumn.boundingBox({ timeout: 5_000 }),
      viewport = await tableViewport.boundingBox();
    expect(cell!.x + cell!.width).toBeLessThanOrEqual(
      viewport!.x + viewport!.width + 1,
    );
    if (width === 390) {
      const sales = await box("dashboard-metric-sales"),
        profit = await box("dashboard-metric-operating-profit"),
        ytd = await box("dashboard-metric-ytd-operating-profit");
      expect(sales.y).toBe(profit.y);
      expect(ytd.y).toBeGreaterThan(sales.y);
      expect(
        Math.abs(ytd.width - (profit.x + profit.width - sales.x)),
      ).toBeLessThan(2);
    }
    if (process.env.SICOST_VERIFY_CAPTURE_DIR) {
      await mkdir(process.env.SICOST_VERIFY_CAPTURE_DIR, { recursive: true });
      await page.screenshot({
        path: `${process.env.SICOST_VERIFY_CAPTURE_DIR}/dashboard-${width}.png`,
        fullPage: true,
      });
      await page.getByTestId("dashboard-supply").scrollIntoViewIfNeeded();
      await page.screenshot({
        path: `${process.env.SICOST_VERIFY_CAPTURE_DIR}/dashboard-${width}-middle.png`,
      });
      await page.getByTestId("dashboard-cash").scrollIntoViewIfNeeded();
      await page.screenshot({
        path: `${process.env.SICOST_VERIFY_CAPTURE_DIR}/dashboard-${width}-bottom.png`,
      });
    }
    console.log(
      `BASIC-156 / ${width}px / ${data.selectedMonth}: 배치·가로 넘침·표 오른쪽 열·실제 배지 크기 통과`,
    );
  }
  await page.setViewportSize({ width: 1440, height: 1000 });
  await dashboard(page);
  const heading = page.locator(
    ".sicost-screen-host:not([hidden]) .sicost-title-row",
  );
  const titleX = (await heading.boundingBox())!.x;
  const titleHeight = (await heading.boundingBox())!.height;
  for (const screen of ["UI-15", "UI-18"]) {
    await page.goto(`/${screen}`);
    await expect(heading).toBeVisible();
    const bounds = (await heading.boundingBox())!;
    expect(bounds.x).toBe(titleX);
    expect(bounds.height).toBe(titleHeight);
  }
  await dashboard(page);
  const active = data.actions.filter(
    (action: { count: number }) => action.count > 0,
  );
  const panel = page.getByTestId("dashboard-actions");
  await expect(panel.locator(".dashboard-action-count").first()).toHaveText(
    `전체 ${active.length}`,
  );
  await expect(panel.locator(".dashboard-action-count").last()).toHaveText(
    `급함 ${active.filter((action: { urgent: boolean }) => action.urgent).length}`,
  );
  await expect(panel.locator('[data-testid^="dashboard-action-"]')).toHaveCount(
    Math.min(5, active.length),
  );
  if (active.length > 5) {
    const open = page.getByTestId("dashboard-actions-all");
    await open.scrollIntoViewIfNeeded();
    const before = await page
      .locator(".sicost-main")
      .evaluate((node) => ({ top: node.scrollTop, height: node.scrollHeight }));
    await open.click();
    const dialog = page.getByRole("dialog");
    await expect(
      dialog.locator('[data-testid^="dashboard-modal-action-"]'),
    ).toHaveCount(active.length);
    expect(
      await page.locator(".sicost-main").evaluate((node) => ({
        top: node.scrollTop,
        height: node.scrollHeight,
      })),
    ).toEqual(before);
    await dialog.locator(".sicost-dialog-body").evaluate((node) => {
      node.scrollTop = node.scrollHeight;
    });
    await page.keyboard.press("Escape");
    await expect(open).toBeFocused();
    await open.click();
    await page.getByTestId("dashboard-actions-close").click();
    await expect(open).toBeFocused();
  } else {
    await expect(page.getByTestId("dashboard-actions-all")).toHaveCount(0);
    console.log(
      `BASIC-156 모달 브라우저 검증 보류: 실제 데이터 ${active.length}유형 (6유형 이상 필요)`,
    );
  }
});

test("BASIC-156 조회월·추이 기간·자금 차트·테마·실제 다운로드", async ({
  page,
}) => {
  test.setTimeout(120_000);
  await login(page);
  let data = await dashboard(page);
  const period = page.getByRole("combobox", { name: "기간", exact: true });
  const past = await period.locator("option").evaluateAll(
    (nodes, current) =>
      nodes
        .map((node) => (node as HTMLOptionElement).value)
        .filter((month) => month < current)
        .at(-1),
    data.currentMonth,
  );
  expect(past).toBeTruthy();
  for (const month of [data.currentMonth, past]) {
    if (month !== data.selectedMonth) {
      const response = page.waitForResponse(
        (r) =>
          new URL(r.url()).pathname === "/api/dashboard" &&
          new URL(r.url()).searchParams.get("month") === month,
      );
      await period.selectOption(month!);
      data = (await (await response).json()).data;
      await expect(page.getByTestId("dashboard-screen")).toHaveAttribute(
        "aria-busy",
        "false",
      );
    }
    await page.getByTestId("dashboard-trend-recent").click();
    const details = page.getByTestId("dashboard-trend-details");
    if (
      !(await details
        .locator("..")
        .evaluate((node) => (node as HTMLDetailsElement).open))
    )
      await details.click();
    for (const range of ["recent", "all"]) {
      await page.getByTestId(`dashboard-trend-${range}`).click();
      const selected = data.selectedMonth;
      const expected = data.salesCostTrend.filter((row: { month: string }) => {
        const distance =
          (Number(selected.slice(0, 4)) - Number(row.month.slice(0, 4))) * 12 +
          Number(selected.slice(5)) -
          Number(row.month.slice(5));
        return range === "all" || (distance >= 0 && distance < 3);
      });
      await expect(
        page
          .getByRole("table", { name: "매출·원가 추이 숫자표" })
          .locator("tbody tr"),
      ).toHaveCount(expected.length);
      for (const row of expected) {
        const cells = page
          .getByTestId(`dashboard-trend-${row.month}`)
          .locator("td");
        for (const [index, value] of [
          row.salesAmount,
          row.salesCostAmount,
          row.salesTargetAmount,
        ].entries())
          await expect(cells.nth(index + 1)).toHaveText(
            formatAmount(value, "천원"),
          );
      }
      await expect(period).toHaveValue(selected);
      await expect(page.getByTestId("dashboard-trend")).toContainText(
        "단위: 천원",
      );
    }
    const cashChart = page.getByTestId("dashboard-cash-chart");
    await cashChart.scrollIntoViewIfNeeded();
    await expect(cashChart.locator("svg")).toBeVisible();
    const cashMonths = data.cashForecast.map(
      (row: { month: string }) => row.month,
    );
    const spansYears =
      new Set(cashMonths.map((month: string) => month.slice(0, 4))).size > 1;
    const lastMonth = cashMonths.at(-1);
    const lastLabel = spansYears
      ? lastMonth
      : `${Number(lastMonth.slice(5))}월`;
    const labelBounds = (await cashChart
      .locator("svg text")
      .filter({ hasText: new RegExp(`^${lastLabel}$`) })
      .first()
      .boundingBox())!;
    const chartBounds = (await cashChart.boundingBox())!;
    expect(labelBounds.x + labelBounds.width).toBeLessThanOrEqual(
      chartBounds.x + chartBounds.width,
    );
    for (const row of data.cashForecast) {
      const item = page.getByTestId(`dashboard-cash-${row.month}`);
      await expect(item).toContainText(
        `월말 잔액 ${formatAmount(row.closingBalance, "천원")} 천원`,
      );
      await expect(item).toContainText(
        `최소 자금 ${formatAmount(row.minimumCashAmount, "천원")} 천원`,
      );
    }
    for (const colorScheme of ["light", "dark"] as const) {
      await page.emulateMedia({ colorScheme });
      const progress = page.getByRole("progressbar", {
        name: "마감 점검 진행",
      });
      await expect(progress.locator("span")).toHaveCount(
        data.closeProgress.totalCheckCount,
      );
      await expect(progress.locator(".passed")).toHaveCount(
        data.closeProgress.passedCheckCount,
      );
      const colors = await progress.locator("span").evaluateAll((nodes) =>
        nodes.map((node) => ({
          passed: node.classList.contains("passed"),
          color: getComputedStyle(node).backgroundColor,
          opacity: getComputedStyle(node).opacity,
        })),
      );
      expect(
        colors
          .filter((color) => !color.passed)
          .every((color) => Number(color.opacity) < 1),
      ).toBe(true);
      if (process.env.SICOST_VERIFY_CAPTURE_DIR) {
        await progress.scrollIntoViewIfNeeded();
        await page.screenshot({
          path: `${process.env.SICOST_VERIFY_CAPTURE_DIR}/dashboard-${colorScheme}-${data.selectedMonth}.png`,
        });
      }
    }
    await page.emulateMedia({ colorScheme: "light" });
    const download = page.waitForEvent("download");
    await page.getByTestId("dashboard-export").click();
    const file = await download;
    expect(await file.failure()).toBeNull();
    expect(file.suggestedFilename()).toMatch(/\.xlsx$/);
    console.log(
      `BASIC-156 / 1440px / ${data.selectedMonth}: 최근3개월·전체기간 원본 값/단위·자금 월별 값·양쪽 테마 마감 구간·엑셀 다운로드 통과`,
    );
  }
});

test("BASIC-156 실제 확인할 일·프로젝트·자금 행의 업무 이동 문맥", async ({
  page,
}) => {
  test.setTimeout(120_000);
  await login(page);
  let data = await dashboard(page);
  const sections: Record<string, string> = {
    OVERDUE_RECEIVABLE: "receivables",
    BILLING_MISSING: "checks",
    CORPORATE_TAX_DUE: "tax",
    STAFF_SHORTAGE: "supply",
  };
  const filters: Record<string, string> = {
    OVERDUE_RECEIVABLE: "overdue",
    MM_OVER: "MM_OVER",
  };
  const action = data.actions.find((row: { count: number }) => row.count > 0);
  if (action) {
    const id = action.actionCode.toLowerCase().replaceAll("_", "-");
    const card = page.getByTestId(`dashboard-action-${id}`);
    if (await card.count()) await card.click();
    else {
      await page.getByTestId("dashboard-actions-all").click();
      await page.getByTestId(`dashboard-modal-action-${id}`).click();
    }
    await expect(page).toHaveURL(new RegExp(`/${action.link.screenId}$`));
    const state = await page.evaluate(() => history.state.usr);
    expect(state).toMatchObject({
      fromDashboard: true,
      linkKey: action.link.linkKey,
      month:
        action.actionCode === "CASH_SHORTAGE" && action.link.linkKey
          ? action.link.linkKey
          : action.basis.basisMonth,
    });
    if (sections[action.actionCode])
      expect(state.section).toBe(sections[action.actionCode]);
    if (filters[action.actionCode])
      expect(state.filter).toBe(filters[action.actionCode]);
    if (action.actionCode === "CORPORATE_TAX_DUE") {
      await expect(page.getByTestId("financial-tax-panel")).toBeVisible();
      await expect(page.getByTestId("financial-period-input")).toHaveValue(
        action.basis.basisMonth,
      );
    }
  }
  data = await dashboard(page);
  if (data.projectAlerts?.length) {
    const alert = data.projectAlerts[0];
    await page
      .getByTestId(
        `dashboard-alert-${alert.projectId.toLowerCase().replace(/[^a-z0-9-]/g, "-")}-${alert.alertType.toLowerCase().replaceAll("_", "-")}`,
      )
      .click();
    expect(new URL(page.url()).searchParams.get("detailProjectId")).toBe(
      alert.projectId,
    );
    expect(new URL(page.url()).searchParams.get("month")).toBe(
      alert.basis.basisMonth,
    );
  } else
    console.log(
      "BASIC-156 프로젝트 경보 이동·상태별 배지 브라우저 검증 보류: 실제 경보 없음",
    );
  data = await dashboard(page);
  for (const row of data.cashForecast ?? []) {
    await page.getByTestId(`dashboard-cash-${row.month}`).click();
    await expect(page).toHaveURL(new RegExp(`/${row.link.screenId}$`));
    await expect(
      page.getByText(`대시보드 요청월 ${row.month}`, { exact: false }),
    ).toBeVisible();
    expect(await page.evaluate(() => history.state.usr)).toMatchObject({
      fromDashboard: true,
      month: row.month,
    });
    data = await dashboard(page);
  }
});

test("BASIC-156 최초·다른 월 스켈레톤·같은 월 갱신·실패/재시도·모션 줄이기", async ({
  page,
}) => {
  test.setTimeout(120_000);
  await login(page);
  await dashboard(page);
  for (const reducedMotion of ["no-preference", "reduce"] as const) {
    const width = reducedMotion === "reduce" ? 390 : 1440;
    await page.setViewportSize({ width, height: 1000 });
    await page.emulateMedia({ reducedMotion });
    let release!: () => void;
    let gate = new Promise<void>((resolve) => {
      release = resolve;
    });
    const hold = async (route: import("@playwright/test").Route) => {
      await gate;
      await route.continue().catch(() => undefined);
    };
    await page.route("**/api/dashboard?*", hold);
    await page.route("**/api/dashboard", hold);
    await page.goto("/UI-16");
    const skeleton = page.getByTestId("dashboard-skeleton");
    await expect(skeleton).toBeVisible();
    await expect(
      page
        .getByRole("status")
        .filter({ hasText: "대시보드를 불러오는 중입니다." }),
    ).toBeVisible();
    if (width >= 768)
      await expect(page.getByTestId("dashboard-export")).toBeDisabled();
    await expect(skeleton).toHaveAttribute("aria-hidden", "true");
    await expect(
      skeleton.locator("button,input,a,select,textarea,[tabindex]"),
    ).toHaveCount(0);
    expect(
      await skeleton
        .locator(".dashboard-skeleton-card")
        .first()
        .evaluate((node) => getComputedStyle(node).animationName),
    ).toBe(reducedMotion === "reduce" ? "none" : "dashboard-pulse");
    expect(
      await skeleton.evaluate((node) => node.scrollWidth <= node.clientWidth),
    ).toBe(true);
    if (process.env.SICOST_VERIFY_CAPTURE_DIR)
      await page.screenshot({
        path: `${process.env.SICOST_VERIFY_CAPTURE_DIR}/dashboard-loading-${width}.png`,
      });
    release();
    await expect(page.getByTestId("dashboard-metric-sales")).toBeVisible();
    await expect(page.getByTestId("dashboard-screen")).toHaveAttribute(
      "aria-busy",
      "false",
    );
    const shown = await page
      .getByTestId("dashboard-metric-sales")
      .textContent();
    gate = new Promise<void>((resolve) => {
      release = resolve;
    });
    await page.evaluate(() =>
      window.dispatchEvent(new CustomEvent("sicost:bills-committed")),
    );
    await expect(page.getByText("갱신 중…", { exact: true })).toBeVisible();
    await expect(skeleton).toHaveCount(0);
    expect(await page.getByTestId("dashboard-metric-sales").textContent()).toBe(
      shown,
    );
    release();
    await expect(page.getByTestId("dashboard-screen")).toHaveAttribute(
      "aria-busy",
      "false",
    );
    gate = new Promise<void>((resolve) => {
      release = resolve;
    });
    const period = page.getByRole("combobox", { name: "기간", exact: true });
    const beforeMonth = await period.inputValue();
    const other = await period
      .locator("option")
      .evaluateAll(
        (nodes, selected) =>
          nodes
            .map((node) => (node as HTMLOptionElement).value)
            .find((month) => month !== selected),
        beforeMonth,
      );
    await period.selectOption(other!);
    await expect(skeleton).toBeVisible();
    await expect(page.getByTestId("dashboard-metric-sales")).toHaveCount(0);
    release();
    await expect(skeleton).toHaveCount(0);
    await expect(period).toHaveValue(other!);
    await page.unroute("**/api/dashboard?*", hold);
    await page.unroute("**/api/dashboard", hold);
    console.log(
      `BASIC-156 / ${width}px / ${beforeMonth}→${other} / motion ${reducedMotion}: 최초·월변경 스켈레톤·같은 월 데이터 유지 갱신 통과`,
    );
  }
  await page.setViewportSize({ width: 1440, height: 1000 });
  await dashboard(page);
  const fail = (route: import("@playwright/test").Route) =>
    route.abort("failed");
  await page.route("**/api/dashboard?*", fail);
  await page.route("**/api/dashboard", fail);
  const failedPeriod = page.getByRole("combobox", {
    name: "기간",
    exact: true,
  });
  const failedMonth = await failedPeriod
    .locator("option")
    .evaluateAll(
      (nodes, selected) =>
        nodes
          .map((node) => (node as HTMLOptionElement).value)
          .find((month) => month !== selected),
      await failedPeriod.inputValue(),
    );
  await failedPeriod.selectOption(failedMonth!);
  await expect(page.getByTestId("dashboard-retry")).toBeVisible();
  await expect(page.getByTestId("dashboard-screen")).toHaveAttribute(
    "aria-busy",
    "false",
  );
  await expect(page.getByTestId("dashboard-export")).toBeDisabled();
  await page.unroute("**/api/dashboard?*", fail);
  await page.unroute("**/api/dashboard", fail);
  await page.getByTestId("dashboard-retry").click();
  await expect(page.getByTestId("dashboard-metric-sales")).toBeVisible();
});

test("UI-16: 두 조회월의 현재 카드·기준·단위·상태를 그대로 인쇄하고 전체 문구를 확인한다", async ({
  page,
}) => {
  test.setTimeout(120_000);
  await login(page);
  let data = await dashboard(page);
  const period = page.getByRole("combobox", { name: "기간", exact: true });
  const months = await period
    .locator("option")
    .evaluateAll((nodes) =>
      nodes.map((node) => (node as HTMLOptionElement).value),
    );
  expect(months.length, "다른 조회월도 준비되어야 합니다").toBeGreaterThan(1);
  const otherMonth = months.find((month) => month !== data.selectedMonth)!;
  for (const index of [0, 1]) {
    if (index === 1) {
      const response = page.waitForResponse(
        (r) =>
          new URL(r.url()).pathname === "/api/dashboard" &&
          new URL(r.url()).searchParams.get("month") === otherMonth,
      );
      await period.selectOption(otherMonth);
      data = (await (await response).json()).data;
      await expect(page.getByTestId("dashboard-screen")).toHaveAttribute(
        "aria-busy",
        "false",
      );
    }
    expect(data.metrics).toHaveLength(5);
    await cleanDashboard(page);
    const shown = await Promise.all(
      cards.map((id) =>
        page.getByTestId(`dashboard-metric-${id}`).textContent(),
      ),
    );
    const dates = await page
      .getByRole("group", { name: "조회 기간 상태" })
      .textContent();
    let extraReads = 0;
    const countReads = (request: import("@playwright/test").Request) => {
      if (new URL(request.url()).pathname === "/api/dashboard") extraReads++;
    };
    page.on("request", countReads);
    await page.emulateMedia({ media: "print" });
    for (const [cardIndex, id] of cards.entries()) {
      const card = page.getByTestId(`dashboard-metric-${id}`);
      await expect(card).toBeVisible();
      expect(await card.textContent()).toBe(shown[cardIndex]);
      expect(
        await card.evaluate((node) => getComputedStyle(node).breakInside),
      ).toBe("avoid");
      await expect(card.locator(".dashboard-basis-period")).toBeVisible();
    }
    await expect(page.getByTestId("dashboard-export")).toBeHidden();
    await expect(page.getByTestId("dashboard-close-open")).toBeHidden();
    await expect(period).toBeHidden();
    await expect(page.locator(".sicost-sidebar")).toBeHidden();
    const pdf = await page.pdf({
      format: "A4",
      printBackground: true,
      margin: { top: "10mm", bottom: "10mm", left: "10mm", right: "10mm" },
    });
    expect(pdf.length).toBeGreaterThan(0);
    const pages = pdfPages(pdf).map((text) => text.replace(/\s/g, ""));
    const labels = ["월매출", "월영업이익", "누계영업이익", "가동률", "미수금"];
    expect(
      pages.some((text) => labels.every((label) => text.includes(label))),
      "다섯 카드가 같은 출력 페이지에 존재",
    ).toBe(true);
    const output = pages.join("");
    for (const id of cards) {
      const value = await page
        .getByTestId(`dashboard-metric-${id}`)
        .locator("strong")
        .textContent();
      expect(output).toContain(value!.replace(/\s/g, ""));
    }
    expect(output).toContain(data.selectedMonth);
    expect(output).toContain(data.today);
    const businessNames: string[] = [
      ...(data.projectAlerts ?? []).map(
        (row: { projectName: string }) => row.projectName,
      ),
      ...data.pipeline.startingSoon.map(
        (row: { opportunityName: string }) => row.opportunityName,
      ),
      ...(data.salesParts ?? []).map(
        (row: { partName: string }) => row.partName,
      ),
    ];
    const systemOutput = businessNames.reduce(
      (text, name) => text.split(name.replace(/\s/g, "")).join(""),
      output,
    );
    expect(systemOutput).not.toMatch(internalText);
    // Validate the real PDF in memory only; never persist business content.
    expect(
      await page.getByRole("group", { name: "조회 기간 상태" }).textContent(),
    ).toBe(dates);
    expect(extraReads, "인쇄는 대시보드를 다시 조회하지 않는다").toBe(0);
    page.off("request", countReads);
    await page.emulateMedia({ media: "screen" });
    console.log(
      `UI-16 / 1440px / ${data.selectedMonth}: 카드 5개·표시값·단위·기준·상태 동일, 추가 조회 0, PDF 생성 통과`,
    );
  }
});

for (const [id, name] of [
  ["UI-23", "사용자"],
  ["UI-27", "공통코드"],
]) {
  for (const width of [390, 767, 768]) {
    test(`${id}: ${width}px 직접 방문은 실제 인증·메뉴 설정을 따른다`, async ({
      page,
    }) => {
      const caps = await login(page);
      for (const screen of ["UI-16", id])
        expect(caps.screens[screen]).toContain("INQ");
      expect(caps.pcOnlyScreens).toContain(id);
      // Establish the same principal can open the desktop business screen first.
      await page.goto(`/${id}`);
      await expect(
        page.locator(
          `.sicost-screen-host:not([hidden]) > .sicost-screen[data-screen-id="SCR-${id}"]`,
        ),
      ).toBeVisible();
      await page.setViewportSize({ width, height: 844 });
      await page.goto(`/${id}`);
      if (width === 768) {
        await expect(page.getByTestId("pc-only-notice")).toHaveCount(0);
        await expect(
          page.locator(
            `.sicost-screen-host:not([hidden]) > .sicost-screen[data-screen-id="SCR-${id}"]`,
          ),
        ).toBeVisible();
      } else {
        const notice = page.getByTestId("pc-only-notice");
        await expect(notice).toContainText(`「${name}」`);
        await expect(page).toHaveURL(new RegExp(`/${id}$`));
        await expect(page.locator(`[data-screen-id="SCR-${id}"]`)).toHaveCount(
          0,
        );
        await expect(
          page.locator(`.sicost-sidebar a[href="/${id}"]`),
        ).toHaveCount(0);
        await page.getByTestId("pc-only-dashboard-button").click();
        await expect(page).toHaveURL(/\/UI-16$/);
        await expect(page.getByTestId("dashboard-screen")).toHaveAttribute(
          "aria-busy",
          "false",
        );
      }
      console.log(
        `${id} / ${width}px: 직접 방문·안내 유지·본문 제한·클릭 이동 통과`,
      );
    });
  }
}

test("UI-16: 요약 카드 5개와 업무 버튼은 기존 대상 화면으로 이동한다", async ({
  page,
}) => {
  test.setTimeout(120_000);
  await login(page);
  for (const id of cards) {
    const data = await dashboard(page);
    const metric = data.metrics.find(
      (item: { metricCode: string }) =>
        item.metricCode.toLowerCase().replaceAll("_", "-") === id,
    );
    expect(metric).toBeTruthy();
    await page.getByTestId(`dashboard-metric-${id}`).click();
    await expect(page).toHaveURL(
      new RegExp(`/${metric.link.screenId}(?:\\?|$)`),
    );
    await expect(
      page.locator(
        `.sicost-screen-host:not([hidden]) > .sicost-screen[data-screen-id="SCR-${metric.link.screenId}"]`,
      ),
    ).toBeVisible();
  }
  await dashboard(page);
  await page.getByTestId("dashboard-close-open").click();
  await expect(page).toHaveURL(/\/UI-14$/);
  await expect(
    page.locator(
      '.sicost-screen-host:not([hidden]) > .sicost-screen[data-screen-id="SCR-UI-14"]',
    ),
  ).toBeVisible();
  await dashboard(page);
  await page.getByTestId("dashboard-pipeline-open").click();
  await expect(page).toHaveURL(/\/UI-29$/);
  await expect(
    page.locator(
      '.sicost-screen-host:not([hidden]) > .sicost-screen[data-screen-id="SCR-UI-29"]',
    ),
  ).toBeVisible();
});

test("모바일: 조회 권한 없는 별도 계정은 PC 안내가 아닌 기존 차단 흐름을 따른다", async ({
  page,
}) => {
  test.skip(
    !process.env.SICOST_RESTRICTED_VERIFY_USER ||
      !process.env.SICOST_RESTRICTED_VERIFY_PASSWORD,
    "조회 권한 없는 별도 검증 계정 미제공",
  );
  const caps = await login(page, "SICOST_RESTRICTED_VERIFY");
  await page.setViewportSize({ width: 390, height: 844 });
  for (const id of ["UI-23", "UI-27"]) {
    expect(caps.screens[id]?.includes("INQ") ?? false).toBe(false);
    await page.goto(`/${id}`);
    await expect(
      page.getByText("이 화면을 볼 권한이 없습니다.", { exact: true }),
    ).toBeVisible();
    await expect(page.getByTestId("pc-only-notice")).toHaveCount(0);
    await expect(page.locator(`[data-screen-id="SCR-${id}"]`)).toHaveCount(0);
  }
});

test("모바일: 대시보드 권한이 없으면 안내 버튼은 기존 첫 화면으로 이동한다", async ({
  page,
}) => {
  test.skip(
    !process.env.SICOST_NO_DASHBOARD_VERIFY_USER ||
      !process.env.SICOST_NO_DASHBOARD_VERIFY_PASSWORD,
    "PC 화면 권한만 가진 별도 검증 계정 미제공",
  );
  const caps = await login(page, "SICOST_NO_DASHBOARD_VERIFY");
  expect(caps.screens["UI-16"]?.includes("INQ") ?? false).toBe(false);
  const target = ["UI-23", "UI-27"].find((id) =>
    caps.screens[id]?.includes("INQ"),
  );
  expect(target).toBeTruthy();
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto(`/${target}`);
  await page.getByTestId("pc-only-dashboard-button").click();
  const first = Object.keys(caps.screens).find(
    (id) => !caps.pcOnlyScreens.includes(id),
  );
  if (first) await expect(page).toHaveURL(new RegExp(`/${first}$`));
  else
    await expect(page.getByTestId("authentication-pc-required")).toBeVisible();
});

test("모바일: 미인증 직접 주소는 기존 로그인 흐름을 따른다", async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/UI-27");
  await expect(page.getByTestId("authentication-login-form")).toBeVisible();
  await expect(page.getByTestId("pc-only-notice")).toHaveCount(0);
});
