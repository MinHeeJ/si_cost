import { test, expect, type Page } from "@playwright/test";
import { spawnSync } from "node:child_process";

// Real session/capabilities/menu APIs only. Never record credentials/cookies in artifacts.
test.use({ trace: "off", screenshot: "off", video: "off" });
const cards = [
  "sales",
  "operating-profit",
  "ytd-operating-profit",
  "utilization",
  "receivable",
];
const internalText =
  /Receivables|CorporateTax|CashFlow|WorkforceForecast|SalesPipeline|MonthClose|OperatingProfit|\/api\/|\b(?:BILL_MISS|SETL|BILL|ALL|REAL_PAY_AMT|VAT_RT)\b|·\s*·/;

async function login(page: Page, prefix = "SICOST_VERIFY") {
  const user = process.env[`${prefix}_USER`],
    password = process.env[`${prefix}_PASSWORD`];
  if (!user || !password)
    throw new Error(
      `${prefix} 계정 환경변수가 필요합니다. 값은 기록하지 않습니다.`,
    );
  await page.goto("/UI-22");
  await expect(page.getByTestId("authentication-login-form")).toBeVisible();
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
  const result = page.waitForResponse(
    (r) =>
      new URL(r.url()).pathname === "/api/auth/login" &&
      r.request().method() === "POST",
  );
  await page.getByRole("button", { name: "로그인", exact: true }).click();
  const response = await result;
  expect(response.status(), "실제 로그인 API 성공").toBe(200);
  // Use a verifier-provisioned account with initial password change/MFA complete.
  expect(
    (await response.json()).data.nextStep,
    "시험 계정의 추가 인증 준비 필요",
  ).toBe("AUTHENTICATED");
  await expect(page.getByTestId("authentication-content")).toBeVisible();
  const later = page.getByTestId(
    "authentication-password-recommendation-later-button",
  );
  if (await later.isVisible()) await later.click();
  const responseCaps = await page.request.get("/api/current-user/capabilities");
  expect(responseCaps.status()).toBe(200);
  return (await responseCaps.json()).data as {
    screens: Record<string, string[]>;
    pcOnlyScreens: string[];
  };
}
async function dashboard(page: Page) {
  const result = page.waitForResponse(
    (r) =>
      new URL(r.url()).pathname === "/api/dashboard" &&
      r.request().method() === "GET",
  );
  await page.goto("/UI-16");
  const response = await result;
  expect(response.status()).toBe(200);
  const data = (await response.json()).data;
  await expect(page.getByTestId("dashboard-screen")).toHaveAttribute(
    "aria-busy",
    "false",
  );
  await expect(
    page.getByRole("combobox", { name: "기간", exact: true }),
  ).toBeEnabled();
  return data;
}
async function cleanDashboard(page: Page) {
  const root = page.getByTestId("dashboard-screen");
  // Names are business data, not system text. Exclude only registered-name nodes.
  const text = await root.evaluate((element) => {
    const copy = element.cloneNode(true) as HTMLElement;
    copy
      .querySelectorAll(
        '[data-testid^="dashboard-alert-"] strong, [data-testid^="dashboard-opportunity-"] strong, [data-testid^="dashboard-part-"] td:first-child',
      )
      .forEach((node) => node.remove());
    return copy.textContent ?? "";
  });
  expect(text).not.toMatch(internalText);
  expect(
    await root
      .locator("[title]")
      .evaluateAll((nodes) =>
        nodes.map((node) => node.getAttribute("title")).join("\n"),
      ),
  ).not.toMatch(internalText);
  await page.getByTestId("dashboard-cash").scrollIntoViewIfNeeded();
  await page.getByTestId("dashboard-basis-help").click();
  await expect(page.getByRole("tooltip")).not.toContainText(internalText);
  await page.keyboard.press("Escape");
  const chart = page.locator(
    '[data-testid="dashboard-trend"] .sicost-chart-host',
  );
  await chart.scrollIntoViewIfNeeded();
  const box = await chart.boundingBox();
  expect(box).not.toBeNull();
  await page.mouse.move(box!.x + box!.width * 0.45, box!.y + box!.height * 0.5);
  const chartTooltip = chart.locator("div:has(>strong)");
  await expect(chartTooltip).toBeVisible();
  await expect(chartTooltip).toContainText("회사 전체 매출목표");
  await expect(chartTooltip).not.toContainText(internalText);
}
function pdfPages(pdf: Buffer): string[] {
  const result = spawnSync(
    "python",
    [
      "-c",
      "import sys,json,io; from pypdf import PdfReader; print(json.dumps([p.extract_text() for p in PdfReader(io.BytesIO(sys.stdin.buffer.read())).pages]))",
    ],
    { input: pdf },
  );
  if (result.status !== 0)
    throw new Error(
      "출력물 검증에 Python pypdf가 필요합니다. PDF 생성만으로 통과 처리하지 않습니다.",
    );
  return JSON.parse(result.stdout.toString());
}

