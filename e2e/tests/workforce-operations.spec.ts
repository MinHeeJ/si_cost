import { expect, test, type Page } from "./support/authenticatedTest";

// Runner-owned real application and DB only; these flows never mutate business or seed data.
async function open(page: Page) {
  const pending = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname === "/api/workforce-operations" &&
      response.request().method() === "GET",
  );
  await page.goto("/UI-28");
  await expect(page.getByTestId("workforce-operations-screen")).toBeVisible();
  const response = await pending;
  expect(response.status()).toBe(200);
  const envelope = await response.json();
  expect(envelope.success).toBe(true);
  await expect(page.getByTestId("calendar-download")).toBeEnabled();
  return envelope.data as { year: number; total: number };
}
async function download(page: Page, button: ReturnType<Page["getByTestId"]>) {
  const [file, response] = await Promise.all([
    page.waitForEvent("download"),
    page.waitForResponse(
      (response) =>
        new URL(response.url()).pathname === "/api/workforce-operations/export",
    ),
    button.click(),
  ]);
  expect(response.status()).toBe(200);
  expect(response.headers()["content-type"]).toContain(
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
  );
  expect(response.headers()["content-disposition"]).toContain(".xlsx");
  expect(await file.failure()).toBeNull();
  const stream = await file.createReadStream();
  expect(stream).not.toBeNull();
  const chunks: Buffer[] = [];
  for await (const chunk of stream!) chunks.push(Buffer.from(chunk));
  const bytes = Buffer.concat(chunks);
  expect(bytes.length).toBeGreaterThan(4);
  expect(bytes.subarray(0, 2).toString()).toBe("PK");
  return { file, query: new URL(response.url()).searchParams };
}

test("UI-28 달력 다운로드는 현재 적용 필터의 전체값을 요청한다", async ({
  page,
}) => {
  const data = await open(page);
  const result = await download(page, page.getByTestId("calendar-download"));
  expect(result.query.get("tab")).toBe("CALENDAR");
  expect(result.query.get("year")).toBe(String(data.year));
  expect(result.query.has("page")).toBe(false);
  expect(result.query.has("size")).toBe(false);
  expect(result.file.suggestedFilename()).toMatch(/^인력운영달력_.*\.xlsx$/);
  await expect(
    page.getByTestId("calendar-panel").getByRole("status"),
  ).toHaveText("다운로드했습니다.");
});

test("UI-28 수요공급 탭 다운로드는 달력 필터 대신 선택 단위를 전송한다", async ({
  page,
}) => {
  await open(page);
  await page.getByRole("tab", { name: "수요·공급", exact: true }).click();
  const panel = page.getByTestId("supply-panel");
  await expect(panel).toBeVisible();
  await expect(panel.getByTestId("workforce-download")).toBeEnabled();
  await panel.getByTestId("workforce-unit").selectOption("MILLION");
  const result = await download(page, panel.getByTestId("workforce-download"));
  expect(result.query.get("tab")).toBe("SUPPLY_DEMAND");
  expect(result.query.get("amountUnit")).toBe("MILLION");
  for (const key of [
    "jobCode",
    "gradeCode",
    "employeeType",
    "remainingOnly",
    "page",
    "size",
    "from",
    "months",
  ])
    expect(result.query.has(key)).toBe(false);
  expect(result.file.suggestedFilename()).toMatch(
    /^인력운영수요공급_.*\.xlsx$/,
  );
});

test("허용외 다운로드 요청은 JSON 필드 오류이며 파일을 보내지 않는다", async ({
  page,
}) => {
  const response = await page.request.get("/api/workforce-operations/export", {
    params: { tab: "CALENDAR", actor: "injected" },
  });
  expect(response.status()).toBe(400);
  expect(response.headers()["content-disposition"]).toBeUndefined();
  const error = await response.json();
  expect(error.success).toBe(false);
  expect(error.code).toBe("UNKNOWN_FIELD");
  expect(error.field).toBe("actor");
  expect(error.requestId).toEqual(expect.any(String));
});
