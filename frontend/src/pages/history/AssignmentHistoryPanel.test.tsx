import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { AssignmentHistoryPanel } from "./AssignmentHistoryPanel";
import * as api from "./assignmentHistoryApi";
import { getSharedCodes } from "../shared/sharedApi";
import { ApiError } from "../../shared/api";
vi.mock("./assignmentHistoryApi");
vi.mock("../shared/sharedApi", () => ({ getSharedCodes: vi.fn() }));
const row: api.AssignmentHistory = {
  sequence: 2,
  type: "UPRC",
  applyDate: "2026-08-01",
  before: "100",
  after: "200",
  reasonCode: "ETC",
  memo: null,
  newUnitPrice: "200",
  newContractMm: "0.50",
  retroactive: "N",
  closeCross: "N",
  corrected: "N",
  correctionTargetSequence: null,
  actor: "admin",
  registeredAt: "2026-07-01T09:00:00.123456",
};
const correction: api.AssignmentHistory = {
  ...row,
  sequence: 3,
  type: "CRCT",
  newUnitPrice: null,
  newContractMm: null,
  correctionTargetSequence: 2,
  after: "감사 보완",
};
const result: api.AssignmentHistoryPage = {
  items: [row],
  total: 1,
  page: 1,
  size: 50,
};
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api.getAssignmentHistory).mockResolvedValue(result);
  vi.mocked(getSharedCodes).mockResolvedValue({
    items: [
      {
        code: "ETC",
        name: "기타",
        addVal1: null,
        addVal2: null,
        addVal3: null,
        codeVal: null,
        unitNm: null,
      },
    ],
  });
  vi.mocked(api.createAssignmentHistoryCorrection).mockResolvedValue({
    original: { ...row, corrected: "Y" },
    correction,
    revision: "new-revision",
    changed: true,
  });
});
function draw(year = 2026, onChanged = vi.fn().mockResolvedValue(undefined)) {
  render(
    <AssignmentHistoryPanel
      assignmentId="selected-assignment"
      year={year}
      currentYear={2026}
      revision="stored-revision"
      onChanged={onChanged}
    />,
  );
  return onChanged;
}
async function open() {
  await userEvent.click(
    await screen.findByRole("button", { name: "이력 정정" }),
  );
  await screen.findByRole("option", { name: "기타" });
}
async function fill(content = "감사 보완") {
  fireEvent.change(screen.getByLabelText("바른 내용 *"), {
    target: { value: content },
  });
  await userEvent.selectOptions(screen.getByLabelText("사유코드 *"), "ETC");
}
it("원 typed값·정정원본·정정행·처리자·코드와 배지를 모두 유지한다", async () => {
  vi.mocked(api.getAssignmentHistory).mockResolvedValue({
    ...result,
    items: [{ ...row, corrected: "Y" }, correction],
  });
  draw();
  expect(await screen.findByText("정정됨")).toHaveClass("sicost-tone-warn");
  expect(
    screen.getByText("감사 정정 (CRCT)", { selector: "span" }),
  ).toHaveClass("sicost-tone-warn");
  expect(screen.getByText("200 / 0.50")).toBeInTheDocument();
  expect(screen.getByText("원행 #2")).toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: "이력 정정" }),
  ).not.toBeInTheDocument();
});
it.each(["INPUT", "CRCT"] as const)(
  "%s 행의 정정은 표시하지 않는다",
  async (type) => {
    vi.mocked(api.getAssignmentHistory).mockResolvedValue({
      ...result,
      items: [{ ...row, type }],
    });
    draw();
    await screen.findByText(
      `${type === "INPUT" ? "최초 투입" : "감사 정정"} (${type})`,
    );
    expect(
      screen.queryByRole("button", { name: "이력 정정" }),
    ).not.toBeInTheDocument();
  },
);
it("과거 연도는 조회만 한다", async () => {
  draw(2025);
  await screen.findByText("200 / 0.50");
  expect(
    screen.getByText("지난 연도는 조회만 할 수 있습니다."),
  ).toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: "이력 정정" }),
  ).not.toBeInTheDocument();
});
it("로딩과 실제 빈 목록을 구별한다", async () => {
  let resolve!: (data: api.AssignmentHistoryPage) => void;
  vi.mocked(api.getAssignmentHistory).mockReturnValue(
    new Promise((r) => {
      resolve = r;
    }),
  );
  draw();
  expect(screen.getByText("불러오는 중입니다.")).toBeInTheDocument();
  await act(async () => resolve({ ...result, items: [], total: 0 }));
  expect(screen.getByText("등록된 변경 이력이 없습니다.")).toBeInTheDocument();
});
it("실패 추적과 조회 재시도만 제공한다", async () => {
  vi.mocked(api.getAssignmentHistory).mockRejectedValueOnce(
    new ApiError(500, {
      success: false,
      meta: {},
      code: "INTERNAL_ERROR",
      message: "조회 실패",
      requestId: "history-trace",
    }),
  );
  draw();
  expect(await screen.findByRole("alert")).toHaveTextContent("history-trace");
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  await screen.findByText("200 / 0.50");
  expect(api.createAssignmentHistoryCorrection).not.toHaveBeenCalled();
});
it("종류·크기 변경은 첫 페이지와 새 서버 조회를 사용한다", async () => {
  vi.mocked(api.getAssignmentHistory).mockResolvedValue({
    ...result,
    total: 101,
  });
  draw();
  await screen.findByText("200 / 0.50");
  await userEvent.click(screen.getByRole("button", { name: "다음 페이지" }));
  await waitFor(() =>
    expect(api.getAssignmentHistory).toHaveBeenLastCalledWith(
      "selected-assignment",
      expect.objectContaining({ page: 2 }),
      expect.anything(),
    ),
  );
  await waitFor(() =>
    expect(screen.getByLabelText("한 화면 건수")).toBeEnabled(),
  );
  await userEvent.selectOptions(screen.getByLabelText("한 화면 건수"), "20");
  await userEvent.selectOptions(screen.getByLabelText("종류"), "CRCT");
  await waitFor(() =>
    expect(api.getAssignmentHistory).toHaveBeenLastCalledWith(
      "selected-assignment",
      { year: 2026, page: 1, size: 20, type: "CRCT" },
      expect.anything(),
    ),
  );
});
it("정정은 정확한 최소 요청으로 한번 저장하고 최신 revision으로 부모와 이력을 갱신한다", async () => {
  const changed = draw();
  await open();
  await fill("  감사 보완  ");
  await userEvent.click(screen.getByRole("button", { name: "정정 저장" }));
  await waitFor(() => expect(changed).toHaveBeenCalledWith("new-revision"));
  expect(api.createAssignmentHistoryCorrection).toHaveBeenCalledTimes(1);
  expect(api.createAssignmentHistoryCorrection).toHaveBeenCalledWith(
    "selected-assignment",
    2,
    {
      year: 2026,
      expectedRevision: "stored-revision",
      correctContent: "감사 보완",
      reasonCode: "ETC",
    },
  );
  await waitFor(() =>
    expect(api.getAssignmentHistory).toHaveBeenCalledTimes(2),
  );
});
it("빈 내용은 필드 오류이며 저장하지 않는다", async () => {
  draw();
  await open();
  await userEvent.click(screen.getByRole("button", { name: "정정 저장" }));
  expect(
    await screen.findByText("바른 내용은 1~200자로 입력해 주세요."),
  ).toBeInTheDocument();
  expect(api.createAssignmentHistoryCorrection).not.toHaveBeenCalled();
});
it("201자는 절단하지 않고 오류로 남긴다", async () => {
  draw();
  await open();
  await fill("가".repeat(201));
  await userEvent.click(screen.getByRole("button", { name: "정정 저장" }));
  expect(screen.getByLabelText("바른 내용 *")).toHaveValue("가".repeat(201));
  expect(api.createAssignmentHistoryCorrection).not.toHaveBeenCalled();
});
it("실패는 입력·필드·추적을 보존하며 자동 재저장하지 않는다", async () => {
  vi.mocked(api.createAssignmentHistoryCorrection).mockRejectedValue(
    new ApiError(409, {
      success: false,
      meta: {},
      code: "STALE_WRITE",
      field: "correctContent",
      message: "다시 조회해 주세요",
      requestId: "write-trace",
    }),
  );
  draw();
  await open();
  await fill();
  await userEvent.click(screen.getByRole("button", { name: "정정 저장" }));
  await screen.findByText(/write-trace/);
  expect(screen.getByLabelText("바른 내용 *")).toHaveValue("감사 보완");
  expect(api.createAssignmentHistoryCorrection).toHaveBeenCalledTimes(1);
});
it("commit 뒤 부모 조회 실패는 저장 버튼을 닫고 재저장하지 않는다", async () => {
  draw(2026, vi.fn().mockRejectedValue(new Error("read failed")));
  await open();
  await fill();
  await userEvent.click(screen.getByRole("button", { name: "정정 저장" }));
  expect(await screen.findByText(/저장은 완료됐지만/)).toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: "정정 저장" }),
  ).not.toBeInTheDocument();
  expect(api.createAssignmentHistoryCorrection).toHaveBeenCalledTimes(1);
});
it("활성 사유 조회 실패는 저장을 차단하고 다시 조회한다", async () => {
  vi.mocked(getSharedCodes).mockRejectedValueOnce(new Error("사유 조회 실패"));
  draw();
  await userEvent.click(
    await screen.findByRole("button", { name: "이력 정정" }),
  );
  await screen.findByText("사유 조회 실패");
  expect(screen.getByRole("button", { name: "정정 저장" })).toBeDisabled();
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  await screen.findByRole("option", { name: "기타" });
});
it("작성중 취소는 계속 작성과 나가기를 구분한다", async () => {
  draw();
  await open();
  await fill();
  await userEvent.click(screen.getByRole("button", { name: "취소" }));
  await userEvent.click(screen.getByRole("button", { name: "계속 작성" }));
  expect(screen.getByLabelText("바른 내용 *")).toHaveValue("감사 보완");
  await userEvent.click(screen.getByRole("button", { name: "취소" }));
  await userEvent.click(screen.getByRole("button", { name: "나가기" }));
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  expect(api.createAssignmentHistoryCorrection).not.toHaveBeenCalled();
});
