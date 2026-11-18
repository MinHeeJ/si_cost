import { expect, test } from "@playwright/test";
import { readFile } from "node:fs/promises";
import { createHash } from "node:crypto";
import {
  account,
  csrf,
  fixtures,
  loginUi,
  role,
  saveRole,
  saveUserRoles,
  targets,
  user,
} from "./authSupport";

// Credentials/OTP must never enter screenshots, traces, videos, or persisted storage state.
test.use({ trace: "off", screenshot: "off", video: "off" });
for (const key of [
  "ADMIN",
  "EXEC",
  "FIN",
  "SALES_LDR",
  "PM",
  "PM_FIN",
  "COPIED",
  "STOPPED_ROLE",
  "MULTI_PART",
  "HQ",
  "PROJ",
  "NO_SCOPE",
]) {
  test(`실제 로그인과 활성 역할·범위 합집합: ${key}`, async ({ page }) => {
    const fixture = account(key);
    await loginUi(page, fixture);
    const current = await page.request.get("/api/current-user");
    expect(current.status()).toBe(200);
    const value = (await current.json()).data;
    expect(value.userId).toBe(fixture.userId);
    expect([...value.roleIds].sort()).toEqual([...fixture.activeRoles].sort());
    const response = await page.request.get("/api/current-user/capabilities");
    expect(response.status()).toBe(200);
    const capabilities = (await response.json()).data;
    expect(capabilities.scope.all).toBe(fixture.all);
    expect(capabilities.sensitive).toBe(fixture.sensitive);
    expect([...capabilities.scope.partIds].sort()).toEqual(
      [...(fixture.partIds ?? [])].sort(),
    );
    expect([...capabilities.scope.projectIds].sort()).toEqual(
      [...(fixture.projectIds ?? [])].sort(),
    );
    const forged = await page.request.get("/api/current-user", {
      headers: {
        "X-User-Id": "SYSTEM",
        "X-Role-Id": "ADMIN",
        "X-Data-Scope": "ALL",
      },
    });
    expect(forged.status()).toBe(200);
    expect((await forged.json()).data.userId).toBe(fixture.userId);
    expect(
      await page.evaluate(() => document.cookie.includes("JSESSIONID")),
    ).toBe(false);
    expect(
      await page.evaluate(() =>
        Object.keys(localStorage).some((key) =>
          /token|sessionid|password|jwt/i.test(key),
        ),
      ),
    ).toBe(false);
    await expect(
      page.getByRole("button", { name: /역할 전환|관리자로 전환/ }),
    ).toHaveCount(0);
    if (key === "NO_SCOPE") {
      const projects = await page.request.get("/api/projects");
      expect(projects.status()).toBe(200);
      expect((await projects.json()).data.items).toHaveLength(0);
    }
  });
}

test("금지 필드 변조는 400이고 기존 자료는 변하지 않는다", async ({
  page,
  browser,
}) => {
  const fixture = account("SALES_LDR"),
    target = targets();
  await loginUi(page, fixture);
  const adminContext = await browser.newContext({
    baseURL: process.env.SICOST_E2E_BASE_URL,
  });
  const admin = await adminContext.newPage();
  try {
    await loginUi(admin, account("ADMIN"));
    const path = `/api/developers/${encodeURIComponent(target.developerId)}`;
    const before = await admin.request.get(path);
    expect(before.status()).toBe(200);
    const original = (await before.json()).data;
    const token = await csrf(page.request);
    const denied = await page.request.put(path, {
      headers: { [token.headerName]: token.token },
      data: { monthlyPay: "87654321", expectedRevision: original.revision },
    });
    expect(denied.status()).toBe(400);
    expect((await denied.json()).code).toBe("FIELD_NOT_EDITABLE");
    const after = await admin.request.get(path);
    expect(after.status()).toBe(200);
    expect((await after.json()).data.revision).toBe(original.revision);
    const safe = await page.request.get(path);
    expect(safe.status()).toBe(200);
    expect(Object.keys((await safe.json()).data)).not.toContain("monthlyPay");
  } finally {
    await adminContext.close();
  }
});

