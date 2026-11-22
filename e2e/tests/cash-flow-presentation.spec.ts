import { test, expect, type Page } from "@playwright/test";
import { readFileSync } from "node:fs";
import { account, loginUi } from "./basic147/authSupport";
import {
  formatAmount,
  type AmountUnit,
} from "../../frontend/src/shared/format";
import type {
  CashFlowResult,
  CashSourcesPage,
} from "../../frontend/src/pages/cashflowops/cashFlowApi";
import type { MenuNode } from "../../frontend/src/pages/shared/sharedApi";

// Read the runner's real app only. Do not insert financial data or intercept APIs.
// Disable recordings because login/session traffic must not become artifacts.
test.use({ trace: "off", screenshot: "off", video: "off" });
const labels = {
  CASH_SALES: "현금매출",
  MISC: "잡수익",
  LOAN: "대여금",
  REG_PAY: "인건비(정규직)",
  CON_FL_PAY: "인건비(계약직·프리랜서)",
  OS_PAY: "용역비(외주)",
  UNPAID_LABOR: "미지급 인건비",
  EXPENSE: "제경비",
  CORP_TAX: "법인세 납부",
};
const kebab = (key: string) => key.toLowerCase().replaceAll("_", "-");
const forbidden =
  /TB_|VAT_AMT|REAL_PAY_AMT|IO_DT|\b(AMT|VAT|FT|CT|FL|OS|ACTUAL|PLANNED)\b/;
const hasStandards = (menus: MenuNode[]): boolean =>
  menus.some(
    (menu) => menu.screenId === "UI-27" || hasStandards(menu.children),
  );
async function openCashFlow(page: Page): Promise<CashFlowResult> {
  const pending = page.waitForResponse(
    (response) => new URL(response.url()).pathname === "/api/cash-flow",
  );
  await page.goto("/UI-21");
  const response = await pending;
  expect(response.status()).toBe(200);
  await expect(page.getByRole("table", { name: "자금수지표" })).toBeVisible();
  return (await response.json()).data;
}

test("stylesheet: planned cells are transparent and gray, shortage cells stay red and bold", async ({
  page,
}) => {
  // An empty DOM checks the actual CSS cascade without inventing any business data.
  const styles = [
    "shared/common.css",
    "pages/shared/financialDisplay.css",
    "pages/operating/operatingProfit.css",
    "pages/cashflowops/cashFlow.css",
  ]
    .map((path) =>
      readFileSync(
        new URL(`../../frontend/src/${path}`, import.meta.url),
        "utf8",
      ),
    )
    .join("\n");
  await page.setContent(`<style>${styles}</style><div class="operating-profit cash-flow"><table><tbody><tr>
    <td data-testid="planned" class="cash-planned"></td>
    <td data-testid="planned-negative" class="cash-planned" data-financial-negative="true"></td>
    <td data-testid="shortage" class="cash-planned cash-shortage" data-financial-negative="true"></td>
    <td data-testid="actual-negative" data-financial-negative="true"></td>
    </tr></tbody></table></div><span id="muted" style="color:var(--text-sub)"></span><span id="red" style="color:var(--bad)"></span>`);
  const muted = await page
    .locator("#muted")
    .evaluate((element) => getComputedStyle(element).color);
  const red = await page
    .locator("#red")
    .evaluate((element) => getComputedStyle(element).color);
  for (const id of ["planned", "planned-negative"]) {
    await expect(page.getByTestId(id)).toHaveCSS("color", muted);
    await expect(page.getByTestId(id)).toHaveCSS(
      "background-color",
      "rgba(0, 0, 0, 0)",
    );
  }
  await expect(page.getByTestId("shortage")).toHaveCSS("color", red);
  await expect(page.getByTestId("shortage")).toHaveCSS("font-weight", "600");
  await expect(page.getByTestId("actual-negative")).toHaveCSS("color", red);
});

