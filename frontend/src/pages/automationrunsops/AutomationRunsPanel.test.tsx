import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { ApiError } from "../../shared/api";
import { AutomationRunsPanel } from "./AutomationRunsPanel";
import * as api from "./automationRunsApi";
vi.mock("./automationRunsApi");
const run: api.AutomationRun = {
  jobCode: "DAILY_OPERATIONS",
  runDate: "2026-07-15",
  state: "SUCCEEDED",
  lastStep: 7,
  startedAt: "2026-07-15T02:00:00+09:00",
  endedAt: "2026-07-15T02:01:00+09:00",
  errorMessage: null,
};
const page: api.AutomationPage = {
  items: [run],
  page: 1,
  size: 50,
  total: 1,
  hasMore: false,
  manualRunEnabled: false,
  observedAt: run.startedAt,
};
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api.getAutomationRuns).mockResolvedValue(page);
});
afterEach(() => {
  vi.useRealTimers();
});
function draw(open = true) {
  const close = vi.fn(),
    deliveries = vi.fn();
  const result = render(
    <AutomationRunsPanel
      open={open}
      onClose={close}
      onOpenReceivables={deliveries}
    />,
  );
  return { ...result, close, deliveries };
}
it("닫힌 패널은 조회하거나 실행하지 않는다", () => {
  draw(false);
  expect(api.getAutomationRuns).not.toHaveBeenCalled();
  expect(api.createAutomationRunToday).not.toHaveBeenCalled();
});
it("실제 실행행과 한국어 상태·완료 단계·시각을 표시한다", async () => {
  draw();
  expect(await screen.findByText("완료")).toHaveClass("sicost-tone-ok");
  for (const label of [
    "일자",
    "작업(02시/09시)",
    "완료 단계",
    "상태",
    "최초 시작",
    "종료",
    "실패 사유",
  ])
    expect(screen.getByText(label)).toBeInTheDocument();
  expect(screen.getByText(run.startedAt)).toBeInTheDocument();
  expect(screen.getByText(run.endedAt!)).toBeInTheDocument();
});
it("검증 허용이 없으면 임시 관리자여도 실행 버튼은 없다", async () => {
  draw();
  await screen.findByText("완료");
  expect(
    screen.queryByRole("button", { name: /오늘0/ }),
  ).not.toBeInTheDocument();
});
it.each(["DAILY_OPERATIONS", "RECEIVABLE_DUNNING"] as const)(
  "%s 실행 버튼은 경로 작업만 보내고 202를 성공으로 표시하지 않는다",
  async (job) => {
    vi.mocked(api.getAutomationRuns).mockResolvedValue({
      ...page,
      manualRunEnabled: true,
    });
    vi.mocked(api.createAutomationRunToday).mockResolvedValue({
      run: { ...run, jobCode: job, state: "RUNNING" },
      accepted: true,
      completed: false,
    });
    draw();
    await userEvent.click(
      await screen.findByRole("button", {
        name:
          job === "DAILY_OPERATIONS" ? "오늘02시작업실행" : "오늘09시작업실행",
      }),
    );
    expect(api.createAutomationRunToday).toHaveBeenCalledTimes(1);
    expect(api.createAutomationRunToday).toHaveBeenCalledWith(job);
    expect(
      await screen.findByText(
        "실행을 접수했습니다. 전체 완료나 메일 성공을 뜻하지 않습니다.",
      ),
    ).toBeInTheDocument();
    expect(api.getAutomationRuns).toHaveBeenCalledTimes(2);
  },
);
it("완료 동일요청은 기존 결과라고 알리고 새 완료를 합성하지 않는다", async () => {
  vi.mocked(api.getAutomationRuns).mockResolvedValue({
    ...page,
    manualRunEnabled: true,
  });
  vi.mocked(api.createAutomationRunToday).mockResolvedValue({
    run,
    accepted: false,
    completed: true,
  });
  draw();
  await userEvent.click(
    await screen.findByRole("button", { name: "오늘02시작업실행" }),
  );
  expect(
    await screen.findByText(
      "오늘 완료한 기존 결과입니다. 다시 실행하지 않습니다.",
    ),
  ).toBeInTheDocument();
});
it("실제 RUNNING은 파란 상태이며 해당 실행을 차단한다", async () => {
  vi.mocked(api.getAutomationRuns).mockResolvedValue({
    ...page,
    manualRunEnabled: true,
    items: [{ ...run, state: "RUNNING", lastStep: 2, endedAt: null }],
  });
  draw();
  expect(await screen.findByText("실행 중")).toHaveClass("sicost-tone-info");
  expect(
    screen.getByRole("button", { name: "오늘02시작업실행" }),
  ).toBeDisabled();
  expect(
    screen.getByRole("button", { name: "오늘09시작업실행" }),
  ).toBeEnabled();
});
it("실패는 빨간 상태와 서버의 실패 사유를 표시한다", async () => {
  vi.mocked(api.getAutomationRuns).mockResolvedValue({
    ...page,
    items: [
      {
        ...run,
        state: "FAILED",
        errorMessage: "단계 4 / ASGN-001 / 저장 실패",
      },
    ],
  });
  draw();
  expect(await screen.findByText("실패")).toHaveClass("sicost-tone-bad");
  expect(screen.getByText("단계 4 / ASGN-001 / 저장 실패")).toBeInTheDocument();
});
it("로딩과 빈 기록은 구분하고 가상 성공행을 만들지 않는다", async () => {
  let resolve!: (v: api.AutomationPage) => void;
  vi.mocked(api.getAutomationRuns).mockReturnValue(
    new Promise((r) => {
      resolve = r;
    }),
  );
  draw();
  expect(screen.getByText("불러오는 중입니다.")).toBeInTheDocument();
  await act(async () => resolve({ ...page, items: [], total: 0 }));
  expect(
    screen.getByText("자동 처리 실행 기록이 없습니다."),
  ).toBeInTheDocument();
  expect(screen.queryByText("완료")).not.toBeInTheDocument();
});
it("실패는 문의 번호와 재조회이며 빈 성공으로 대체하지 않는다", async () => {
  vi.mocked(api.getAutomationRuns).mockRejectedValueOnce(
    new ApiError(500, {
      success: false,
      meta: {},
      code: "INTERNAL_ERROR",
      message: "조회 실패",
      requestId: "automation-trace",
    }),
  );
  draw();
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "automation-trace",
  );
  expect(
    screen.queryByText("자동 처리 실행 기록이 없습니다."),
  ).not.toBeInTheDocument();
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  expect(await screen.findByText("완료")).toBeInTheDocument();
});
it("명령 중 중복 클릭을 차단하고 닫아도 서버 실행취소는 보내지 않는다", async () => {
  let resolve!: (v: api.AutomationCommandResult) => void;
  vi.mocked(api.getAutomationRuns).mockResolvedValue({
    ...page,
    manualRunEnabled: true,
  });
  vi.mocked(api.createAutomationRunToday).mockReturnValue(
    new Promise((r) => {
      resolve = r;
    }),
  );
  const view = draw();
  const button = await screen.findByRole("button", {
    name: "오늘02시작업실행",
  });
  await userEvent.dblClick(button);
  expect(api.createAutomationRunToday).toHaveBeenCalledTimes(1);
  expect(button).toBeDisabled();
  await userEvent.click(screen.getByRole("button", { name: "닫기" }));
  expect(view.close).toHaveBeenCalledOnce();
  view.unmount();
  await act(async () => resolve({ run, accepted: false, completed: true }));
  expect(api.getAutomationRuns).toHaveBeenCalledTimes(1);
});
it("페이지와 크기는 서버에 전달하며 크기 변경시 첫 페이지로 돌아간다", async () => {
  vi.mocked(api.getAutomationRuns).mockResolvedValue({
    ...page,
    total: 101,
    hasMore: true,
  });
  draw();
  await screen.findByText("완료");
  await userEvent.click(screen.getByRole("button", { name: "다음 페이지" }));
  await waitFor(() =>
    expect(api.getAutomationRuns).toHaveBeenLastCalledWith(
      { page: 2, size: 50 },
      expect.anything(),
    ),
  );
  await waitFor(() =>
    expect(screen.getByLabelText("한 화면 건수")).toBeEnabled(),
  );
  await userEvent.selectOptions(screen.getByLabelText("한 화면 건수"), "20");
  await waitFor(() =>
    expect(api.getAutomationRuns).toHaveBeenLastCalledWith(
      { page: 1, size: 20 },
      expect.anything(),
    ),
  );
});
it("전달 확인은 청구 식별자를 만들지 않고 기존 미수 화면으로 이동한다", async () => {
  const view = draw();
  await screen.findByText("완료");
  await userEvent.click(
    screen.getByRole("button", { name: "청구·입금에서 전달상태 확인" }),
  );
  expect(view.deliveries).toHaveBeenCalledTimes(1);
  expect(view.deliveries).toHaveBeenCalledWith();
  expect(api.createAutomationRunToday).not.toHaveBeenCalled();
});
it("RUNNING일 때만 2초 조회하고 닫히면 폴링을 중지한다", async () => {
  vi.mocked(api.getAutomationRuns).mockResolvedValue({
    ...page,
    items: [{ ...run, state: "RUNNING" }],
  });
  vi.useFakeTimers();
  const view = draw();
  await act(async () => {});
  expect(screen.getByText("실행 중")).toBeInTheDocument();
  await act(async () => {
    await vi.advanceTimersByTimeAsync(2000);
  });
  expect(api.getAutomationRuns).toHaveBeenCalledTimes(2);
  view.unmount();
  await act(async () => {
    await vi.advanceTimersByTimeAsync(4000);
  });
  expect(api.getAutomationRuns).toHaveBeenCalledTimes(2);
});
