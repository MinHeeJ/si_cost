import { afterEach, describe, expect, it, vi } from "vitest";
import * as api from "./apiClient";

const id = "selected/project";
const root = "/api/projects/selected%2Fproject";
const write = { year: 2026, expectedRevision: "progress-revision" };
const memo = { ...write, thisWeek: "진행 확인", actualProgress: 0 };
const issue = {
  ...write,
  title: "확인 사항",
  dueDate: "2026-11-20",
  importance: "MID",
  status: "OPEN",
  action: null,
};
const steps = [{ stepSn: 1, endDate: "2026-11-30" }];
const cases: {
  name: string;
  path: string;
  method?: string;
  body?: unknown;
  call(): Promise<unknown>;
}[] = [
  {
    name: "getProjectCosts",
    path: "/api/project-costs?month=2026-11",
    call: () => api.getProjectCosts({ month: "2026-11" }),
  },
  {
    name: "getProjectCostSummary",
    path: "/api/project-costs/summary?month=2026-11",
    call: () => api.getProjectCostSummary({ month: "2026-11", page: 2 }),
  },
  {
    name: "getProjectCostAnalysis",
    path: `${root}/cost-analysis?month=2026-11`,
    call: () => api.getProjectCostAnalysis(id, "2026-11"),
  },
  {
    name: "getProjectCostMonths",
    path: `${root}/cost-analysis/months?month=2026-11`,
    call: () => api.getProjectCostMonths(id, "2026-11"),
  },
  {
    name: "getProjectCostAssignments",
    path: `${root}/cost-analysis/assignments?month=2026-11`,
    call: () => api.getProjectCostAssignments(id, "2026-11"),
  },
  {
    name: "getProjectCostCauses",
    path: `${root}/cost-analysis/causes?month=2026-11`,
    call: () => api.getProjectCostCauses(id, "2026-11"),
  },
  {
    name: "createProjectCostSimulation",
    path: `${root}/cost-analysis/simulation`,
    method: "POST",
    body: { month: "2026-11", c: "999999999999999", n: -1 },
    call: () =>
      api.createProjectCostSimulation(id, {
        month: "2026-11",
        c: "999999999999999",
        n: -1,
      }),
  },
  {
    name: "getProjectStatusBoard",
    path: "/api/project-status-board?light=%EC%9C%84%ED%97%98",
    call: () => api.getProjectStatusBoard({ light: "위험" }),
  },
  {
    name: "getProjectStatusBoardSummary",
    path: "/api/project-status-board/summary?light=%EC%9C%84%ED%97%98",
    call: () => api.getProjectStatusBoardSummary({ light: "위험", page: 3 }),
  },
  {
    name: "getProjectProgress",
    path: `${root}/progress?year=2026`,
    call: () => api.getProjectProgress(id, 2026),
  },
  {
    name: "getProjectProgressHistory",
    path: `${root}/progress/history?page=2&size=20`,
    call: () => api.getProjectProgressHistory(id, { page: 2, size: 20 }),
  },
  {
    name: "createProjectProgressSteps",
    path: `${root}/progress/steps/initialize`,
    method: "POST",
    body: write,
    call: () => api.createProjectProgressSteps(id, write),
  },
  {
    name: "createProjectStepDatesPreview",
    path: `${root}/progress/steps/preview`,
    method: "POST",
    body: { year: 2026, steps },
    call: () => api.createProjectStepDatesPreview(id, { year: 2026, steps }),
  },
  {
    name: "saveProjectStepDates",
    path: `${root}/progress/steps`,
    method: "PUT",
    body: { ...write, steps },
    call: () => api.saveProjectStepDates(id, { ...write, steps }),
  },
  {
    name: "saveProjectStepCompletion",
    path: `${root}/progress/steps/1/complete`,
    method: "POST",
    body: write,
    call: () => api.saveProjectStepCompletion(id, 1, write),
  },
  {
    name: "createProjectWeekMemo",
    path: `${root}/progress/week-memos`,
    method: "POST",
    body: memo,
    call: () => api.createProjectWeekMemo(id, memo),
  },
  {
    name: "putProjectWeekMemo",
    path: `${root}/progress/week-memos/9`,
    method: "PUT",
    body: memo,
    call: () => api.putProjectWeekMemo(id, 9, memo),
  },
  {
    name: "getProjectIssues",
    path: `${root}/issues?status=ALL&page=2&size=20`,
    call: () => api.getProjectIssues(id, { status: "ALL", page: 2, size: 20 }),
  },
  {
    name: "getProjectStatusIssues",
    path: "/api/project-status-board/issues?status=OVERDUE&page=1&size=50&projectId=selected%2Fproject",
    call: () =>
      api.getProjectStatusIssues({
        status: "OVERDUE",
        page: 1,
        size: 50,
        projectId: id,
      }),
  },
  {
    name: "createProjectIssue",
    path: `${root}/issues`,
    method: "POST",
    body: issue,
    call: () => api.createProjectIssue(id, issue),
  },
  {
    name: "putProjectIssue",
    path: `${root}/issues/9999999999`,
    method: "PUT",
    body: issue,
    call: () => api.putProjectIssue(id, 9999999999, issue),
  },
];
afterEach(() => vi.unstubAllGlobals());
describe("프로젝트 분석 API 등록", () => {
  it.each(cases)(
    "$name delegates to its existing relative transport",
    async (test) => {
      const data = { exactDecimal: "999999999999999", unknown: null };
      const fetcher = vi
        .fn()
        .mockResolvedValue(
          new Response(JSON.stringify({ success: true, meta: {}, data })),
        );
      vi.stubGlobal("fetch", fetcher);
      expect(await test.call()).toEqual(data);
      expect(fetcher).toHaveBeenCalledOnce();
      const [path, options] = fetcher.mock.calls[0] as [string, RequestInit];
      expect(path).toBe(test.path);
      expect(options.method ?? "GET").toBe(test.method ?? "GET");
      expect(
        options.body ? JSON.parse(String(options.body)) : undefined,
      ).toEqual(test.body);
    },
  );
  it.each(cases)("$name preserves API errors without retry", async (test) => {
    const body = {
      success: false,
      meta: {},
      code: "STALE_WRITE",
      message: "다시 조회해 주세요.",
      field: "expectedRevision",
      requestId: "analysis-request",
    };
    const fetcher = vi
      .fn()
      .mockResolvedValue(new Response(JSON.stringify(body), { status: 409 }));
    vi.stubGlobal("fetch", fetcher);
    await expect(test.call()).rejects.toMatchObject({ status: 409, body });
    expect(fetcher).toHaveBeenCalledOnce();
  });
  it.each(["projectCost", "statusBoard"] as const)(
    "%s export keeps binary response and removes pagination",
    async (view) => {
      const filename =
        view === "projectCost"
          ? "프로젝트 원가_202611_20261113_120000.xlsx"
          : "프로젝트현황_20261113_120000.xlsx";
      const fetcher = vi.fn().mockResolvedValue(
        new Response("workbook", {
          headers: {
            "Content-Type":
              "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "Content-Disposition": `attachment; filename*=UTF-8''${encodeURIComponent(filename)}`,
          },
        }),
      );
      vi.stubGlobal("fetch", fetcher);
      const result =
        view === "projectCost"
          ? await api.getProjectCostExport({ month: "2026-11", page: 2 })
          : await api.getProjectAnalysisDownload(view, { page: 2 });
      expect(result.filename).toBe(filename);
      expect(result.blob.size).toBeGreaterThan(0);
      expect(fetcher.mock.calls[0][0]).toBe(
        view === "projectCost"
          ? "/api/project-costs/export?month=2026-11"
          : "/api/project-status-board/export?",
      );
    },
  );
});
