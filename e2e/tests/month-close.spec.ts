import { expect, test, type Page } from "./support/authenticatedTest";
import type { MonthCloses } from "../../frontend/src/pages/monthclosesops/monthCloseApi";

// Runner-owned app and database only. These scenarios never close or reopen shared months.
async function open(page: Page): Promise<MonthCloses> {
  const pending = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname === "/api/month-closes" &&
      response.request().method() === "GET",
  );
  await page.goto("/UI-14");
  await expect(page.getByTestId("month-close-screen")).toBeVisible();
  const response = await pending;
  expect(response.status()).toBe(200);
  const body = await response.json();
  expect(body.success).toBe(true);
  return body.data as MonthCloses;
}

test("실제 API의 현재 또는 당시 6점검·이력을 표시하고 서버 허용동작을 따른다", async ({
  page,
}) => {
  const data = await open(page);
  await expect(page.getByTestId("month-close-screen")).toHaveAttribute(
    "data-screen-id",
    "UI-14",
  );
  expect(data.checks.map((check) => check.code)).toEqual([
    "PAY",
    "MM_OVER",
    "BILL",
    "FI",
    "SETL",
    "BILL_MISS",
  ]);
  for (const check of data.checks) {
    const card = page.getByTestId(
      `month-close-check-${check.code.toLowerCase().replaceAll("_", "-")}`,
    );
    await expect(card).toContainText(check.name);
    await expect(card).toContainText(
      data.status === "CLOSE"
        ? check.code === "PAY"
          ? `${check.count}명 업로드 완료`
          : "확인 완료"
        : (check.description ?? "–"),
    );
    if (data.status === "CLOSE")
      await expect(card.getByRole("button")).toHaveCount(0);
  }
  await expect(
    page.getByTestId("month-close-check-pay").getByRole("button"),
  ).toHaveCount(0);
  if (data.status === "CLOSE") {
    const notice = page.getByTestId("month-close-closed-notice");
    await expect(notice).toContainText(`${data.month}는 마감되었습니다(`);
    await expect(notice).toContainText(data.closedByName ?? "–");
    await expect(notice).toContainText(
      "고칠 내용은 다음 달 전월 조정으로 입력하세요.",
    );
    await expect(notice).toContainText(
      data.month === data.latestCloseMonth
        ? "마감 해제는 시스템 관리자만 할 수 있습니다"
        : "가장 최근에 마감한 달만 해제할 수 있습니다",
    );
    await expect(
      page.getByRole("button", { name: "전월 조정 입력", exact: true }),
    ).toHaveCount(0);
    await expect(page.getByRole("button", { name: /확인 필요 .*건/ })).toHaveCount(0);
    await expect(page.getByTestId("month-close-check-button")).toHaveCount(0);
    await expect(page.getByTestId("month-close-close-button")).toHaveCount(0);
    await expect(page.getByTestId("month-close-reopen-button")).toHaveCount(
      data.canReopen ? 1 : 0,
    );
  } else {
    const warning = page.getByRole("button", {
      name: `확인 필요 ${data.warningCount}건`,
      exact: true,
    });
    if (data.warningCount > 0) {
      await expect(warning).toBeVisible();
      await warning.click();
      const dialog = page.getByRole("dialog", { name: `확인 필요 ${data.warningCount}건` });
      await expect(dialog.getByRole("heading", { name: data.month })).toBeVisible();
      await page.keyboard.press("Escape");
    } else {
      await expect(warning).toHaveCount(0);
    }
    await expect(page.getByTestId("month-close-check-button")).toBeEnabled({
      enabled: data.managementAllowed && data.canCheck,
    });
    await expect(page.getByTestId("month-close-close-button")).toBeEnabled({
      enabled: data.managementAllowed && data.canClose,
    });
  }
  for (const entry of data.history.items)
    await expect(
      page.getByTestId(
        `month-close-history-row-${entry.month}-${entry.historySequence}`,
      ),
    ).toContainText(entry.actorName ?? "–");
});

test("5개 POST 핸들러가 누락 revision과 서버 주체 주입을 거부하고 상태를 유지한다", async ({
  page,
}) => {
  const data = await open(page);
  for (const action of [
    "checks",
    "preview",
    "close",
    "reopen-preview",
    "reopen",
  ]) {
    const path = `/api/month-closes/${encodeURIComponent(data.month)}/${action}`;
    const missing = await page.request.post(path, { data: {} });
    expect(missing.status(), `${action} handler`).toBe(400);
    const error = await missing.json();
    expect(error.success).toBe(false);
    expect(error.field).toBe("expectedRevision");
    expect(error.requestId).toEqual(expect.any(String));
    const injection = await page.request.post(path, {
      data: { expectedRevision: data.revision, actor: "client-actor" },
    });
    expect(injection.status()).toBe(400);
    expect((await injection.json()).code).toBe("UNKNOWN_FIELD");
  }
  const after = await page.request.get(
    `/api/month-closes?${new URLSearchParams({ month: data.month })}`,
  );
  expect(after.status()).toBe(200);
  const result = (await after.json()).data as MonthCloses;
  expect(result.revision).toBe(data.revision);
  expect(result.status).toBe(data.status);
  expect(result.history.total).toBe(data.history.total);
});

test("최신 미리보기 모달 취소는 명령을 보내지 않고 초점을 복원한다", async ({
  page,
}) => {
  const data = await open(page);
  const canClose =
    data.status === "WORK" && data.managementAllowed && data.canClose;
  const canReopen =
    data.status === "CLOSE" && data.managementAllowed && data.canReopen;
  test.skip(
    !canClose && !canReopen,
    "선택월은 다음 마감월 또는 최신 마감월이 아닙니다. 전체 성공 경로는 격리 DB 계약시험에서 검증합니다.",
  );
  const action = canClose ? "preview" : "reopen-preview";
  const button = page.getByTestId(
    canClose ? "month-close-close-button" : "month-close-reopen-button",
  );
  const writes: string[] = [];
  page.on("request", (request) => {
    const path = new URL(request.url()).pathname;
    if (
      request.method() === "POST" &&
      /\/api\/month-closes\/[^/]+\/(checks|close|reopen)$/.test(path)
    )
      writes.push(path);
  });
  const pending = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname ===
      `/api/month-closes/${data.month}/${action}`,
  );
  await button.click();
  expect((await pending).status()).toBe(200);
  const dialog = page.getByTestId(
    canClose ? "month-close-preview-dialog" : "month-close-reopen-dialog",
  );
  await expect(dialog).toBeVisible();
  await expect(dialog).toContainText(data.month);
  await expect(dialog).not.toContainText(/TB_|WARN|AUTO|CNFM|WORK|PART-|UI-\d/);
  await expect(dialog.getByRole("heading")).toHaveText(
    canClose
      ? `${data.month}를 마감할까요?`
      : `${data.month} 마감을 해제할까요?`,
  );
  await dialog.getByRole("button", { name: "취소", exact: true }).click();
  await expect(dialog).toHaveCount(0);
  await expect(button).toBeFocused();
  expect(writes).toEqual([]);
  const after = await page.request.get(
    `/api/month-closes?${new URLSearchParams({ month: data.month })}`,
  );
  expect(after.status()).toBe(200);
  expect((await after.json()).data.revision).toBe(data.revision);
});
