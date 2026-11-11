import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApplicationRoutes, applicationRoutes } from "./router";
import { screenRegistry } from "../pages/shared/screenRegistry";
import * as shared from "../pages/shared/sharedApi";
import * as prefs from "../pages/me/preferencesApi";
import * as codes from "../pages/codegroups/codeGroupsApi";
import * as mappings from "../pages/codemappings/codeMappingsApi";
import * as standards from "../pages/operatingstandards/operatingStandardsApi";

vi.mock("../pages/shared/sharedApi");
vi.mock("../pages/me/preferencesApi", async (original) => ({
  ...(await original<typeof import("../pages/me/preferencesApi")>()),
  getPreferences: vi.fn(),
  putPreference: vi.fn(),
  deletePreference: vi.fn(),
  preferencesApi: {
    getPreferences: (...args: []) => prefs.getPreferences(...args),
    putPreference: (...args: Parameters<typeof prefs.putPreference>) =>
      prefs.putPreference(...args),
    deletePreference: (...args: Parameters<typeof prefs.deletePreference>) =>
      prefs.deletePreference(...args),
  },
}));
vi.mock("../pages/codegroups/codeGroupsApi", async (original) => ({
  ...(await original<typeof import("../pages/codegroups/codeGroupsApi")>()),
  getCodeGroups: vi.fn(),
  getGroupCodes: vi.fn(),
  saveGroupCodes: vi.fn(),
}));
vi.mock("../pages/codemappings/codeMappingsApi", async (original) => ({
  ...(await original<typeof import("../pages/codemappings/codeMappingsApi")>()),
  getCodeMappings: vi.fn(),
  getMappingGroups: vi.fn(),
}));
vi.mock(
  "../pages/operatingstandards/operatingStandardsApi",
  async (original) => ({
    ...(await original<
      typeof import("../pages/operatingstandards/operatingStandardsApi")
    >()),
    getOperatingStandards: vi.fn(),
    getStandardsPeriodContext: vi.fn(),
    getStandardsMonthClose: vi.fn(),
  }),
);

const context = {
  currentDate: "2026-11-10",
  currentMonth: "2026-11",
  timezone: "Asia/Seoul",
  cashBaseMonth: "2026-01",
  monthClose: { month: "2026-11", status: "작업중" },
};
const menus: shared.MenuNode[] = [
  {
    menuId: "SYSTEM",
    parentId: null,
    name: "시스템",
    screenId: null,
    sortOrder: 1,
    icon: null,
    pcOnly: false,
    children: Object.keys(screenRegistry).map((id, index) => ({
      menuId: `M${index}`,
      parentId: "SYSTEM",
      name: `DB ${id}`,
      screenId: id,
      sortOrder: index,
      icon: null,
      pcOnly: ["UI-10", "UI-14", "UI-23", "UI-24", "UI-25", "UI-27"].includes(
        id,
      ),
      children: [],
    })),
  },
];
let preferences: prefs.Preference[];
const group: codes.CodeGroup = {
  groupId: "EDUCATION",
  name: "교육",
  sysCodeYn: "N",
  valCodeYn: "N",
  useYn: "Y",
  sortOrder: 1,
  codeCount: 1,
};
const row: codes.Code = {
  code: "A",
  name: "기존 이름",
  sortOrder: 1,
  useYn: "Y",
  addVal1: null,
  addVal2: null,
  addVal3: null,
  codeVal: null,
  unitNm: null,
};
const columnViews = {
  codes: {
    order: ["code", "name", "addVal1", "addVal2", "sortOrder", "useYn"],
    hidden: [],
  },
  valueCodes: { order: ["code", "name", "codeVal", "unitNm"], hidden: [] },
  mappings: { order: ["sourceText", "groupId", "code"], hidden: [] },
  standards: {
    order: ["key", "name", "value", "unitNm", "effectiveMonth", "description"],
    hidden: [],
  },
  history: {
    order: [
      "occurredAt",
      "target",
      "groupId",
      "entityKey",
      "type",
      "item",
      "before",
      "after",
      "actor",
    ],
    hidden: [],
  },
};
const open = (path = "/UI-27") =>
  render(
    <MemoryRouter initialEntries={[path]}>
      <ApplicationRoutes />
    </MemoryRouter>,
  );
