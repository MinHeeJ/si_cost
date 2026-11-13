import { expect, test, type Page } from "@playwright/test";
import type {
  IndividualProfitabilityData,
  IndividualProfitabilityPersonData,
} from "../../frontend/src/pages/profitability/individualProfitabilityApi";
import {
  formatAmount,
  type AmountUnit,
} from "../../frontend/src/shared/format";

// Real runner-started application and existing-data DB only: no route interception or fake API.
const listResponse = (page: Page) =>
  page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname === "/api/individual-profitability" &&
      response.request().method() === "GET",
  );
async function open(page: Page) {
  await page.goto("/UI-03");
  await expect(
    page.getByTestId("individual-profitability-screen"),
  ).toBeVisible({ timeout: 15000 });
  await expect(page.getByTestId("profitability-search-button")).toBeEnabled();
  const pending = listResponse(page);
  await page.getByTestId("profitability-search-button").click();
  const response = await pending;
  expect(response.status()).toBe(200);
  const envelope = await response.json();
  expect(envelope.success).toBe(true);
  return envelope.data as IndividualProfitabilityData;
}
async function checkServerProjection(
  page: Page,
  data: IndividualProfitabilityData,
) {
  await expect(page.getByTestId("profitability-context")).toContainText(
    `${data.context.period.year}년`,
  );
  const unit = (await page
    .getByTestId("profitability-unit-select")
    .inputValue()) as AmountUnit;
  await expect(
    page.getByTestId("profitability-summary-cost").locator("strong"),
  ).toHaveText(formatAmount(data.summary.averageCost, unit));
  await expect(
    page.getByTestId("profitability-summary-profit").locator("strong"),
  ).toHaveText(formatAmount(data.summary.averageProfit, unit));
  const table = page.getByTestId("profitability-table");
  const headers = await table.getByRole("columnheader").allTextContents();
  for (const [label, id] of [
    ["개인 단가", "revenue"],
    ["원가합계", "totalCost"],
    ["영업이익", "profit"],
  ] as const) {
    const index = headers.findIndex((header) => header.includes(label));
    expect(index).toBeGreaterThanOrEqual(0);
    await expect(
      page
        .getByTestId("profitability-table-totals")
        .getByRole("cell")
        .nth(index),
    ).toHaveText(formatAmount(data.totals[id], unit));
  }
  for (const person of data.items)
    await expect(
      table.getByRole("button", {
        name: `${person.name} (${person.managementNo})`,
        exact: true,
      }),
    ).toBeVisible();
}

test("실제 응답의 기간·인물·합계·근거를 표시하고 시각 비교 캡처를 남긴다", async ({
  page,
}, info) => {
  const data = await open(page);
  expect(
    data.total,
    "runner FX-B137-PERIOD의 실제 인력이 필요합니다",
  ).toBeGreaterThan(0);
  await checkServerProjection(page, data);
  await expect(
    page.getByTestId("profitability-company-profit-note"),
  ).toHaveText(
    "내부 프로젝트·대기 인력은 매출이 없어 영업이익이 −원가로 보입니다. 회사 전체 영업이익표에서는 내부 몫은 연구개발비(판관비), 대기 몫은 대기 원가로 따로 잡혀 이 표의 합계와 다를 수 있습니다.",
  );
  await page.getByTestId("profitability-calculation-toggle").click();
  await expect(
    page.getByTestId("profitability-calculation-basis").getByRole("listitem"),
  ).toHaveText(data.calculationBasis);
  await page.screenshot({
    path: info.outputPath("individual-profitability-desktop.png"),
    fullPage: true,
  });
});

test("월평균 전환 후 원장은 상세 API의 원본 행·출처를 표시한다", async ({
  page,
}) => {
  await open(page);
  const pending = listResponse(page);
  await page
    .getByRole("button", { name: "월 평균", exact: true })
    .click();
  const response = await pending;
  expect(response.status()).toBe(200);
  const { data } = (await response.json()) as {
    data: IndividualProfitabilityData;
  };
  await checkServerProjection(page, data);
  expect(
    data.items.length,
    "월원장 검증용 실제 인력이 필요합니다",
  ).toBeGreaterThan(0);
  const person = data.items[0];
  const detailPending = page.waitForResponse(
    (r) =>
      new URL(r.url()).pathname ===
      `/api/individual-profitability/${encodeURIComponent(person.personId)}`,
  );
  await page
    .getByRole("button", { name: `${person.name} 펼치기`, exact: true })
    .click();
  const detailResponse = await detailPending;
  expect(detailResponse.status()).toBe(200);
  const { data: detail } = (await detailResponse.json()) as {
    data: IndividualProfitabilityPersonData;
  };
  const ledger = page.locator(".profitability-ledger").first();
  await expect(ledger.getByRole("heading")).toHaveText(
    "월별 원본 원장 · 월평균 전 금액",
  );
  for (const month of detail.months) {
    await expect(
      ledger.getByRole("list", { name: "월별 인건비 출처", exact: true }),
    ).toContainText(month.laborSource.source);
    for (const row of month.rows)
      await expect(
        ledger
          .getByRole("cell", { name: row.assignmentId, exact: true })
          .first(),
      ).toBeVisible();
  }
  await page.getByTestId("profitability-collapse-all").click();
  await expect(ledger).toHaveCount(0);
  await expect(page.getByRole("button", { name: "월 평균", exact: true })).toHaveAttribute("aria-pressed", "true");
});

