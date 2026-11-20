import { test, expect, type Page } from "@playwright/test";
import { mkdir } from "node:fs/promises";
import { join } from "node:path";
import type { StructureData } from "../../frontend/src/pages/structure/profitStructureApi";

test.use({
  viewport: { width: 1440, height: 900 },
  launchOptions: {
    executablePath:
      "/opt/ms-playwright/chromium_headless_shell-1161/chrome-linux/headless_shell",
  },
  trace: "off",
  screenshot: "off",
  video: "off",
});

const screens = [
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

async function settleVisuals(page: Page) {
  await page.evaluate(async () => {
    await document.fonts.ready;
    await Promise.all(
      document
        .getAnimations()
        .filter(
          (animation) =>
            animation.effect?.getComputedTiming().iterations !== Infinity,
        )
        .map((animation) => animation.finished.catch(() => {})),
    );
  });
}

async function login(page: Page) {
  const user = process.env.SICOST_VERIFY_USER,
    password = process.env.SICOST_VERIFY_PASSWORD;
  if (!user || !password)
    throw new Error(
      "검증 계정 환경변수가 필요합니다. 계정값은 출력하지 않습니다.",
    );
  await page.goto("/UI-22");
  await expect(page.locator('input[name="userId"]')).toBeVisible();
  // No credential-bearing locator action is recorded in an error log or trace.
  await page.evaluate(
    ({ user, password }) => {
      const set = Object.getOwnPropertyDescriptor(
        HTMLInputElement.prototype,
        "value",
      )!.set!;
      for (const [name, value] of [
        ["userId", user],
        ["password", password],
      ]) {
        const input = document.querySelector<HTMLInputElement>(
          `input[name="${name}"]`,
        )!;
        set.call(input, value);
        input.dispatchEvent(new Event("input", { bubbles: true }));
      }
    },
    { user, password },
  );
  await page.locator('button[type="submit"]').click();
  await expect(page).toHaveURL(/\/UI-16$/);
  await expect(page.locator(".sicost-sidebar nav")).toBeVisible();
  await expect(
    page.locator(".sicost-screen-host:not([hidden]) .sicost-star"),
  ).toBeEnabled();
  const later = page.getByRole("button", { name: "나중에", exact: true });
  if (await later.isVisible()) await later.click();
}

async function openScreen(page: Page, id: string) {
  const link = page.locator(
    `.sicost-sidebar nav .sicost-nav-children a[href="/${id}"]`,
  );
  if (!(await link.isVisible()))
    await link
      .locator('xpath=ancestor::div[contains(@class,"sicost-nav-group")]')
      .locator(".sicost-nav-parent > button")
      .click();
  await link.click();
  await expect(page).toHaveURL(new RegExp(`/${id}$`));
}

test("UI-22 renders the reference hero, real recovery form and responsive login without demo accounts", async ({
  page,
}) => {
  await page.goto("/UI-22");
  await expect(page.getByTestId("login-form")).toBeVisible();
  await expect(page.locator(".lhero")).toBeVisible();
  await expect(page.locator(".lh-copy")).toContainText("프로젝트 손익을");
  await expect(page.getByText("시안 계정")).toHaveCount(0);
  await page.getByTestId("login-recovery").click();
  await expect(page.getByRole("heading", { name: /비밀번호/ })).toBeVisible();
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.locator(".lhero")).toBeHidden();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
});

