import { randomUUID } from "node:crypto";
import {
  expect,
  test,
  type APIRequestContext,
  type APIResponse,
  type Page,
} from "@playwright/test";
import type { CostPage } from "../../frontend/src/pages/apiops/projectAnalysisApi";
import type {
  Assignments,
  Causes,
  Detail,
  Months,
  SimulationResult,
} from "../../frontend/src/pages/costanalysis/projectCostApi";
import type {
  ProgressHistoryPage,
  ProgressMutation,
  ProjectProgress,
  StepPreview,
} from "../../frontend/src/pages/progress/projectProgressApi";
import type { PeriodContext } from "../../frontend/src/pages/shared/sharedApi";
import { formatAmount } from "../../frontend/src/shared/format";

// Run against the runner's disposable database, never production. No intercepted APIs or seed-row updates.
// The write scenario creates its own project. Progress has no delete API: the runner disposes of the database.
async function data<T>(response: APIResponse): Promise<T> {
  expect(response.status(), response.url()).toBe(200);
  const body = await response.json();
  expect(body.success).toBe(true);
  expect(body.meta).toEqual(expect.any(Object));
  return body.data as T;
}
function response(page: Page, path: string, method = "GET") {
  return page.waitForResponse(
    (r) =>
      new URL(r.url()).pathname === path && r.request().method() === method,
  );
}
async function costProject(request: APIRequestContext, simulation = false) {
  const list = await data<CostPage>(
    await request.get("/api/project-costs?size=100"),
  );
  expect(
    list.items.length,
    "FX-US2/FX-US4 실제 원가 프로젝트가 필요합니다.",
  ).toBeGreaterThan(0);
  for (const row of list.items) {
    if (row.contractType === "IN") continue;
    const detail = await data<Detail>(
      await request.get(
        `/api/projects/${encodeURIComponent(row.projectId)}/cost-analysis`,
        { params: { month: list.month } },
      ),
    );
    if (!simulation || detail.simulationDefaults.enabled) return detail;
  }
  throw new Error(
    "FX-US4: 첫 100개 원가 프로젝트에 계산 가능한 마진 상세가 없습니다.",
  );
}
function costRoute(detail: Detail, tab: string) {
  return `/UI-04?${new URLSearchParams({ month: detail.month, projectId: detail.projectId, detailProjectId: detail.projectId, costTab: tab })}`;
}

test("원가 상세의 월별·실제 투입·원인 API와 화면 바인딩을 보존한다", async ({
  page,
  request,
}) => {
  const detail = await costProject(request);
  const base = `/api/projects/${encodeURIComponent(detail.projectId)}/cost-analysis`;
  const pendingMonths = response(page, `${base}/months`);
  await page.goto(costRoute(detail, "months"));
  const monthsResponse = await pendingMonths;
  expect(monthsResponse.status()).toBe(200);
  const months = (await monthsResponse.json()).data as Months;
  const table = page.getByTestId("cost-months");
  await expect(table).toBeVisible();
  await expect(page.getByTestId("cost-metrics")).toContainText(detail.month);
  for (const month of months.items)
    await expect(table).toContainText(month.month);
  await page
    .getByTestId("project-cost-detail")
    .getByRole("button", { name: "닫기", exact: true })
    .click();
  await page.getByTestId("project-cost-unit").selectOption("원");
  await expect(page.getByTestId("project-cost-unit")).toHaveValue("원");
  const preferences = await data<{items: {type: string; key: string; value: unknown}[]}>(await request.get("/api/me/preferences"));
  expect(preferences.items.find(item=>item.type==="AMT_UNIT"&&item.key==="*")?.value).toBe("원");
  await page
    .getByTestId(`project-cost-open-${detail.projectId.toLowerCase()}`)
    .click();
  await expect(page.getByTestId("cost-metrics")).toContainText(
    "누적 원가 (원)",
  );
  await expect(page.getByTestId("cost-metrics")).toContainText(
    formatAmount(detail.row.cumulativeCost, "원"),
  );

  const pendingAssignments = response(page, `${base}/assignments`);
  await page.getByTestId("cost-tab-assignments").click();
  const assignmentsResponse = await pendingAssignments;
  expect(assignmentsResponse.status()).toBe(200);
  const assignments = (await assignmentsResponse.json()).data as Assignments;
  const people = page.getByTestId("cost-assignments");
  await expect(people).toBeVisible();
  for (const assignment of assignments.items) {
    await expect(people).toContainText(assignment.assignmentId);
    await expect(people).toContainText(assignment.personId);
  }

  const pendingCauses = response(page, `${base}/causes`);
  await page.getByTestId("cost-tab-causes").click();
  const causesResponse = await pendingCauses;
  expect(causesResponse.status()).toBe(200);
  const causes = (await causesResponse.json()).data as Causes;
  await expect(page.getByTestId("cost-panel-causes")).toContainText(
    causes.message,
  );
  for (const cause of causes.items)
    await expect(page.getByTestId("cost-causes")).toContainText(cause.formula);
  await expect(page).toHaveURL(/costTab=causes/);
  const pendingReload = response(page, `${base}/causes`);
  await page.reload();
  expect((await pendingReload).status()).toBe(200);
  await expect(page.getByTestId("cost-panel-causes")).toContainText(
    causes.message,
  );
  await page.getByRole("button", { name: "닫기", exact: true }).click();
  await expect(page.getByTestId("project-cost-detail")).toHaveCount(0);
  expect(new URL(page.url()).searchParams.has("costTab")).toBe(false);
  await page.reload();
  await expect(page.getByTestId("project-cost-analysis")).toBeVisible();
  await expect(page.getByTestId("project-cost-detail")).toHaveCount(0);
});

