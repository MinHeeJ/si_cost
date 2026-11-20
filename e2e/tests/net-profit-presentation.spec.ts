import { test, expect, type Page } from "@playwright/test";
import { spawnSync } from "node:child_process";
import { account, loginUi } from "./basic147/authSupport";
import {
  compareDecimal,
  formatAmount,
  formatRate,
} from "../../frontend/src/shared/format";
import type { NetProfitData } from "../../frontend/src/pages/net/netProfitApi";
import type { OperatingProfitData } from "../../frontend/src/pages/operating/operatingProfitApi";
import type { StructureData } from "../../frontend/src/pages/structure/profitStructureApi";

// Real disposable app only: no intercepted API, financial writes or generated history.
test.use({ trace: "off", screenshot: "off", video: "off" });
test.beforeEach(async ({ page }) => {
  await loginUi(page, account("FIN"));
});
const endpoint = "/api/profit-loss/net";
const noProfit = "흑자인 유형이 없어 비율과 막대를 표시하지 않습니다.";

async function openNet(page: Page): Promise<NetProfitData> {
  const pending = page.waitForResponse(
    (r) => new URL(r.url()).pathname === endpoint,
  );
  await page.goto("/UI-09");
  const response = await pending;
  expect(response.status()).toBe(200);
  await expect(page.getByTestId("net-export")).toBeEnabled();
  return (await response.json()).data;
}
async function selectPeriod(
  page: Page,
  field: "year" | "month",
  value: string,
): Promise<NetProfitData> {
  const pending = page.waitForResponse((r) => {
    const url = new URL(r.url());
    return url.pathname === endpoint && url.searchParams.get(field) === value;
  });
  await page
    .getByLabel(field === "year" ? "누계 연도" : "누계 월")
    .selectOption(value);
  const response = await pending;
  expect(response.status()).toBe(200);
  await expect(page.getByTestId("net-export")).toBeEnabled();
  return (await response.json()).data;
}
function workbookSheets(bytes: Buffer): Record<string, (string | null)[][]> {
  const result = spawnSync(
    "python",
    [
      "-c",
      `
import io, json, posixpath, sys, zipfile
import xml.etree.ElementTree as ET
ns = {'s': 'http://schemas.openxmlformats.org/spreadsheetml/2006/main'}
with zipfile.ZipFile(io.BytesIO(sys.stdin.buffer.read())) as archive:
    shared = []
    if 'xl/sharedStrings.xml' in archive.namelist():
        shared = [''.join(item.itertext()) for item in ET.fromstring(archive.read('xl/sharedStrings.xml'))]
    targets = {item.attrib['Id']: item.attrib['Target'] for item in ET.fromstring(archive.read('xl/_rels/workbook.xml.rels'))}
    output = {}
    for sheet in ET.fromstring(archive.read('xl/workbook.xml')).find('s:sheets', ns):
        target = targets[sheet.attrib['{http://schemas.openxmlformats.org/officeDocument/2006/relationships}id']]
        path = target.lstrip('/') if target.startswith('/') else posixpath.normpath(posixpath.join('xl', target))
        rows = []
        for row in ET.fromstring(archive.read(path)).findall('s:sheetData/s:row', ns):
            values = []
            for cell in row:
                column = 0
                for char in cell.attrib['r']:
                    if not char.isalpha():
                        break
                    column = column * 26 + ord(char) - ord('A') + 1
                values.extend([None] * (column - len(values)))
                if cell.find('s:f', ns) is not None:
                    raise ValueError('Financial export must not contain formulas')
                value = cell.findtext('s:v', None, ns)
                if cell.attrib.get('t') == 's':
                    value = shared[int(value)]
                elif cell.attrib.get('t') == 'inlineStr':
                    value = ''.join(cell.find('s:is', ns).itertext())
                values[column - 1] = value
            rows.append(values)
        output[sheet.attrib['name']] = rows
    print(json.dumps(output, ensure_ascii=False))
`,
    ],
    { input: bytes, maxBuffer: 4 * 1024 * 1024 },
  );
  expect(
    result.status,
    "XLSX inspection requires Python's standard library",
  ).toBe(0);
  return JSON.parse(result.stdout.toString());
}
function numericOrBlank(
  actual: string | null,
  expected: string | null | undefined,
  scale = 1,
) {
  if (expected == null) expect(actual).toBeNull();
  else {
    expect(actual).not.toBeNull();
    expect(Number(actual)).toBeCloseTo(Number(expected) / scale, 8);
  }
}
async function assertWorkbook(page: Page, data: NetProfitData) {
  await page.getByTestId("net-unit").selectOption("천원");
  const pending = page.waitForEvent("download");
  await page.getByTestId("net-export").click();
  const download = await pending;
  expect(await download.failure()).toBeNull();
  const stream = await download.createReadStream();
  if (!stream) throw new Error("Downloaded workbook stream is unavailable");
  const chunks: Buffer[] = [];
  for await (const chunk of stream) chunks.push(Buffer.from(chunk));
  const sheets = workbookSheets(Buffer.concat(chunks));
  const summary = sheets["요약"];
  expect(summary).toHaveLength(3);
  expect(summary[0]).toEqual([
    "항목",
    "금액",
    "순이익률(%)",
    "비교 기준",
    "비교 금액",
    "증감률(%)",
  ]);
  data.summary.forEach((card, index) => {
    const row = summary[index + 1];
    const comparison = index === 0 ? card.previousYear : card.previous;
    expect(row).toHaveLength(6);
    expect(row[0]).toBe(index === 0 ? "누계 당기순이익" : "기준월 순이익");
    numericOrBlank(row[1], card.amount, 1000);
    numericOrBlank(row[2], card.rate);
    expect(row[3]).toBe(index === 0 ? "전년 같은 기간" : "전월");
    numericOrBlank(row[4], comparison?.comparisonValue, 1000);
    numericOrBlank(row[5], comparison?.changeRate);
  });
  const contribution = sheets["기준월 기여도"];
  expect(contribution[0]).toEqual([
    "계약 유형",
    "기준월 매출",
    "배분 원가",
    "유형별 매출총이익",
    "기여도(%)",
    "상태",
  ]);
  const groups = data.contributions.visible
    ? data.contributions.contractTypes
    : [];
  expect(contribution).toHaveLength(groups.length + 1);
  groups.forEach((group, index) => {
    const row = contribution[index + 1];
    expect(row[0]).toBe(group.groupName);
    expect(row[0]).not.toMatch(/\b(MO|TK|DP)\b/);
    numericOrBlank(row[1], group.salesAmt, 1000);
    numericOrBlank(row[2], group.allocatedLaborCostAmt, 1000);
    numericOrBlank(row[3], group.contributionAmt, 1000);
    numericOrBlank(row[4], group.positiveContributionShare);
    expect(row[5]).toBe(
      group.loss ? "적자" : group.showBar ? "흑자" : "비율 없음",
    );
  });
  const info = sheets["다운로드정보"];
  expect(info.find((row) => row[0] === "기간단위")?.[1]).toBe("연 누계");
  const selected = data.months.filter(
    (month) => month.month <= data.context.month,
  );
  const expectedRows =
    11 +
    data.notices.length +
    (data.contributions.reason == null ? 0 : 1) +
    selected.reduce(
      (count, month) =>
        count +
        1 +
        month.completeness.estimated.length +
        month.completeness.issues.length,
      0,
    );
  expect(info).toHaveLength(expectedRows);
  for (const row of info)
    expect(row.join(" ")).not.toMatch(
      /TB_[A-Z0-9_]+|\b[a-z][A-Za-z]*(?:Amt|Rate|Share)\b/,
    );
}