for (const id of screens) {
  test(`${id}: real authorized navigation, loading completion and reference-sized capture`, async ({
    page,
  }) => {
    const pageErrors: string[] = [];
    page.on("pageerror", (error) => pageErrors.push(error.message));
    await login(page);
    const link =
      id === "UI-16"
        ? page.locator('.sicost-sidebar nav a[href="/UI-16"]').first()
        : page.locator(
            `.sicost-sidebar nav .sicost-nav-children a[href="/${id}"]`,
          );
    if (!(await link.count())) {
      // A restricted principal must not acquire a menu or private screen through a direct URL.
      await page.goto(`/${id}`);
      await expect(page).toHaveURL(/\/UI-16$/);
      await expect(
        page.locator(
          `.sicost-sidebar nav .sicost-nav-children a[href="/${id}"]`,
        ),
      ).toHaveCount(0);
      return;
    }
    if (!(await link.isVisible()))
      await link
        .locator('xpath=ancestor::div[contains(@class,"sicost-nav-group")]')
        .locator(".sicost-nav-parent > button")
        .click();
    await link.click();
    await expect(page).toHaveURL(new RegExp(`/${id}$`));
    const host = page.locator(".sicost-screen-host:not([hidden])");
    await expect(
      host.locator(`[data-screen-id="SCR-${id}"]`).first(),
    ).toBeVisible();
    await expect(host.locator('[aria-busy="true"]')).toHaveCount(0, {
      timeout: 20000,
    });
    await expect(
      host
        .getByRole("status")
        .filter({ hasText: /불러오는 중|조회 중|확인하는 중/ }),
    ).toHaveCount(0, { timeout: 20000 });
    await expect(page.locator(".sicost-header")).toHaveCSS("height", "64px");
    await expect(page.locator(".sicost-sidebar")).toHaveCSS("width", "248px");
    await expect(host.getByRole("heading", { level: 1 }).first()).toBeVisible();
    if (process.env.TMPDIR) {
      const directory = join(process.env.TMPDIR, "basic149-e2e");
      await mkdir(directory, { recursive: true });
      await settleVisuals(page);
      await page.screenshot({
        path: join(directory, `${id}.png`),
        animations: "disabled",
        mask: [
          page.getByText(process.env.SICOST_VERIFY_USER!, { exact: true }),
        ],
      });
    }
    const tabs = host.locator(".sicost-segmented-tabs button:visible");
    const count = await tabs.count();
    for (let index = 0; index < count; index++) {
      const tab = tabs.nth(index);
      if (await tab.isDisabled()) continue;
      await tab.click();
      await expect(
        host.getByRole("heading", { level: 1 }).first(),
      ).toBeVisible();
      await expect(host.locator('[aria-busy="true"]')).toHaveCount(0, {
        timeout: 20000,
      });
      await expect(
        host
          .getByRole("status")
          .filter({ hasText: /불러오는 중|조회 중|확인하는 중/ }),
      ).toHaveCount(0, { timeout: 20000 });
      await settleVisuals(page);
      if (process.env.TMPDIR)
        await page.screenshot({
          path: join(
            process.env.TMPDIR,
            "basic149-e2e",
            `${id}-tab-${index}.png`,
          ),
          animations: "disabled",
          mask: [
            page.getByText(process.env.SICOST_VERIFY_USER!, { exact: true }),
          ],
        });
    }
    expect(pageErrors).toEqual([]);
  });
}

