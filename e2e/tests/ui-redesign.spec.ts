import { test, expect, type Page } from "@playwright/test";
import { join } from "node:path";
import { mkdir } from "node:fs/promises";

// Read-only real-session checks. Never intercept auth/API responses, persist storageState,
// or include credential values in titles, attachments, screenshots or traces.
test.use({
  trace: "off",
  screenshot: "off",
  video: "off",
  actionTimeout: 15000,
  launchOptions: {
    executablePath:
      "/opt/ms-playwright/chromium_headless_shell-1161/chrome-linux/headless_shell",
  },
});
const ids = [
  "UI-16",
  "UI-15",
  "UI-02",
  "UI-01",
  "UI-31",
  "UI-12",
  "UI-05",
  "UI-11",
  "UI-28",
  "UI-17",
  "UI-29",
  "UI-33",
  "UI-13",
  "UI-03",
  "UI-04",
  "UI-19",
  "UI-32",
  "UI-10",
  "UI-07",
  "UI-08",
  "UI-09",
  "UI-18",
  "UI-21",
  "UI-14",
  "UI-26",
  "UI-30",
  "UI-23",
  "UI-24",
  "UI-25",
  "UI-27",
];
const directory = join(process.env.TMPDIR ?? process.cwd(), "basic151-screens");
async function capture(page: Page, name: string) {
  await mkdir(directory, { recursive: true });
  await page.screenshot({
    path: join(directory, `${name}.png`),
    animations: "disabled",
    mask: [page.locator(".sicost-user-anchor, .authentication-session")],
  });
}
async function settle(page: Page) {
  await expect(page.locator(".sicost-screen-host:not([hidden])")).toBeVisible();
  await expect
    .poll(
      () =>
        page
          .locator('.sicost-screen-host:not([hidden]) [aria-busy="true"]')
          .count(),
      { timeout: 30000 },
    )
    .toBe(0);
}

async function checkDraft(page: Page, id: string) {
  const developer = id === "UI-02";
  if (!developer && id !== "UI-15") return;
  const launch = page.getByTestId(
    developer ? "developer-create-button" : "clients-register",
  );
  if (!(await launch.isVisible())) return;
  await launch.click();
  const input = page.getByTestId(
    developer ? "developer-name-input" : "client-name-input",
  );
  await input.fill("입력 유지 검증");
  const help = page.getByRole("button", {
    name: developer ? "관리번호 도움말" : "사업자번호 도움말",
  });
  await help.click();
  await expect(page.getByRole("tooltip")).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(page.getByRole("tooltip")).toHaveCount(0);
  await expect(input).toHaveValue("입력 유지 검증");
  await capture(page, `app-${id}-registration`);
  await page
    .getByTestId(developer ? "developer-form-cancel" : "client-form-back")
    .click();
  const warning = page.getByRole("dialog", {
    name: "저장하지 않은 내용이 있습니다.",
  });
  await expect(warning).toBeVisible();
  await warning.getByRole("button", { name: "계속 작성" }).click();
  await expect(input).toHaveValue("입력 유지 검증");
  await page
    .getByTestId(developer ? "developer-form-cancel" : "client-form-back")
    .click();
  await warning.getByRole("button", { name: "나가기", exact: true }).click();
  await expect(input).toHaveCount(0);
  await expect(launch).toBeVisible();
}

test("시안의 전체 업무 메뉴와 로그아웃 로그인 화면을 실제 클릭으로 연다", async ({
  page,
}) => {
  test.setTimeout(180000);
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto(
    "http://host.docker.internal:19060/si-cost-ui-redesign-v22.html",
    { waitUntil: "domcontentloaded" },
  );
  for (const id of ids) {
    const link = page.locator(`.nav-tree a[data-id="${id}"]`);
    if (!(await link.isVisible()))
      await link
        .locator('xpath=ancestor::div[contains(@class,"nav-g")]')
        .locator('button[data-act="navg"]')
        .click();
    await link.click();
    await expect(page.locator("#page h1").first()).toBeVisible();
    await capture(page, `reference-${id}`);
    if (id !== "UI-16")
      await page.locator(`[data-act="tabclose"][data-id="${id}"]`).click();
  }
  await page.locator('[data-act="umenu"]').click();
  await page.locator('[data-act="logout"]').click();
  await expect(page.locator(".lcard")).toBeVisible();
  // The reference includes demo accounts. Never capture that block.
  await page
    .locator(".lcard .cap")
    .last()
    .evaluate((node) => node.remove());
  await capture(page, "reference-UI-22");
});

