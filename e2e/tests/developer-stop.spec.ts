import { expect, test } from "@playwright/test";

// Runner-provided sample data; this test does not change developer status.
test("개발자 상세에서 사용중지 전 실제 투입을 확인한다", async ({ page }) => {
  await page.goto("/UI-02");
  await expect(page.getByTestId("developers-screen")).toBeVisible();
  const search = page.getByTestId("developer-search-input");
  await search.fill("[예시] 김정상");
  await page.getByTestId("developer-search-button").click();
  await expect(page.getByTestId("developer-name-dev-900001")).toBeVisible();
  await page.getByTestId("developer-name-dev-900001").click();
  const drawer = page.getByTestId("developer-drawer");
  await expect(drawer).toBeVisible();
  const assignments = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname ===
        "/api/developers/DEV-900001/assignments" &&
      response.request().method() === "GET",
  );
  await drawer.getByTestId("developer-status-n").click();
  expect((await assignments).status()).toBe(200);
  const warning = page.getByRole("dialog", {
    name: "사용을 중지할 수 없습니다",
  });
  await expect(warning).toContainText("[예시] 김정상");
  await expect(warning).toContainText("[예시] 유통 운영개발 ~2027-02-28");
  await expect(warning.getByRole("button")).toHaveCount(1);
  await warning.getByRole("button", { name: "확인" }).click();
  await expect(warning).not.toBeVisible();
});