test("UI-07: server-backed shares, permanent tables and readable methods preserve all period tabs", async ({
  page,
}) => {
  test.setTimeout(120000);
  await login(page);
  const received = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname === "/api/profit-loss/structure" &&
      response.request().method() === "GET",
  );
  await openScreen(page, "UI-07");
  const initial = await received;
  expect(initial.status()).toBe(200);
  let data = (await initial.json()).data as StructureData;
  const screen = page.getByTestId("profit-structure-screen");
  await screen.getByTestId("structure-unit").selectOption("천원");
  for (const [mode, tab, caption] of [
    ["MONTH", "월", "월별"],
    ["QUARTER", "분기", "분기별"],
    ["YTD", "연 누계", "월별 누계"],
  ] as const) {
    if (mode !== "MONTH") {
      const response = page.waitForResponse((response) => {
        const url = new URL(response.url());
        return (
          url.pathname === "/api/profit-loss/structure" &&
          url.searchParams.get("aggregation") === mode
        );
      });
      await screen.getByRole("tab", { name: tab, exact: true }).click();
      const result = await response;
      expect(result.status()).toBe(200);
      data = (await result.json()).data as StructureData;
    }
    expect(data.context.aggregation).toBe(mode);
    await expect(
      screen.getByRole("tab", { name: tab, exact: true }),
    ).toHaveAttribute("aria-selected", "true");
    await expect(
      screen.getByRole("button", { name: "숫자로 보기", exact: true }),
    ).toHaveCount(0);
    await expect(
      screen.getByRole("button", { name: "계산 방법", exact: true }),
    ).toHaveCount(0);
    await expect(
      screen.getByText(`${caption} · 단위 천원`, { exact: true }),
    ).toBeVisible();
    const numeric = screen.getByRole("table", { name: "기간별 손익 숫자표" });
    await expect(numeric).toBeVisible();
    await expect(
      numeric.locator("tbody tr.profit-structure-subtotal"),
    ).toHaveCount(4);
    for (const metric of [
      "sales-amt",
      "gross-prft-amt",
      "oprt-prft-amt",
      "net-prft-amt",
    ]) {
      const row = screen.getByTestId(`structure-numeric-${metric}`);
      await expect(row.getByRole("rowheader")).toHaveCSS("font-weight", "700");
      await expect(row.getByRole("cell").last()).toHaveCSS(
        "font-weight",
        "700",
      );
    }
    for (const label of ["└ 배분 원가", "└ 대기 원가", "└ 직접 경비"])
      await expect(
        numeric.getByRole("rowheader", { name: label, exact: true }),
      ).toBeVisible();
    for (const label of [
      "당기순이익",
      "법인세(월할)",
      "매출총이익률",
      "대기 원가 비중",
    ])
      await expect(
        numeric.getByRole("rowheader", { name: label, exact: true }),
      ).toBeVisible();
    await expect(
      numeric.getByRole("rowheader", {
        name: /^(순이익|월할 세금|총이익률|대기 비중)$/,
      }),
    ).toHaveCount(0);
    const methods = screen.getByRole("table", {
      name: "계산 방법",
      exact: true,
    });
    await expect(methods).toBeVisible();
    await expect(methods.getByRole("columnheader")).toHaveText([
      "항목",
      "계산",
      "참고",
    ]);
    await expect(methods.locator("tbody tr")).toHaveCount(7);
    await expect(methods).not.toContainText(
      /TB_|salesAmt|grossPrftAmt|idleCostShare/,
    );
    for (const method of data.calculationMethods) {
      const row = methods
        .getByRole("rowheader", { name: method.metric, exact: true })
        .locator("..");
      await expect(row.getByRole("cell")).toHaveText([
        method.formula,
        method.sources.join(" "),
      ]);
    }
    const idle = data.summary.find((card) => card.metric === "idleCostAmt")!;
    const idleCard = screen.getByTestId("structure-card-idle-cost-amt");
    const previousLabel =
      mode === "QUARTER"
        ? data.buckets.at(-1)!.monthCount < 3
          ? "전 분기 같은 기간 대비"
          : "전 분기 대비"
        : "전월 대비";
    for (const [comparison, label] of [
      [idle.previous, previousLabel],
      [idle.previousYear, "전년 같은 기간 대비"],
    ] as const) {
      if (!comparison) continue;
      expect(comparison.metric).toBe("idleCostShare");
      expect(comparison.changeRate).toBeNull();
      if (comparison.percentagePointChange !== null)
        await expect(
          idleCard.getByText(`${label} ${comparison.percentagePointChange}%p`, {
            exact: true,
          }),
        ).toHaveClass(
          new RegExp(`\\b${comparison.direction.toLowerCase()}\\b`),
        );
    }
    if (mode === "YTD") expect(idle.previous).toBeNull();
    const last = data.buckets.at(-1)!;
    if (last.composition.visible) {
      await expect(
        screen.getByRole("heading", { name: "고객사 상위 5곳", exact: true }),
      ).toBeVisible();
      const part = screen.getByTestId("structure-part-composition");
      await expect(
        part.getByText("프로젝트를 따온 파트 기준", { exact: true }),
      ).toBeVisible();
      await expect(
        part.getByText(/영업 파트 이익금은 사람 소속 파트 기준/),
      ).toBeVisible();
      for (const group of last.composition.contractTypes)
        await expect(
          screen
            .getByTestId("structure-type-composition")
            .getByText(group.groupName, { exact: true }),
        ).toBeVisible();
    }
  }
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(screen.getByTestId("structure-export")).toBeDisabled();
  await expect(
    screen.getByRole("table", { name: "계산 방법", exact: true }),
  ).toBeVisible();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
});

test("developer full-page registration preserves cancellation and does not issue a create request", async ({
  page,
}) => {
  await login(page);
  await page
    .getByRole("button", { name: "기준정보 하위 메뉴 펼치기", exact: true })
    .click();
  await page.locator('.sicost-nav-children a[href="/UI-02"]').click();
  const creates: string[] = [];
  page.on("request", (request) => {
    if (
      request.method() === "POST" &&
      new URL(request.url()).pathname === "/api/developers"
    )
      creates.push(request.method());
  });
  await page.getByTestId("developer-create-button").click();
  const form = page.getByRole("dialog", { name: "개발자 등록", exact: true });
  await expect(form).toHaveClass(/sicost-dialog-page/);
  await expect(
    form.getByRole("group", { name: "기본 정보", exact: true }),
  ).toBeVisible();
  if (process.env.TMPDIR)
    await page.screenshot({
      path: join(process.env.TMPDIR, "basic149-e2e", "UI-02-form.png"),
      animations: "disabled",
    });
  await page.getByTestId("developer-form-cancel").click();
  await expect(form).toHaveCount(0);
  expect(creates).toEqual([]);
});

