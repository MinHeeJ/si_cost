import { test, expect } from "@playwright/test";
import { mkdir, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { account, csrf } from "./basic147/authSupport";

// Real API and DB only. Requires a disposable app and an allowed FIN fixture.
// A running project with a progress stage is required for the row-navigation case.
test.use({ trace: "off", screenshot: "off", video: "off" });
test.beforeEach(async ({ page }) => {
  if (!process.env.SICOST_E2E_BASE_URL || process.env.BASIC147_E2E_DISPOSABLE !== "true")
    throw new Error("Disposable live app and FIN fixture are required");
  const fixture = account("FIN");
  const token = await csrf(page.request);
  const login = await page.request.post("/api/auth/login", {
    headers: { [token.headerName]: token.token },
    data: { userId: fixture.userId, password: fixture.password },
  });
  expect(login.status()).toBe(200);
  expect((await login.json()).data.nextStep).toBe("AUTHENTICATED");
});

for (const item of [
  { route: "/UI-02", root: "developers-screen", input: "developer-search-input", filter: "developer-filter-jobcode", submit: "developer-search-button", toggle: "developer-filter-form-toggle", reset: "developer-empty-reset" },
  { route: "/UI-01", root: "projects-screen", input: "project-search", filter: "project-filter-status", submit: "project-query", toggle: "project-filter-form-toggle", reset: "project-empty-reset" },
]) {
  test(`${item.route}: applied conditions, empty result and reset`, async ({ page }) => {
    await page.goto(item.route);
    const screen = page.getByTestId(item.root);
    await page.getByTestId(item.input).fill("없는이름zz");
    const filter = page.getByTestId(item.filter);
    await expect(filter.locator("option").nth(1)).toBeAttached();
    await filter.selectOption({ index: 1 });
    await page.getByTestId(item.submit).click();
    await expect(page.getByTestId(item.toggle)).toHaveText("[조건 (2)]");
    await expect(screen.getByText("조건에 맞는 결과가 없습니다.", { exact: true }).filter({ visible: true })).toBeVisible();
    await page.getByTestId(item.reset).click();
    await expect(page.getByTestId(item.toggle)).toHaveText("[조건 (0)]");
    await expect(page.getByTestId(item.input)).toHaveValue("");
    await expect(filter).toHaveValue("");
  });
}

for (const item of [
  { route: "/UI-33", root: "candidates-screen", fields: ["candidate-filter-job-code", "candidate-filter-grade-code"], submit: "candidate-query", reset: "candidate-empty-reset" },
  { route: "/UI-12", root: "staffing-assignments-screen", fields: ["assignments-project-filter", "assignments-job-filter"], submit: "assignments-query", reset: "assignments-empty-reset" },
  { route: "/UI-11", root: "staffing-comparison-screen", fields: ["comparison-project-filter", "comparison-difference-filter"], submit: "comparison-search", reset: "comparison-empty-reset" },
  { route: "/UI-05", root: "withdrawal-list-screen", fields: ["withdrawal-period", "withdrawal-extension-filter"], submit: "withdrawal-search", reset: "withdrawal-empty-reset" },
]) {
  test(`${item.route}: two applied conditions and empty-result reset`, async ({ page }) => {
    test.setTimeout(90_000);
    await page.goto(item.route);
    const screen = page.getByTestId(item.root);
    for (const id of item.fields) {
      const select = page.getByTestId(id);
      await expect(select.locator("option").nth(1)).toBeAttached({ timeout: 20_000 });
      await select.selectOption({ index: 1 });
    }
    await page.getByTestId(item.submit).click();
    await expect(screen.getByText("[조건 (2)]", { exact: true })).toBeVisible();
    await expect(screen.getByText("조건에 맞는 결과가 없습니다.", { exact: true }).filter({ visible: true })).toBeVisible();
    await page.getByTestId(item.reset).click();
    await expect(screen.getByText("[조건 (0)]", { exact: true })).toBeVisible();
  });
}

test("UI-31: applied filters and empty-result reset", async ({ page }) => {
  await page.goto("/UI-31");
  await page.getByTestId("status-light").selectOption("정상");
  await page.getByTestId("status-type").selectOption("IN");
  await page.getByTestId("status-search").click();
  const screen = page.getByTestId("project-status-board");
  await expect(screen.getByText("[조건 (2)]", { exact: true })).toBeVisible();
  await expect(screen.getByText("조건에 맞는 결과가 없습니다.", { exact: true })).toBeVisible();
  await page.getByTestId("status-empty-reset").click();
  await expect(screen.getByText("[조건 (0)]", { exact: true })).toBeVisible();
});

test("UI-29: closed list conditions and empty-result reset", async ({ page }) => {
  await page.goto("/UI-29");
  await page.getByTestId("pipeline-closed-tab").click();
  await page.getByTestId("pipeline-result-select").selectOption("LOSE");
  await page.getByTestId("pipeline-include-stopped-input").check();
  const screen = page.getByTestId("sales-pipeline-screen");
  await expect(screen.getByText("[조건 (2)]", { exact: true })).toBeVisible();
  await expect(screen.getByText("조건에 맞는 결과가 없습니다.", { exact: true })).toBeVisible();
  await page.getByTestId("pipeline-empty-reset").click();
  await expect(screen.getByText("[조건 (0)]", { exact: true })).toBeVisible();
  await expect(page.getByTestId("pipeline-result-select")).toHaveValue("WIN");
  await expect(page.getByTestId("pipeline-include-stopped-input")).not.toBeChecked();
});

test("UI-29: closed list has filtered total and page sizes", async ({ page }) => {
  await page.goto("/UI-29");
  await page.getByTestId("pipeline-closed-tab").click();
  const screen = page.getByTestId("sales-pipeline-screen");
  await expect(screen.getByText(/^총 \d+건$/)).toBeVisible();
  const size = page.getByTestId("pipeline-page-size");
  await expect(size).toHaveValue("50");
  await expect(size.locator("option")).toHaveText(["20건씩", "50건씩", "100건씩"]);
  const response = page.waitForResponse(r => r.url().includes("/api/sales-opportunities") && r.request().method() === "GET" && new URL(r.url()).searchParams.get("size") === "20");
  await size.selectOption("20");
  expect((await response).ok()).toBeTruthy();
  await expect(size).toHaveValue("20");
});

test("UI-31: non-link current-step cell opens the same progress detail", async ({ page }) => {
  test.setTimeout(60_000);
  const loaded = page.waitForResponse(r => new URL(r.url()).pathname === "/api/project-status-board" && r.status() === 200);
  await page.goto("/UI-31");
  const board = (await (await loaded).json()).data;
  expect(board.items.length).toBeGreaterThan(0);
  const row = page.getByTestId("status-board-table").locator("tbody tr").filter({ has: page.locator('[data-column="currentStep"]') }).first();
  await expect(row).toBeVisible({ timeout: 20_000 });
  const link = row.locator('[data-testid^="status-project-"]');
  const target = await link.getAttribute("href");
  expect(target).toBeTruthy();
  // Clicking the cell's whitespace, not the project/current-step links, tests row handling.
  await row.locator('[data-column="currentStep"]').click({ position: { x: 3, y: 3 } });
  await expect(page).toHaveURL(url => url.pathname + url.search === target);
  expect(new URL(page.url()).searchParams.get("tab")).toBe("progress");
});

test("UI-01: unfiltered and running lists download all matching rows", async ({ page }, testInfo) => {
  test.setTimeout(120_000);
  await page.goto("/UI-01");
  for (const status of ["", "ING"]) {
    await page.getByTestId("project-filter-status").selectOption(status);
    const response = page.waitForResponse(r => r.url().includes("/api/projects?") && r.request().method() === "GET");
    await page.getByTestId("project-query").click();
    const listed = await response;
    expect(listed.ok()).toBeTruthy();
    const total = (await listed.json()).data.total;
    await expect(page.getByTestId("projects-screen").getByText(/^총 \d+건$/)).toBeVisible();
    const download = page.waitForEvent("download");
    const exported = page.waitForResponse(r => r.url().includes("/api/exports/master-data") && r.request().method() === "POST");
    await page.getByTestId("project-export").click();
    const exportResponse = await exported;
    const failure = exportResponse.status() === 200 ? "" : JSON.stringify(await exportResponse.json());
    expect(exportResponse.status(), failure).toBe(200);
    const file = await download;
    expect(await file.failure()).toBeNull();
    expect(file.suggestedFilename()).toMatch(/^프로젝트_\d{4}_\d{8}_\d{6}\.xlsx$/);
    const directory = process.env.E2E_DOWNLOAD_DIR ?? testInfo.outputPath("downloads");
    await mkdir(directory, { recursive: true });
    const path = join(directory, `${status || "ALL"}-${file.suggestedFilename()}`);
    await file.saveAs(path);
    await writeFile(`${path}.json`, JSON.stringify({ status, total, filename: file.suggestedFilename() }));
  }
});

test("A exports: real authenticated responses keep screen names and period", async ({ page }, testInfo) => {
  test.setTimeout(120_000);
  const directory = process.env.E2E_DOWNLOAD_DIR ?? testInfo.outputPath("downloads");
  await mkdir(directory, { recursive: true });
  const token = await csrf(page.request);
  const cases = [
    { name: "개발자 풀", view: "DEVELOPERS", columns: ["name", "companyName", "employeeType", "grade", "job", "status", "currentProjects", "withdrawalDate", "monthlyRate"] },
    { name: "외부 인력 후보", view: "CANDIDATES", columns: ["name", "employeeType", "companyName", "job", "grade", "skills", "homeAddress", "desiredRate", "availableDate", "contactStatus", "lastContactDate", "source"] },
    { name: "투입 배정", view: "ASSIGNMENTS", columns: ["warnings", "projectName", "developerName", "jobName", "startDate", "endDate", "billingUnitPrice", "contractMm", "yearMm", "extensionPossibility", "inputType"] },
    { name: "인원 계획 대비 투입", url: "/api/staffing-plan-comparison/export?year=2026&difference=SHORT&amountUnit=WON" },
    { name: "프로젝트 현황판", url: "/api/project-status-board/export?hidden=" },
    { name: "철수 리스트", url: "/api/withdrawals/export?year=2026" },
    { name: "인력 운영 보드", url: "/api/workforce-operations/export?year=2026&tab=CALENDAR&amountUnit=WON" },
    { name: "영업 파이프라인", url: "/api/sales-opportunities/forecast/export?amountUnit=WON" },
    { name: "인력 수요 예측", url: "/api/workforce-forecasts/export?from=2026-11&months=6&includeUnfilled=true&amountUnit=WON" },
  ];
  for (const item of cases) {
    const response = "view" in item
      ? await page.request.post("/api/exports/master-data", {
          headers: { [token.headerName]: token.token },
          data: { view: item.view, filters: item.view === "ASSIGNMENTS" ? { year: 2026 } : {}, columns: item.columns, amountUnit: "원" },
        })
      : await page.request.get(item.url!);
    expect(response.status(), item.name).toBe(200);
    expect(response.headers()["content-type"]).toContain("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    const encoded = /filename\*=UTF-8''([^;]+)/i.exec(response.headers()["content-disposition"])?.[1];
    const filename = decodeURIComponent(encoded ?? "");
    const period = ["개발자 풀", "외부 인력 후보"].includes(item.name) ? "" : item.name === "프로젝트 현황판" ? "_\\d{8}" : "_\\d{4}";
    expect(filename).toMatch(new RegExp(`^${item.name}${period}_\\d{8}_\\d{6}\\.xlsx$`));
    const body = await response.body();
    expect(body.subarray(0, 2).toString()).toBe("PK");
    await writeFile(join(directory, filename), body);
  }
});

for (const item of [
  { route: "/UI-31", button: "project-download-button", prefix: "프로젝트 현황판", tab: undefined },
  { route: "/UI-29", button: "forecast-export-button", prefix: "영업 파이프라인", tab: "pipeline-forecast-tab" },
  { route: "/UI-17", button: "workforce-download", prefix: "인력 수요 예측", tab: undefined },
]) {
  test(`${item.route}: download button accepts the menu-based filename`, async ({ page }, testInfo) => {
    test.setTimeout(90_000);
    await page.goto(item.route);
    if (item.tab) await page.getByTestId(item.tab).click();
    const button = page.getByTestId(item.button);
    await expect(button).toBeEnabled({ timeout: 30_000 });
    const download = page.waitForEvent("download");
    await button.click();
    const file = await download;
    expect(await file.failure()).toBeNull();
    expect(file.suggestedFilename()).toMatch(new RegExp(`^${item.prefix}_\\d{4,8}_\\d{8}_\\d{6}\\.xlsx$`));
    const directory = process.env.E2E_DOWNLOAD_DIR ?? testInfo.outputPath("downloads");
    await mkdir(directory, { recursive: true });
    await file.saveAs(join(directory, file.suggestedFilename()));
  });
}