test("철회 응답은 원요청을 재전송하지 않고 열린 폼·초안·캐시를 제거한다", async ({
  page,
  browser,
}) => {
  const fixture = account("REVOKABLE"),
    target = targets();
  if (!fixture.roleId) throw new Error("합성 철회 역할이 필요합니다.");
  expect(fixture.activeRoles).toEqual([fixture.roleId]);
  await loginUi(page, fixture);
  const context = await browser.newContext({
    baseURL: process.env.SICOST_E2E_BASE_URL,
  });
  const admin = await context.newPage();
  let original: Awaited<ReturnType<typeof role>> | undefined;
  try {
    await loginUi(admin, account("ADMIN"));
    original = await role(admin.request, fixture.roleId);
    await page.goto("/UI-02");
    await page.getByText(target.developerName, { exact: true }).first().click();
    await page.getByTestId("developer-edit-button").click();
    const dialog = page.getByRole("dialog", {
      name: "개발자 수정",
      exact: true,
    });
    await expect(dialog).toBeVisible();
    await dialog
      .locator("input:not([readonly])")
      .first()
      .fill("합성_폐기할_초안");
    const revoked = original.menuPermissions.map(
      (p: Record<string, unknown>) =>
        p.screenId === "UI-02"
          ? {
              ...p,
              inquire: false,
              register: false,
              update: false,
              delete: false,
              upload: false,
              download: false,
              confirm: false,
            }
          : p,
    );
    await saveRole(admin.request, fixture.roleId, original, revoked);
    let writes = 0;
    page.on("request", (request) => {
      if (
        request.method() === "PUT" &&
        new URL(request.url()).pathname ===
          `/api/developers/${target.developerId}`
      )
        writes++;
    });
    const rejected = page.waitForResponse(
      (response) =>
        response.request().method() === "PUT" &&
        new URL(response.url()).pathname ===
          `/api/developers/${target.developerId}`,
    );
    await dialog.getByTestId("developer-form-save").click();
    expect((await rejected).status()).toBe(403);
    await expect(dialog).toHaveCount(0);
    await expect(page.locator('input[value="합성_폐기할_초안"]')).toHaveCount(
      0,
    );
    await page.waitForTimeout(500);
    expect(writes).toBe(1);
    const persisted = await admin.request.get(
      `/api/developers/${encodeURIComponent(target.developerId)}`,
    );
    expect(persisted.status()).toBe(200);
    expect((await persisted.json()).data.name).toBe(target.developerName);
  } finally {
    if (original)
      await saveRole(
        admin.request,
        fixture.roleId,
        original,
        original.menuPermissions,
      );
    await context.close();
  }
});

test("이미 로그인한 사용자에게 ADMIN 추가 시 실제 MFA 전 업무를 차단한다", async ({
  page,
  browser,
}) => {
  const fixture = account("MUTABLE");
  await loginUi(page, fixture);
  const context = await browser.newContext({
    baseURL: process.env.SICOST_E2E_BASE_URL,
  });
  const admin = await context.newPage();
  let original: string[] | undefined;
  try {
    await loginUi(admin, account("ADMIN"));
    original = (await user(admin.request, fixture.userId)).roleIds;
    expect(original).not.toContain("ADMIN");
    await saveUserRoles(admin.request, fixture.userId, [...original!, "ADMIN"]);
    const denied = await page.request.get("/api/current-user");
    expect(denied.status()).toBe(401);
    expect((await denied.json()).code).toBe("AUTH_INCOMPLETE");
    await page.reload();
    await expect(
      page.getByRole("heading", { name: "이메일 인증", exact: true }),
    ).toBeVisible();
    await expect(page.locator(".sicost-screen-host")).toHaveCount(0);
  } finally {
    if (original) await saveUserRoles(admin.request, fixture.userId, original);
    await context.close();
  }
});

for (const [key, screen] of [
  ["SALES_LDR", "UI-19"],
  ["SALES_LDR", "UI-32"],
  ["PM", "UI-31"],
  ["PM", "UI-33"],
]) {
  test(`허용 예외의 화면·응답에서 금지 제목과 원천 값을 제외한다: ${screen}`, async ({
    page,
  }) => {
    const errors: string[] = [];
    page.on("pageerror", () => errors.push("pageerror"));
    await loginUi(page, account(key));
    const loaded = page.waitForResponse((response) => {
      if (response.request().method() !== "GET") return false;
      const path = new URL(response.url()).pathname;
      return screen === "UI-19"
        ? /^\/api\/sales-part-settlements\/[^/]+$/.test(path)
        : screen === "UI-32"
          ? path === "/api/rate-renegotiations"
          : screen === "UI-33"
            ? path === "/api/candidates"
            : path === "/api/project-status-board";
    });
    await page.goto(`/${screen}`);
    const response = await loaded;
    expect(response.status()).toBe(200);
    const value = (await response.json()).data;
    if (screen !== "UI-31") expect(value.items.length).toBeGreaterThan(0);
    if (screen === "UI-31") {
      const project = targets().projectId;
      const progress = page.waitForResponse(
        (response) =>
          response.request().method() === "GET" &&
          new URL(response.url()).pathname ===
            `/api/projects/${project}/progress`,
      );
      await page.getByTestId(`status-project-${project.toLowerCase()}`).click();
      const detail = await progress;
      expect(detail.status()).toBe(200);
      const status = (await detail.json()).data.status;
      expect(Object.keys(status)).toEqual(
        expect.arrayContaining(["costUsage", "costProgressGap"]),
      );
      for (const field of ["costAmount", "profitAmount", "budgetAmount"])
        expect(Object.keys(status)).not.toContain(field);
    }
    await page.waitForLoadState("networkidle");
    const host = page.locator(".sicost-screen-host:not([hidden])");
    await expect(host).toBeVisible();
    await expect(page.locator(".sicost-header")).toBeVisible();
    expect(errors).toEqual([]);
    await expect(host).not.toContainText(
      /월 급여|공통관리비|노동비|개인별 원가|법인카드/,
    );
    if (screen === "UI-33") await expect(host).not.toContainText("희망 단가");
    if (screen === "UI-19")
      await expect(
        host.getByRole("columnheader", { name: /원가|비용 계|이익금/ }),
      ).toHaveCount(0);
  });
}

