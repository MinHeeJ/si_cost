import { expect, test, type Page } from "./support/authenticatedTest";
import type {
  FinancialEditor,
  Preview,
  YearStatus,
} from "../../frontend/src/pages/financialitems/financialApi";

async function openEditor(page: Page): Promise<FinancialEditor> {
  const received = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname === "/api/financial-items" &&
      response.request().method() === "GET",
  );
  await page.goto("/UI-10");
  const response = await received;
  expect(response.status()).toBe(200);
  const result = await response.json();
  expect(result.success).toBe(true);
  await expect(page.getByTestId("financial-editor")).toBeVisible();
  const current = await page.request.get(
    `/api/financial-items?month=${result.data.month}`,
  );
  expect(current.status()).toBe(200);
  return (await current.json()).data;
}

async function assertHistory(page: Page, month: string, scope = "MONTH") {
  const panel = page.getByTestId("financial-history-panel");
  const toggle = panel.getByTestId("financial-history-toggle");
  await expect(panel).toBeVisible();
  await expect(page.getByTestId("financial-history-toggle")).toHaveCount(1);
  await expect(toggle).toHaveAttribute("aria-expanded", "false");
  await expect(panel.getByRole("table")).toHaveCount(0);
  const open = async () => {
    const pending = page.waitForResponse((response) => {
      const url = new URL(response.url());
      return (
        url.pathname === "/api/financial-history" &&
        url.searchParams.get("month") === month &&
        url.searchParams.get("scope") === scope
      );
    });
    await toggle.click();
    const response = await pending;
    expect(response.status()).toBe(200);
    expect((await response.json()).success).toBe(true);
    await expect(toggle).toHaveAttribute("aria-expanded", "true");
    await expect(panel.getByTestId("financial-history-scope")).toHaveValue(
      scope,
    );
    await expect(panel.getByRole("table")).toBeVisible();
  };
  await open();
  await toggle.click();
  await expect(toggle).toHaveAttribute("aria-expanded", "false");
  await expect(panel.getByRole("table")).toHaveCount(0);
  await open();
  await panel.getByRole("button", { name: "닫기", exact: true }).click();
  await expect(toggle).toHaveAttribute("aria-expanded", "false");
  await expect(toggle).toBeFocused();
  await expect(panel.getByRole("table")).toHaveCount(0);
  await expect(panel).toBeVisible();
}

// Real authenticated runner-owned disposable app; no intercepted APIs or production fixture rows.
test("UI-10 제목은 두 편집 버튼만, 양식은 업로드 창 안에서 받고 이력은 아래에서 한 번에 펼친다", async ({
  page,
}) => {
  const data = await openEditor(page);
  if (!data.editable) {
    expect(
      data.firstEditableMonth,
      "runner must provide an editable month",
    ).toBeTruthy();
    const month = data.firstEditableMonth!;
    await page.getByLabel("입력 현황 연도").selectOption(month.slice(0, 4));
    await page.getByTestId(`financial-month-${month}`).click();
  }
  const upload = page.getByTestId("financial-upload-button");
  await expect(upload).toBeEnabled();
  const title = page
    .getByTestId("financial-editor")
    .locator(".sicost-title-action-group");
  await expect(title.getByRole("button")).toHaveText([
    "지난달 항목 불러오기",
    "엑셀 업로드",
  ]);
  await expect(
    title.getByRole("button", { name: /양식 내려받기|변경 이력/ }),
  ).toHaveCount(0);
  await upload.click();
  const dialog = page.getByRole("dialog", { name: "엑셀 업로드 · 재무 항목" });
  const template = dialog.getByRole("button", { name: "양식 내려받기" });
  await expect(template).toBeVisible();
  const pending = page.waitForEvent("download");
  await template.click();
  const download = await pending;
  expect(await download.failure()).toBeNull();
  expect(download.suggestedFilename()).toMatch(/\.xlsx$/i);
  await dialog.getByRole("button", { name: "취소", exact: true }).click();
  await expect(dialog).toHaveCount(0);
  const month = await page
    .locator('[data-testid^="financial-month-"][aria-pressed="true"]')
    .getAttribute("data-testid");
  await assertHistory(page, month!.replace("financial-month-", ""));
  const ids = await page
    .getByTestId("financial-editor")
    .locator("[data-testid]")
    .evaluateAll((elements) =>
      elements.map((element) => element.getAttribute("data-testid")),
    );
  expect(new Set(ids).size).toBe(ids.length);
});

