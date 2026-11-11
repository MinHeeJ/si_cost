import { describe, expect, it, vi, beforeEach } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { CommonCodesScreen } from "./CommonCodesScreen";
import * as api from "./codeGroupsApi";
import { ApiError } from "../../shared/api";

vi.mock("./codeGroupsApi", () => ({
  getCodeGroups: vi.fn(),
  getGroupCodes: vi.fn(),
  saveGroupCodes: vi.fn(),
  createCodeGroup: vi.fn(),
  deleteGroupCode: vi.fn(),
  notifyCodeGroupsCommitted: vi.fn(),
}));
const groups: api.CodeGroup[] = [
  {
    groupId: "CONTRACT",
    name: "계약유형",
    sysCodeYn: "Y",
    valCodeYn: "Y",
    useYn: "Y",
    sortOrder: 1,
    codeCount: 1,
  },
  {
    groupId: "EDUCATION",
    name: "교육",
    sysCodeYn: "N",
    valCodeYn: "N",
    useYn: "Y",
    sortOrder: 2,
    codeCount: 1,
  },
  {
    groupId: "ALLOWANCE",
    name: "수당코드",
    sysCodeYn: "N",
    valCodeYn: "Y",
    useYn: "Y",
    sortOrder: 3,
    codeCount: 1,
  },
];
const row: api.Code = {
  code: "A",
  name: "기존 이름",
  sortOrder: 1,
  useYn: "Y",
  addVal1: null,
  addVal2: null,
  addVal3: null,
  codeVal: "9.4000",
  unitNm: "%",
};
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api.getCodeGroups).mockResolvedValue({ items: groups });
  vi.mocked(api.getGroupCodes).mockResolvedValue({
    items: [row],
    total: 1,
    page: 1,
    size: 50,
    groupRevision: "original",
  });
  vi.mocked(api.saveGroupCodes).mockResolvedValue({
    changed: true,
    groupRevision: "saved",
    items: [row],
  });
});
describe("공통코드 편집 계약", () => {
  it("현재 코드 탭을 다시 누르면 폐기 확인 없이 입력을 유지한다", async () => {
    render(<CommonCodesScreen />);
    fireEvent.change(await screen.findByLabelText("A 이름"), {
      target: { value: "작성 중" },
    });
    await userEvent.click(screen.getByRole("button", { name: "코드" }));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(screen.getByLabelText("A 이름")).toHaveValue("작성 중");
    expect(api.saveGroupCodes).not.toHaveBeenCalled();
  });
  it("화면을 닫아 편집을 폐기하면 상위 dirty 상태도 해제한다", async () => {
    const onDirtyChange = vi.fn();
    const view = render(<CommonCodesScreen onDirtyChange={onDirtyChange} />);
    fireEvent.change(await screen.findByLabelText("A 이름"), {
      target: { value: "폐기할 입력" },
    });
    expect(onDirtyChange).toHaveBeenLastCalledWith(true);
    view.unmount();
    expect(onDirtyChange).toHaveBeenLastCalledWith(false);
  });
  it.each(["groups", "codes"])(
    "%s 조회 실패를 정상 빈 목록으로 표시하지 않는다",
    async (target) => {
      if (target === "groups")
        vi.mocked(api.getCodeGroups).mockRejectedValueOnce(
          new Error("연결 실패"),
        );
      else
        vi.mocked(api.getGroupCodes).mockRejectedValueOnce(
          new Error("연결 실패"),
        );
      render(<CommonCodesScreen />);
      await screen.findByRole("alert");
      expect(
        screen.queryByText("등록된 그룹이 없습니다."),
      ).not.toBeInTheDocument();
      expect(
        screen.queryByText("등록된 코드가 없습니다."),
      ).not.toBeInTheDocument();
      await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
      expect(await screen.findByLabelText("A 이름")).toBeInTheDocument();
      expect(api.saveGroupCodes).not.toHaveBeenCalled();
    },
  );
  it("사용 필드 서버 오류는 해당 체크박스에 연결하고 포커스와 입력을 보존한다", async () => {
    vi.mocked(api.saveGroupCodes).mockRejectedValueOnce(
      new ApiError(400, {
        success: false,
        meta: {},
        code: "INVALID_FIELD",
        message: "입력값을 확인해 주세요.",
        requestId: "validation-reference",
        details: {
          violations: [
            {
              field: "changes[0].useYn",
              message: "사용 상태를 확인해 주세요.",
            },
          ],
        },
      }),
    );
    render(<CommonCodesScreen />);
    await userEvent.click(
      await screen.findByRole("button", { name: /교육.*1/ }),
    );
    const input = await screen.findByRole("checkbox", { name: "A 사용" });
    await userEvent.click(input);
    await userEvent.click(screen.getByRole("button", { name: "저장" }));
    await waitFor(() => expect(input).toHaveAttribute("aria-invalid", "true"));
    expect(input).toHaveAccessibleDescription("사용 상태를 확인해 주세요.");
    expect(input).toHaveFocus();
    expect(input).not.toBeChecked();
    expect(api.saveGroupCodes).toHaveBeenCalledTimes(1);
  });
  it("첫 그룹을 선택하고 SYS가 VAL보다 우선하여 이름만 편집한다", async () => {
    render(<CommonCodesScreen />);
    expect(await screen.findByLabelText("A 이름")).not.toHaveAttribute(
      "readonly",
    );
    expect(
      screen.queryByRole("button", { name: "수당코드 추가" }),
    ).not.toBeInTheDocument();
    expect(screen.getByLabelText("A 사용")).toBeDisabled();
    expect(
      screen.queryByRole("button", { name: "A 삭제" }),
    ).not.toBeInTheDocument();
    expect(api.getGroupCodes).toHaveBeenCalledWith("CONTRACT", 1, 50);
  });
  it("값 모드는 네 열과 기존 값만 편집하고 정밀 문자열을 보낸다", async () => {
    render(<CommonCodesScreen />);
    await userEvent.click(
      await screen.findByRole("button", { name: /수당코드.*1/ }),
    );
    const input = await screen.findByLabelText("A 값");
    expect(
      screen.queryByRole("columnheader", { name: "사용" }),
    ).not.toBeInTheDocument();
    expect(screen.getByLabelText("A 이름")).toHaveAttribute("readonly");
    fireEvent.change(input, { target: { value: "9.1234" } });
    await userEvent.click(screen.getByRole("button", { name: "저장" }));
    await waitFor(() =>
      expect(api.saveGroupCodes).toHaveBeenCalledWith("ALLOWANCE", {
        expectedRevision: "original",
        changes: [{ code: "A", isNew: false, codeVal: "9.1234" }],
      }),
    );
  });
  it("신규 창 추가는 로컬행이며 하단 취소가 복원한다", async () => {
    render(<CommonCodesScreen />);
    await userEvent.click(
      await screen.findByRole("button", { name: /교육.*1/ }),
    );
    await screen.findByLabelText("A 이름");
    await userEvent.click(screen.getByRole("button", { name: "코드 추가" }));
    await userEvent.type(screen.getByRole("textbox", { name: "코드" }), "NEW");
    await userEvent.type(
      screen.getByRole("textbox", { name: "이름" }),
      "새 이름",
    );
    await userEvent.click(screen.getByRole("button", { name: "추가" }));
    expect(await screen.findByText("NEW")).toBeInTheDocument();
    expect(api.saveGroupCodes).not.toHaveBeenCalled();
    await userEvent.click(screen.getByRole("button", { name: "취소" }));
    expect(screen.queryByText("NEW")).not.toBeInTheDocument();
  });
  it("미저장 그룹 추가는 계속 작성으로 입력을 보존하고 POST하지 않는다", async () => {
    render(<CommonCodesScreen />);
    const input = await screen.findByLabelText("A 이름");
    fireEvent.change(input, { target: { value: "계속 작성 내용" } });
    await userEvent.click(screen.getByRole("button", { name: "그룹 추가" }));
    await userEvent.click(screen.getByRole("button", { name: "계속 작성" }));
    expect(screen.getByLabelText("A 이름")).toHaveValue("계속 작성 내용");
    expect(api.createCodeGroup).not.toHaveBeenCalled();
  });
  it("저장 실패에 입력을 보존하고 자동 재시도하지 않는다", async () => {
    vi.mocked(api.saveGroupCodes).mockRejectedValue(
      new Error("다른 사용자가 변경했습니다."),
    );
    render(<CommonCodesScreen />);
    fireEvent.change(await screen.findByLabelText("A 이름"), {
      target: { value: "보존" },
    });
    await userEvent.click(screen.getByRole("button", { name: "저장" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "다른 사용자가 변경했습니다.",
    );
    expect(screen.getByLabelText("A 이름")).toHaveValue("보존");
    expect(api.saveGroupCodes).toHaveBeenCalledTimes(1);
  });
  it("빈 목록과 조회 실패를 별도로 표시한다", async () => {
    vi.mocked(api.getGroupCodes).mockResolvedValue({
      items: [],
      total: 0,
      page: 1,
      size: 50,
      groupRevision: "empty",
    });
    render(<CommonCodesScreen />);
    expect(
      await screen.findByText("등록된 코드가 없습니다."),
    ).toBeInTheDocument();
  });
  it("다른 페이지 편집을 보존하고 첫 편집 revision으로 변경분만 보낸다", async () => {
    vi.mocked(api.getGroupCodes).mockImplementation(
      async (_group, page = 1) => ({
        items: [page === 1 ? row : { ...row, code: "B", name: "다른 페이지" }],
        total: 51,
        page,
        size: 50,
        groupRevision: page === 1 ? "original" : "external-change",
      }),
    );
    render(<CommonCodesScreen />);
    fireEvent.change(await screen.findByLabelText("A 이름"), {
      target: { value: "첫 페이지 편집" },
    });
    await userEvent.click(screen.getByRole("button", { name: "다음 페이지" }));
    fireEvent.change(await screen.findByLabelText("B 이름"), {
      target: { value: "다른 페이지 편집" },
    });
    await userEvent.click(screen.getByRole("button", { name: "이전 페이지" }));
    expect(await screen.findByLabelText("A 이름")).toHaveValue(
      "첫 페이지 편집",
    );
    await userEvent.click(screen.getByRole("button", { name: "저장" }));
    await waitFor(() =>
      expect(api.saveGroupCodes).toHaveBeenCalledWith("CONTRACT", {
        expectedRevision: "original",
        changes: [
          { code: "A", isNew: false, name: "첫 페이지 편집" },
          { code: "B", isNew: false, name: "다른 페이지 편집" },
        ],
      }),
    );
  });
  it("조회 실패는 자동 재시도하지 않으며 수동 다시 조회로 회복한다", async () => {
    vi.mocked(api.getGroupCodes).mockRejectedValueOnce(new Error("연결 실패"));
    render(<CommonCodesScreen />);
    expect(await screen.findByRole("alert")).toHaveTextContent("연결 실패");
    expect(api.getGroupCodes).toHaveBeenCalledTimes(1);
    await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
    expect(await screen.findByLabelText("A 이름")).toBeInTheDocument();
  });
  it("그룹 추가 성공은 재조회 후 새 그룹을 선택한다", async () => {
    const created = {
      ...groups[1],
      groupId: "NEW_GROUP",
      name: "새 그룹",
      codeCount: 0,
    };
    vi.mocked(api.createCodeGroup).mockResolvedValue(undefined);
    render(<CommonCodesScreen />);
    await screen.findByLabelText("A 이름");
    vi.mocked(api.getCodeGroups).mockResolvedValue({
      items: [...groups, created],
    });
    await userEvent.click(screen.getByRole("button", { name: "그룹 추가" }));
    await userEvent.type(
      screen.getByRole("textbox", { name: "그룹 ID" }),
      "new_group",
    );
    await userEvent.type(
      screen.getByRole("textbox", { name: "그룹 이름" }),
      "새 그룹",
    );
    await userEvent.click(screen.getByRole("button", { name: "추가" }));
    await waitFor(() =>
      expect(api.createCodeGroup).toHaveBeenCalledWith({
        groupId: "NEW_GROUP",
        name: "새 그룹",
      }),
    );
    await waitFor(() =>
      expect(api.getGroupCodes).toHaveBeenCalledWith("NEW_GROUP", 1, 50),
    );
  });
  it("일반 삭제는 별도 확인 전 요청하지 않고 성공 뒤 재조회한다", async () => {
    render(<CommonCodesScreen />);
    await userEvent.click(
      await screen.findByRole("button", { name: /교육.*1/ }),
    );
    await userEvent.click(
      await screen.findByRole("button", { name: "A 삭제" }),
    );
    expect(api.deleteGroupCode).not.toHaveBeenCalled();
    await userEvent.click(screen.getByRole("button", { name: "삭제 확인" }));
    await waitFor(() =>
      expect(api.deleteGroupCode).toHaveBeenCalledWith(
        "EDUCATION",
        "A",
        "original",
      ),
    );
    await waitFor(() =>
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument(),
    );
  });
  it("편집 중 삭제는 먼저 저장 또는 취소를 요구한다", async () => {
    render(<CommonCodesScreen />);
    await userEvent.click(
      await screen.findByRole("button", { name: /교육.*1/ }),
    );
    fireEvent.change(await screen.findByLabelText("A 이름"), {
      target: { value: "보존" },
    });
    await userEvent.click(screen.getByRole("button", { name: "A 삭제" }));
    await userEvent.click(screen.getByRole("button", { name: "계속 작성" }));
    expect(screen.getByLabelText("A 이름")).toHaveValue("보존");
    expect(api.deleteGroupCode).not.toHaveBeenCalled();
  });
  it("저장 commit 후 그룹 재조회 실패를 저장 실패와 구분한다", async () => {
    render(<CommonCodesScreen />);
    fireEvent.change(await screen.findByLabelText("A 이름"), {
      target: { value: "저장 내용" },
    });
    vi.mocked(api.getCodeGroups).mockRejectedValue(new Error("재조회 실패"));
    await userEvent.click(screen.getByRole("button", { name: "저장" }));
    expect(await screen.findByRole("status")).toHaveTextContent(
      "저장됐지만 다시 불러오지 못했습니다.",
    );
    expect(api.saveGroupCodes).toHaveBeenCalledTimes(1);
    expect(screen.getByRole("button", { name: "저장" })).toBeDisabled();
  });
  it("단순 탭 이동 계속 작성은 입력을 보존한다", async () => {
    render(<CommonCodesScreen panels={{ history: <p>이력 패널</p> }} />);
    fireEvent.change(await screen.findByLabelText("A 이름"), {
      target: { value: "보존" },
    });
    await userEvent.click(screen.getByRole("button", { name: "변경 이력" }));
    await userEvent.click(screen.getByRole("button", { name: "계속 작성" }));
    expect(screen.getByLabelText("A 이름")).toHaveValue("보존");
    expect(screen.queryByText("이력 패널")).not.toBeInTheDocument();
  });
  it("공통 열·밀도 설정은 읽기전용 및 필수열 계약을 유지한다", async () => {
    const open = vi.fn();
    render(
      <CommonCodesScreen
        dense
        columnView={{
          order: ["name", "code", "useYn", "sortOrder"],
          hidden: ["name", "sortOrder"],
        }}
        onColumnsChange={open}
      />,
    );
    await screen.findByLabelText("A 이름");
    expect(screen.getByRole("table")).toHaveClass("sicost-dense");
    expect(
      screen.getByRole("columnheader", { name: "이름" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("columnheader", { name: "순서" }),
    ).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "열 설정" }));
    expect(open).toHaveBeenCalledTimes(1);
  });
  it("수당 신규 빈값은 로컬 0으로 정리하고 저장 전 API를 쓰지 않는다", async () => {
    render(<CommonCodesScreen />);
    await userEvent.click(
      await screen.findByRole("button", { name: /수당코드.*1/ }),
    );
    await screen.findByLabelText("A 값");
    await userEvent.click(
      screen.getByRole("button", { name: "수당코드 추가" }),
    );
    fireEvent.change(screen.getByLabelText(/^코드 \*/), {
      target: { value: "B" },
    });
    fireEvent.change(screen.getByLabelText(/^이름 \*/), {
      target: { value: "추가 수당" },
    });
    fireEvent.change(screen.getByLabelText("값", { exact: true }), {
      target: { value: "" },
    });
    await userEvent.click(screen.getByRole("button", { name: "추가" }));
    expect(screen.getByLabelText("B 값")).toHaveValue("0");
    expect(api.saveGroupCodes).not.toHaveBeenCalled();
    expect(api.createCodeGroup).not.toHaveBeenCalled();
  });
  it("수당 정밀도 오류는 모달과 입력을 유지하고 값 필드로 안내한다", async () => {
    render(<CommonCodesScreen />);
    await userEvent.click(
      await screen.findByRole("button", { name: /수당코드.*1/ }),
    );
    await screen.findByLabelText("A 값");
    await userEvent.click(
      screen.getByRole("button", { name: "수당코드 추가" }),
    );
    fireEvent.change(screen.getByLabelText(/^코드 \*/), {
      target: { value: "B" },
    });
    fireEvent.change(screen.getByLabelText(/^이름 \*/), {
      target: { value: "추가 수당" },
    });
    fireEvent.change(screen.getByLabelText("값", { exact: true }), {
      target: { value: "1.12345" },
    });
    await userEvent.click(screen.getByRole("button", { name: "추가" }));
    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(screen.getByLabelText("값", { exact: true })).toHaveValue("1.12345");
    expect(screen.getByRole("alert")).toHaveTextContent("소수 4자리");
    expect(api.saveGroupCodes).not.toHaveBeenCalled();
  });
});