const forms = [
  ["UI-15", "clients-register", "client-form", "client-form-cancel"],
  ["UI-01", "project-create", "project-edit-form", "project-form-cancel"],
  ["UI-12", "assignment-new", "assignment-create-form", ""],
  [
    "UI-29",
    "pipeline-create-button",
    "sales-opportunity-form",
    "opportunity-form-cancel",
  ],
  ["UI-33", "candidate-create", "candidate-form", ""],
  ["UI-13", "bill-create", "bill-form-dialog", ""],
  ["UI-23", "business-user-register", "business-user-form", ""],
] as const;

for (const [id, buttonId, formId, cancelId] of forms) {
  test(`${id}: existing registration form opens and cancels without a business write`, async ({
    page,
  }) => {
    await login(page);
    await openScreen(page, id);
    const writes: string[] = [];
    page.on("request", (request) => {
      const path = new URL(request.url()).pathname;
      if (
        !["GET", "HEAD", "OPTIONS"].includes(request.method()) &&
        path.startsWith("/api/") &&
        !path.startsWith("/api/auth/") &&
        !path.includes("/preferences")
      )
        writes.push(`${request.method()} ${path}`);
    });
    const create = page.getByTestId(buttonId);
    await expect(create).toBeEnabled({ timeout: 20000 });
    await create.click();
    const form = page.getByTestId(formId);
    await expect(form).toBeVisible();
    await expect(form.getByText(/불러오는 중|조회 중|확인하는 중/)).toHaveCount(
      0,
      { timeout: 20000 },
    );
    await settleVisuals(page);
    if (id === "UI-29") {
      const name = await form.getByTestId("opportunity-name").boundingBox();
      expect(name!.width).toBeGreaterThan(240);
      expect(name!.x).toBeGreaterThanOrEqual(248);
      expect(name!.x + name!.width).toBeLessThanOrEqual(1440);
    }
    if (process.env.TMPDIR)
      await page.screenshot({
        path: join(process.env.TMPDIR, "basic149-e2e", `${id}-form.png`),
        animations: "disabled",
        mask: [
          page.locator('input[type="password"]'),
          page.getByText(process.env.SICOST_VERIFY_USER!, { exact: true }),
        ],
      });
    if (cancelId) await page.getByTestId(cancelId).click();
    else
      await page
        .getByRole("dialog")
        .last()
        .getByRole("button", { name: "취소", exact: true })
        .click();
    const leave = page.getByRole("dialog", {
      name: "작성 중인 내용을 나가시겠습니까?",
      exact: true,
    });
    if (await leave.isVisible())
      await leave.locator('button[type="submit"]').click();
    await expect(form).toBeHidden();
    expect(writes).toEqual([]);
  });
}

test("mobile menu, reduced motion and collapsed desktop rail preserve navigation", async ({
  page,
}) => {
  await login(page);
  await page.getByTestId("workspace-menu-toggle").click();
  await expect(page.locator(".sicost-sidebar")).toHaveCSS("width", "84px");
  await page.getByTestId("workspace-menu-toggle").click();
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.locator(".sicost-header")).toHaveCSS("height", "56px");
  await expect(page.locator(".sicost-screen-tabs")).toBeHidden();
  await page.emulateMedia({ reducedMotion: "reduce" });
  await page.getByTestId("workspace-menu-toggle").click();
  await expect(page.locator(".sicost-sidebar")).toHaveClass(/mobile-open/);
  await page.keyboard.press("Escape");
  await expect(page.locator(".sicost-sidebar")).not.toHaveClass(/mobile-open/);
  await page.getByTestId("workspace-menu-toggle").click();
  // The overlay center is behind the 288px menu; use its exposed right edge.
  await page
    .getByRole("button", { name: "메뉴 닫기", exact: true })
    .click({ position: { x: 374, y: 80 } });
  await expect(page.locator(".sicost-sidebar")).not.toHaveClass(/mobile-open/);
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
});