test("UI-16: 두 조회월의 현재 카드·기준·단위·상태를 그대로 인쇄하고 전체 문구를 확인한다", async ({
  page,
}) => {
  test.setTimeout(120_000);
  await login(page);
  let data = await dashboard(page);
  const period = page.getByRole("combobox", { name: "기간", exact: true });
  const months = await period
    .locator("option")
    .evaluateAll((nodes) =>
      nodes.map((node) => (node as HTMLOptionElement).value),
    );
  expect(months.length, "다른 조회월도 준비되어야 합니다").toBeGreaterThan(1);
  const otherMonth = months.find((month) => month !== data.selectedMonth)!;
  for (const index of [0, 1]) {
    if (index === 1) {
      const response = page.waitForResponse(
        (r) =>
          new URL(r.url()).pathname === "/api/dashboard" &&
          new URL(r.url()).searchParams.get("month") === otherMonth,
      );
      await period.selectOption(otherMonth);
      data = (await (await response).json()).data;
      await expect(page.getByTestId("dashboard-screen")).toHaveAttribute(
        "aria-busy",
        "false",
      );
    }
    expect(data.metrics).toHaveLength(5);
    await cleanDashboard(page);
    const shown = await Promise.all(
      cards.map((id) =>
        page.getByTestId(`dashboard-metric-${id}`).textContent(),
      ),
    );
    const dates = await page
      .getByRole("group", { name: "조회 기간 상태" })
      .textContent();
    let extraReads = 0;
    const countReads = (request: import("@playwright/test").Request) => {
      if (new URL(request.url()).pathname === "/api/dashboard") extraReads++;
    };
    page.on("request", countReads);
    await page.emulateMedia({ media: "print" });
    for (const [cardIndex, id] of cards.entries()) {
      const card = page.getByTestId(`dashboard-metric-${id}`);
      await expect(card).toBeVisible();
      expect(await card.textContent()).toBe(shown[cardIndex]);
      expect(
        await card.evaluate((node) => getComputedStyle(node).breakInside),
      ).toBe("avoid");
      await expect(card.locator(".dashboard-basis-period")).toBeVisible();
    }
    await expect(page.getByTestId("dashboard-export")).toBeHidden();
    await expect(page.getByTestId("dashboard-close-open")).toBeHidden();
    await expect(period).toBeHidden();
    await expect(page.locator(".sicost-sidebar")).toBeHidden();
    const pdf = await page.pdf({
      format: "A4",
      printBackground: true,
      margin: { top: "10mm", bottom: "10mm", left: "10mm", right: "10mm" },
    });
    expect(pdf.length).toBeGreaterThan(0);
    const pages = pdfPages(pdf).map((text) => text.replace(/\s/g, ""));
    const labels = ["월매출", "월영업이익", "누계영업이익", "가동률", "미수금"];
    expect(
      pages.some((text) => labels.every((label) => text.includes(label))),
      "다섯 카드가 같은 출력 페이지에 존재",
    ).toBe(true);
    const output = pages.join("");
    for (const id of cards) {
      const value = await page
        .getByTestId(`dashboard-metric-${id}`)
        .locator("strong")
        .textContent();
      expect(output).toContain(value!.replace(/\s/g, ""));
    }
    expect(output).toContain(data.selectedMonth);
    expect(output).toContain(data.today);
    const businessNames: string[] = [
      ...(data.projectAlerts ?? []).map(
        (row: { projectName: string }) => row.projectName,
      ),
      ...data.pipeline.startingSoon.map(
        (row: { opportunityName: string }) => row.opportunityName,
      ),
      ...(data.salesParts ?? []).map(
        (row: { partName: string }) => row.partName,
      ),
    ];
    const systemOutput = businessNames.reduce(
      (text, name) => text.split(name.replace(/\s/g, "")).join(""),
      output,
    );
    expect(systemOutput).not.toMatch(internalText);
    // Validate the real PDF in memory only; never persist business content.
    expect(
      await page.getByRole("group", { name: "조회 기간 상태" }).textContent(),
    ).toBe(dates);
    expect(extraReads, "인쇄는 대시보드를 다시 조회하지 않는다").toBe(0);
    page.off("request", countReads);
    await page.emulateMedia({ media: "screen" });
    console.log(
      `UI-16 / 1440px / ${data.selectedMonth}: 카드 5개·표시값·단위·기준·상태 동일, 추가 조회 0, PDF 생성 통과`,
    );
  }
});