test("실제 인증·권한 메뉴·빈 상태·작업 탭·반응형을 보존한다", async ({
  page,
}) => {
  test.setTimeout(300000);
  test.skip(
    !process.env.SICOST_VERIFY_USER || !process.env.SICOST_VERIFY_PASSWORD,
    "전용 검증 계정 환경변수가 필요합니다.",
  );
  const errors: string[] = [];
  const writes: string[] = [];
  page.on("pageerror", (error) => errors.push(error.name));
  page.on("request", (request) => {
    if (
      request.method() === "POST" &&
      /^\/api\/(developers|clients)$/.test(new URL(request.url()).pathname)
    )
      writes.push(new URL(request.url()).pathname);
  });
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto("/UI-22", { waitUntil: "domcontentloaded" });
  await expect(page.getByTestId("authentication-login-form")).toBeVisible();
  await expect(page.locator(".lhero")).toBeVisible();
  await capture(page, "app-UI-22");
  await page
    .locator('input[name="userId"]')
    .fill(process.env.SICOST_VERIFY_USER!);
  await page
    .locator('input[name="password"]')
    .fill(process.env.SICOST_VERIFY_PASSWORD!);
  await page.locator('button[type="submit"]').click();
  await page.waitForURL("**/UI-16");
  await page.locator(".sicost-sidebar").waitFor();
  if (
    await page
      .locator(".sicost-sidebar")
      .evaluate((node) => node.classList.contains("folded"))
  )
    await page.getByRole("button", { name: "메뉴 열기·접기" }).click();
  const allowed = new Set(
    await page
      .locator(".sicost-sidebar a")
      .evaluateAll((nodes) =>
        nodes.map((n) => n.getAttribute("href")?.slice(1)),
      ),
  );
  const unavailable: string[] = [];
  for (const id of ids) {
    if (!allowed.has(id)) {
      unavailable.push(id);
      continue;
    }
    const links = page.locator(`.sicost-sidebar a[href="/${id}"]`);
    const link = links.last();
    if (!(await link.isVisible()))
      await link
        .locator('xpath=ancestor::div[contains(@class,"sicost-nav-group")]')
        .getByRole("button", { name: /하위 메뉴/ })
        .click();
    await link.click();
    await expect(page).toHaveURL(new RegExp(`/${id}(?:[?#].*)?$`));
    await settle(page);
    await expect(
      page.locator(".sicost-screen-host:not([hidden])"),
    ).not.toContainText("이 화면은 다음 단계에서 만듭니다.");
    await capture(page, `app-${id}`);
    await test.step(`${id} 미저장 입력·도움말 확인`, () =>
      checkDraft(page, id));
    if (id !== "UI-16")
      await page
        .locator(".sicost-screen-tabs > div.active")
        .getByRole("button", { name: /탭 닫기/ })
        .click();
  }
  console.log(`권한으로 제외된 화면: ${unavailable.join(", ") || "없음"}`);
  for (const size of [
    { width: 1440, height: 1000 },
    { width: 1280, height: 900 },
    { width: 768, height: 900 },
    { width: 390, height: 900 },
  ]) {
    await page.setViewportSize(size);
    await settle(page);
    await capture(page, `app-UI-16-${size.width}`);
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    ).toBe(true);
  }
  await page.emulateMedia({ reducedMotion: "reduce" });
  await expect(page.locator(".sicost-brand-mark .brand-bar").first()).toHaveCSS(
    "animation-name",
    "none",
  );
  expect(errors).toEqual([]);
  expect(writes).toEqual([]);
});