for (const role of ["FIN", "EXEC"]) {
  test(`${role}: UI-21 labels, amounts, warnings, evidence and settings follow server data`, async ({
    page,
  }) => {
    await loginUi(page, account(role));
    const data = await openCashFlow(page);
    const unit: AmountUnit =
      data.unit === "WON"
        ? "원"
        : data.unit === "THOUSAND_WON"
          ? "천원"
          : "백만원";
    const table = page.getByRole("table", { name: "자금수지표" });
    const periods = [...data.months, ...data.periodTotals];
    for (const [key, label] of Object.entries(labels)) {
      await expect(
        table.getByRole("rowheader", { name: label, exact: true }),
      ).toBeVisible();
      const cells = page
        .getByTestId(`cash-row-${kebab(key)}`)
        .getByRole("cell");
      for (let index = 0; index < periods.length; index++) {
        const amount = periods[index].items.find(
          (item) => item.item === key,
        )?.amount;
        await expect(cells.nth(index)).toHaveText(formatAmount(amount, unit));
      }
    }
    for (const [key, field] of [
      ["opening", "openingBalance"],
      ["income", "incomeTotal"],
      ["expense", "expenseTotal"],
      ["balance", "closingBalance"],
    ] as const) {
      const cells = page.getByTestId(`cash-row-${key}`).getByRole("cell");
      for (let index = 0; index < periods.length; index++)
        await expect(cells.nth(index)).toHaveText(
          formatAmount(periods[index][field], unit),
        );
    }
    await expect(
      table.getByRole("rowheader", { name: "이월금", exact: true }),
    ).toBeVisible();
    for (const old of ["외주비", "정규직 급여", "월말 잔액"])
      await expect(
        table.getByRole("rowheader", { name: old, exact: true }),
      ).toHaveCount(0);
    await expect(page.getByTestId("cash-flow-guide")).toContainText(
      "예정(회색 글자)",
    );
    await expect(page.getByTestId("cash-flow-guide")).not.toContainText(
      forbidden,
    );

    for (let index = 0; index < data.months.length; index++) {
      const month = data.months[index];
      const closing = page
        .getByTestId("cash-row-balance")
        .getByRole("cell")
        .nth(index);
      const income = page
        .getByTestId("cash-row-income")
        .getByRole("cell")
        .nth(index);
      const warning = page.getByTestId(`cash-shortage-warning-${month.month}`);
      if (month.status === "PLANNED") {
        await expect(income).toHaveCSS("background-color", "rgba(0, 0, 0, 0)");
        expect(
          await income.evaluate((element) => {
            const probe = document.createElement("span");
            probe.style.color = "var(--text-sub)";
            element.append(probe);
            const matches =
              getComputedStyle(probe).color === getComputedStyle(element).color;
            probe.remove();
            return matches;
          }),
        ).toBe(true);
      }
      if (month.shortage) {
        expect(month.status).toBe("PLANNED");
        await expect(closing).toHaveClass(/cash-shortage/);
        await expect(closing).toHaveCSS("font-weight", "600");
        await expect(warning).toBeVisible();
        await expect(warning).toHaveAttribute(
          "title",
          "월말 잔액이 최소 운영자금보다 적을 것으로 예상",
        );
      } else await expect(warning).toHaveCount(0);
    }
    const banner = page.getByTestId("cash-shortage-alert");
    if (data.shortages.length) {
      await expect(banner).toBeVisible();
      await expect(banner).toContainText(
        `최소 운영자금(${formatAmount(data.minimumCash, "원")}원)`,
      );
      for (const point of data.shortages)
        await expect(banner).toContainText(
          `${Number(point.month.slice(5))}월 ${formatAmount(point.balance, "원")}원`,
        );
      await expect(
        banner.getByRole("button", { name: "미수금 보기" }),
      ).toBeVisible();
    } else await expect(banner).toHaveCount(0);

    const month = data.months.find((entry) => entry.status !== "BEFORE_BASE");
    expect(
      month,
      "The operating year must expose a source-enabled month",
    ).toBeDefined();
    const pending = page.waitForResponse(
      (response) =>
        new URL(response.url()).pathname === "/api/cash-flow/sources",
    );
    await page.getByTestId(`cash-source-reg-pay-${month!.month}`).click();
    const sourceResponse = await pending;
    expect(sourceResponse.status()).toBe(200);
    const sources: CashSourcesPage = (await sourceResponse.json()).data;
    const drawer = page.getByTestId("cash-source-panel");
    await expect(drawer.getByRole("heading")).toContainText(
      "인건비(정규직) 계산근거",
    );
    await expect(drawer.getByText("불러오는 중", { exact: false })).toHaveCount(
      0,
    );
    for (let index = 0; index < sources.items.length; index++) {
      const row = drawer.getByTestId(`cash-source-row-${index}`);
      const source = sources.items[index];
      await expect(row).toContainText(source.sourceTypeLabel);
      await expect(row).toContainText(
        source.status === "ACTUAL"
          ? "실적"
          : source.status === "PLANNED"
            ? "예정"
            : "미확인",
      );
      // Source numbers must remain untouched, including identifiers containing codes.
      for (const column of [0, 1, 2, 3, 4, 5]) {
        if (column === 0) continue;
        await expect(row.getByRole("cell").nth(column)).not.toContainText(
          forbidden,
        );
      }
      if (source.employeeType)
        await expect(row).toContainText(
          sources.employeeTypeLabels[source.employeeType],
        );
    }
    await page.getByTestId("cash-source-close").click();

    const guide = page.getByTestId("balance-trend-chart");
    await expect(
      guide.getByRole("heading", { name: "월말 잔액 흐름" }),
    ).toBeVisible();
    const minimumLabel = guide
      .locator('.sicost-chart-host > div[aria-hidden="true"] svg text')
      .filter({ hasText: /^최소 운영자금$/ });
    await expect(minimumLabel).toHaveCount(1);
    expect(
      await minimumLabel.evaluate((element) => {
        const bounds = element.getBoundingClientRect();
        const svg = element.closest("svg")!.getBoundingClientRect();
        return (
          bounds.left >= svg.left &&
          bounds.right <= svg.right &&
          bounds.top >= svg.top &&
          bounds.bottom <= svg.bottom
        );
      }),
    ).toBe(true);

    const menuResponse = await page.request.get("/api/menus");
    expect(menuResponse.status()).toBe(200);
    const menus: MenuNode[] = (await menuResponse.json()).data.items;
    if (hasStandards(menus)) {
      await page
        .getByRole("button", { name: "기초 잔액·최소 운영자금 설정" })
        .click();
      await expect(
        page.getByTestId("common-codes-tab-standards"),
      ).toHaveAttribute("aria-selected", "true");
      await page.goto("/UI-27");
      await expect(page.getByTestId("common-codes-tab-codes")).toHaveAttribute(
        "aria-selected",
        "true",
      );
    } else await expect(page.getByTestId("cash-standards")).toHaveCount(0);
  });
}
