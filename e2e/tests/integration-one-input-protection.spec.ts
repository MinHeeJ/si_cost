import { test, expect } from "@playwright/test";
import { account, loginUi } from "./basic147/authSupport";

// Run only against the runner's isolated DB-backed application. No intercepted APIs,
// persisted sessions, screenshots, or credentials are written by these checks.
test.use({ trace: "off", screenshot: "off", video: "off" });
test.beforeEach(async ({ page }) => {
  await loginUi(page, account("ADMIN"));
});

test("UI-33 취소 확인 후 계속 작성하면 후보 입력이 유지된다", async ({
  page,
}) => {
  await page.goto("/UI-33");
  await expect(page.getByTestId("candidates-screen")).toBeVisible();
  await page.getByTestId("candidate-create").click();
  const form = page.getByRole("dialog", {
    name: "외부 인력 후보 등록",
    exact: true,
  });
  const name = form.getByLabel("이름 *", { exact: true });
  await name.fill("미저장 입력 확인");
  await form.getByRole("button", { name: "취소", exact: true }).click();
  const warning = page.getByRole("dialog", {
    name: "저장하지 않은 내용이 있습니다",
    exact: true,
  });
  await expect(warning).toContainText(
    "저장하지 않은 내용이 있습니다. 나가면 입력한 내용이 사라집니다.",
  );
  await warning.getByRole("button", { name: "계속 작성", exact: true }).click();
  await expect(name).toHaveValue("미저장 입력 확인");
  await form.getByRole("button", { name: "취소", exact: true }).click();
  await warning.getByRole("button", { name: "나가기", exact: true }).click();
  await expect(form).not.toBeVisible();
});

test("UI-29 숫자를 즉시 정제하고 미저장 화면 탭을 표시한다", async ({
  page,
}) => {
  await page.goto("/UI-29");
  await page.getByTestId("pipeline-create-button").click();
  const form = page.getByTestId("sales-opportunity-form");
  await expect(form.getByTestId("opportunity-form-save")).toBeEnabled();
  await form.getByTestId("opportunity-amount").fill("12a,3b4");
  await form.getByTestId("opportunity-probability").fill("5x0");
  await form.getByLabel("1번째 줄 월MM", { exact: true }).fill("0.a5");
  await expect(form.getByTestId("opportunity-amount")).toHaveValue("12,34");
  await expect(form.getByTestId("opportunity-probability")).toHaveValue("50");
  await expect(form.getByLabel("1번째 줄 월MM", { exact: true })).toHaveValue(
    "0.5",
  );
  await expect(
    page
      .getByRole("navigation", { name: "열린 화면" })
      .getByRole("button", { name: /^● / }),
  ).toBeVisible();
  await form.getByTestId("opportunity-form-back").click();
  await page
    .getByRole("dialog", { name: "저장하지 않은 내용이 있습니다", exact: true })
    .getByRole("button", { name: "계속 작성", exact: true })
    .click();
  await expect(form.getByTestId("opportunity-amount")).toHaveValue("12,34");
});
