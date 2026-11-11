import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { ApiError } from "../../shared/api";
import { BusinessUsersScreen } from "./BusinessUsersScreen";
import * as api from "./businessUsersApi";
vi.mock("./businessUsersApi");
const user: api.BusinessUser = {
  userId: "ab12",
  name: "업무 담당",
  email: "user@example.org",
  roles: ["관리자", "프로젝트 담당"],
  businessRoleIds: ["PM"],
  status: "USE",
  lastLoginAt: null,
  revision: "raw-revision",
};
function error(code: string, field?: string) {
  return new ApiError(409, {
    success: false,
    meta: {},
    code,
    message: "영업 파트에서 먼저 파트장을 바꾸세요.",
    requestId: "trace",
    field,
  });
}
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api.getBusinessUsers).mockResolvedValue({
    items: [user],
    total: 1,
    page: 1,
    size: 50,
  });
  vi.mocked(api.getBusinessUser).mockResolvedValue(user);
  vi.mocked(api.getBusinessRoleOptions).mockResolvedValue({
    items: [],
    roleOptions: [
      { roleId: "PM", name: "프로젝트 담당" },
      { roleId: "SALES_LDR", name: "영업 파트장" },
    ],
  });
  vi.mocked(api.getBusinessUserHistory).mockResolvedValue({
    items: [],
    total: 0,
    page: 1,
    size: 50,
  });
  vi.mocked(api.createBusinessUser).mockResolvedValue({
    changed: true,
    message: "등록했습니다.",
    user,
  });
  vi.mocked(api.putBusinessUser).mockResolvedValue({
    changed: true,
    message: "저장했습니다.",
    user: { ...user, name: "새 이름" },
  });
});
async function openCreate() {
  await screen.findByText("ab12");
  await userEvent.click(screen.getByRole("button", { name: "등록" }));
  return screen.getByRole("dialog", { name: "사용자 등록" });
}
async function fillCreate(dialog: HTMLElement) {
  fireEvent.change(within(dialog).getByLabelText("아이디"), {
    target: { value: "new123" },
  });
  fireEvent.change(within(dialog).getByLabelText("이름"), {
    target: { value: "새 사용자" },
  });
  fireEvent.change(within(dialog).getByLabelText("이메일"), {
    target: { value: "new@example.org" },
  });
  await within(dialog).findByLabelText("프로젝트 담당");
}
it("초기 조건 전체와 전체 역할·상태별 배지 및 NULL 접속을 표시한다", async () => {
  vi.mocked(api.getBusinessUsers).mockResolvedValue({
    items: [
      user,
      { ...user, userId: "cd12", status: "LOCK" },
      { ...user, userId: "ef12", status: "STOP" },
    ],
    total: 3,
    page: 1,
    size: 50,
  });
  render(<BusinessUsersScreen />);
  await screen.findByText("ab12");
  expect(screen.getByLabelText("역할")).toHaveValue("");
  expect(screen.getByLabelText("상태")).toHaveValue("");
  expect(screen.getByText("사용", { selector: "span" })).toHaveClass(
    "sicost-tone-ok",
  );
  expect(screen.getByText("잠김", { selector: "span" })).toHaveClass(
    "sicost-tone-warn",
  );
  expect(screen.getByText("중지", { selector: "span" })).toHaveClass(
    "sicost-tone-lock",
  );
  expect(screen.getAllByText("관리자")[0]).toHaveClass("sicost-tone-info");
  expect(screen.getAllByText("–")).toHaveLength(3);
  expect(api.getBusinessUsers).toHaveBeenCalledWith({
    page: 1,
    size: 50,
    sort: "name",
    direction: "ASC",
  });
  expect(
    screen.queryByRole("button", { name: "엑셀 다운로드" }),
  ).not.toBeInTheDocument();
});
it("검색은 입력 중 호출하지 않고 Enter로 이름·역할·상태를 함께 적용하고 초기화한다", async () => {
  render(<BusinessUsersScreen />);
  await screen.findByText("ab12");
  fireEvent.change(screen.getByPlaceholderText("이름 또는 아이디"), {
    target: { value: " 업무 " },
  });
  fireEvent.change(screen.getByLabelText("역할"), { target: { value: "PM" } });
  fireEvent.change(screen.getByLabelText("상태"), {
    target: { value: "LOCK" },
  });
  expect(api.getBusinessUsers).toHaveBeenCalledTimes(1);
  await userEvent.click(screen.getByRole("button", { name: "조회" }));
  await waitFor(() =>
    expect(api.getBusinessUsers).toHaveBeenLastCalledWith(
      expect.objectContaining({
        q: "업무",
        businessRole: "PM",
        status: "LOCK",
        page: 1,
      }),
    ),
  );
  await userEvent.click(screen.getByRole("button", { name: "초기화" }));
  await waitFor(() =>
    expect(api.getBusinessUsers).toHaveBeenLastCalledWith({
      page: 1,
      size: 50,
      sort: "name",
      direction: "ASC",
    }),
  );
});
it("빈 목록과 오류·로딩을 구별하고 실패 조회만 수동 재시도한다", async () => {
  let resolve!: (value: api.BusinessUsersPage) => void;
  vi.mocked(api.getBusinessUsers).mockReturnValueOnce(
    new Promise((yes) => {
      resolve = yes;
    }),
  );
  render(<BusinessUsersScreen />);
  expect(screen.getByRole("status")).toHaveTextContent("불러오는 중");
  await act(async () => resolve({ items: [], total: 0, page: 1, size: 50 }));
  expect(
    await screen.findByText("조회된 사용자가 없습니다."),
  ).toBeInTheDocument();
  vi.mocked(api.getBusinessUsers).mockRejectedValueOnce(new Error("조회 실패"));
  await userEvent.click(screen.getByRole("button", { name: "조건 초기화" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("조회 실패");
  expect(
    screen.queryByText("조회된 사용자가 없습니다."),
  ).not.toBeInTheDocument();
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  await screen.findByText("ab12");
  expect(api.createBusinessUser).not.toHaveBeenCalled();
});
it("행 상세는 기본정보와 USER 변경이력을 조회하고 상태와 인증은 편집하지 않는다", async () => {
  render(<BusinessUsersScreen />);
  await userEvent.click(await screen.findByRole("button", { name: "ab12" }));
  const dialog = screen.getByRole("dialog", { name: "사용자 상세" });
  expect(
    await within(dialog).findByText("user@example.org"),
  ).toBeInTheDocument();
  expect(api.getBusinessUser).toHaveBeenCalledWith("ab12");
  await userEvent.click(within(dialog).getByRole("tab", { name: "변경이력" }));
  expect(
    await within(dialog).findByText("변경 이력이 없습니다."),
  ).toBeInTheDocument();
  expect(api.getBusinessUserHistory).toHaveBeenCalledWith("ab12", 1);
  await userEvent.click(within(dialog).getByRole("button", { name: "수정" }));
  const form = screen.getByRole("dialog", { name: "사용자 수정" });
  expect(within(form).getByLabelText("아이디")).toHaveAttribute("readonly");
  expect(within(form).queryByLabelText("상태")).not.toBeInTheDocument();
  expect(within(form).queryByLabelText("비밀번호")).not.toBeInTheDocument();
});
it("신규는 DB 표시명과 PM만 기본 체크하고 등록 후 목록·상세를 갱신한다", async () => {
  render(<BusinessUsersScreen />);
  const dialog = await openCreate();
  await fillCreate(dialog);
  expect(within(dialog).getByLabelText("프로젝트 담당")).toBeChecked();
  expect(within(dialog).getByLabelText("영업 파트장")).not.toBeChecked();
  await userEvent.click(within(dialog).getByRole("button", { name: "저장" }));
  await waitFor(() =>
    expect(api.createBusinessUser).toHaveBeenCalledWith({
      userId: "new123",
      name: "새 사용자",
      email: "new@example.org",
      businessRoleIds: ["PM"],
    }),
  );
  expect(await screen.findByText("등록했습니다.")).toBeInTheDocument();
  expect(api.notifyBusinessUsersCommitted).toHaveBeenCalledTimes(1);
  await waitFor(() => expect(api.getBusinessUsers).toHaveBeenCalledTimes(2));
  expect(
    await screen.findByRole("dialog", { name: "사용자 상세" }),
  ).toBeInTheDocument();
});
it("신규 필수·형식 오류는 필드별 안내하고 저장 요청을 보내지 않는다", async () => {
  render(<BusinessUsersScreen />);
  const dialog = await openCreate();
  await within(dialog).findByLabelText("프로젝트 담당");
  await userEvent.click(within(dialog).getByRole("button", { name: "저장" }));
  expect(within(dialog).getByLabelText("아이디")).toHaveAttribute(
    "aria-invalid",
    "true",
  );
  expect(within(dialog).getByLabelText("이름")).toHaveAttribute(
    "aria-invalid",
    "true",
  );
  expect(within(dialog).getByLabelText("이메일")).toHaveAttribute(
    "aria-invalid",
    "true",
  );
  expect(api.createBusinessUser).not.toHaveBeenCalled();
});
it("등록 실패는 입력을 보존하며 성공 토스트와 자동 재시도는 없다", async () => {
  vi.mocked(api.createBusinessUser).mockRejectedValue(
    new ApiError(500, {
      success: false,
      meta: {},
      code: "INTERNAL_ERROR",
      message: "접근기록 저장 실패",
      requestId: "trace",
    }),
  );
  render(<BusinessUsersScreen />);
  const dialog = await openCreate();
  await fillCreate(dialog);
  await userEvent.click(within(dialog).getByRole("button", { name: "저장" }));
  expect(await within(dialog).findByRole("alert")).toHaveTextContent(
    "접근기록 저장 실패",
  );
  expect(within(dialog).getByLabelText("아이디")).toHaveValue("new123");
  expect(api.createBusinessUser).toHaveBeenCalledTimes(1);
  expect(screen.queryByText("등록했습니다.")).not.toBeInTheDocument();
  expect(api.getBusinessUsers).toHaveBeenCalledTimes(1);
});
it("미저장 취소는 계속 작성과 나가기로 나뉘며 저장하지 않는다", async () => {
  render(<BusinessUsersScreen />);
  const dialog = await openCreate();
  fireEvent.change(within(dialog).getByLabelText("이름"), {
    target: { value: "작성 중" },
  });
  await userEvent.click(within(dialog).getByRole("button", { name: "취소" }));
  await userEvent.click(screen.getByRole("button", { name: "계속 작성" }));
  expect(within(dialog).getByLabelText("이름")).toHaveValue("작성 중");
  await userEvent.click(within(dialog).getByRole("button", { name: "취소" }));
  await userEvent.click(screen.getByRole("button", { name: "나가기" }));
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  expect(api.createBusinessUser).not.toHaveBeenCalled();
});
it("수정은 아이디 없이 최신 revision을 보내며 서버 충돌에 입력을 유지한다", async () => {
  vi.mocked(api.putBusinessUser).mockRejectedValue(
    error("USER_IS_PART_LEADER", "businessRoleIds"),
  );
  render(<BusinessUsersScreen />);
  await userEvent.click(await screen.findByRole("button", { name: "ab12" }));
  const detail = screen.getByRole("dialog");
  await within(detail).findByText("user@example.org");
  await userEvent.click(within(detail).getByRole("button", { name: "수정" }));
  const form = screen.getByRole("dialog", { name: "사용자 수정" });
  await within(form).findByLabelText("프로젝트 담당");
  fireEvent.change(within(form).getByLabelText("이름"), {
    target: { value: "새 이름" },
  });
  await userEvent.click(within(form).getByRole("button", { name: "저장" }));
  await waitFor(() =>
    expect(api.putBusinessUser).toHaveBeenCalledWith("ab12", {
      name: "새 이름",
      email: user.email,
      businessRoleIds: ["PM"],
      expectedRevision: "raw-revision",
    }),
  );
  expect(within(form).getByLabelText("이름")).toHaveValue("새 이름");
  expect(api.putBusinessUser).toHaveBeenCalledTimes(1);
  expect(api.notifyBusinessUsersCommitted).not.toHaveBeenCalled();
});
it("저장 성공 후 조회 실패는 재저장 없이 다시 조회만 제공한다", async () => {
  vi.mocked(api.getBusinessUsers)
    .mockResolvedValueOnce({ items: [user], total: 1, page: 1, size: 50 })
    .mockRejectedValueOnce(new Error("후속 조회 실패"));
  render(<BusinessUsersScreen />);
  const dialog = await openCreate();
  await fillCreate(dialog);
  await userEvent.click(within(dialog).getByRole("button", { name: "저장" }));
  expect(
    await screen.findByText("저장됐지만 다시 불러오지 못했습니다."),
  ).toBeInTheDocument();
  expect(api.createBusinessUser).toHaveBeenCalledTimes(1);
});
it("역할 옵션 실패와 이력 실패는 가짜 항목을 만들지 않는다", async () => {
  vi.mocked(api.getBusinessRoleOptions).mockRejectedValue(
    new Error("역할 조회 실패"),
  );
  render(<BusinessUsersScreen />);
  const dialog = await openCreate();
  expect(await within(dialog).findByRole("alert")).toHaveTextContent(
    "역할 조회 실패",
  );
  expect(within(dialog).queryByRole("checkbox")).not.toBeInTheDocument();
});
it("열 순서·필수열과 밀도·헤더 정렬을 유지한다", async () => {
  render(
    <BusinessUsersScreen
      dense
      columnView={{
        order: ["name", "userId", "email", "roles", "status", "lastLoginAt"],
        hidden: ["email", "userId"],
      }}
    />,
  );
  await screen.findByText("ab12");
  const headers = screen.getAllByRole("columnheader");
  expect(headers[0]).toHaveTextContent("이름");
  expect(headers[1]).toHaveTextContent("아이디");
  expect(
    screen.queryByRole("columnheader", { name: "이메일" }),
  ).not.toBeInTheDocument();
  expect(screen.getByRole("table")).toHaveClass("sicost-dense");
  await userEvent.click(screen.getByRole("button", { name: "이름 정렬" }));
  await waitFor(() =>
    expect(api.getBusinessUsers).toHaveBeenLastCalledWith(
      expect.objectContaining({ sort: "name", direction: "DESC", page: 1 }),
    ),
  );
});
