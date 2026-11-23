import { test, expect, type Page, type Locator } from "@playwright/test";
import { spawnSync } from "node:child_process";
import { account, loginUi } from "./basic147/authSupport";
import { formatAmount } from "../../frontend/src/shared/format";
import type { OperatingProfitData } from "../../frontend/src/pages/operating/operatingProfitApi";
import type { NetProfitData } from "../../frontend/src/pages/net/netProfitApi";
import type { StructureData } from "../../frontend/src/pages/structure/profitStructureApi";

// Use the runner's disposable app and FIN login. No API interception, financial
// fixture writes, uploads, session recordings, or replacement calculations.
test.use({ trace: "off", screenshot: "off", video: "off" });
test.beforeEach(async ({ page }) => {
  await loginUi(page, account("FIN"));
});
const endpoint = "/api/profit-loss/operating";
const labels = [
  "누계 매출",
  "누계 매출총이익",
  "누계 영업이익",
  "기준월 영업이익",
];
const kebab = (value: string) =>
  value.replace(/[A-Z]/g, (c) => `-${c.toLowerCase()}`);

async function openOperating(page: Page): Promise<OperatingProfitData> {
  const response = page.waitForResponse(
    (r) => new URL(r.url()).pathname === endpoint,
  );
  await page.goto("/UI-08");
  const received = await response;
  expect(received.status()).toBe(200);
  await expect(page.getByTestId("operating-export")).toBeEnabled();
  return (await received.json()).data;
}
async function selectPeriod(
  page: Page,
  field: string,
  value: string,
): Promise<OperatingProfitData> {
  const response = page.waitForResponse((r) => {
    const url = new URL(r.url());
    return url.pathname === endpoint && url.searchParams.get(field) === value;
  });
  await page
    .getByLabel(field === "year" ? "누계 연도" : "누계 월")
    .selectOption(value);
  const received = await response;
  expect(received.status()).toBe(200);
  await expect(page.getByTestId("operating-export")).toBeEnabled();
  return (await received.json()).data;
}
async function assertWeights(table: Locator, boldLabels: string[]) {
  const rows = table.locator("tbody tr");
  for (const row of await rows.all()) {
    const heading = row.getByRole("rowheader");

    // Field-help buttons may contribute text; match the row's leading business label.
    const label = await heading.evaluate(
      (element) => element.firstChild?.textContent ?? "",
    );
    const weight = boldLabels.includes(label) ? "600" : "400";
    await expect(heading).toHaveCSS("font-weight", weight);
    for (const cell of await row.getByRole("cell").all())
      await expect(cell).toHaveCSS("font-weight", weight);
  }
  await expect(table.locator("tbody .operating-total")).toHaveCount(
    boldLabels.length,
  );
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
    relationships = ET.fromstring(archive.read('xl/_rels/workbook.xml.rels'))
    targets = {item.attrib['Id']: item.attrib['Target'] for item in relationships}
    output = {}
    for sheet in ET.fromstring(archive.read('xl/workbook.xml')).find('s:sheets', ns):
        name = sheet.attrib['name']
        if name not in ('요약', '다운로드정보'):
            continue
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
        output[name] = rows
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
async function assertWorkbook(page: Page, data: OperatingProfitData) {
  await page.getByTestId("operating-unit").selectOption("천원");
  const pending = page.waitForEvent("download");
  await page.getByTestId("operating-export").click();
  const download = await pending;
  expect(await download.failure()).toBeNull();
  const stream = await download.createReadStream();
  if (!stream) throw new Error("Downloaded workbook stream is unavailable");
  const chunks: Buffer[] = [];
  for await (const chunk of stream) chunks.push(Buffer.from(chunk));
  const sheets = workbookSheets(Buffer.concat(chunks));
  const summary = sheets["요약"];
  expect(summary).toHaveLength(5);
  expect(summary[0]).toEqual([
    "항목",
    "금액",
    "이익률(%)",
    "비교 기준",
    "비교 금액",
    "증감률(%)",
  ]);
  data.summary.forEach((card, index) => {
    const row = summary[index + 1];
    const comparison = index === 3 ? card.previous : card.previousYear;
    expect(row).toHaveLength(6);
    expect(row[0]).toBe(labels[index]);
    numericOrBlank(row[1], card.amount, 1000);
    numericOrBlank(row[2], card.rate);
    expect(row[3]).toBe(index === 3 ? "전월" : "전년 같은 기간");
    numericOrBlank(row[4], comparison?.comparisonValue, 1000);
    numericOrBlank(row[5], comparison?.changeRate);
  });
  const info = sheets["다운로드정보"];
  expect(info.find((row) => row[0] === "기간단위")?.[1]).toBe("연 누계");
  const selected = data.months.filter(
    (month) => month.month <= data.context.month,
  );
  const expectedRows =
    10 +
    data.notices.length +
    selected.reduce(
      (count, month) =>
        count +
        1 +
        month.completeness.estimated.length +
        month.completeness.issues.length,
      0,
    );
  expect(info).toHaveLength(expectedRows);
  const technicalNames = /TB_[A-Z0-9_]+|\b[a-z][A-Za-z]*(?:Amt|Rate|Share)\b/;
  for (const row of info.filter((row) =>
    ["미확인 원천", "미확인·이관 범위", "예정 포함"].includes(row[0] ?? ""),
  ))
    expect(row.join(" ")).not.toMatch(technicalNames);
}

test("UI-08 제목 동작·쉬운 안내·카드 비교·표 글자와 숫자 굵기·실제 엑셀", async ({
  page,
}) => {
  let data = await openOperating(page);
  const now = data.context.nowMonth;
  if (data.context.month.slice(0, 4) !== now.slice(0, 4))
    data = await selectPeriod(page, "year", now.slice(0, 4));
  if (data.context.month !== now) data = await selectPeriod(page, "month", now);
  await page.getByTestId("operating-unit").selectOption("천원");
  await expect(
    page.getByRole("button", { name: "전년 손익 양식", exact: true }),
  ).toHaveCount(0);
  await expect(page.getByTestId("operating-upload")).toBeEnabled();
  const note = page.getByText(/턴키는 청구한 달의 공급가액/);
  await expect(note).toContainText(
    "월단위·파견은 일한 달의 매출입니다(부가세 제외).",
  );
  await expect(note).not.toContainText(/TK|MO|DP/);
  await assertWeights(
    page.getByRole("table", { name: "영업이익표", exact: true }),
    ["매출액", "매출총이익", "영업이익"],
  );
  for (const card of data.summary) {
    const shown = page.getByTestId(`operating-card-${kebab(card.metric)}`);
    await expect(shown).toContainText(formatAmount(card.amount, "천원"));
    const comparison = card.previousYear ?? card.previous;
    if (comparison) {
      await expect(shown).toContainText(
        card.metric === "currentMonthOperatingProfit"
          ? "전월 대비"
          : "전년 같은 기간 대비",
      );
      await expect(shown.locator(".operating-change")).toHaveClass(
        new RegExp(comparison.direction.toLowerCase()),
      );
    }
  }
  await page.getByTestId("operating-upload").click();
  const dialog = page.getByRole("dialog", {
    name: "전년 손익 업로드",
    exact: true,
  });
  await expect(dialog).not.toContainText(/BASE|NULL/);
  await expect(dialog).toContainText(
    "선택 칸을 비우거나 –를 넣으면 「–」(값 없음)로 저장됩니다.",
  );
  const template = page.waitForEvent("download");
  await dialog
    .getByRole("button", { name: "양식 내려받기", exact: true })
    .click();
  expect(await (await template).failure()).toBeNull();
  await dialog.getByRole("button", { name: "취소", exact: true }).click();
  await expect(dialog).toHaveCount(0);
  await assertWorkbook(page, data);

  const params = { year: String(data.context.year), month: data.context.month };
  const netResponse = await page.request.get("/api/profit-loss/net", {
    params,
  });
  expect(netResponse.status()).toBe(200);
  const net = (await netResponse.json()).data as NetProfitData;
  const structureResponse = await page.request.get(
    "/api/profit-loss/structure",
    { params: { ...params, aggregation: "YTD" } },
  );
  expect(structureResponse.status()).toBe(200);
  const structure = (await structureResponse.json()).data as StructureData;
  const operating = data.rows.find((row) => row.metric === "oprtPrftAmt")!;
  expect(
    net.rows.find((row) => row.metric === "oprtPrftAmt")?.monthlyValues,
  ).toEqual(operating.monthlyValues);
  expect(
    net.rows.find((row) => row.metric === "oprtPrftAmt")?.cumulativeValue,
  ).toBe(operating.cumulativeValue);
  expect(
    structure.summary.find((card) => card.metric === "oprtPrftAmt")?.amount,
  ).toBe(operating.cumulativeValue);
  await page.goto("/UI-09");
  await expect(page.getByTestId("net-export")).toBeEnabled();
  await assertWeights(
    page.getByRole("table", { name: "순이익표", exact: true }),
    ["매출액", "매출총이익", "영업이익", "당기순이익"],
  );
});

test("UI-08 2025년 이관 빈칸·전년 비교 없음·한글 다운로드정보", async ({
  page,
}) => {
  let data = await openOperating(page);
  if (data.context.year !== 2025)
    data = await selectPeriod(page, "year", "2025");
  if (data.context.month !== "2025-12")
    data = await selectPeriod(page, "month", "2025-12");
  expect(data.cumulativeProvisional).toBe(false);
  expect(data.showPreviousYear).toBe(false);
  await expect(
    page.getByRole("columnheader", { name: "전년 누계", exact: true }),
  ).toHaveCount(0);
  await expect(
    page.getByRole("columnheader", { name: "전년 대비", exact: true }),
  ).toHaveCount(0);
  await expect(
    page.getByRole("table", { name: "영업이익표", exact: true }),
  ).not.toContainText("잠정");
  await assertWorkbook(page, data);
});