test("UI-10 실제 마감월도 편집 버튼 없이 아래 이력을 펼칠 수 있다", async ({
  page,
}) => {
  const data = await openEditor(page);
  let closedMonth: string | undefined;
  for (
    let year = Number(data.today.slice(0, 4));
    year >= Number(data.cashBaseMonth.slice(0, 4));
    year--
  ) {
    const result = await page.request.get(
      `/api/financial-items/year-status?year=${year}`,
    );
    expect(result.status()).toBe(200);
    const status = (await result.json()).data as YearStatus;
    closedMonth = status.months.find(
      (month) => month.selectable && month.closeStatus === "CLOSE",
    )?.month;
    if (closedMonth) break;
  }
  expect(
    closedMonth,
    "runner must provide an already closed month; this test never closes financial data",
  ).toBeTruthy();
  await page
    .getByLabel("입력 현황 연도")
    .selectOption(closedMonth!.slice(0, 4));
  await page.getByTestId(`financial-month-${closedMonth}`).click();
  await expect(page.getByTestId("financial-read-only")).toContainText(
    `${closedMonth}는 마감되었습니다.`,
  );
  await expect(page.getByTestId("financial-load-previous")).toHaveCount(0);
  await expect(page.getByTestId("financial-upload-button")).toHaveCount(0);
  await expect(page.getByTestId("financial-save")).toHaveCount(0);
  await assertHistory(page, closedMonth!);
});

for (const screen of [
  {
    route: "/UI-08",
    endpoint: "/api/profit-loss/operating",
    scope: "HISTORICAL_PROFIT_LOSS",
  },
  {
    route: "/UI-18",
    endpoint: "/api/business-plans/performance",
    scope: "BUSINESS_PLAN",
  },
]) {
  test(`${screen.route} 공통 이력의 초기 접힘·범위·펼치기·닫기는 그대로다`, async ({
    page,
  }) => {
    const pending = page.waitForResponse(
      (response) => new URL(response.url()).pathname === screen.endpoint,
    );
    await page.goto(screen.route);
    const response = await pending;
    expect(response.status()).toBe(200);
    const data = (await response.json()).data;
    await assertHistory(page, data.context.month, screen.scope);
  });
}

test("UI-10 계산 줄·표 안 합계·월할 접기와 마감월 숨김", async ({ page }) => {
  const data = await openEditor(page);
  const table = page.getByTestId("financial-items-table");
  await expect(
    table.getByTestId("financial-items-table-row-tax-calculated"),
  ).toContainText("연간 예상액에서 자동 계산");
  for (const key of ["sga", "income", "expense", "tax"])
    await expect(
      table.getByTestId(`financial-items-table-row-total-${key}`),
    ).toBeVisible();
  await expect(page.getByTestId("financial-calculate")).toHaveCount(0);
  await expect(page.getByTestId("financial-tax-allocation-table")).toHaveCount(
    0,
  );
  await page.getByTestId("financial-tax-allocation-toggle").click();
  await expect(
    page.getByTestId("financial-tax-allocation-table"),
  ).toBeVisible();
  await page.getByTestId("financial-tax-allocation-toggle").click();
  await expect(page.getByTestId("financial-tax-allocation-table")).toHaveCount(
    0,
  );
  if (!data.editable) {
    await expect(page.getByTestId("financial-save")).toHaveCount(0);
    await expect(page.getByTestId("financial-cancel")).toHaveCount(0);
    await expect(page.getByTestId("financial-items-add")).toHaveCount(0);
    await expect(page.getByTestId("financial-load-previous")).toHaveCount(0);
    await expect(page.getByTestId("financial-upload-button")).toHaveCount(0);
  } else {
    for (const size of [
      { width: 1440, height: 900 },
      { width: 390, height: 844 },
    ]) {
      await page.setViewportSize(size);
      await page.getByTestId("financial-tax-panel").scrollIntoViewIfNeeded();
      const footer = await page.getByTestId("financial-footer").boundingBox();
      expect(footer).not.toBeNull();
      expect(footer!.y).toBeGreaterThanOrEqual(0);
      expect(footer!.y + footer!.height).toBeLessThanOrEqual(size.height);
      await expect(page.getByTestId("financial-save")).toBeInViewport();
    }
  }
  await page.getByTestId("financial-tab-cash").click();
  await expect(
    page.getByTestId("financial-cards-table-row-card-total"),
  ).toContainText("카드별 합계");
  await expect(
    page.getByTestId("financial-cards-table-row-cash-total"),
  ).toContainText("자금수지 반영 합계");
  await expect(
    page.getByTestId("financial-transfers-table-row-total-transfers"),
  ).toContainText("합계");
});