test("UI-09 카드·기여도·쉬운 안내·엑셀과 다른 손익 화면의 실제 응답 일치", async ({
  page,
}) => {
  let data = await openNet(page);
  const now = data.context.nowMonth;
  if (data.context.year !== Number(now.slice(0, 4)))
    data = await selectPeriod(page, "year", now.slice(0, 4));
  if (data.context.month !== now) data = await selectPeriod(page, "month", now);
  await page.getByTestId("net-unit").selectOption("천원");
  for (const [index, card] of data.summary.entries()) {
    const shown = page.getByTestId(
      index === 0 ? "net-cumulative-card" : "net-month-card",
    );
    await expect(shown.getByRole("heading")).toHaveText(
      index === 0 ? "누계 당기순이익" : "기준월 순이익",
    );
    await expect(shown).toContainText(formatAmount(card.amount, "천원"));
    await expect(shown).toContainText(`순이익률 ${formatRate(card.rate)}`);
    const comparison = index === 0 ? card.previousYear : card.previous;
    if (comparison) {
      await expect(shown).toContainText(
        index === 0 ? "전년 같은 기간 대비" : "전월 대비",
      );
      await expect(shown.locator(".operating-change")).toHaveClass(
        new RegExp(comparison.direction.toLowerCase()),
      );
    }
  }
  const contributions = page.getByTestId("net-contributions");
  await expect(contributions).toContainText(
    `${data.contributionMonth} 매출총이익 기준`,
  );
  await expect(contributions).not.toContainText(/\b(MO|TK|DP)\b/);
  if (data.contributions.visible) {
    for (const [index, group] of data.contributions.contractTypes.entries()) {
      const row = page.getByTestId(`net-contribution-${index}`);
      await expect(row).toContainText(group.groupName);
      await expect(row).toContainText(
        formatAmount(group.contributionAmt, "천원"),
      );
      if (group.loss) {
        await expect(row.locator(".adverse")).toHaveText(
          `적자 ${formatAmount(group.contributionAmt, "천원")} 천원`,
        );
        await expect(row).not.toContainText(/%|–/);
        await expect(row.locator(".net-contribution-track")).toHaveCount(0);
      } else if (group.showBar) {
        await expect(row).toContainText(
          formatRate(group.positiveContributionShare),
        );
        await expect(row.locator(".net-contribution-track")).toHaveCount(1);
      }
    }
    await expect(page.getByTestId("net-contribution-loss-note")).toHaveCount(
      data.contributions.contractTypes.some((group) => group.loss) ? 1 : 0,
    );
    const positive = data.contributions.contractTypes.some(
      (group) =>
        group.contributionAmt != null &&
        compareDecimal(group.contributionAmt, "0") > 0,
    );
    await expect(
      contributions.getByText(noProfit, { exact: true }),
    ).toHaveCount(positive ? 0 : 1);
  }
  const note = page.getByText(/턴키는 청구한 달의 공급가액/);
  await expect(note).toContainText(
    "실제로 낸 법인세는 자금수지표에만 잡힙니다.",
  );
  await expect(note).toContainText(
    "내부(자체 개발) 비용은 판관비(연구개발비)로 처리합니다.",
  );
  await expect(note).not.toContainText(/TK|MO|DP|IN/);
  await expect(
    page.getByText("전월 조정과 연간 예상 법인세는 재무 항목에서 입력합니다."),
  ).toBeVisible();
  await expect(page.getByTestId("net-tax-link")).toBeVisible();
  await expect(page.getByTestId("net-cashflow-link")).toBeVisible();
  await assertWorkbook(page, data);

  const params = { year: String(data.context.year), month: data.context.month };
  const operatingResponse = await page.request.get(
    "/api/profit-loss/operating",
    { params },
  );
  const structureResponse = await page.request.get(
    "/api/profit-loss/structure",
    { params: { ...params, aggregation: "MONTH" } },
  );
  expect(operatingResponse.status()).toBe(200);
  expect(structureResponse.status()).toBe(200);
  const operating = (await operatingResponse.json())
    .data as OperatingProfitData;
  const structure = (await structureResponse.json()).data as StructureData;
  for (const row of operating.rows) {
    const netRow = data.rows.find((value) => value.metric === row.metric)!;
    expect(netRow.monthlyValues).toEqual(row.monthlyValues);
    expect(netRow.cumulativeValue).toBe(row.cumulativeValue);
  }
  const netRow = data.rows.find((row) => row.metric === "netPrftAmt")!;
  structure.buckets.forEach((bucket, index) =>
    expect(bucket.amounts.netPrftAmt).toBe(netRow.monthlyValues[index]),
  );
  const types = structure.buckets.at(-1)!.composition.contractTypes;
  for (const group of data.contributions.contractTypes)
    expect(group.groupName).toBe(
      types.find((type) => type.groupId === group.groupId)?.groupName,
    );
});

test("UI-09 운영 시작 전 기여도 숨김과 비교 빈칸·한글 엑셀", async ({
  page,
}) => {
  let data = await openNet(page);
  if (data.context.year !== 2025)
    data = await selectPeriod(page, "year", "2025");
  if (data.context.month !== "2025-12")
    data = await selectPeriod(page, "month", "2025-12");
  expect(data.contributions.visible).toBe(false);
  expect(data.cumulativeProvisional).toBe(false);
  await expect(page.getByTestId("net-contributions")).toContainText(
    data.contributions.reason!,
  );
  await expect(page.getByTestId("net-contribution-0")).toHaveCount(0);
  await expect(page.getByTestId("net-contribution-loss-note")).toHaveCount(0);
  await assertWorkbook(page, data);
});
