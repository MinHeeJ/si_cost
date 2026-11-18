import { test, expect, type Page } from "./support/authenticatedTest";

type Menu = {
  screenId: string | null;
  name: string;
  pcOnly: boolean;
  children: Menu[];
};
function screens(items: Menu[]): Menu[] {
  return items.flatMap((item) => [
    ...(item.screenId ? [item] : []),
    ...screens(item.children),
  ]);
}
async function menus(page: Page) {
  const response = await page.request.get("/api/menus");
  expect(response.status()).toBe(200);
  const envelope = await response.json();
  expect(envelope.success).toBe(true);
  return screens(envelope.data.items);
}

for (const viewport of [
  { width: 1440, height: 1000 },
  { width: 1280, height: 900 },
  { width: 768, height: 1000 },
  { width: 390, height: 844 },
]) {
  test(`authorized menu screens retain real content at ${viewport.width}px`, async ({
    page,
  }, testInfo) => {
    test.setTimeout(180_000);
    await page.setViewportSize(viewport);
    const available = await menus(page);
    expect(new Set(available.map((item) => item.screenId)).size).toBe(30);
    for (const item of available.filter(
      (item) => viewport.width >= 768 || !item.pcOnly,
    )) {
      await page.goto(`/${item.screenId}`);
      const root = page.getByTestId(`screen-${item.screenId!.toLowerCase()}`);
      await expect(root).toBeVisible();
      await expect(
        root.getByRole("heading", { name: item.name, exact: true }).first(),
      ).toBeVisible();
      await expect(root).not.toContainText("이 화면은 다음 단계에서 만듭니다.");
      await expect(page.locator(".sicost-screen-tabs")).toBeVisible({
        visible: viewport.width >= 768,
      });
      const overflow = await page.evaluate(
        () =>
          document.documentElement.scrollWidth >
          document.documentElement.clientWidth,
      );
      expect(overflow, `${item.screenId} document overflow`).toBe(false);
      await page.screenshot({
        path: testInfo.outputPath(`${item.screenId}-${viewport.width}.png`),
        fullPage: true,
      });
    }
  });
}

test("developer full-page form keeps drafts, permission-backed actions and nested-dialog focus", async ({
  page,
}) => {
  await page.goto("/UI-02");
  await page.getByTestId("developer-create-button").click();
  const form = page.getByTestId("developer-form-page");
  await expect(form).toBeVisible();
  await form.getByLabel("이름", { exact: false }).fill("UI 회귀 확인");
  await form.getByTestId("developer-form-cancel").click();
  const confirmation = page.getByRole("dialog", {
    name: "저장하지 않은 내용이 있습니다.",
  });
  await expect(confirmation).toBeVisible();
  await confirmation.getByRole("button", { name: "계속 작성" }).click();
  await expect(form.getByLabel("이름", { exact: false })).toHaveValue(
    "UI 회귀 확인",
  );
  await expect(form.getByTestId("developer-form-cancel")).toBeFocused();
  await form.getByTestId("developer-form-cancel").click();
  await confirmation.getByRole("button", { name: "나가기" }).click();
  await expect(form).not.toBeVisible();
});

test("reduced motion leaves the logo and menu search immediately usable", async ({
  page,
}) => {
  await page.emulateMedia({ reducedMotion: "reduce" });
  await page.goto("/UI-16");
  await expect(page.getByTestId("application-workspace")).toBeVisible();
  expect(
    await page
      .locator(".sicost-brand-mark path")
      .evaluate((element) => getComputedStyle(element).animationName),
  ).toBe("none");
  await page.keyboard.press("Control+k");
  const search = page.getByRole("dialog", { name: "메뉴 찾기" });
  await expect(search).toBeVisible();
  await search.getByLabel("화면명·번호").fill("UI-02");
  await search.getByRole("button", { name: "이동", exact: true }).click();
  await expect(page.getByTestId("developers-screen")).toBeVisible();
});