test("89일은 권고하지 않고 90일은 서버 권고를 표시한다", async ({
  page,
  browser,
}) => {
  await loginUi(page, account("AGE89"));
  await expect(
    page.getByRole("dialog", { name: "비밀번호 변경 권장" }),
  ).toHaveCount(0);
  const context = await browser.newContext({
    baseURL: process.env.SICOST_E2E_BASE_URL,
  });
  const other = await context.newPage();
  try {
    await loginUi(other, account("AGE90"));
    await expect(
      other.getByRole("dialog", { name: "비밀번호 변경 권장" }),
    ).toBeVisible();
  } finally {
    await context.close();
  }
});

test("업로드 검사 후 자격 철회는 전량 반영을 거부하고 급여 자료를 유지한다", async ({
  page,
  browser,
}) => {
  const file = process.env.BASIC147_E2E_B1_FILE;
  if (!file) throw new Error("합성 B.1 XLSX 파일이 필요합니다.");
  const fixture = account("UPLOAD"),
    target = targets();
  await loginUi(page, fixture);
  const context = await browser.newContext({
    baseURL: process.env.SICOST_E2E_BASE_URL,
  });
  const admin = await context.newPage();
  let original: string[] | undefined;
  try {
    await loginUi(admin, account("ADMIN"));
    original = (await user(admin.request, fixture.userId)).roleIds;
    const before = await admin.request.get(
      `/api/payroll?month=${encodeURIComponent(target.month)}&page=1&size=100`,
    );
    expect(before.status()).toBe(200);
    const digest = (value: unknown) =>
      createHash("sha256").update(JSON.stringify(value)).digest("hex");
    const originalRows = digest((await before.json()).data.items);
    const bytes = await readFile(file),
      token = await csrf(page.request);
    const checked = await page.request.post("/api/excel/B1/validate", {
      headers: { [token.headerName]: token.token },
      multipart: {
        month: target.month,
        file: {
          name: "synthetic-payroll.xlsx",
          mimeType:
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
          buffer: bytes,
        },
      },
    });
    expect(checked.status()).toBe(200);
    const validation = (await checked.json()).data;
    expect(validation.summary.errorRows).toBe(0);
    await saveUserRoles(
      admin.request,
      fixture.userId,
      account("DENIED").activeRoles,
    );
    const fresh = await csrf(page.request);
    const denied = await page.request.post("/api/excel/B1/apply", {
      headers: { [fresh.headerName]: fresh.token },
      multipart: {
        month: target.month,
        uploadId: String(validation.uploadId),
        expectedRevision: validation.revision,
        confirmFullReplacement: "true",
        file: {
          name: "synthetic-payroll.xlsx",
          mimeType:
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
          buffer: bytes,
        },
      },
    });
    expect(denied.status()).toBe(403);
    const after = await admin.request.get(
      `/api/payroll?month=${encodeURIComponent(target.month)}&page=1&size=100`,
    );
    expect(after.status()).toBe(200);
    expect(digest((await after.json()).data.items)).toBe(originalRows);
  } finally {
    if (original) await saveUserRoles(admin.request, fixture.userId, original);
    await context.close();
  }
});

