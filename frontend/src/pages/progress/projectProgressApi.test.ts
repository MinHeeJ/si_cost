import { beforeEach, expect, it, vi } from "vitest";
import { ApiError } from "../../shared/api";
import * as api from "./projectProgressApi";
beforeEach(() =>
  vi.stubGlobal(
    "fetch",
    vi.fn().mockImplementation(
      async () =>
        new Response(
          JSON.stringify({
            success: true,
            meta: {},
            data: { progressRevision: "opaque", changed: false },
          }),
        ),
    ),
  ),
);
it("조회는 선택한 프로젝트/연도를 상대경로로 인코딩한다", async () => {
  await api.getProjectProgress("selected/project", 2026);
  expect(vi.mocked(fetch).mock.calls[0][0]).toBe(
    "/api/projects/selected%2Fproject/progress?year=2026",
  );
});
it("이력은 선택한 대상키와 페이지를 인코딩하고 GET 본문이 없다", async () => {
  await api.getProjectProgressHistory("selected-id", {
    targetTable: "TB_PJ_STEP",
    targetKey: "selected-id:2",
    page: 2,
    size: 20,
  });
  const [path, options] = vi.mocked(fetch).mock.calls[0];
  expect(path).toBe(
    "/api/projects/selected-id/progress/history?targetTable=TB_PJ_STEP&targetKey=selected-id%3A2&page=2&size=20",
  );
  expect(options?.body).toBeUndefined();
});
it("모든 진행 쓰기 및 미리보기는 승인된 메서드/경로/본문만 사용한다", async () => {
  const body = { year: 2026, expectedRevision: "progress-token" },
    steps = [{ stepSn: 4, endDate: "2026-08-01" }];
  await api.createProjectProgressSteps("selected-id", body);
  await api.createProjectStepDatesPreview("selected-id", { year: 2026, steps });
  await api.saveProjectStepDates("selected-id", { ...body, steps });
  await api.saveProjectStepCompletion("selected-id", 4, body);
  await api.createProjectWeekMemo("selected-id", {
    ...body,
    actualProgress: 0,
  });
  await api.putProjectWeekMemo("selected-id", 17, {
    ...body,
    thisWeek: "수정",
  });
  expect(
    vi
      .mocked(fetch)
      .mock.calls.map(([path, options]) => [
        path,
        options?.method,
        JSON.parse(options?.body as string),
      ]),
  ).toEqual([
    ["/api/projects/selected-id/progress/steps/initialize", "POST", body],
    [
      "/api/projects/selected-id/progress/steps/preview",
      "POST",
      { year: 2026, steps },
    ],
    ["/api/projects/selected-id/progress/steps", "PUT", { ...body, steps }],
    ["/api/projects/selected-id/progress/steps/4/complete", "POST", body],
    [
      "/api/projects/selected-id/progress/week-memos",
      "POST",
      { ...body, actualProgress: 0 },
    ],
    [
      "/api/projects/selected-id/progress/week-memos/17",
      "PUT",
      { ...body, thisWeek: "수정" },
    ],
  ]);
});
it("409와 필드/문의번호를 유지하고 자동 재전송하지 않는다", async () => {
  vi.mocked(fetch).mockResolvedValue(
    new Response(
      JSON.stringify({
        success: false,
        meta: {},
        code: "STALE_WRITE",
        message: "다른 변경이 있습니다.",
        field: "expectedRevision",
        requestId: "progress-request",
      }),
      { status: 409 },
    ),
  );
  await expect(
    api.createProjectWeekMemo("selected-id", {
      year: 2026,
      expectedRevision: "old",
      actualProgress: 0,
    }),
  ).rejects.toBeInstanceOf(ApiError);
  expect(fetch).toHaveBeenCalledOnce();
});