beforeEach(() => {
  vi.resetAllMocks();
  preferences = Object.entries({
    FAV: [],
    RECENT: [],
    MENU_FOLD: false,
    PAGE_SIZE: 50,
    DENSE: "넓게",
    AMT_UNIT: "천원",
  }).map(([type, value]) => ({
    type: type as prefs.PreferenceType,
    key: "*",
    value,
    revision: null,
    stored: false,
  }));
  preferences.push({
    type: "COLS",
    key: "UI-27",
    value: columnViews,
    revision: null,
    stored: false,
  });
  vi.mocked(prefs.getPreferences).mockImplementation(async () => ({
    items: preferences,
  }));
  vi.mocked(prefs.putPreference).mockImplementation(
    async (type, key, value) => {
      const preference = { type, key, value, revision: "saved", stored: true };
      preferences = preferences.map((item) =>
        item.type === type && item.key === key ? preference : item,
      );
      return { changed: true, preference };
    },
  );
  vi.mocked(shared.getSharedPreferences).mockImplementation(async () => ({
    items: preferences,
  }));
  vi.mocked(shared.getMenus).mockResolvedValue({
    items: menus,
    diagnostics: [],
  });
  vi.mocked(shared.getCurrentUser).mockResolvedValue({
    userId: "admin",
    name: "임시 관리자",
    roleIds: ["ADMIN"],
    developmentMode: true,
  });
  vi.mocked(shared.getPeriodContext).mockResolvedValue(context);
  vi.mocked(shared.getMonthClose).mockResolvedValue({
    items: [context.monthClose],
  });
  vi.mocked(shared.getCommonCodeHistory).mockResolvedValue({
    items: [],
    total: 0,
    page: 1,
    size: 50,
  });
  vi.mocked(codes.getCodeGroups).mockResolvedValue({ items: [group] });
  vi.mocked(codes.getGroupCodes).mockResolvedValue({
    items: [row],
    total: 1,
    page: 1,
    size: 50,
    groupRevision: "original",
  });
  vi.mocked(mappings.getMappingGroups).mockResolvedValue({ items: [group] });
  vi.mocked(mappings.getCodeMappings).mockResolvedValue({
    items: [],
    total: 0,
    page: 1,
    size: 50,
  });
  vi.mocked(standards.getOperatingStandards).mockResolvedValue({ items: [] });
  vi.mocked(standards.getStandardsPeriodContext).mockResolvedValue(context);
  vi.mocked(standards.getStandardsMonthClose).mockResolvedValue({ items: [] });
});
describe("공식 진입점 합성", () => {
  it.each(Object.keys(screenRegistry))(
    "%s는 등록된 실제 화면으로 진입한다",
    async (id) => {
      open("/" + id);
      await screen.findByRole("heading", { name: `DB ${id}` });
      expect(applicationRoutes.some((route) => route.path === "/" + id)).toBe(
        true,
      );
      if (id === "UI-27") {
        expect(await screen.findByLabelText("A 이름")).toBeInTheDocument();
        expect(
          screen.getAllByRole("navigation", { name: "공통코드 하위 탭" }),
        ).toHaveLength(1);
        expect(
          screen.queryByText("화면 구성 요소가 아직 연결되지 않았습니다."),
        ).not.toBeInTheDocument();
      } else
        expect(
          screen
            .getAllByText("이 화면은 다음 단계에서 만듭니다.")
            .some((node) => !node.closest("[hidden]")),
        ).toBe(true);
    },
  );
  it.each(["/", "/#UI-01", "/UI-20", "/unknown"])(
    "%s는 DB 메뉴에 맞춰 복구한다",
    async (path) => {
      open(path);
      await screen.findByRole("heading", {
        name: path.includes("#UI-01") ? "DB UI-01" : "DB UI-16",
      });
      expect(applicationRoutes.some((route) => route.path === "/UI-20")).toBe(
        false,
      );
    },
  );
  it("UI-27 네 탭의 실제 기능 본문을 연결하고 추가 route를 만들지 않는다", async () => {
    open();
    await screen.findByLabelText("A 이름");
    const tabs = screen.getByRole("navigation", { name: "공통코드 하위 탭" });
    await userEvent.click(
      within(tabs).getByRole("button", { name: "엑셀 표기 대응" }),
    );
    await screen.findByText("등록된 표기가 없습니다.");
    expect(mappings.getCodeMappings).toHaveBeenCalled();
    await userEvent.click(
      within(tabs).getByRole("button", { name: "운영 기준값" }),
    );
    await waitFor(() =>
      expect(standards.getOperatingStandards).toHaveBeenCalled(),
    );
    await userEvent.click(
      within(tabs).getByRole("button", { name: "변경 이력" }),
    );
    await screen.findByText("조회된 변경 이력이 없습니다.");
    expect(screen.getAllByRole("heading", { name: "DB UI-27" })).toHaveLength(
      1,
    );
  });
  it("일반 화면 전환은 편집을 보존하고 닫기 취소도 입력을 보존한다", async () => {
    const confirm = vi.spyOn(window, "confirm").mockReturnValue(false);
    open();
    fireEvent.change(await screen.findByLabelText("A 이름"), {
      target: { value: "보존 입력" },
    });
    await userEvent.click(screen.getByRole("button", { name: "DB UI-16" }));
    await userEvent.click(screen.getByRole("button", { name: /DB UI-27$/ }));
    expect(screen.getByLabelText("A 이름")).toHaveValue("보존 입력");
    await userEvent.click(
      screen.getByRole("button", { name: "DB UI-27 탭 닫기" }),
    );
    expect(confirm).toHaveBeenCalled();
    expect(screen.getByLabelText("A 이름")).toHaveValue("보존 입력");
    confirm.mockRestore();
  });
  it("임시 ADMIN은 메뉴와 코드 CTA를 사용하고 폐기 후 재진입은 깨끗한 화면이다", async () => {
    const confirm = vi.spyOn(window, "confirm").mockReturnValue(true);
    open();
    const input = await screen.findByLabelText("A 이름");
    const sidebar = within(
      within(screen.getByRole("complementary", { name: "주 메뉴" })).getByRole(
        "navigation",
      ),
    );
    expect(sidebar.getByRole("link", { name: /DB UI-27/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "그룹 추가" })).toBeEnabled();
    expect(screen.getByRole("button", { name: "코드 추가" })).toBeEnabled();
    fireEvent.change(input, { target: { value: "폐기" } });
    await userEvent.click(
      screen.getByRole("button", { name: "DB UI-27 탭 닫기" }),
    );
    await screen.findByRole("heading", { name: "DB UI-16" });
    expect(confirm).toHaveBeenCalledTimes(1);
    await userEvent.click(sidebar.getByRole("link", { name: /DB UI-27/ }));
    expect(await screen.findByLabelText("A 이름")).toHaveValue("기존 이름");
    expect(screen.getByRole("button", { name: "저장" })).toBeDisabled();
    await userEvent.click(
      screen.getByRole("button", { name: "DB UI-27 탭 닫기" }),
    );
    expect(confirm).toHaveBeenCalledTimes(1);
  });
  it("설정 모달과 편집 표는 같은 저장 결과를 즉시 사용한다", async () => {
    open();
    await screen.findByLabelText("A 이름");
    await userEvent.click(screen.getByRole("button", { name: "임시 관리자" }));
    await userEvent.click(screen.getByRole("button", { name: "화면 설정" }));
    const dialog = screen.getByRole("dialog", { name: "화면 설정" });
    fireEvent.change(within(dialog).getByLabelText("한 화면 건수"), {
      target: { value: "20" },
    });
    await waitFor(() =>
      expect(prefs.putPreference).toHaveBeenCalledWith(
        "PAGE_SIZE",
        "*",
        20,
        null,
      ),
    );
    await waitFor(() =>
      expect(codes.getGroupCodes).toHaveBeenLastCalledWith("EDUCATION", 1, 20),
    );
    expect(screen.getAllByRole("dialog")).toHaveLength(1);
  });
  it("열 설정은 표시 중 모드의 전체 정의를 쓰고 작업열은 제외한다", async () => {
    open();
    await screen.findByLabelText("A 이름");
    await userEvent.click(screen.getByRole("button", { name: "열 설정" }));
    const dialog = screen.getByRole("dialog", { name: "현재 표 열 설정" });
    expect(within(dialog).getByLabelText("코드 숨김")).toBeDisabled();
    expect(within(dialog).getByLabelText("추가값 2 숨김")).toBeInTheDocument();
    expect(
      within(dialog).queryByLabelText("삭제 숨김"),
    ).not.toBeInTheDocument();
    fireEvent.click(within(dialog).getByLabelText("순서 숨김"));
    await userEvent.click(within(dialog).getByRole("button", { name: "적용" }));
    await waitFor(() =>
      expect(
        screen.queryByRole("columnheader", { name: "순서" }),
      ).not.toBeInTheDocument(),
    );
  });
  it("설정 통신 오류는 기존 표와 입력을 보존하며 자동 재저장하지 않는다", async () => {
    open();
    fireEvent.change(await screen.findByLabelText("A 이름"), {
      target: { value: "보존 입력" },
    });
    vi.mocked(prefs.putPreference).mockRejectedValueOnce(
      new Error("설정 통신 오류"),
    );
    fireEvent.change(screen.getByLabelText("한 화면 건수"), {
      target: { value: "20" },
    });
    await screen.findByText("설정 통신 오류");
    expect(screen.getByLabelText("한 화면 건수")).toHaveValue("50");
    expect(screen.getByLabelText("A 이름")).toHaveValue("보존 입력");
    expect(prefs.putPreference).toHaveBeenCalledTimes(2);
  });
  it("commit 이벤트는 열린 이력을 재조회한다", async () => {
    open();
    await screen.findByLabelText("A 이름");
    await userEvent.click(screen.getByRole("button", { name: "변경 이력" }));
    await screen.findByText("조회된 변경 이력이 없습니다.");
    const reads = vi.mocked(shared.getCommonCodeHistory).mock.calls.length;
    act(() =>
      window.dispatchEvent(new CustomEvent(mappings.CODE_MAPPINGS_COMMITTED)),
    );
    await waitFor(() =>
      expect(shared.getCommonCodeHistory).toHaveBeenCalledTimes(reads + 1),
    );
  });
  it("CASH commit만 열린 기간 정보를 재조회한다", async () => {
    open("/UI-16");
    await screen.findByLabelText("기간");
    const reads = vi.mocked(shared.getPeriodContext).mock.calls.length;
    act(() => standards.notifyOperatingStandardsCommitted(false));
    expect(shared.getPeriodContext).toHaveBeenCalledTimes(reads);
    act(() => standards.notifyOperatingStandardsCommitted(true));
    await waitFor(() =>
      expect(shared.getPeriodContext).toHaveBeenCalledTimes(reads + 1),
    );
  });
  it("메뉴 오류는 업무 본문 없이 재시도를 제공한다", async () => {
    vi.mocked(shared.getMenus).mockRejectedValueOnce(
      new Error("메뉴 통신 오류"),
    );
    open();
    await screen.findByText("메뉴 통신 오류");
    expect(codes.getCodeGroups).not.toHaveBeenCalled();
    await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
    await screen.findByLabelText("A 이름");
  });
});