test("UI-10 필수 칸 포커스·자동 서버 미리보기·삭제/취소 확인은 저장 전 DB를 바꾸지 않는다", async ({
  page,
}) => {
  let data = await openEditor(page);
  if (!data.editable && data.firstEditableMonth) {
    const moved = page.waitForResponse(
      (response) =>
        new URL(response.url()).pathname === "/api/financial-items" &&
        response.request().method() === "GET",
    );
    await page.getByTestId("financial-open-adjustment").click();
    data = (await (await moved).json()).data;
    // Opening adjustment adds an unsaved row: discard it before isolating item validation.
    await page.getByTestId("financial-cancel").click();
    await page
      .getByRole("dialog")
      .getByRole("button", { name: "되돌리기", exact: true })
      .click();
  }
  expect(data.editable, "runner must provide an editable month").toBe(true);
  const writes: string[] = [];
  page.on("request", (request) => {
    if (
      request.method() === "PUT" &&
      new URL(request.url()).pathname === "/api/financial-items"
    )
      writes.push(request.url());
  });
  await page.getByTestId("financial-items-add").click();
  const row = page
    .getByTestId("financial-items-table")
    .locator("tbody tr")
    .filter({ has: page.locator('input[data-testid$="-item-name"]:enabled') })
    .last();
  const name = row.locator('input[data-testid$="-item-name"]');
  const amount = row.locator('input[data-testid$="-amount"]').first();
  await page.getByTestId("financial-tab-cash").click();
  await page.getByTestId("financial-save").click();
  await expect(name).toBeFocused();
  await expect(name).toHaveAttribute("aria-invalid", "true");
  await name.fill("UI-10 저장 전 검증");
  await page.getByTestId("financial-save").click();
  await expect(amount).toBeFocused();
  const previewed = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname === "/api/financial-items/preview" &&
      response.request().postDataJSON().action === "CALCULATE",
  );
  await amount.fill("0");
  const response = await previewed;
  expect(response.status()).toBe(200);
  const preview = (await response.json()).data as Preview;
  await page.getByTestId("financial-display-unit").selectOption("원");
  await expect(
    page.getByTestId("financial-items-table-row-total-sga"),
  ).toContainText(
    BigInt(preview.contributions.sgaAmount)
      .toLocaleString("ko-KR")
      .replace("-", "−"),
  );
  await row.getByRole("button", { name: /삭제/ }).click();
  await expect(page.getByRole("dialog")).toContainText("이 행을 삭제할까요?");
  await page
    .getByRole("dialog")
    .getByRole("button", { name: "취소", exact: true })
    .click();
  await expect(name).toHaveValue("UI-10 저장 전 검증");
  await page.getByTestId("financial-cancel").click();
  await page
    .getByRole("dialog")
    .getByRole("button", { name: "되돌리기", exact: true })
    .click();
  await expect(page.locator(".sicost-toast")).toHaveText(
    "변경을 취소했습니다.",
  );
  expect(writes).toHaveLength(0);
  const readback = await page.request.get(
    `/api/financial-items?month=${data.month}`,
  );
  expect(readback.status()).toBe(200);
  expect((await readback.json()).data.revision).toBe(data.revision);
});
