import { test, expect, type Page, type Locator } from "@playwright/test";
import { account, loginUi } from "./basic147/authSupport";
import type { CostPage } from "../../frontend/src/pages/apiops/projectAnalysisApi";
import type { SimulationResult } from "../../frontend/src/pages/costanalysis/projectCostApi";
import { formatDecimal } from "../../frontend/src/shared/format";

// Requires the runner's disposable example-data app and FIN account. Never intercept APIs,
// replace server calculations, write seed rows, or record credentials/session state.
test.use({ trace: "off", screenshot: "off", video: "off" });
test.beforeEach(async ({ page }) => {
  await loginUi(page, account("FIN"));
});
const month = "2026-11";
async function exampleProject(page: Page) {
  for (let number = 1; ; number++) {
    const response = await page.request.get("/api/project-costs", {
      params: { month, page: number, size: 100 },
    });
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    const data = body.data as CostPage;
    const project = data.items.find((row) =>
      row.projectName.includes("금융 차세대 구축"),
    );
    if (project) return project;
    if (!data.items.length || number * data.size >= data.total)
      throw new Error(
        "2026-11 예시 데이터의 금융 차세대 구축 프로젝트가 필요합니다.",
      );
  }
}
function route(id: string, tab?: string) {
  return `/UI-04?${new URLSearchParams({ month, projectId: id, ...(tab ? { detailProjectId: id, costTab: tab } : {}) })}`;
}
async function box(locator: Locator) {
  await expect(locator).toBeVisible();
  const value = await locator.boundingBox();
  expect(value).not.toBeNull();
  return value!;
}
async function closeDetail(page: Page) {
  await page
    .getByTestId("project-cost-detail")
    .getByRole("button", { name: "닫기", exact: true })
    .click();
  await expect(page.getByTestId("project-cost-detail")).toHaveCount(0);
}

test("UI-04 목록 배치·네 설명·단위·세로 정렬·행 클릭과 Enter", async ({
  page,
}) => {
  const project = await exampleProject(page);
  await page.goto(route(project.projectId));
  const table = page.getByTestId("project-cost-table");
  const name = page.getByTestId(
    `project-cost-open-${project.projectId.toLowerCase()}`,
  );
  const row = table.locator("tbody tr").filter({ has: name });
  await expect(row).toBeVisible();
  const summary = page.getByTestId("analysis-summary-root");
  await expect(summary.locator(":scope > section")).toHaveCount(5);
  const unitBox = await box(page.getByTestId("project-cost-unit"));
  const exportBox = await box(page.getByTestId("project-download-projectcost"));
  const summaryBox = await box(summary);
  const columnsBox = await box(page.getByTestId("project-cost-columns"));
  const filtersBox = await box(page.getByTestId("project-cost-filters"));
  expect(unitBox.y + unitBox.height).toBeLessThanOrEqual(summaryBox.y);
  expect(exportBox.y + exportBox.height).toBeLessThanOrEqual(summaryBox.y);
  expect(summaryBox.y + summaryBox.height).toBeLessThan(columnsBox.y);
  expect(columnsBox.y + columnsBox.height).toBeLessThan(filtersBox.y);
  await expect(page.getByTestId("project-cost-column-toolbar")).toHaveCSS(
    "justify-content",
    "flex-end",
  );
  await page.getByTestId("project-cost-columns").click();
  const settings = page.getByRole("dialog");
  await expect(settings).toBeVisible();
  await settings.getByRole("button", { name: "취소", exact: true }).click();

  const descriptions = [
    [
      "cumulative-revenue",
      "턴키는 진행기준(원가÷예산), 월단위·파견은 귀속월 매출 누계",
    ],
    [
      "cumulative-cost",
      "인건비 배분 원가 + 직접 경비(팀비·프로젝트경비·추가 직접 경비)",
    ],
    [
      "forecast-margin",
      "턴키: (현재 계약금액 − 원가 누계 − 남은 기간 예상 원가) ÷ 현재 계약금액. 월단위·파견: 당월 마진율",
    ],
    [
      "budget-usage",
      "원가 누계 ÷ 예산(프로젝트별 입력값, 기본값 계약금액 × (1 − 목표 마진율))",
    ],
  ];
  for (const [id, description] of descriptions) {
    const info = page.getByTestId(`project-cost-help-${id}`);
    await info.hover();
    await expect(page.getByRole("tooltip")).toHaveText(description);
    const tip = await box(page.getByRole("tooltip"));
    expect(tip.x).toBeGreaterThanOrEqual(0);
    expect(tip.x + tip.width).toBeLessThanOrEqual(page.viewportSize()!.width);
    await info.press("Escape");
    await expect(page.getByRole("tooltip")).toHaveCount(0);
  }
  const sorted = page.waitForResponse((response) => {
    const url = new URL(response.url());
    return (
      url.pathname === "/api/project-costs" &&
      url.searchParams.get("sort") === "cumulativeRevenue"
    );
  });
  await page.getByTestId("sort-cumulativeRevenue").click();
  expect((await sorted).status()).toBe(200);
  await page.getByTestId("project-cost-help-cumulative-revenue").hover();
  await expect(page.getByRole("tooltip")).toHaveText(descriptions[0][1]);
  await page
    .getByTestId("project-cost-help-cumulative-revenue")
    .press("Escape");
  await expect(
    table.getByRole("columnheader", { name: "목표 대비", exact: true }),
  ).toBeVisible();
  await expect(row.locator('[data-column="targetGap"]')).toHaveText(
    project.targetGap == null
      ? "–"
      : `${Number(project.targetGap) > 0 ? "+" : ""}${formatDecimal(String(project.targetGap))}%p`,
  );
  for (const cell of await row.locator("td").all())
    await expect(cell).toHaveCSS("vertical-align", "middle");
  expect(await name.evaluate((element) => element.tagName)).toBe("SPAN");
  await expect(page.getByTestId("project-cost-row-hint")).toHaveText(
    "행을 누르면 월별 원가·시뮬레이션",
  );
  await row.hover();
  await expect(row).toHaveCSS("cursor", "pointer");
  await row.locator('[data-column="cumulativeCost"]').click();
  await expect(page.getByTestId("project-cost-detail")).toBeVisible();
  await closeDetail(page);
  await row.focus();
  await row.press("Enter");
  await expect(page.getByTestId("project-cost-detail")).toBeVisible();
});

