import { test, expect, type Page } from "@playwright/test";
import { account, loginUi } from "./basic147/authSupport";
import type { RenegotiationsResult } from "../../frontend/src/pages/apiops/rateRenegotiationsApi";
import { formatAmount, formatRate } from "../../frontend/src/shared/format";

// Requires the disposable Jira example-data app, its server date, and prepared
// SALES_LDR_OPERATIONS / SALES_LDR_DIGITAL / FIN accounts. No API interception,
// stored authentication, credential logging, or production data mutation.
test.use({ trace: "off", screenshot: "off", video: "off" });

async function open(page: Page, role: string) {
  await loginUi(page, account(role));
  const pending = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname === "/api/rate-renegotiations",
  );
  await page.goto("/UI-32");
  const response = await pending;
  expect(response.status()).toBe(200);
  await expect(page.getByTestId("renegotiation-search")).toBeEnabled();
  return (await response.json()).data as RenegotiationsResult;
}

async function allConditions(page: Page) {
  await page.getByTestId("renegotiation-window").selectOption("ALL");
  await page.getByTestId("renegotiation-margin").selectOption("ALL");
  const pending = page.waitForResponse((response) => {
    const url = new URL(response.url());
    return (
      url.pathname === "/api/rate-renegotiations" &&
      url.searchParams.get("renewalWindow") === "ALL" &&
      url.searchParams.get("marginBelow") === "ALL"
    );
  });
  await page.getByTestId("renegotiation-search").click();
  const response = await pending;
  expect(response.status()).toBe(200);
  await expect(page.getByTestId("renegotiation-search")).toBeEnabled();
  return (await response.json()).data as RenegotiationsResult;
}

async function summaries(page: Page, data: RenegotiationsResult) {
  const unit = (await page.getByTestId("renegotiation-unit").inputValue()) as
    | "원"
    | "천원"
    | "백만원";
  const cards = page
    .getByTestId("renegotiation-screen")
    .locator(".renegotiation-summary article");
  await expect(cards).toHaveCount(4);
  await expect(
    cards.filter({ hasText: "대상 인원" }).locator("strong"),
  ).toHaveText(`${data.summary.personCount}명`);
  await expect(
    cards.filter({ hasText: "대상 평균 이익률" }).locator("strong"),
  ).toHaveText(formatRate(data.summary.weightedMarginRate));
  await expect(
    cards.filter({ hasText: `${data.appliedIncreaseRate}% 올리면 연 이익` }).locator("strong"),
  ).toHaveText(formatAmount(data.summary.annualGain, unit));
  await expect(
    cards.filter({ hasText: "목표 단가까지 올리면 연 이익" }).locator("strong"),
  ).toHaveText(formatAmount(data.summary.targetAnnualGain, unit));
  await expect(
    page
      .getByTestId("renegotiation-table-totals")
      .locator('[data-column="annualGain"]'),
  ).toHaveText(formatAmount(data.summary.annualGain, unit));
}

async function leaderVisibility(page: Page, data: RenegotiationsResult) {
  const table = page.getByTestId("renegotiation-table");
  await expect(
    table.getByRole("columnheader", { name: /월 원가/ }),
  ).toHaveCount(0);
  await expect(
    table.getByRole("columnheader", { name: "구분", exact: true }),
  ).toBeVisible();
  await expect(page.getByTestId("renegotiation-employee")).toBeVisible();
  await page.getByTestId("renegotiation-guide-summary").click();
  await expect(page.getByTestId("renegotiation-guide")).toContainText(
    "필요 인상률",
  );
  await expect(page.getByText(/협상 반영은/)).toHaveCount(0);
  for (const row of data.items) {
    expect(row).not.toHaveProperty("monthlyLaborCost");
    expect(row).toHaveProperty("employeeType");
    expect(row).toHaveProperty("managementNo");
  }
  await summaries(page, data);
}

test("운영사업파트 기본 1건·전체 3건과 네 요약·합계·다운로드", async ({
  page,
}) => {
  const defaults = await open(page, "SALES_LDR_OPERATIONS");
  expect(defaults.items.map((row) => row.name)).toEqual(["이저마진"]);
  await leaderVisibility(page, defaults);
  const all = await allConditions(page);
  expect(all.items.map((row) => row.name).sort()).toEqual(
    ["오원천검증", "이저마진", "임겸임"].sort(),
  );
  expect(
    all.items.every((row) => row.projectName.includes("저마진 파견")),
  ).toBe(true);
  await summaries(page, all);
  const pending = page.waitForRequest(
    (request) =>
      new URL(request.url()).pathname === "/api/exports/master-data" &&
      request.method() === "POST",
  );
  const downloaded = page.waitForEvent("download");
  await page.getByTestId("renegotiation-download").click();
  const payload = (await pending).postDataJSON();
  expect(payload.columns).toContain("employeeType");
  expect(payload.columns).not.toContain("monthlyLaborCost");
  expect(payload.filters).toMatchObject({
    renewalWindow: "ALL",
    marginBelow: "ALL",
  });
  expect(await (await downloaded).failure()).toBeNull();
});

test("디지털사업파트는 소속 인력의 타 파트 배정이 아닌 담당 프로젝트만 조회", async ({
  page,
}) => {
  await open(page, "SALES_LDR_DIGITAL");
  const data = await allConditions(page);
  expect(data.items.length).toBeGreaterThan(0);
  expect(
    data.items.every((row) => row.projectName.includes("유통 운영개발")),
  ).toBe(true);
  expect(
    data.items.some((row) => row.projectName.includes("저마진 파견")),
  ).toBe(false);
  await leaderVisibility(page, data);
});

test("재무 전체 범위·원가 표시와 이름 링크 스타일·개발자 상세·변경 기록 유지", async ({
  page,
}) => {
  await open(page, "FIN");
  const data = await allConditions(page);
  expect(
    data.items.filter((row) => row.projectName.includes("저마진 파견")),
  ).toHaveLength(3);
  expect(
    data.items.some((row) => row.projectName.includes("유통 운영개발")),
  ).toBe(true);
  await expect(
    page
      .getByTestId("renegotiation-table")
      .getByRole("columnheader", { name: /월 원가/ }),
  ).toBeVisible();
  await summaries(page, data);
  const row = data.items.find((row) =>
    row.projectName.includes("저마진 파견"),
  )!;
  const marker = row.assignmentId.toLowerCase().replace(/[^a-z0-9-]/g, "-");
  const name = page.getByTestId(`renegotiation-person-${marker}`);
  await expect(name).toHaveCSS("border-top-width", "0px");
  await expect(name).toHaveCSS("background-color", "rgba(0, 0, 0, 0)");
  await expect(name).toHaveCSS("color", "rgb(29, 78, 216)");
  await expect(name).toContainText(row.managementNo);
  await name.hover();
  await expect(name).toHaveCSS("text-decoration-line", "underline");
  await name.click();
  await expect(page.getByTestId("developer-drawer")).toBeVisible();
  await expect(page.getByTestId("developer-drawer")).toContainText(row.name);
  await page
    .getByTestId("developer-drawer")
    .getByRole("button", { name: "닫기", exact: true })
    .click();
  await expect(
    page.getByTestId(`renegotiation-assignment-${marker}`),
  ).toHaveAttribute(
    "href",
    `/UI-12?${new URLSearchParams({ assignmentId: row.assignmentId, tab: "history" })}`,
  );
  await expect(page.getByText(/협상 반영은/)).toHaveCount(0);
});
