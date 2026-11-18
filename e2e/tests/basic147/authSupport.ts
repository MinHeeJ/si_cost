import { expect, type Page, type APIRequestContext } from "@playwright/test";

export type Account = {
  userId: string;
  password: string;
  email: string;
  activeRoles: string[];
  all: boolean;
  sensitive: boolean;
  roleId?: string;
  partIds?: string[];
  projectIds?: string[];
};
export function fixtures(): Record<string, Account> {
  if (process.env.BASIC147_E2E_DISPOSABLE !== "true")
    throw new Error("명시적으로 격리한 합성 검증 앱만 사용할 수 있습니다.");
  const raw = process.env.BASIC147_E2E_ACCOUNTS_JSON;
  if (!raw)
    throw new Error("BASIC147_E2E_ACCOUNTS_JSON 합성 계정 구성이 필요합니다.");
  let value: Record<string, Account>;
  try {
    value = JSON.parse(raw);
  } catch {
    throw new Error("합성 계정 구성 형식이 올바르지 않습니다.");
  }
  for (const account of Object.values(value))
    if (
      !account.userId ||
      !account.password ||
      !account.email ||
      !Array.isArray(account.activeRoles)
    )
      throw new Error("합성 계정 구성의 필수 필드가 없습니다.");
  return value;
}
export function account(role: string): Account {
  const found = fixtures()[role];
  if (!found) throw new Error(`합성 ${role} 계정이 필요합니다.`);
  return found;
}
export function targets(): {
  developerId: string;
  developerName: string;
  projectId: string;
  partId: string;
  candidateId: string;
  month: string;
  sessionMinutes: number;
} {
  try {
    return JSON.parse(process.env.BASIC147_E2E_TARGETS_JSON ?? "");
  } catch {
    throw new Error("합성 업무 대상 구성이 필요합니다.");
  }
}
async function receivedCode(email: string, after: number): Promise<string> {
  const collector = process.env.BASIC147_MAIL_COLLECTOR;
  if (!collector) throw new Error("실제 SMTP 수집 서버가 필요합니다.");
  for (let attempt = 0; attempt < 30; attempt++) {
    const response = await fetch(`${collector}/api/v2/messages`);
    if (!response.ok) throw new Error("SMTP 수집 서버를 조회하지 못했습니다.");
    const value = (await response.json()) as {
      items?: Array<{
        Created: string;
        Content: { Headers: Record<string, string[]>; Body: string };
      }>;
    };
    for (const item of value.items ?? [])
      if (
        Date.parse(item.Created) >= after &&
        (item.Content.Headers.To ?? []).some((to) => to.includes(email))
      ) {
        const body =
          (
            item.Content.Headers["Content-Transfer-Encoding"]?.[0] ?? ""
          ).toLowerCase() === "base64"
            ? Buffer.from(item.Content.Body, "base64").toString("utf8")
            : item.Content.Body.replace(/=\r?\n/g, "");
        const found = body.match(/(?:인증번호[^0-9]*)?\b([0-9]{6})\b/);
        if (found) return found[1];
      }
    await new Promise((resolve) => setTimeout(resolve, 200));
  }
  throw new Error("실제로 수집한 인증 메일을 확인하지 못했습니다.");
}
export async function loginUi(page: Page, fixture: Account) {
  await page.goto("/UI-22");
  await page.getByLabel("아이디", { exact: true }).fill(fixture.userId);
  await page
    .getByLabel("비밀번호", { exact: true })
    .evaluate((element, value) => {
      Object.getOwnPropertyDescriptor(
        HTMLInputElement.prototype,
        "value",
      )!.set!.call(element, value);
      element.dispatchEvent(new Event("input", { bubbles: true }));
    }, fixture.password);
  const sentAt = Date.now();
  const result = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname === "/api/auth/login" &&
      response.request().method() === "POST",
  );
  await page.getByRole("button", { name: "로그인", exact: true }).click();
  const response = await result;
  expect(response.status()).toBe(200);
  let stage = (await response.json()).data.nextStep;
  if (stage === "MFA") {
    await page.getByLabel("인증번호", { exact: true }).evaluate(
      (element, value) => {
        Object.getOwnPropertyDescriptor(
          HTMLInputElement.prototype,
          "value",
        )!.set!.call(element, value);
        element.dispatchEvent(new Event("input", { bubbles: true }));
      },
      await receivedCode(fixture.email, sentAt),
    );
    const verified = page.waitForResponse(
      (response) => new URL(response.url()).pathname === "/api/auth/mfa/verify",
    );
    await page.getByRole("button", { name: "인증 확인", exact: true }).click();
    const response = await verified;
    expect(response.status()).toBe(200);
    stage = (await response.json()).data.nextStep;
  }
  expect(stage, "합성 계정은 비밀번호 변경을 완료한 상태여야 합니다.").toBe(
    "AUTHENTICATED",
  );
  const state = await page.request.get("/api/auth/session");
  expect((await state.json()).data.nextStep).toBe("AUTHENTICATED");
}
export async function csrf(request: APIRequestContext) {
  const result = await request.get("/api/auth/csrf");
  expect(result.status()).toBe(200);
  return (await result.json()).data as { headerName: string; token: string };
}
export async function role(request: APIRequestContext, id: string) {
  if (!id.startsWith("fx147"))
    throw new Error("기존 역할은 수정할 수 없습니다. 합성 역할이 필요합니다.");
  const result = await request.get(`/api/roles/${encodeURIComponent(id)}`);
  expect(result.status()).toBe(200);
  const value = (await result.json()).data;
  expect(value.systemRole).toBe(false);
  return value;
}
export async function saveRole(
  request: APIRequestContext,
  id: string,
  original: Awaited<ReturnType<typeof role>>,
  permissions: typeof original.menuPermissions,
) {
  const current = await role(request, id),
    token = await csrf(request);
  const result = await request.put(`/api/roles/${encodeURIComponent(id)}`, {
    headers: { [token.headerName]: token.token },
    data: {
      dataScope: original.dataScope,
      sensitiveView: original.sensitiveView,
      menuPermissions: permissions.map(
        (p: {
          menuId: string;
          inquire: boolean;
          register: boolean;
          update: boolean;
          delete: boolean;
          upload: boolean;
          download: boolean;
          confirm: boolean;
        }) => ({
          menuId: p.menuId,
          inquire: p.inquire,
          register: p.register,
          update: p.update,
          delete: p.delete,
          upload: p.upload,
          download: p.download,
          confirm: p.confirm,
        }),
      ),
      expectedRevision: current.revision,
    },
  });
  expect(result.status()).toBe(200);
  const persisted = await role(request, id);
  for (const expected of permissions) {
    const actual = persisted.menuPermissions.find(
      (p: { menuId: string }) => p.menuId === expected.menuId,
    );
    for (const flag of [
      "inquire",
      "register",
      "update",
      "delete",
      "upload",
      "download",
      "confirm",
    ])
      expect(actual[flag]).toBe(expected[flag]);
  }
}
export async function user(request: APIRequestContext, id: string) {
  if (!id.startsWith("fx147"))
    throw new Error("기존 계정은 수정할 수 없습니다. 합성 계정이 필요합니다.");
  const response = await request.get(
    `/api/business-users/${encodeURIComponent(id)}`,
  );
  expect(response.status()).toBe(200);
  return (await response.json()).data;
}
export async function saveUserRoles(
  request: APIRequestContext,
  id: string,
  roleIds: string[],
) {
  const current = await user(request, id),
    token = await csrf(request);
  const response = await request.put(
    `/api/business-users/${encodeURIComponent(id)}`,
    {
      headers: { [token.headerName]: token.token },
      data: {
        name: current.name,
        email: current.email,
        roleIds,
        headOffice: current.headOffice,
        expectedRevision: current.revision,
      },
    },
  );
  expect(response.status()).toBe(200);
  const persisted = await user(request, id);
  expect([...persisted.roleIds].sort()).toEqual([...roleIds].sort());
}
