import { expect, test, type Page } from "./support/authenticatedTest";
import type {
  SettlementResult,
  SettlementTabsResult,
} from "../../frontend/src/pages/salespartsettlements/salesPartSettlementsApi";
import type { TrendResult } from "../../frontend/src/pages/salespartsettlementsops/settlementCommandsApi";
import { formatAmount } from "../../frontend/src/shared/format";

// Real runner-owned application only. Never mutate shared source or seed rows.
async function open(page: Page) {
  const pending = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname === "/api/sales-part-settlements" &&
      response.request().method() === "GET",
  );
  await page.goto("/UI-19");
  await expect(page.getByTestId("sales-part-settlement-screen")).toBeVisible();
  const response = await pending;
  expect(response.status()).toBe(200);
  const envelope = await response.json();
  expect(envelope.success).toBe(true);
  const tabs = envelope.data as SettlementTabsResult;
  const selected =
    tabs.parts.find((part) => part.eligible) ??
    tabs.parts.find((part) => part.historyOnly);
  expect(
    selected,
    "runner 정산 대상 또는 확정 이력 파트가 필요합니다",
  ).toBeDefined();
  await expect(page.getByTestId("settlement-command-panel")).toBeVisible();
  const detail = await page.request.get(
    `/api/sales-part-settlements/${encodeURIComponent(selected!.partId)}?${new URLSearchParams({ month: tabs.month })}`,
  );
  expect(detail.status()).toBe(200);
  const data = (await detail.json()).data as SettlementResult;
  return data;
}

test("상단 버튼과 계산 방법 표·비고 근거는 실제 정산 응답을 표시한다", async ({
  page,
}) => {
  const data = await open(page);
  const header = page.getByTestId("settlement-header-actions");
  await expect(header.getByRole("button")).toHaveText([
    "변경 이력",
    "엑셀 다운로드",
    ...(data.canConfirm ? ["정산 확정"] : []),
    ...(data.canCancel ? ["확정 취소"] : []),
  ]);
  const toolbar = page.getByTestId("settlement-month-toolbar");
  await expect(toolbar.getByRole("heading")).toHaveCount(0);
  await expect(toolbar).toHaveText(/단위\s*원\s*천원\s*백만원/);
  for (const row of data.items) {
    const id = row.developerId.toLowerCase().replace(/[^a-z0-9-]/g, "-");
    const remark = page
      .getByTestId(`settlement-person-${id}`)
      .locator("xpath=ancestor::tr")
      .locator(".settlement-remark");
    await expect(remark.locator("details")).toHaveCount(0);
    await expect(remark).toHaveAttribute(
      "title",
      new RegExp(`급여 원천: ${row.basis.paySource}`),
    );
    if (row.basis.firstMonthMissingPay)
      await expect(remark).toContainText("⚠ 월 급여 없음(시작 달 급여 0)");
  }
  await page.getByTestId("settlement-guide-toggle").click();
  const guide = page.getByRole("table", { name: "계산 방법", exact: true });
  await expect(guide.getByRole("columnheader")).toHaveText([
    "항목",
    "계산식",
    "참고",
  ]);
  await expect(guide.locator("tbody tr")).toHaveCount(10);
  await expect(guide).toContainText(
    `파트 기준값(${formatAmount(data.standard.salesExpenseAmount, "원")}원)`,
  );
  await expect(page.getByTestId("settlement-calculation-guide")).toContainText(
    "확정 사본의 저장되지 않은 당시 근거는 현재 자료로 복원하지 않습니다.",
  );
});

test("취소 창의 빈 사유는 서버 쓰기 없이 빨간 오류를 표시한다", async ({
  page,
}) => {
  const data = await open(page);
  test.skip(
    !data.canCancel,
    "runner의 취소 가능한 CNFM/AUTO 파트가 필요합니다",
  );
  await page.getByTestId("settlement-cancel-open").click();
  const dialog = page.getByRole("dialog", {
    name: "정산 확정을 취소할까요?",
    exact: true,
  });
  await expect(dialog).toContainText(
    "취소하면 이 달 파트 정산을 다시 고칠 수 있습니다. 사유는 기록에 남습니다.",
  );
  await expect(dialog.getByLabel("취소 사유 *")).toHaveAttribute(
    "maxlength",
    "500",
  );
  const writes: string[] = [];
  page.on("request", (request) => {
    if (
      request.method() === "POST" &&
      new URL(request.url()).pathname.endsWith("/cancel")
    )
      writes.push(request.url());
  });
  await dialog.getByRole("button", { name: "확정 취소", exact: true }).click();
  const error = page.getByTestId("settlement-reason-error");
  await expect(error).toHaveText("취소 사유를 입력해 주세요.");
  await expect(error).toHaveClass(/sicost-field-error/);
  await dialog.getByRole("button", { name: "닫기", exact: true }).click();
  expect(writes).toEqual([]);
});

