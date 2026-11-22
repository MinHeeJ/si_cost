import { test, expect, type Page } from "@playwright/test";
import { spawnSync } from "node:child_process";
import { account, loginUi } from "./basic147/authSupport";
import {
  compareDecimal,
  formatAmount,
  formatRate,
} from "../../frontend/src/shared/format";
import type {
  BusinessPlanPerformanceData,
  PlanMonth,
} from "../../frontend/src/pages/businessplansops/businessPlanPerformanceApi";
import type { OperatingProfitData } from "../../frontend/src/pages/operating/operatingProfitApi";

// Read-only checks against the runner's isolated app. Never upload or seed targets,
// close a month, change accounts, or replace API responses to manufacture a state.
test.use({ trace: "off", screenshot: "off", video: "off" });
test.beforeEach(async ({ page }) => {
  await loginUi(page, account("FIN"));
});

async function openPerformance(
  page: Page,
): Promise<BusinessPlanPerformanceData> {
  const pending = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname === "/api/business-plans/performance",
  );
  await page.goto("/UI-18");
  const response = await pending;
  expect(response.status()).toBe(200);
  await expect(
    page.getByTestId("business-plan-summary-cumulative-sales"),
  ).toBeVisible();
  return (await response.json()).data;
}

function reason(
  month: PlanMonth,
  key: "salesAchievementRate" | "operatingMarginDifference",
) {
  if (month[key] != null || month.actual.sourceKind === "FUTURE") return null;
  if (
    month.salesTargetAmt == null ||
    compareDecimal(month.salesTargetAmt, "0") === 0 ||
    (key === "operatingMarginDifference" &&
      month.operatingProfitTargetAmt == null)
  )
    return "목표 없음";
  if (
    month.salesActualAmt == null ||
    (key === "operatingMarginDifference" &&
      month.operatingProfitActualAmt == null)
  )
    return "실적 자료 미확인";
  if (
    key === "operatingMarginDifference" &&
    compareDecimal(month.salesActualAmt, "0") === 0
  )
    return "매출 실적이 0이라 이익률을 비교하지 않습니다";
  return null;
}

async function assertTable(page: Page, data: BusinessPlanPerformanceData) {
  const table = page.getByRole("table", { name: "사업계획 월별 실적" });
  await expect(table.getByRole("columnheader")).toHaveCount(13);
  await expect(table.getByRole("rowheader")).toHaveCount(6);
  for (const label of [
    "매출 목표",
    "매출 실적",
    "영업이익 목표",
    "영업이익 실적",
  ]) {
    const header = table.getByRole("rowheader", { name: label, exact: true });
    const help = header.getByRole("button", { name: `${label} 도움말` });
    await expect(help).toHaveCount(1);
    await expect(header.getByText("ⓘ", { exact: true })).toHaveCount(0);
    await expect(header.locator('[aria-label$="원천 설명"]')).toHaveCount(0);
    await help.click();
    await expect(page.getByRole("tooltip")).toBeVisible();
    await page.keyboard.press("Escape");
  }
  for (const [key, id, flag] of [
    ["salesAchievementRate", "sales-achievement-rate", "salesUnderTarget"],
    [
      "operatingMarginDifference",
      "operating-margin-difference",
      "operatingMarginAdverse",
    ],
  ] as const) {
    const row = page.getByTestId(`business-plan-row-${id}`);
    await expect(row.getByRole("rowheader")).toHaveCSS("font-weight", "700");
    for (const month of data.months) {
      const cell = page.getByTestId(`business-plan-cell-${id}-${month.month}`);
      await expect(cell).toHaveCSS("font-weight", "700");
      await expect(cell).toHaveText(
        month[key] == null
          ? "–"
          : key === "operatingMarginDifference"
            ? `${month[key]}%p`
            : formatRate(month[key]),
      );
      expect(await cell.getAttribute("title")).toBe(reason(month, key));
      if (month[key] == null)
        await expect(cell).not.toHaveClass(/business-plan-adverse/);
      else if (month[flag])
        await expect(cell).toHaveClass(/business-plan-adverse/);
    }
  }
  for (const [key, id] of [
    ["salesTargetAmt", "sales-target-amt"],
    ["salesActualAmt", "sales-actual-amt"],
    ["operatingProfitTargetAmt", "operating-profit-target-amt"],
    ["operatingProfitActualAmt", "operating-profit-actual-amt"],
  ] as const) {
    for (const month of data.months) {
      const cell = page.getByTestId(`business-plan-cell-${id}-${month.month}`);
      await expect(cell).toHaveText(formatAmount(month[key], "원"));
      expect(await cell.getAttribute("title")).toBeNull();
    }
  }
  for (const summary of data.summary) {
    const card = page.getByTestId(
      `business-plan-summary-${summary.metric.toLowerCase().replaceAll("_", "-")}`,
    );
    await expect(card.locator("strong")).toHaveText(
      formatRate(summary.achievementRate),
    );
    await expect(card).toContainText(
      `실적 ${formatAmount(summary.actualAmt, "원")} / 목표 ${formatAmount(summary.targetAmt, "원")}`,
    );
  }
  const banner = page.getByTestId("business-plan-unregistered");
  if (data.targetStatus === "UNREGISTERED") {
    const message = `${data.context.year}년 사업계획 목표가 아직 없습니다. [목표 업로드]로 올려 주세요.`;
    await expect(banner).toHaveText(message);
    await expect(page.getByText(message, { exact: true })).toHaveCount(1);
    await expect(
      page.locator(".financial-notice-summary li").filter({ hasText: message }),
    ).toHaveCount(0);
    expect(
      await banner.evaluate((element) =>
        Boolean(
          element.compareDocumentPosition(
            document.querySelector(".business-plan-summary")!,
          ) & Node.DOCUMENT_POSITION_FOLLOWING,
        ),
      ),
    ).toBe(true);
    for (const summary of data.summary)
      expect(summary.achievementRate).toBeNull();
  } else await expect(banner).toHaveCount(0);
  await expect(page.getByTestId("business-plan-basis")).toContainText(
    "실적은 영업이익표의 같은 달 매출액·영업이익입니다",
  );
  await expect(page.getByTestId("business-plan-basis")).not.toContainText(
    /\b(TK|MO|DP)\b/,
  );
}