test("B7 upload keeps real file validation disabled until a file is selected", async ({
  page,
}) => {
  await login(page);
  await openScreen(page, "UI-15");
  await page.getByTestId("clients-upload").click();
  await expect(page.getByTestId("b7-file-input")).toBeVisible();
  await expect(page.getByTestId("b7-file-input")).toHaveAttribute(
    "type",
    "file",
  );
  const dialog = page.getByRole("dialog").last();
  await expect(dialog.locator('button[type="submit"]')).toBeDisabled();
  if (process.env.TMPDIR)
    await page.screenshot({
      path: join(process.env.TMPDIR, "basic149-e2e", "UI-15-upload.png"),
      animations: "disabled",
    });
  await dialog.getByRole("button", { name: /취소|닫기/ }).click();
  await expect(page.getByTestId("b7-file-input")).toHaveCount(0);
});

test("all authorized screens retain contained layouts at the four review viewports", async ({
  page,
}) => {
  test.setTimeout(240000);
  page.on("dialog", async (dialog) => {
    expect(dialog.type()).toBe("confirm");
    expect(dialog.message()).toContain("화면을 닫고 이동할까요?");
    await dialog.accept();
  });
  await login(page);
  const allowed: string[] = [];
  for (const id of screens)
    if (await page.locator(`.sicost-sidebar nav a[href="/${id}"]`).count())
      allowed.push(id);
  const directory = join(
    process.env.TMPDIR ?? process.cwd(),
    "basic149-e2e-responsive",
  );
  if (process.env.TMPDIR) await mkdir(directory, { recursive: true });
  for (const id of allowed) {
    await page.setViewportSize({ width: 1440, height: 1000 });
    const link = page.locator(`.sicost-sidebar nav a[href="/${id}"]`).first();
    if (id === "UI-16") await link.click();
    else await openScreen(page, id);
    const host = page.locator(".sicost-screen-host:not([hidden])");
    await expect(host.locator('[aria-busy="true"]')).toHaveCount(0, {
      timeout: 20000,
    });
    await expect(
      host
        .getByRole("status")
        .filter({ hasText: /불러오는 중|조회 중|확인하는 중/ }),
    ).toHaveCount(0, { timeout: 20000 });
    for (const [width, height] of [
      [1440, 1000],
      [1280, 900],
      [768, 1024],
      [390, 844],
    ]) {
      await page.setViewportSize({ width, height });
      await settleVisuals(page);
      expect(
        await page.evaluate(
          () => document.documentElement.scrollWidth <= innerWidth,
        ),
        `${id} at ${width}px`,
      ).toBe(true);
      if (process.env.TMPDIR)
        await page.screenshot({
          path: join(directory, `${id}-${width}.png`),
          animations: "disabled",
          mask: [
            page.getByText(process.env.SICOST_VERIFY_USER!, { exact: true }),
          ],
        });
    }
  }
});

test("mobile filters collapse without discarding an unapplied search draft", async ({
  page,
}) => {
  await login(page);
  await openScreen(page, "UI-15");
  await page.setViewportSize({ width: 390, height: 844 });
  const toggle = page.getByTestId("clients-filter-form-toggle"),
    form = page.getByTestId("clients-filter-form");
  await expect(form).toBeHidden();
  await toggle.click();
  await expect(form).toBeVisible();
  const query = form.getByRole("textbox");
  await query.fill("보존할 검색 초안");
  await toggle.click();
  await expect(form).toBeHidden();
  await toggle.click();
  await expect(query).toHaveValue("보존할 검색 초안");
  await expect(toggle).toHaveAttribute("aria-expanded", "true");
  await page.setViewportSize({ width: 1440, height: 900 });
  await openScreen(page, "UI-02");
  await page.setViewportSize({ width: 390, height: 844 });
  await settleVisuals(page);
  const create = await page
    .getByTestId("developer-create-button")
    .boundingBox();
  const condition = await page
    .locator(".sicost-screen-host:not([hidden]) .sicost-filter-toggle")
    .boundingBox();
  expect(condition!.y).toBeGreaterThanOrEqual(create!.y + create!.height);
});

