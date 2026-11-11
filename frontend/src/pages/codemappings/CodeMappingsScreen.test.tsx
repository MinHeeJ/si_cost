import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { CodeMappingsScreen } from "./CodeMappingsScreen";
import * as api from "./codeMappingsApi";
import { ApiError } from "../../shared/api";
vi.mock("./codeMappingsApi");
const row = {
  mapId: 1,
  sourceText: "독립 표기",
  groupId: "GROUP",
  code: "A",
  codeName: "선택 코드",
  revision: "revision",
};
const page = { items: [row], page: 1, size: 50 as const, total: 61 };
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api.getCodeMappings).mockResolvedValue(page);
  vi.mocked(api.getMappingGroups).mockResolvedValue({
    items: [{ groupId: "GROUP", name: "독립 그룹" }],
  });
  vi.mocked(api.getMappingCodes).mockResolvedValue({
    items: [{ code: "A", name: "선택 코드" }],
  });
  vi.mocked(api.createCodeMapping).mockResolvedValue(undefined);
  vi.mocked(api.deleteCodeMappings).mockResolvedValue(undefined);
});
async function openAdd() {
  await screen.findByText("독립 표기");
  await userEvent.click(screen.getByRole("button", { name: "추가" }));
  const dialog = screen.getByRole("dialog");
  await userEvent.type(within(dialog).getByLabelText(/엑셀 표기/), " 새 표기 ");
  await userEvent.selectOptions(
    within(dialog).getByLabelText("코드 그룹"),
    "GROUP",
  );
  await waitFor(() =>
    expect(
      within(dialog).getByRole("option", { name: "A · 선택 코드" }),
    ).toBeInTheDocument(),
  );
  await userEvent.selectOptions(
    within(dialog).getByLabelText("바꿀 코드"),
    "A",
  );
  return dialog;
}
describe("엑셀 표기 대응", () => {
  it("전체건수와 페이지 행수를 분리하고 읽기전용 코드·이름을 표시한다", async () => {
    render(<CodeMappingsScreen />);
    await screen.findByText("독립 표기");
    expect(screen.getByText("A · 선택 코드")).toBeInTheDocument();
    expect(screen.getByText("1-50 / 61건")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "선택 삭제" })).toBeDisabled();
    expect(
      screen.queryByRole("button", { name: "수정" }),
    ).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "다음 페이지" }));
    await waitFor(() =>
      expect(api.getCodeMappings).toHaveBeenLastCalledWith(
        expect.objectContaining({ page: 2 }),
      ),
    );
  });
  it("검색과 그룹 필터를 API로 보내며 페이지를 초기화한다", async () => {
    render(<CodeMappingsScreen />);
    await screen.findByText("독립 표기");
    await userEvent.type(screen.getByLabelText("엑셀 표기 검색"), "부분");
    await userEvent.selectOptions(screen.getByLabelText("그룹 검색"), "GROUP");
    await userEvent.click(screen.getByRole("button", { name: "조회" }));
    await waitFor(() =>
      expect(api.getCodeMappings).toHaveBeenLastCalledWith(
        expect.objectContaining({
          sourceText: "부분",
          groupId: "GROUP",
          page: 1,
        }),
      ),
    );
  });
  it("행수 선택을 부모 설정 저장과 연결하고 서버 페이지를 조회한다", async () => {
    const size = vi.fn().mockResolvedValue(undefined);
    render(<CodeMappingsScreen onPageSizeChange={size} />);
    await screen.findByText("독립 표기");
    await userEvent.selectOptions(screen.getByLabelText("한 화면 건수"), "20");
    await waitFor(() => expect(size).toHaveBeenCalledWith(20));
    await waitFor(() =>
      expect(api.getCodeMappings).toHaveBeenLastCalledWith(
        expect.objectContaining({ size: 20, page: 1 }),
      ),
    );
  });
  it("빈 결과 문구는 오류가 아니다", async () => {
    vi.mocked(api.getCodeMappings).mockResolvedValue({
      items: [],
      total: 0,
      page: 1,
      size: 50,
    });
    render(<CodeMappingsScreen />);
    expect(
      await screen.findByText("등록된 표기가 없습니다."),
    ).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });
  it("loading과 오류를 구분하고 조회만 수동 재시도한다", async () => {
    let reject!: (e: Error) => void;
    vi.mocked(api.getCodeMappings).mockReturnValue(
      new Promise((_, no) => {
        reject = no;
      }),
    );
    render(<CodeMappingsScreen />);
    expect(screen.getByRole("status")).toHaveTextContent("불러오는 중");
    await act(async () => reject(new Error("목록 실패")));
    expect(screen.getByRole("alert")).toHaveTextContent("목록 실패");
    expect(api.getCodeMappings).toHaveBeenCalledTimes(1);
    vi.mocked(api.getCodeMappings).mockResolvedValue(page);
    await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
    expect(await screen.findByText("독립 표기")).toBeInTheDocument();
    expect(api.createCodeMapping).not.toHaveBeenCalled();
  });
  it("실제 선택 코드로 세 필드만 추가하고 목록과 관련 캐시를 갱신한다", async () => {
    const committed = vi.fn().mockResolvedValue(undefined);
    render(<CodeMappingsScreen onCommitted={committed} />);
    const dialog = await openAdd();
    expect(within(dialog).queryByLabelText("메모")).not.toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole("button", { name: "추가" }));
    await waitFor(() =>
      expect(api.createCodeMapping).toHaveBeenCalledWith({
        sourceText: "새 표기",
        groupId: "GROUP",
        code: "A",
      }),
    );
    await waitFor(() =>
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument(),
    );
    expect(api.getCodeMappings).toHaveBeenCalledTimes(2);
    expect(committed).toHaveBeenCalledTimes(1);
  });
  it("중복 오류시 필드와 입력을 보존하고 자동 재저장하지 않는다", async () => {
    vi.mocked(api.createCodeMapping).mockRejectedValue(
      new ApiError(409, {
        success: false,
        meta: {},
        code: "DUPLICATE_MAPPING",
        message: "이미 등록된 표기입니다.",
        field: "sourceText",
        requestId: "case",
      }),
    );
    render(<CodeMappingsScreen />);
    const dialog = await openAdd();
    await userEvent.click(within(dialog).getByRole("button", { name: "추가" }));
    await waitFor(() =>
      expect(within(dialog).getByLabelText(/엑셀 표기/)).toHaveAttribute(
        "aria-invalid",
        "true",
      ),
    );
    expect(within(dialog).getByLabelText(/엑셀 표기/)).toHaveValue(" 새 표기 ");
    expect(api.createCodeMapping).toHaveBeenCalledTimes(1);
    expect(api.getCodeMappings).toHaveBeenCalledTimes(1);
  });
  it("빈 표기를 서버로 보내지 않는다", async () => {
    render(<CodeMappingsScreen />);
    await screen.findByText("독립 표기");
    await userEvent.click(screen.getByRole("button", { name: "추가" }));
    fireEvent.submit(screen.getByRole("dialog"));
    await waitFor(() =>
      expect(screen.getByLabelText(/엑셀 표기 \*/)).toHaveAttribute(
        "aria-invalid",
        "true",
      ),
    );
    expect(api.createCodeMapping).not.toHaveBeenCalled();
  });
  it("선택 삭제는 확인 후 조회 revision으로 제출하고 성공 후 재조회한다", async () => {
    render(<CodeMappingsScreen />);
    await screen.findByText("독립 표기");
    await userEvent.click(screen.getByRole("checkbox"));
    await userEvent.click(screen.getByRole("button", { name: "선택 삭제" }));
    expect(api.deleteCodeMappings).not.toHaveBeenCalled();
    const dialog = screen.getByRole("dialog");
    expect(within(dialog).getByText("독립 표기")).toBeInTheDocument();
    await userEvent.click(
      within(dialog).getByRole("button", { name: "삭제 확인" }),
    );
    await waitFor(() =>
      expect(api.deleteCodeMappings).toHaveBeenCalledWith({
        items: [{ mapId: 1, expectedRevision: "revision" }],
      }),
    );
    await waitFor(() =>
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument(),
    );
    expect(api.getCodeMappings).toHaveBeenCalledTimes(2);
  });
  it("삭제 충돌은 선택과 확인창을 유지한다", async () => {
    vi.mocked(api.deleteCodeMappings).mockRejectedValue(
      new Error("다른 사용자가 변경했습니다."),
    );
    render(<CodeMappingsScreen />);
    await screen.findByText("독립 표기");
    await userEvent.click(screen.getByRole("checkbox"));
    await userEvent.click(screen.getByRole("button", { name: "선택 삭제" }));
    await userEvent.click(
      within(screen.getByRole("dialog")).getByRole("button", {
        name: "삭제 확인",
      }),
    );
    await screen.findByText("다른 사용자가 변경했습니다.");
    expect(screen.getByRole("checkbox")).toBeChecked();
    expect(api.deleteCodeMappings).toHaveBeenCalledTimes(1);
  });
  it("pending 중 중복삭제와 취소를 막는다", async () => {
    let done!: () => void;
    vi.mocked(api.deleteCodeMappings).mockReturnValue(
      new Promise((resolve) => {
        done = resolve;
      }),
    );
    render(<CodeMappingsScreen />);
    await screen.findByText("독립 표기");
    await userEvent.click(screen.getByRole("checkbox"));
    await userEvent.click(screen.getByRole("button", { name: "선택 삭제" }));
    const dialog = screen.getByRole("dialog"),
      button = within(dialog).getByRole("button", { name: "삭제 확인" });
    fireEvent.click(button);
    fireEvent.click(button);
    await waitFor(() =>
      expect(
        within(dialog).getByRole("button", { name: "취소" }),
      ).toBeDisabled(),
    );
    expect(api.deleteCodeMappings).toHaveBeenCalledTimes(1);
    await act(async () => done());
  });
  it("commit 후 조회실패는 성공한 요청을 재전송하지 않는다", async () => {
    render(<CodeMappingsScreen />);
    const dialog = await openAdd();
    vi.mocked(api.getCodeMappings).mockRejectedValue(new Error("재조회 실패"));
    await userEvent.click(within(dialog).getByRole("button", { name: "추가" }));
    expect(
      await screen.findByText("저장됐지만 다시 불러오지 못했습니다."),
    ).toBeInTheDocument();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(api.createCodeMapping).toHaveBeenCalledTimes(1);
  });
});