test("가상 계산은 실제 서버 결과를 표시하며 프로젝트와 원가 상세를 저장하지 않는다", async ({
  page,
  request,
}) => {
  const detail = await costProject(request, true);
  const base = `/api/projects/${encodeURIComponent(detail.projectId)}/cost-analysis`;
  const before = await data<Detail>(
    await request.get(base, { params: { month: detail.month } }),
  );
  await page.goto(costRoute(detail, "simulation"));
  await expect(page.getByTestId("cost-simulation-submit")).toBeEnabled();
  await page.getByTestId("cost-simulation-n").fill("0");
  await page.getByTestId("cost-simulation-c").fill("0");
  await page.getByTestId("cost-simulation-m").fill("1");
  if (detail.row.contractType !== "TK")
    await page.getByTestId("cost-simulation-r").fill("0");
  const pending = response(page, `${base}/simulation`, "POST");
  await page.getByTestId("cost-simulation-submit").click();
  const resultResponse = await pending;
  expect(resultResponse.status()).toBe(200);
  const result = (await resultResponse.json()).data as SimulationResult;
  expect(result.saved).toBe(false);
  expect(result.afterMargin).toBe(result.beforeMargin);
  expect(result.differencePp).toBe("0.0");
  await expect(page.getByTestId("cost-simulation-result")).toContainText(
    `${result.afterMargin}%`,
  );
  await expect(page.getByTestId("cost-simulation-result")).toContainText(
    "저장되지 않음",
  );
  const payload = resultResponse.request().postDataJSON();
  expect(payload).toEqual({
    month: detail.month,
    n: 0,
    c: "0",
    m: 1,
    ...(detail.row.contractType === "TK" ? {} : { r: "0" }),
  });
  expect(
    await data<Detail>(
      await request.get(base, { params: { month: detail.month } }),
    ),
  ).toEqual(before);
});

