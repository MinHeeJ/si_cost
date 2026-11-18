import { test, expect } from "@playwright/test";

// No credential entry or authentication bypass: the actual unauthenticated boundary is exercised.
for (const viewport of [
  { width: 1440, height: 1000 },
  { width: 1280, height: 900 },
  { width: 768, height: 1000 },
  { width: 390, height: 844 },
]) {
  test(`UI-22 login fits ${viewport.width}px and password visibility does not submit`, async ({
    page,
  }, testInfo) => {
    await page.setViewportSize(viewport);
    const loginRequests: string[] = [];
    page.on("request", (request) => {
      if (new URL(request.url()).pathname === "/api/auth/login")
        loginRequests.push(request.method());
    });
    await page.goto("/UI-22");
    await expect(
      page.getByRole("heading", { name: "로그인", exact: true }),
    ).toBeVisible();
    const password = page.getByLabel("비밀번호", { exact: true });
    await expect(page.getByLabel("아이디", { exact: true })).toBeVisible();
    await expect(password).toHaveAttribute("type", "password");
    await page.getByTestId("login-password-toggle").click();
    await expect(password).toHaveAttribute("type", "text");
    await page.getByTestId("login-password-toggle").click();
    await expect(password).toHaveAttribute("type", "password");
    expect(loginRequests).toEqual([]);
    expect(
      await page.evaluate(
        () =>
          document.documentElement.scrollWidth >
          document.documentElement.clientWidth,
      ),
    ).toBe(false);
    expect(
      await page
        .getByTestId("login-submit")
        .evaluate((element) => element.getBoundingClientRect().height),
    ).toBeGreaterThanOrEqual(44);
    await page.screenshot({
      path: testInfo.outputPath(`login-${viewport.width}.png`),
      fullPage: true,
    });
  });
}
