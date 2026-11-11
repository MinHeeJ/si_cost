import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { ChangeHistoryTable } from "./ChangeHistoryTable";
import {
  getMasterHistory,
  type MasterHistoryEntry,
  type MasterHistoryPage,
} from "./masterHistoryApi";
import { ApiError } from "../../shared/api";
vi.mock("./masterHistoryApi");
const row: MasterHistoryEntry = {
  historyId: 1,
  targetTable: "TB_BS_CLNT",
  targetKey: "C-001",
  changeType: "UPD",
  itemName: "상호",
  beforeValue: "이전 상호",
  afterValue: "변경 당시 상호",
  reason: null,
  actorId: "admin",
  actorName: "임시 관리자",
  createdAt: "2026-11-11 09:10",
};
const result = (
  items: MasterHistoryEntry[] = [],
  total = items.length,
): MasterHistoryPage => ({ items, total, page: 1, size: 50 });
beforeEach(() => vi.resetAllMocks());
it("선택한 대상의 당시 문자열과 서버 순서, 유형별 색을 읽기 전용으로 표시한다", async () => {
  vi.mocked(getMasterHistory).mockResolvedValue(
    result([
      { ...row, historyId: 4, changeType: "NEW" },
      { ...row, historyId: 3, changeType: "UPD" },
      { ...row, historyId: 2, changeType: "STOP" },
      {
        ...row,
        historyId: 1,
        changeType: "RESUME",
        itemName: "계좌",
        beforeValue: "****1234",
        afterValue: "****5678",
      },
    ]),
  );
  render(<ChangeHistoryTable targetType="CLIENT" targetId="C-001" />);
  expect(await screen.findByText("등록")).toHaveClass("sicost-tone-ok");
  expect(screen.getByText("변경")).toHaveClass("sicost-tone-info");
  expect(screen.getByText("중지")).toHaveClass("sicost-tone-lock");
  expect(screen.getByText("재개")).toHaveClass("sicost-tone-ok");
  expect(screen.getByText("****1234")).toBeInTheDocument();
  expect(screen.getAllByRole("row")[1]).toHaveTextContent("변경 당시 상호");
  expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
  for (const name of ["수정", "삭제", "저장", "정렬", "로그인"])
    expect(screen.queryByRole("button", { name })).not.toBeInTheDocument();
  expect(getMasterHistory).toHaveBeenCalledWith(
    "CLIENT",
    "C-001",
    1,
    50,
    expect.any(AbortSignal),
  );
});
it("로딩과 빈 이력을 구분한다", async () => {
  let resolve!: (page: MasterHistoryPage) => void;
  vi.mocked(getMasterHistory).mockReturnValue(
    new Promise((done) => {
      resolve = done;
    }),
  );
  render(<ChangeHistoryTable targetType="USER" targetId="admin" />);
  expect(screen.getByRole("status")).toHaveTextContent("불러오는 중");
  expect(
    screen.queryByText("조회된 변경 이력이 없습니다."),
  ).not.toBeInTheDocument();
  resolve(result());
  expect(
    await screen.findByText("조회된 변경 이력이 없습니다."),
  ).toBeInTheDocument();
});
it("오류 문의 번호와 명시적 재시도를 제공하고 자동 재시도하지 않는다", async () => {
  vi.mocked(getMasterHistory)
    .mockRejectedValueOnce(
      new ApiError(404, {
        success: false,
        meta: {},
        code: "NOT_FOUND",
        message: "요청한 기준정보를 찾을 수 없습니다.",
        requestId: "trace-history",
      }),
    )
    .mockResolvedValueOnce(result());
  render(<ChangeHistoryTable targetType="PART" targetId="PART-001" />);
  expect(await screen.findByRole("alert")).toHaveTextContent("trace-history");
  expect(getMasterHistory).toHaveBeenCalledTimes(1);
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  await screen.findByText("조회된 변경 이력이 없습니다.");
  expect(getMasterHistory).toHaveBeenCalledTimes(2);
});
it("페이지와 크기 변경은 서버 조회하며 크기를 바꾸면 첫 페이지로 돌아간다", async () => {
  vi.mocked(getMasterHistory).mockResolvedValue(result([row], 101));
  render(<ChangeHistoryTable targetType="CLIENT" targetId="C-001" />);
  await screen.findByText("변경 당시 상호");
  await userEvent.click(screen.getByRole("button", { name: "다음 페이지" }));
  await waitFor(() =>
    expect(getMasterHistory).toHaveBeenLastCalledWith(
      "CLIENT",
      "C-001",
      2,
      50,
      expect.any(AbortSignal),
    ),
  );
  await waitFor(() =>
    expect(screen.getByLabelText("한 화면 건수")).toBeEnabled(),
  );
  await userEvent.selectOptions(screen.getByLabelText("한 화면 건수"), "20");
  await waitFor(() =>
    expect(getMasterHistory).toHaveBeenLastCalledWith(
      "CLIENT",
      "C-001",
      1,
      20,
      expect.any(AbortSignal),
    ),
  );
});
it("대상을 바꾸면 이전 이력은 즉시 사라지고 늦은 응답을 무시한다", async () => {
  let resolve!: (page: MasterHistoryPage) => void;
  vi.mocked(getMasterHistory)
    .mockReturnValueOnce(
      new Promise((done) => {
        resolve = done;
      }),
    )
    .mockResolvedValueOnce(result());
  const { rerender } = render(
    <ChangeHistoryTable targetType="USER" targetId="first" />,
  );
  rerender(<ChangeHistoryTable targetType="USER" targetId="second" />);
  await screen.findByText("조회된 변경 이력이 없습니다.");
  resolve(result([row]));
  await waitFor(() =>
    expect(screen.queryByText("변경 당시 상호")).not.toBeInTheDocument(),
  );
  expect(getMasterHistory).toHaveBeenLastCalledWith(
    "USER",
    "second",
    1,
    50,
    expect.any(AbortSignal),
  );
});
it("저장 후 refreshKey가 바뀌면 이력을 다시 읽는다", async () => {
  vi.mocked(getMasterHistory).mockResolvedValue(result());
  const { rerender } = render(
    <ChangeHistoryTable
      targetType="PART"
      targetId="PART-001"
      refreshKey="old"
    />,
  );
  await screen.findByText("조회된 변경 이력이 없습니다.");
  rerender(
    <ChangeHistoryTable
      targetType="PART"
      targetId="PART-001"
      refreshKey="new"
    />,
  );
  await waitFor(() => expect(getMasterHistory).toHaveBeenCalledTimes(2));
});
it("긴 값은 원문을 잘라 저장하거나 HTML로 실행하지 않고 펼쳐 읽는다", async () => {
  const value = "<script>보존 문자열</script>".repeat(5);
  vi.mocked(getMasterHistory).mockResolvedValue(
    result([{ ...row, beforeValue: value }]),
  );
  render(<ChangeHistoryTable targetType="CLIENT" targetId="C-001" />);
  await userEvent.click(await screen.findByText("긴 값 펼침"));
  expect(screen.getByText(value)).toBeInTheDocument();
  expect(screen.getByText(value).closest("details")).toHaveAttribute("open");
});
it("선택 loader와 row adapter는 기존 master-history를 호출하거나 응답 원본을 바꾸지 않는다", async () => {
  const source = result([row]);
  const loader = vi.fn().mockResolvedValue(source);
  const adapter = (entry: MasterHistoryEntry) => ({
    ...entry,
    itemName: "변환된 항목",
  });
  render(
    <ChangeHistoryTable
      targetType="DEVELOPER"
      targetId="selected-person"
      loader={loader}
      rowAdapter={adapter}
    />,
  );
  await screen.findByText("변환된 항목");
  expect(loader).toHaveBeenCalledWith(
    "DEVELOPER",
    "selected-person",
    1,
    50,
    expect.any(AbortSignal),
  );
  expect(getMasterHistory).not.toHaveBeenCalled();
  expect(source.items[0].itemName).toBe("상호");
});