function workbookRows(bytes: Buffer): (string | null)[][] {
  const result = spawnSync(
    "python",
    [
      "-c",
      `
import io, json, sys, zipfile
import xml.etree.ElementTree as ET
ns = {'s': 'http://schemas.openxmlformats.org/spreadsheetml/2006/main'}
with zipfile.ZipFile(io.BytesIO(sys.stdin.buffer.read())) as archive:
    strings = []
    if 'xl/sharedStrings.xml' in archive.namelist():
        strings = [''.join(item.itertext()) for item in ET.fromstring(archive.read('xl/sharedStrings.xml'))]
    rows = []
    for row in ET.fromstring(archive.read('xl/worksheets/sheet1.xml')).findall('s:sheetData/s:row', ns):
        values = []
        for cell in row:
            value = cell.findtext('s:v', None, ns)
            if cell.attrib.get('t') == 's':
                value = strings[int(value)]
            elif cell.attrib.get('t') == 'inlineStr':
                value = ''.join(cell.find('s:is', ns).itertext())
            values.append(value)
        rows.append(values)
    print(json.dumps(rows, ensure_ascii=False))
`,
    ],
    { input: bytes },
  );
  expect(
    result.status,
    "XLSX inspection requires Python's standard library",
  ).toBe(0);
  return JSON.parse(result.stdout.toString());
}

test("UI-18 retains server amounts and operating-profit parity with simplified help and upload template", async ({
  page,
}) => {
  const data = await openPerformance(page);
  await page.getByTestId("business-plan-unit").selectOption("원");
  await expect(page.getByRole("button", { name: "양식 내려받기" })).toHaveCount(
    0,
  );
  await expect(page.getByText(/양식:.*업로드:/)).toHaveCount(0);
  await assertTable(page, data);

  const operating = await page.request.get(
    `/api/profit-loss/operating?${new URLSearchParams({
      year: String(data.context.year),
      month: data.context.month,
    })}`,
  );
  expect(operating.status()).toBe(200);
  const reference: OperatingProfitData = (await operating.json()).data;
  for (const month of data.months) {
    const actual = reference.months.find((item) => item.month === month.month);
    expect(actual, `영업이익표 ${month.month}`).toBeDefined();
    expect(month.salesActualAmt).toBe(actual!.amounts.salesAmt);
    expect(month.operatingProfitActualAmt).toBe(actual!.amounts.oprtPrftAmt);
  }

  if (data.context.year === Number(data.context.nowMonth.slice(0, 4))) {
    await page.getByTestId("business-plan-upload").click();
    const dialog = page.getByRole("dialog", {
      name: "목표 업로드",
      exact: true,
    });
    await expect(dialog).toContainText("금액은 원 단위 (예: 50,000,000)");
    const pending = page.waitForEvent("download");
    await dialog.getByRole("button", { name: "양식 내려받기" }).click();
    const download = await pending;
    expect(await download.failure()).toBeNull();
    const stream = await download.createReadStream();
    if (!stream) throw new Error("Downloaded workbook stream is unavailable");
    const chunks: Buffer[] = [];
    for await (const chunk of stream) chunks.push(Buffer.from(chunk));
    const rows = workbookRows(Buffer.concat(chunks));
    expect(rows[0]).toEqual(["연월*", "매출 목표(원)*", "영업이익 목표(원)*"]);
    expect(rows).toHaveLength(13);
    await dialog.getByRole("button", { name: "취소", exact: true }).click();
    await expect(dialog).toHaveCount(0);
  } else await expect(page.getByTestId("business-plan-upload")).toBeDisabled();
});

test("UI-18 mobile retains data and explains PC-only upload", async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await openPerformance(page);
  await expect(
    page.getByText("목표 업로드는 PC에서 이용해 주세요."),
  ).toBeVisible();
  await expect(page.getByTestId("business-plan-upload")).toBeDisabled();
  await expect(page.getByRole("button", { name: "양식 내려받기" })).toHaveCount(
    0,
  );
  await expect(
    page.getByRole("table", { name: "사업계획 월별 실적" }),
  ).toBeVisible();
});
