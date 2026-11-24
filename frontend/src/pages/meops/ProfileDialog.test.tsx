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
import { ProfileDialog, ProfileMenu } from "./ProfileDialog";
import * as api from "./profileApi";

vi.mock("./profileApi");
const profile: api.MyProfile = {
  userId: "actual-user",
  userName: "실제 사용자",
  activeRoles: [
    { roleId: "FIN", roleName: "재무" },
    { roleId: "PM", roleName: "프로젝트 관리자" },
  ],
  parts: [
    { partId: "part-a", partName: "영업 파트" },
    { partId: "headquarters", partName: "본사" },
  ],
  basisMonth: "2026-08",
  editGuidance:
    "이름·역할은 관리자, 보유 스킬·거주지는 개발자 풀에서 수정합니다.",
};
beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(api.getMyProfile).mockResolvedValue(structuredClone(profile));
});

it("메뉴를 열 때만 인자 없이 서버 현재주체를 조회한다", async () => {
  render(<ProfileMenu />);
  expect(api.getMyProfile).not.toHaveBeenCalled();
  await userEvent.click(screen.getByRole("button", { name: "내 정보" }));
  const dialog = screen.getByRole("dialog", { name: "내 정보" });
  expect(await within(dialog).findByText("실제 사용자")).toBeInTheDocument();
  expect(api.getMyProfile).toHaveBeenCalledTimes(1);
  expect(api.getMyProfile).toHaveBeenCalledWith();
  expect(within(dialog).getByText("actual-user")).toBeInTheDocument();
  expect(within(dialog).getByText("재무")).toBeInTheDocument();
  expect(within(dialog).getByText("프로젝트 관리자")).toBeInTheDocument();
  expect(within(dialog).getByText("영업 파트")).toBeInTheDocument();
  expect(within(dialog).getByText("본사")).toBeInTheDocument();
  expect(within(dialog).getByText("2026-08")).toBeInTheDocument();
  expect(within(dialog).getByText(profile.editGuidance)).toHaveClass(
    "sicost-tone-info",
  );
  expect(within(dialog).queryByRole("textbox")).not.toBeInTheDocument();
  expect(
    within(dialog).queryByRole("button", {
      name: /저장|수정|비밀번호|로그아웃/,
    }),
  ).not.toBeInTheDocument();
});
it("조회 중에도 닫을 수 있고 가짜 사용자와 소속을 표시하지 않는다", () => {
  vi.mocked(api.getMyProfile).mockReturnValue(new Promise(() => {}));
  const close = vi.fn();
  render(<ProfileDialog open onClose={close} />);
  expect(screen.getByRole("status")).toHaveTextContent(
    "내 정보를 불러오는 중…",
  );
  expect(screen.queryByText(profile.userName)).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "닫기" }));
  expect(close).toHaveBeenCalledTimes(1);
  expect(api.getMyProfile).toHaveBeenCalledTimes(1);
});
it("역할 없는 주체도 차단하지 않고 활성 역할을 대시로 표시한다", async () => {
  vi.mocked(api.getMyProfile).mockResolvedValue({
    ...profile,
    activeRoles: [],
  });
  render(<ProfileDialog open onClose={() => {}} />);
  await screen.findByText(profile.userName);
  expect(screen.getByText("활성 역할").parentElement).toHaveTextContent("–");
  expect(screen.queryByRole("alert")).not.toBeInTheDocument();
});
it("소속이 없으면 회사나 개발자 파트를 합성하지 않고 대시로 표시한다", async () => {
  vi.mocked(api.getMyProfile).mockResolvedValue({ ...profile, parts: [] });
  render(<ProfileDialog open onClose={() => {}} />);
  await screen.findByText(profile.userName);
  expect(screen.getByText("소속 파트").parentElement).toHaveTextContent("–");
  expect(screen.queryByText("본사")).not.toBeInTheDocument();
});
it("실제 사용자 없음404와 문의번호를 표시한다", async () => {
  vi.mocked(api.getMyProfile).mockRejectedValue(
    new ApiError(404, {
      success: false,
      meta: {},
      code: "NOT_FOUND",
      message: "현재 사용자 정보를 찾을 수 없습니다.",
      requestId: "profile-not-found",
    }),
  );
  render(<ProfileDialog open onClose={() => {}} />);
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "현재 사용자 정보를 찾을 수 없습니다.",
  );
  expect(screen.getByRole("alert")).toHaveTextContent("profile-not-found");
  expect(screen.queryByText(profile.userName)).not.toBeInTheDocument();
});
it("서버500은 오류와 재조회를 제공하고 성공하면 실제 결과로 교체한다", async () => {
  vi.mocked(api.getMyProfile).mockRejectedValueOnce(
    new ApiError(500, {
      success: false,
      meta: {},
      code: "INTERNAL_ERROR",
      message: "자료 처리 중 오류가 발생했습니다.",
      requestId: "profile-error",
    }),
  );
  render(<ProfileDialog open onClose={() => {}} />);
  expect(await screen.findByRole("alert")).toHaveTextContent("profile-error");
  expect(api.getMyProfile).toHaveBeenCalledTimes(1);
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  expect(await screen.findByText(profile.userName)).toBeInTheDocument();
  expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  expect(api.getMyProfile).toHaveBeenCalledTimes(2);
});
it("닫기와Esc는 쓰기나 추가조회 없이 초점을 호출 메뉴로 돌린다", async () => {
  render(<ProfileMenu />);
  const trigger = screen.getByRole("button", { name: "내 정보" });
  await userEvent.click(trigger);
  await screen.findByText(profile.userName);
  const dialog = within(screen.getByRole("dialog", { name: "내 정보" }));
  expect(dialog.queryByRole("button", { name: "취소" })).not.toBeInTheDocument();
  await userEvent.click(dialog.getByRole("button", { name: "닫기" }));
  await waitFor(() =>
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument(),
  );
  expect(trigger).toHaveFocus();
  expect(api.getMyProfile).toHaveBeenCalledTimes(1);
  await userEvent.click(trigger);
  await screen.findByText(profile.userName);
  await userEvent.keyboard("{Escape}");
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  expect(trigger).toHaveFocus();
  expect(api.getMyProfile).toHaveBeenCalledTimes(2);
});
it("닫은 뒤 도착한 응답이 재개방된 주체 정보를 덮어쓰지 않는다", async () => {
  let resolve!: (value: api.MyProfile) => void;
  vi.mocked(api.getMyProfile).mockReturnValueOnce(
    new Promise((done) => {
      resolve = done;
    }),
  );
  const { rerender } = render(<ProfileDialog open onClose={() => {}} />);
  rerender(<ProfileDialog open={false} onClose={() => {}} />);
  rerender(<ProfileDialog open onClose={() => {}} />);
  await screen.findByText(profile.userName);
  await act(async () => {
    resolve({ ...profile, userName: "이전 응답" });
  });
  expect(screen.queryByText("이전 응답")).not.toBeInTheDocument();
  expect(screen.getByText(profile.userName)).toBeInTheDocument();
});
it("서버 문자열은HTML이 아닌 텍스트로 표시한다", async () => {
  vi.mocked(api.getMyProfile).mockResolvedValue({
    ...profile,
    userName: "<script>name</script>",
  });
  render(<ProfileDialog open onClose={() => {}} />);
  expect(await screen.findByText("<script>name</script>")).toBeInTheDocument();
  expect(
    screen.getByTestId("profile-content").querySelector("script"),
  ).toBeNull();
});