test("UI-04 상세 상단 정보·투입자·관련 내역·시뮬레이션 표시와 이동 보존", async ({
  page,
}) => {
  const project = await exampleProject(page);
  await page.goto(route(project.projectId, "assignments"));
  const drawer = page.getByTestId("project-cost-detail");
  const info = drawer.getByRole("button", {
    name: "프로젝트 정보",
    exact: true,
  });
  const close = drawer.getByRole("button", { name: "닫기", exact: true });
  const infoBox = await box(info),
    closeBox = await box(close);
  expect(infoBox.x + infoBox.width).toBeLessThan(closeBox.x);
  expect(infoBox.height).toBe(closeBox.height);
  expect(infoBox.y).toBe(closeBox.y);
  await expect(
    page
      .getByTestId("cost-detail-content")
      .getByText("프로젝트 정보", { exact: true }),
  ).toHaveCount(0);
  const returnUrl = page.url();
  await info.click();
  await expect(page).toHaveURL(/\/UI-01\?/);
  expect(new URL(page.url()).searchParams.get("projectId")).toBe(
    project.projectId,
  );
  await page.goBack();
  await expect(page).toHaveURL(returnUrl);
  const people = page
    .getByTestId("cost-assignments")
    .locator(".cost-person-link");
  await expect(people.first()).toBeVisible();
  await expect(people.first()).toHaveCSS("border-top-width", "0px");
  await expect(people.first()).toHaveCSS(
    "background-color",
    "rgba(0, 0, 0, 0)",
  );
  await people.first().hover();
  await expect(people.first()).toHaveCSS("text-decoration-line", "underline");
  await people.first().click();
  await expect(page.getByTestId("developer-drawer")).toBeVisible();
  await page
    .getByTestId("developer-drawer")
    .getByRole("button", { name: "닫기", exact: true })
    .click();
  await page.getByTestId("cost-tab-causes").click();
  const related = page.getByTestId("cost-causes");
  for (const name of ["투입 인력", "인원 계획", "원가 검토", "인력 수요"]) {
    const control = related.getByRole(
      name === "투입 인력" ? "button" : "link",
      { name, exact: true },
    );
    await expect(control).toHaveCSS("height", "28px");
    await expect(control).toHaveCSS("border-top-width", "1px");
    await expect(control).toHaveCSS("border-radius", "4px");
  }
  await expect(
    related.getByRole("link", { name: "인원 계획" }),
  ).toHaveAttribute(
    "href",
    `/UI-01?${new URLSearchParams({ projectId: project.projectId, tab: "plans" })}`,
  );
  await expect(
    related.getByRole("link", { name: "인력 수요" }),
  ).toHaveAttribute(
    "href",
    `/UI-11?${new URLSearchParams({ projectId: project.projectId })}`,
  );
  await expect(
    related.getByRole("link", { name: "원가 검토" }),
  ).toHaveAttribute(
    "href",
    `/UI-32?${new URLSearchParams({ projectId: project.projectId })}`,
  );
  const noteBox = await box(related.getByText("후속 화면 준비 안내에서 확인"));
  const reviewBox = await box(related.getByRole("link", { name: "원가 검토" }));
  expect(noteBox.y).toBeGreaterThanOrEqual(reviewBox.y + reviewBox.height);
  await related.getByRole("button", { name: "투입 인력", exact: true }).click();
  await expect(page.getByTestId("cost-tab-assignments")).toHaveAttribute(
    "aria-selected",
    "true",
  );
  expect(new URL(page.url()).searchParams.get("month")).toBe(month);
  await page.getByTestId("cost-tab-simulation").click();
  await expect(page.getByTestId("cost-simulation-submit")).toBeEnabled();
  await page.getByTestId("cost-simulation-n").fill("0");
  await page.getByTestId("cost-simulation-c").fill("0");
  await page.getByTestId("cost-simulation-m").fill("1");
  if (project.contractType !== "TK")
    await page.getByTestId("cost-simulation-r").fill("0");
  const pending = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname.endsWith("/cost-analysis/simulation") &&
      response.request().method() === "POST",
  );
  await page.getByTestId("cost-simulation-submit").click();
  const response = await pending;
  expect(response.status()).toBe(200);
  const result = (await response.json()).data as SimulationResult;
  expect(result.saved).toBe(false);
  for (const [label, value] of [
    ["변경 전 목표 대비", result.beforeTargetGap],
    ["변경 후 목표 대비", result.afterTargetGap],
  ]) {
    const cell = page
      .getByTestId("cost-simulation-result")
      .getByText(label!, { exact: true })
      .locator("+ dd");
    await expect(cell).toHaveText(value == null ? "–" : `${value}%p`);
    if (value?.startsWith("-")) await expect(cell).toHaveClass("cost-negative");
  }
});