test("실제 추이 API의 과거 인력·5행·전체 기간 합계를 표시한다", async ({
  page,
}) => {
  const data = await open(page);
  const pending = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname ===
      `/api/sales-part-settlements/${encodeURIComponent(data.partId)}/trend`,
  );
  await page
    .getByRole("tab", { name: "기간 추이 (1월~기준월)", exact: true })
    .click();
  const response = await pending;
  expect(response.status()).toBe(200);
  const envelope = await response.json();
  expect(envelope.success).toBe(true);
  const trend = envelope.data as TrendResult;
  const panel = page.getByTestId("settlement-trend-panel");
  await expect(panel).toBeVisible();
  await page.getByTestId("settlement-guide-toggle").click();
  await expect(
    page
      .getByRole("table", { name: "계산 방법", exact: true })
      .locator("tbody tr"),
  ).toHaveCount(10);
  await expect(page.locator(".settlement-toolbar[hidden]")).toBeHidden();
  await panel.getByTestId("settlement-trend-unit").selectOption("원");
  await expect(
    panel.locator("tfoot tr td").nth(trend.months.length),
  ).toHaveText(formatAmount(trend.periodPartProfitAmount, "원"));
  await expect(
    panel.getByRole("columnheader", { name: `${trend.month.slice(5)}월` }),
  ).toHaveClass(/settlement-base-month/);
  const highlighted = panel.locator(".settlement-base-month");
  await expect(highlighted).toHaveCount(trend.items.length * 5 + 2);
  const selectedBackground = await panel.evaluate((element) =>
    getComputedStyle(element).getPropertyValue("--primary-bg").trim(),
  );
  for (const cell of await highlighted.all()) {
    expect(
      await cell.evaluate((element, color) => {
        const probe = document.createElement("span");
        probe.style.backgroundColor = color;
        element.appendChild(probe);
        const expected = getComputedStyle(probe).backgroundColor;
        probe.remove();
        return getComputedStyle(element).backgroundColor === expected;
      }, selectedBackground),
    ).toBe(true);
  }
  for (const person of trend.items) {
    expect(person.metrics).toHaveLength(5);
    const id = person.developerId.toLowerCase().replace(/[^a-z0-9-]/g, "-");
    const remark = panel
      .getByTestId(`settlement-trend-${id}-mm`)
      .locator("td[rowspan]");
    await expect(remark).toHaveAttribute("rowspan", "5");
    await expect(remark).toHaveText(person.baseMonthRemark ?? "–");
    const tooltip = [
      ...new Set(person.metrics.map((metric) => metric.remark).filter(Boolean)),
    ].join("\n");
    if (tooltip) await expect(remark).toHaveAttribute("title", tooltip);
    for (const metric of person.metrics) {
      const row = panel.getByTestId(
        `settlement-trend-${id}-${metric.metric.toLowerCase()}`,
      );
      await expect(row).toBeVisible();
      if (metric.metric !== "MM")
        await expect(row.getByRole("cell").nth(trend.months.length)).toHaveText(
          formatAmount(metric.total, "원"),
        );
    }
  }
});

test("카드 초안 미리보기·확정창 취소는 업무 쓰기 없이 초안을 유지한다", async ({
  page,
}) => {
  const data = await open(page);
  test.skip(!data.canConfirm, "runner의 올해 미마감 WORK 파트가 필요합니다");
  const writes: string[] = [];
  page.on("request", (request) => {
    if (
      ["POST", "PUT"].includes(request.method()) &&
      new URL(request.url()).pathname.startsWith("/api/sales-part-settlements/")
    )
      writes.push(request.url());
  });
  const draft = data.cardAmount === "9" ? "8" : "9";
  await page.getByLabel("법인카드 사용액", { exact: true }).fill(draft);
  await expect(page.getByTestId("settlement-card-draft")).toContainText(
    "저장 전",
  );
  await page.getByRole("button", { name: "정산 확정", exact: true }).click();
  const dialog = page.getByRole("dialog", {
    name: new RegExp(`${data.month} 정산을 확정할까요\\?`),
  });
  await expect(dialog).toContainText(`전체 ${data.summary.personCount}명`);
  await expect(dialog).toContainText(
    "확정하면 이 달 파트 정산(법인카드 사용액 포함)이 잠깁니다.",
  );
  await expect(
    dialog.getByRole("button", { name: "확정하기", exact: true }),
  ).toBeVisible();
  await dialog.getByRole("button", { name: "취소", exact: true }).click();
  await expect(dialog).toHaveCount(0);
  await expect(page.getByLabel("법인카드 사용액", { exact: true })).toHaveValue(
    draft,
  );
  expect(writes).toEqual([]);
});

test("세 쓰기 핸들러는 잘못된 입력을 거부하고 파트월 상태를 보존한다", async ({
  page,
}) => {
  const data = await open(page);
  const path = `/api/sales-part-settlements/${encodeURIComponent(data.partId)}`;
  for (const action of ["card", "confirm", "cancel"] as const) {
    const response = await page.request.fetch(`${path}/${action}`, {
      method: action === "card" ? "PUT" : "POST",
      data: {
        month: data.month,
        expectedRevision: data.revision,
        ...(action === "cancel" ? { reason: " " } : { cardAmount: "1,00" }),
      },
    });
    expect(response.status()).toBe(400);
    const body = await response.json();
    expect(body.success).toBe(false);
    expect(body.field).toBe(action === "cancel" ? "reason" : "cardAmount");
    expect(body.requestId).toEqual(expect.any(String));
  }
  const after = await page.request.get(
    `${path}?${new URLSearchParams({ month: data.month })}`,
  );
  expect(after.status()).toBe(200);
  const result = (await after.json()).data as SettlementResult;
  expect(result.revision).toBe(data.revision);
  expect(result.cardAmount).toBe(data.cardAmount);
  expect(result.status).toBe(data.status);
});