test("명시 다운로드는 실제 XLSX 파일로 저장한다", async ({ page }, info) => {
  await open(page);
  const downloadPending = page.waitForEvent("download");
  await page.getByTestId("profitability-export-button").click();
  const download = await downloadPending;
  expect(download.suggestedFilename()).toMatch(
    /^개인원가수익_\d{4}_\d{8}_\d{6}\.xlsx$/,
  );
  await download.saveAs(info.outputPath(download.suggestedFilename()));
  expect(await download.failure()).toBeNull();
  await expect(
    page
      .getByRole("status")
      .filter({ hasText: "전체 결과 엑셀을 내려받았습니다." }),
  ).toBeVisible();
});

test("모바일 실제 카드·서버 근거와 데스크톱 복귀를 보존한다", async ({
  page,
}, info) => {
  await page.setViewportSize({ width: 390, height: 844 });
  const data = await open(page);
  expect(data.size).toBe(20);
  await expect(page.getByTestId("profitability-mobile-cards")).toBeVisible();
  await expect(page.getByTestId("profitability-export-button")).toHaveCount(0);
  await page.getByTestId("profitability-calculation-toggle").click();
  await expect(
    page.getByTestId("profitability-calculation-basis").getByRole("listitem"),
  ).toHaveText(data.calculationBasis);
  await page.screenshot({
    path: info.outputPath("individual-profitability-mobile.png"),
    fullPage: true,
  });
  const next = listResponse(page);
  await page.setViewportSize({ width: 1440, height: 1000 });
  expect((await next).status()).toBe(200);
  await expect(page.getByTestId("profitability-table")).toBeVisible();
  await expect(page.getByTestId("profitability-export-button")).toBeVisible();
});

for (const width of [1900, 1500, 1280]) {
  test(`${width}px: 표는 내부 스크롤, 공통 필터와 모달은 화면 안에 유지한다`, async ({ page }, info) => {
    await page.setViewportSize({ width, height: 950 });
    const failures: string[] = [];
    page.on("pageerror", (error) => failures.push(error.message));
    page.on("response", (response) => {
      if (response.status() >= 400 && new URL(response.url()).pathname.startsWith("/api/"))
        failures.push(`${response.status()} ${new URL(response.url()).pathname}`);
    });
    await open(page);
    const viewport = () => page.evaluate(() => ({
      scroll: document.documentElement.scrollWidth,
      client: document.documentElement.clientWidth,
    }));
    expect(await viewport()).toEqual({ scroll: width, client: width });
    const filter = page.getByTestId("profitability-filters");
    await expect(filter).toHaveClass(/sicost-filterbar/);
    const input = page.getByRole("textbox", { name: "이름 검색" });
    const label = await input.locator("..").boundingBox();
    const control = await input.boundingBox();
    expect(label!.x).toBeLessThan(control!.x);
    expect(Math.abs(label!.y + label!.height / 2 - (control!.y + control!.height / 2))).toBeLessThan(2);
    for (const button of await page.locator(".sicost-primary:not(:disabled)").all()) {
      await expect(button).toHaveCSS("color", "rgb(255, 255, 255)");
    }
    const primary = page.getByTestId("profitability-search-button");
    await expect(primary).toHaveCSS("color", "rgb(255, 255, 255)");
    await page.mouse.move(0, 0);
    await page.waitForTimeout(200);
    const background = await primary.evaluate((button) => getComputedStyle(button).backgroundColor);
    await primary.hover();
    await expect(primary).toHaveCSS("color", "rgb(255, 255, 255)");
    expect(await primary.evaluate((button) => getComputedStyle(button).backgroundColor)).not.toBe(background);
    await page.getByTestId("profitability-table-columns-button").click();
    const dialog = page.getByRole("dialog");
    await expect(dialog).toBeVisible();
    await expect(page.getByTestId("profitability-table")).toBeVisible();
    const bounds = await dialog.boundingBox();
    expect(bounds!.x).toBeGreaterThanOrEqual(0);
    expect(bounds!.x + bounds!.width).toBeLessThanOrEqual(width);
    expect(bounds!.y).toBeGreaterThanOrEqual(0);
    expect(bounds!.y + bounds!.height).toBeLessThanOrEqual(950);
    expect(await viewport()).toEqual({ scroll: width, client: width });
    await page.screenshot({ path: info.outputPath(`ui03-${width}-columns.png`) });
    expect(failures).toEqual([]);
  });
}
