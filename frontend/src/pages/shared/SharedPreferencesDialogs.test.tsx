import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, expect, it, vi } from "vitest";
import { SharedApplication } from "./SharedApplication";
import { SharedScreenContext } from "./SharedScreenContext";
import { FinancialItemsScreen } from "./FinancialItemsScreen";
import * as api from "./sharedApi";
vi.mock("./sharedApi");
const child = (screenId: string, name: string, pcOnly = false) => ({
  menuId: screenId,
  parentId: "G",
  name,
  screenId,
  sortOrder: 1,
  icon: null,
  pcOnly,
  children: [],
});
const menus = [
  {
    menuId: "G",
    parentId: null,
    name: "DB 묶음",
    screenId: null,
    sortOrder: 1,
    icon: "folder",
    pcOnly: false,
    children: [
      child("UI-16", "대시보드"),
      child("UI-01", "프로젝트"),
      child("UI-10", "재무 항목", true),
    ],
  },
];
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api.getMenus).mockResolvedValue({ items: menus, diagnostics: [] });
  vi.mocked(api.getCurrentUser).mockResolvedValue({
    userId: "admin",
    name: "임시 관리자",
    roleIds: ["ADMIN"],
    developmentMode: true,
  });
  vi.mocked(api.getSharedPreferences).mockResolvedValue({
    items: [
      {
        type: "FAV",
        key: "*",
        value: ["UI-16", "UI-01"],
        revision: "fav",
        stored: true,
      },
    ],
  });
  vi.mocked(api.getPeriodContext).mockResolvedValue({
    currentDate: "2026-11-10",
    currentMonth: "2026-11",
    timezone: "Asia/Seoul",
    cashBaseMonth: "2026-01",
    monthClose: { month: "2026-11", status: "작업중" },
  });
  vi.mocked(api.getMonthClose).mockResolvedValue({
    items: [{ month: "2026-11", status: "작업중" }],
  });
  vi.mocked(api.putSharedPreference).mockImplementation(
    async (type, key, value) => ({
      changed: true,
      preference: { type, key, value, revision: "new", stored: true },
    }),
  );
});
it("화면 설정의 명시 변경만 해당 설정을 저장하고 성공 후 표시한다", async () => {
  render(
    <MemoryRouter initialEntries={["/UI-16"]}>
      <SharedApplication />
    </MemoryRouter>,
  );
  await screen.findByRole("heading", { name: "대시보드" });
  await userEvent.click(screen.getByRole("button", { name: "임시 관리자" }));
  await userEvent.click(screen.getByRole("button", { name: "화면 설정" }));
  const dialog = screen.getByRole("dialog");
  expect(api.putSharedPreference).not.toHaveBeenCalledWith(
    "PAGE_SIZE",
    expect.anything(),
    expect.anything(),
    expect.anything(),
  );
  fireEvent.change(within(dialog).getByLabelText("한 화면 건수"), {
    target: { value: "20" },
  });
  await waitFor(() =>
    expect(api.putSharedPreference).toHaveBeenCalledWith(
      "PAGE_SIZE",
      "*",
      20,
      null,
    ),
  );
  expect(within(dialog).getByLabelText("한 화면 건수")).toHaveValue("20");
});
it("즐겨찾기 재정렬과 해제는 서버 성공 후 순서를 적용한다", async () => {
  render(
    <MemoryRouter initialEntries={["/UI-16"]}>
      <SharedApplication />
    </MemoryRouter>,
  );
  await screen.findByRole("heading", { name: "대시보드" });
  await userEvent.click(screen.getByRole("button", { name: "편집" }));
  const dialog = screen.getByRole("dialog");
  await userEvent.click(
    within(dialog).getByRole("button", { name: "프로젝트 위로" }),
  );
  await waitFor(() =>
    expect(api.putSharedPreference).toHaveBeenCalledWith(
      "FAV",
      "*",
      ["UI-01", "UI-16"],
      "fav",
    ),
  );
  await userEvent.click(
    within(dialog).getAllByRole("button", { name: "별 해제" })[0],
  );
  await waitFor(() =>
    expect(api.putSharedPreference).toHaveBeenCalledWith(
      "FAV",
      "*",
      ["UI-16"],
      "new",
    ),
  );
});
it("실패한 별 해제는 원래 즐겨찾기를 보존한다", async () => {
  vi.mocked(api.putSharedPreference).mockImplementation(
    async (type, key, value) => {
      if (type === "FAV") throw new Error("별 저장 실패");
      return {
        changed: true,
        preference: { type, key, value, revision: "new", stored: true },
      };
    },
  );
  render(
    <MemoryRouter initialEntries={["/UI-16"]}>
      <SharedApplication />
    </MemoryRouter>,
  );
  await screen.findByRole("heading", { name: "대시보드" });
  await userEvent.click(screen.getByRole("button", { name: "즐겨찾기 해제" }));
  await screen.findByText("별 저장 실패");
  expect(screen.getByRole("button", { name: "즐겨찾기 해제" })).toHaveAttribute(
    "aria-pressed",
    "true",
  );
});
it("PC 전용 화면은 안내·뒤로·홈과 보존되는 본문 컨테이너를 가진다", () => {
  render(
    <MemoryRouter>
      <SharedScreenContext.Provider
        value={{ menus, favorites: [], star: vi.fn(), pending: false }}
      >
        <FinancialItemsScreen />
      </SharedScreenContext.Provider>
    </MemoryRouter>,
  );
  expect(screen.getByText("PC에서 이용해 주세요.")).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "뒤로" })).toBeInTheDocument();
  expect(screen.getByRole("link", { name: "대시보드" })).toHaveAttribute(
    "href",
    "/UI-16",
  );
  expect(
    screen
      .getByText("이 화면은 다음 단계에서 만듭니다.")
      .closest(".sicost-pc-body"),
  ).toHaveClass("sicost-pc-body");
});