for (const [id, name] of [
  ["UI-23", "사용자"],
  ["UI-27", "공통코드"],
]) {
  for (const width of [390, 767, 768]) {
    test(`${id}: ${width}px 직접 방문은 실제 인증·메뉴 설정을 따른다`, async ({
      page,
    }) => {
      const caps = await login(page);
      for (const screen of ["UI-16", id])
        expect(caps.screens[screen]).toContain("INQ");
      expect(caps.pcOnlyScreens).toContain(id);
      // Establish the same principal can open the desktop business screen first.
      await page.goto(`/${id}`);
      await expect(
        page.locator(
          `.sicost-screen-host:not([hidden]) > .sicost-screen[data-screen-id="SCR-${id}"]`,
        ),
      ).toBeVisible();
      await page.setViewportSize({ width, height: 844 });
      await page.goto(`/${id}`);
      if (width === 768) {
        await expect(page.getByTestId("pc-only-notice")).toHaveCount(0);
        await expect(
          page.locator(
            `.sicost-screen-host:not([hidden]) > .sicost-screen[data-screen-id="SCR-${id}"]`,
          ),
        ).toBeVisible();
      } else {
        const notice = page.getByTestId("pc-only-notice");
        await expect(notice).toContainText(`「${name}」`);
        await expect(page).toHaveURL(new RegExp(`/${id}$`));
        await expect(page.locator(`[data-screen-id="SCR-${id}"]`)).toHaveCount(
          0,
        );
        await expect(
          page.locator(`.sicost-sidebar a[href="/${id}"]`),
        ).toHaveCount(0);
        await page.getByTestId("pc-only-dashboard-button").click();
        await expect(page).toHaveURL(/\/UI-16$/);
        await expect(page.getByTestId("dashboard-screen")).toHaveAttribute(
          "aria-busy",
          "false",
        );
      }
      console.log(
        `${id} / ${width}px: 직접 방문·안내 유지·본문 제한·클릭 이동 통과`,
      );
    });
  }
}

test("UI-16: 요약 카드 5개와 업무 버튼은 기존 대상 화면으로 이동한다", async ({
  page,
}) => {
  test.setTimeout(120_000);
  await login(page);
  for (const id of cards) {
    const data = await dashboard(page);
    const metric = data.metrics.find(
      (item: { metricCode: string }) =>
        item.metricCode.toLowerCase().replaceAll("_", "-") === id,
    );
    expect(metric).toBeTruthy();
    await page.getByTestId(`dashboard-metric-${id}`).click();
    await expect(page).toHaveURL(
      new RegExp(`/${metric.link.screenId}(?:\\?|$)`),
    );
    await expect(
      page.locator(
        `.sicost-screen-host:not([hidden]) > .sicost-screen[data-screen-id="SCR-${metric.link.screenId}"]`,
      ),
    ).toBeVisible();
  }
  await dashboard(page);
  await page.getByTestId("dashboard-close-open").click();
  await expect(page).toHaveURL(/\/UI-14$/);
  await expect(
    page.locator(
      '.sicost-screen-host:not([hidden]) > .sicost-screen[data-screen-id="SCR-UI-14"]',
    ),
  ).toBeVisible();
  await dashboard(page);
  await page.getByTestId("dashboard-pipeline-open").click();
  await expect(page).toHaveURL(/\/UI-29$/);
  await expect(
    page.locator(
      '.sicost-screen-host:not([hidden]) > .sicost-screen[data-screen-id="SCR-UI-29"]',
    ),
  ).toBeVisible();
});

test("모바일: 조회 권한 없는 별도 계정은 PC 안내가 아닌 기존 차단 흐름을 따른다", async ({
  page,
}) => {
  test.skip(
    !process.env.SICOST_RESTRICTED_VERIFY_USER ||
      !process.env.SICOST_RESTRICTED_VERIFY_PASSWORD,
    "조회 권한 없는 별도 검증 계정 미제공",
  );
  const caps = await login(page, "SICOST_RESTRICTED_VERIFY");
  await page.setViewportSize({ width: 390, height: 844 });
  for (const id of ["UI-23", "UI-27"]) {
    expect(caps.screens[id]?.includes("INQ") ?? false).toBe(false);
    await page.goto(`/${id}`);
    await expect(
      page.getByText("이 화면을 볼 권한이 없습니다.", { exact: true }),
    ).toBeVisible();
    await expect(page.getByTestId("pc-only-notice")).toHaveCount(0);
    await expect(page.locator(`[data-screen-id="SCR-${id}"]`)).toHaveCount(0);
  }
});

test("모바일: 대시보드 권한이 없으면 안내 버튼은 기존 첫 화면으로 이동한다", async ({
  page,
}) => {
  test.skip(
    !process.env.SICOST_NO_DASHBOARD_VERIFY_USER ||
      !process.env.SICOST_NO_DASHBOARD_VERIFY_PASSWORD,
    "PC 화면 권한만 가진 별도 검증 계정 미제공",
  );
  const caps = await login(page, "SICOST_NO_DASHBOARD_VERIFY");
  expect(caps.screens["UI-16"]?.includes("INQ") ?? false).toBe(false);
  const target = ["UI-23", "UI-27"].find((id) =>
    caps.screens[id]?.includes("INQ"),
  );
  expect(target).toBeTruthy();
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto(`/${target}`);
  await page.getByTestId("pc-only-dashboard-button").click();
  const first = Object.keys(caps.screens).find(
    (id) => !caps.pcOnlyScreens.includes(id),
  );
  if (first) await expect(page).toHaveURL(new RegExp(`/${first}$`));
  else
    await expect(page.getByTestId("authentication-pc-required")).toBeVisible();
});

test("모바일: 미인증 직접 주소는 기존 로그인 흐름을 따른다", async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/UI-27");
  await expect(page.getByTestId("authentication-login-form")).toBeVisible();
  await expect(page.getByTestId("pc-only-notice")).toHaveCount(0);
});