test("독립 프로젝트에서 단계 초기화·미리보기·저장·완료와 메모 신규·수정·감사를 영속화한다", async ({
  page,
  request,
}) => {
  test.setTimeout(90000);
  const context = await data<PeriodContext>(
    await request.get("/api/period-context"),
  );
  const year = parseInt(context.currentDate.slice(0, 4), 10);
  const name = `진행 E2E ${randomUUID()}`;
  const created = await data<{ project: { id: string } }>(
    await request.post("/api/projects", {
      data: {
        year,
        name,
        contractType: "IN",
        initialStatus: "ING",
        startDate: context.currentDate,
        endDate: `${year + 1}-12-31`,
        teamExpenseAmount: "0",
        projectExpenseAmount: "0",
      },
    }),
  );
  const id = created.project.id;
  const projectPath = `/api/projects/${encodeURIComponent(id)}`;
  const base = `${projectPath}/progress`;
  const beforeProject = await data<{ project: unknown }>(
    await request.get(projectPath),
  );
  const initial = await data<ProjectProgress>(
    await request.get(base, { params: { year } }),
  );
  expect(initial.readOnly).toBe(false);
  expect(initial.steps).toHaveLength(7);
  const initialized = await data<ProgressMutation>(
    await request.post(`${base}/steps/initialize`, {
      data: { year, expectedRevision: initial.progressRevision },
    }),
  );
  expect(initialized.changed).toBe(false);
  expect(initialized.progress.steps).toEqual(initial.steps);

  await page.goto(
    `/UI-01?${new URLSearchParams({ projectId: id, tab: "progress" })}`,
  );
  await expect(page.getByTestId("project-progress-panel")).toBeVisible();
  await expect(page.getByTestId("progress-memo-new")).toBeEnabled();
  await page.getByTestId("progress-dates").click();
  const first = initial.steps[0];
  const boundary = new Date(`${first.endDate}T00:00:00Z`);
  boundary.setUTCDate(boundary.getUTCDate() + 1);
  const endDate = boundary.toISOString().slice(0, 10);
  await page.getByTestId(`progress-end-date-${first.stepSn}`).fill(endDate);
  const pendingPreview = response(page, `${base}/steps/preview`, "POST");
  await page.getByTestId("progress-dates-preview").click();
  const previewResponse = await pendingPreview;
  expect(previewResponse.status()).toBe(200);
  const preview = (await previewResponse.json()).data as StepPreview;
  expect(preview.steps[0].endDate).toBe(endDate);
  expect(
    (await data<ProjectProgress>(await request.get(base, { params: { year } })))
      .steps,
  ).toEqual(initial.steps);
  const pendingDates = response(page, `${base}/steps`, "PUT");
  await page
    .getByRole("dialog", { name: "단계 계획 입력" })
    .getByRole("button", { name: "전체 저장" })
    .click();
  const datesResponse = await pendingDates;
  expect(datesResponse.status()).toBe(200);
  const dates = (await datesResponse.json()).data as ProgressMutation;
  expect(dates.changed).toBe(true);
  expect(
    dates.progress.steps.map((s) => [s.stepSn, s.startDate, s.endDate]),
  ).toEqual(preview.steps.map((s) => [s.stepSn, s.startDate, s.endDate]));
  await expect(page.getByTestId(`progress-step-${first.stepSn}`)).toContainText(
    endDate,
  );

  await page.getByTestId(`progress-complete-${first.stepSn}`).click();
  const pendingComplete = response(
    page,
    `${base}/steps/${first.stepSn}/complete`,
    "POST",
  );
  await page
    .getByRole("dialog", { name: "단계 완료 확인" })
    .getByRole("button", { name: "완료", exact: true })
    .click();
  const completeResponse = await pendingComplete;
  expect(completeResponse.status()).toBe(200);
  const completed = (await completeResponse.json()).data as ProgressMutation;
  expect(completed.progress.steps[0].completedDate).toBe(context.currentDate);
  expect(completed.progress.status.actualProgress).toBe(
    initial.status.actualProgress,
  );
  expect(completed.progress.status.lastUpdatedDate).toBeNull();
  await expect(
    page.getByTestId(`progress-complete-${first.stepSn}`),
  ).toHaveCount(0);

  await page.getByTestId("progress-memo-new").click();
  const draft = `독립 메모 ${randomUUID()}`;
  await page.getByTestId("progress-memo-this-week").fill(draft);
  await page.getByTestId("progress-memo-actual").fill("0");
  const pendingMemo = response(page, `${base}/week-memos`, "POST");
  await page
    .getByRole("dialog", { name: "이번 주 상태 적기" })
    .getByRole("button", { name: "저장", exact: true })
    .click();
  const memoResponse = await pendingMemo;
  expect(memoResponse.status()).toBe(200);
  const saved = (await memoResponse.json()).data as ProgressMutation;
  const memo = saved.memo!;
  expect(memo.writtenDate).toBe(context.currentDate);
  expect(memo.actualProgress).toBe(0);
  await expect(page.getByTestId(`progress-memo-${memo.memoSn}`)).toContainText(
    draft,
  );
  const stale = await request.post(`${base}/week-memos`, {
    data: {
      year,
      expectedRevision: completed.progressRevision,
      thisWeek: draft,
    },
  });
  expect(stale.status()).toBe(409);
  expect((await stale.json()).code).toBe("STALE_WRITE");

  await page.getByTestId(`progress-memo-edit-${memo.memoSn}`).click();
  await page.getByTestId("progress-memo-this-week").fill(`${draft} 수정`);
  await page.getByTestId("progress-memo-actual").fill("25");
  const pendingEdit = response(
    page,
    `${base}/week-memos/${memo.memoSn}`,
    "PUT",
  );
  await page
    .getByRole("dialog", { name: "주간 메모 수정" })
    .getByRole("button", { name: "저장", exact: true })
    .click();
  const editResponse = await pendingEdit;
  expect(editResponse.status()).toBe(200);
  const edited = (await editResponse.json()).data as ProgressMutation;
  expect(edited.memo!.writtenDate).toBe(memo.writtenDate);
  expect(edited.memo!.registeredBy).toEqual(memo.registeredBy);
  expect(edited.memo!.registeredAt).toBe(memo.registeredAt);
  expect(edited.progress.status.actualProgress).toBe("25.0");
  await expect(page.getByTestId(`progress-memo-${memo.memoSn}`)).toContainText(
    `${draft} 수정`,
  );

  const pendingHistory = response(page, `${base}/history`);
  await page.getByTestId("progress-history").click();
  const historyResponse = await pendingHistory;
  expect(historyResponse.status()).toBe(200);
  const history = (await historyResponse.json()).data as ProgressHistoryPage;
  // Initialization alone writes 28 field audits; inspect every page, without assuming
  // that all later events fit the current UI page or that the host clock is monotonic.
  const historyItems = [...history.items];
  for (let next = 2; (next - 1) * history.size < history.total; next++) {
    const more = await data<ProgressHistoryPage>(await request.get(`${base}/history`, {params: {page: next, size: history.size}}));
    historyItems.push(...more.items);
  }
  expect(
    historyItems.some(
      (h) =>
        h.targetKey === `${id}:${first.stepSn}` && h.itemName === "CMPL_DT",
    ),
  ).toBe(true);
  expect(
    historyItems.some(
      (h) =>
        h.targetKey === `${id}:${memo.memoSn}` &&
        h.itemName === "THIS_WEEK_CN" &&
        h.after === `${draft} 수정`,
    ),
  ).toBe(true);
  await page.getByTestId("progress-history-table").selectOption("TB_PJ_WEEK_MEMO");
  await expect(page.getByTestId("progress-history-panel")).toContainText(
    "THIS_WEEK_CN",
  );
  await page.reload();
  await expect(page.getByTestId(`progress-memo-${memo.memoSn}`)).toContainText(
    `${draft} 수정`,
  );
  const persisted = await data<ProjectProgress>(
    await request.get(base, { params: { year } }),
  );
  expect(persisted.memos).toHaveLength(1);
  expect(persisted.status.actualProgress).toBe("25.0");
  expect(persisted.steps[0].completedDate).toBe(context.currentDate);
  expect(
    (await data<{ project: unknown }>(await request.get(projectPath))).project,
  ).toEqual(beforeProject.project);
});

test("현황판→프로젝트→원가 분석 이동은 세 폭에서 활성 상세 패널 하나만 표시한다", async ({page,request}) => {
  const board = await data<{items: {projectId: string; contractType: string; sourceStatus: string}[]}>(await request.get("/api/project-status-board?size=100"));
  const project = board.items.find(row=>row.contractType!=="IN"&&row.sourceStatus!=="PRE");
  expect(project).toBeDefined();
  for (const width of [1900,1500,1280]) {
    await page.setViewportSize({width,height:1000});
    await page.goto("/UI-31");
    await page.getByTestId(`status-project-${project!.projectId.toLowerCase()}`).click();
    await expect(page.getByTestId("project-progress-panel")).toBeVisible();
    await page.getByTestId("project-tab-basic").click();
    await page.getByTestId("project-cost-summary").getByRole("link",{name:"원가 분석",exact:true}).click();
    await expect(page.getByTestId("project-cost-detail")).toBeVisible();
    await expect(page.locator('[role="dialog"]:visible')).toHaveCount(1);
    await expect(page.getByTestId("project-detail")).not.toBeVisible();
    expect(await page.evaluate(()=>document.documentElement.scrollWidth)).toBe(width);
  }
});
