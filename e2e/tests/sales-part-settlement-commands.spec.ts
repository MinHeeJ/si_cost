import { expect, test, type Page } from "@playwright/test";
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
  await expect(page.locator(".settlement-toolbar[hidden]")).toBeHidden();
  await panel.getByTestId("settlement-trend-unit").selectOption("원");
  await expect(
    panel.locator("tfoot tr td").nth(trend.months.length),
  ).toHaveText(formatAmount(trend.periodPartProfitAmount, "원"));
  for (const person of trend.items) {
    expect(person.metrics).toHaveLength(5);
    const id = person.developerId.toLowerCase().replace(/[^a-z0-9-]/g, "-");
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
  const dialog = page.getByRole("dialog", { name: "정산 확정 미리보기" });
  await expect(dialog).toContainText(`전체 ${data.summary.personCount}명`);
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