test("pipeline and workforce summaries precede their tabs without changing the active panel lifecycle", async ({
  page,
}) => {
  await login(page);
  for (const [id, summarySelector, tabsLabel] of [
    ["UI-29", ".sales-command-summary", "영업 파이프라인 보기"],
    ["UI-28", ".workforce-calendar-summary", "인력 운영 보기"],
  ]) {
    await openScreen(page, id);
    const host = page.locator(".sicost-screen-host:not([hidden])");
    const summary = host.locator(summarySelector),
      tabs = host.getByRole("tablist", { name: tabsLabel });
    await expect(summary).toBeVisible();
    const summaryBox = await summary.boundingBox(),
      tabsBox = await tabs.boundingBox();
    expect(summaryBox!.y + summaryBox!.height).toBeLessThanOrEqual(tabsBox!.y);
    await tabs.getByRole("tab").nth(1).click();
    await expect(tabs.getByRole("tab").nth(1)).toHaveAttribute(
      "aria-selected",
      "true",
    );
    await tabs.getByRole("tab").first().click();
    await expect(summary).toBeVisible();
  }
});

test("client registration keeps customer and vendor dependent fields, drafts, and a write-free return", async ({
  page,
}) => {
  await login(page);
  await openScreen(page, "UI-15");
  const creates: string[] = [];
  page.on("request", (request) => {
    if (
      request.method() === "POST" &&
      new URL(request.url()).pathname === "/api/clients"
    )
      creates.push(request.method());
  });
  await page.getByTestId("clients-register").click();
  const form = page.getByTestId("client-form");
  await expect(
    form.getByRole("heading", { level: 1, name: "거래처 등록" }),
  ).toBeVisible();
  const type = form.getByLabel("구분", { exact: true });
  await type.selectOption("CUST");
  await expect(form.getByTestId("client-tax-email-input")).toBeVisible();
  await type.selectOption("VNDR");
  await expect(form.getByTestId("client-tax-email-input")).toHaveCount(0);
  await expect(form.getByTestId("client-account-number-input")).toBeVisible();
  await page.getByTestId("client-form-back").click();
  const leave = page.getByRole("dialog", {
    name: "저장하지 않은 내용이 있습니다.",
    exact: true,
  });
  await expect(leave).toBeVisible();
  await leave.getByRole("button", { name: "계속 작성", exact: true }).click();
  await expect(type).toHaveValue("VNDR");
  await page.getByTestId("client-form-back").click();
  await leave.locator('button[type="submit"]').click();
  await expect(form).toHaveCount(0);
  expect(creates).toEqual([]);
  await expect(
    page
      .locator(".sicost-screen-host:not([hidden])")
      .getByRole("heading", { level: 1, name: "거래처·영업 파트" }),
  ).toBeVisible();
});

test("a real user drawer retains its authorized detail tabs and returns focus to its opener", async ({
  page,
}) => {
  await login(page);
  await openScreen(page, "UI-23");
  const opener = page.getByTestId(/^business-user-open-/).first();
  await expect(opener).toBeVisible();
  await opener.click();
  const drawer = page.getByTestId("business-user-drawer");
  await expect(drawer).toBeVisible();
  const tabs = drawer
    .getByRole("tablist", { name: "사용자 상세 탭" })
    .getByRole("tab");
  for (let index = 0; index < (await tabs.count()); index++) {
    await tabs.nth(index).click();
    await expect(tabs.nth(index)).toHaveAttribute("aria-selected", "true");
    await expect(
      drawer.getByRole("status").filter({ hasText: /불러오는 중|조회 중/ }),
    ).toHaveCount(0, { timeout: 20000 });
    if (process.env.TMPDIR)
      await page.screenshot({
        path: join(
          process.env.TMPDIR,
          "basic149-e2e",
          `UI-23-detail-${index}.png`,
        ),
        animations: "disabled",
        mask: [
          page.getByText(process.env.SICOST_VERIFY_USER!, { exact: true }),
        ],
      });
  }
  await page.getByTestId("business-user-drawer-close").click();
  await expect(drawer).toHaveCount(0);
  await expect(opener).toBeFocused();
});

test("header AI conversation is modeless, closes with Escape and restores its trigger on desktop and mobile", async ({
  page,
}) => {
  await login(page);
  const trigger = page.getByTestId("header-analysis-open");
  for (const [width, height, top] of [
    [1440, 900, "64px"],
    [390, 844, "56px"],
  ] as const) {
    await page.setViewportSize({ width, height });
    await trigger.click();
    const panel = page.getByTestId("header-analysis-panel");
    await expect(panel).toBeVisible();
    await expect(panel).toHaveCSS("top", top);
    await expect(page.getByTestId("header-analysis-close")).toBeFocused();
    await expect(panel.getByRole("textbox")).toBeVisible();
    await page.keyboard.press("Escape");
    await expect(panel).toHaveCount(0);
    await expect(trigger).toBeFocused();
  }
});
