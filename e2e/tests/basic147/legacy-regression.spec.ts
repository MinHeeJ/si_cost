import { expect, test } from "../support/authenticatedTest";

// A route smoke matrix supplements (does not replace) the unchanged calculation,
// close, settlement and cancellation assertions in the pre-existing browser suites.
for (const screen of [
  "UI-01",
  "UI-02",
  "UI-03",
  "UI-04",
  "UI-05",
  "UI-07",
  "UI-08",
  "UI-09",
  "UI-10",
  "UI-11",
  "UI-12",
  "UI-13",
  "UI-14",
  "UI-15",
  "UI-16",
  "UI-17",
  "UI-18",
  "UI-19",
  "UI-21",
  "UI-23",
  "UI-24",
  "UI-25",
  "UI-26",
  "UI-27",
  "UI-28",
  "UI-29",
  "UI-30",
  "UI-31",
  "UI-32",
  "UI-33",
]) {
  test(`기존 구현 화면의 실제 인증·API 연결 회귀: ${screen}`, async ({
    page,
  }) => {
    const response = await page.request.get("/api/current-user/capabilities");
    expect(response.status()).toBe(200);
    const capabilities = (await response.json()).data;
    test.skip(
      !capabilities.screens[screen]?.includes("INQ"),
      "저장된 관리자 메뉴 설정이 허용하지 않아 이 화면은 미검증입니다. 권한을 덮어쓰지 않습니다.",
    );
    const errors: string[] = [],
      failures: Array<{ path: string; status: number }> = [];
    page.on("pageerror", (error) => errors.push(error.name));
    page.on("response", (response) => {
      const url = new URL(response.url());
      if (url.pathname.startsWith("/api/") && response.status() >= 500)
        failures.push({ path: url.pathname, status: response.status() });
    });
    await page.goto(`/${screen}`);
    await expect(page).toHaveURL(new RegExp(`/${screen}$`));
    await expect(
      page.locator(".sicost-screen-host:not([hidden])"),
    ).toBeVisible();
    await expect(
      page.getByRole("heading", { name: "로그인", exact: true }),
    ).toHaveCount(0);
    await expect(
      page.locator(".sicost-screen-host:not([hidden])"),
    ).not.toContainText(/임시 관리자|개발 모드|항상 허용/);
    await page.waitForTimeout(300);
    expect(errors).toEqual([]);
    expect(failures).toEqual([]);
    const current = await page.request.get("/api/current-user");
    expect(current.status()).toBe(200);
    const user = (await current.json()).data;
    expect(user.developmentMode).toBe(false);
    expect(user.accessEnforcement).toBe(true);
  });
}