test("조회 전용 화면의 버튼·단축키가 쓰기를 실행하지 않는다", async ({
  page,
}) => {
  await loginUi(page, account("READ_ONLY"));
  const capability = await page.request.get("/api/current-user/capabilities");
  expect(capability.status()).toBe(200);
  expect((await capability.json()).data.screens["UI-02"]).toEqual(["INQ"]);
  await page.goto("/UI-02");
  await expect(page.getByText(/수정 권한이 없어 보기만/)).toBeVisible();
  await expect(page.getByTestId("developer-edit-button")).toHaveCount(0);
  let writes = 0;
  page.on("request", (request) => {
    if (
      ["POST", "PUT", "PATCH", "DELETE"].includes(request.method()) &&
      new URL(request.url()).pathname.startsWith("/api/developers")
    )
      writes++;
  });
  await page.keyboard.press("Control+s");
  await page.waitForTimeout(300);
  expect(writes).toBe(0);
  await expect(
    page.getByRole("dialog", { name: /개발자 등록|개발자 수정/ }),
  ).toHaveCount(0);
});

test("실제 AI 비동기 작업 대기 중 철회하면 결과 패널과 재조회가 차단된다", async ({
  page,
  browser,
}) => {
  test.skip(
    process.env.BASIC147_E2E_ASYNC_GATE_READY !== "true",
    "실제 지연 AI 원천이 없어 비동기 통합은 미검증입니다.",
  );
  const fixture = account("ASYNC"),
    target = targets();
  await loginUi(page, fixture);
  const context = await browser.newContext({
    baseURL: process.env.SICOST_E2E_BASE_URL,
  });
  const admin = await context.newPage();
  let original: string[] | undefined;
  try {
    await loginUi(admin, account("ADMIN"));
    original = (await user(admin.request, fixture.userId)).roleIds;
    const before = await page.request.get("/api/analysis/questions?limit=50");
    expect(before.status()).toBe(200);
    const previous = (await before.json()).data.items.map(
      (item: { qnaId: string }) => item.qnaId,
    );
    await page
      .getByRole("button", { name: "AI에게 묻기", exact: true })
      .click();
    const panel = page.locator(".analysis-side-panel");
    await panel
      .locator("textarea")
      .fill(
        `${target.projectId} 프로젝트의 ${target.month} 매출을 분석해 주세요.`,
      );
    const submitted = page.waitForResponse(
      (response) =>
        response.request().method() === "POST" &&
        new URL(response.url()).pathname === "/api/analysis/questions",
    );
    await panel
      .getByRole("button", { name: /질문|보내기/ })
      .last()
      .click();
    const stream = await submitted;
    expect(stream.status()).toBe(200);
    await saveUserRoles(
      admin.request,
      fixture.userId,
      account("DENIED").activeRoles,
    );
    await stream.finished();
    const body = await stream.text();
    expect(/event:\s*(final|error)\b/.test(body)).toBe(true);
    expect(/"persisted"\s*:\s*true/.test(body)).toBe(false);
    await page.reload();
    await expect(page.locator(".analysis-side-panel")).toHaveCount(0);
    const denied = await page.request.get("/api/analysis/questions");
    expect(denied.status()).toBe(403);
    await saveUserRoles(admin.request, fixture.userId, original!);
    const after = await page.request.get("/api/analysis/questions?limit=50");
    expect(after.status()).toBe(200);
    expect(
      (await after.json()).data.items.map(
        (item: { qnaId: string }) => item.qnaId,
      ),
    ).toEqual(previous);
  } finally {
    if (original) await saveUserRoles(admin.request, fixture.userId, original);
    await context.close();
  }
});

test("두 탭의 자동 폴링은 실제 미사용 만료를 연장하지 않는다", async ({
  page,
  context,
}) => {
  test.setTimeout(180000);
  const target = targets();
  expect(target.sessionMinutes).toBe(1);
  await loginUi(page, account("IDLE"));
  const second = await context.newPage();
  await second.goto(page.url());
  const response = await page.request.get("/api/auth/session");
  const state = (await response.json()).data;
  const remaining = Date.parse(state.expiresAt) - Date.parse(state.serverTime);
  expect(remaining).toBeGreaterThan(0);
  expect(remaining).toBeLessThanOrEqual(60000);
  await page.waitForTimeout(remaining + 1500);
  const expired = await page.request.get("/api/current-user");
  expect(expired.status()).toBe(401);
  // The common client, not an injected role or cookie replacement, clears both actual tabs.
  await page.reload();
  await expect(
    page.getByRole("heading", { name: "로그인", exact: true }),
  ).toBeVisible();
  await second.reload();
  await expect(
    second.getByRole("heading", { name: "로그인", exact: true }),
  ).toBeVisible();
  expect(await page.locator(".sicost-screen-host").count()).toBe(0);
  expect(await second.locator(".sicost-screen-host").count()).toBe(0);
});
