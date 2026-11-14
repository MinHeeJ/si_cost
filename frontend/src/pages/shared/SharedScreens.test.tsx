import {
  fireEvent,
  render as renderUi,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import type { ReactElement } from "react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, useLocation } from "react-router-dom";
import { PreferencesProvider } from "../me/usePreferences";
import type { PreferenceApi } from "../me/preferencesApi";
import {
  profitabilityColumns,
  profitabilityReferenceColumns,
} from "../profitability/profitabilityColumns";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { PeriodSelector } from "./PeriodSelector";
import { ScreenPeriodBar } from "./ScreenPeriodBar";
import { ChangeHistoryPanel } from "./ChangeHistoryPanel";
import { SharedApplication, SharedRoutes } from "./SharedApplication";
import { screenRegistry } from "./screenRegistry";
import * as api from "./sharedApi";
vi.mock("./sharedApi");
const context = {
  currentDate: "2026-11-10",
  currentMonth: "2026-11",
  timezone: "Asia/Seoul",
  cashBaseMonth: "2026-01",
  monthClose: { month: "2026-11", status: "작업중" },
};
const menus = [
  {
    menuId: "G",
    parentId: null,
    name: "묶음",
    screenId: null,
    sortOrder: 1,
    icon: "folder",
    pcOnly: false,
    children: Object.keys(screenRegistry).map((id, i) => ({
      menuId: `M${i}`,
      parentId: "G",
      name: `DB ${id}`,
      screenId: id,
      sortOrder: i,
      icon: null,
      pcOnly: ["UI-10", "UI-14", "UI-23", "UI-24", "UI-25", "UI-27"].includes(
        id,
      ),
      children: [],
    })),
  },
];
const columnApi: PreferenceApi = {
  getPreferences: async () => ({
    items: [
      {
        type: "COLS",
        key: "UI-03",
        value: {
          individualProfitability: {
            order: profitabilityColumns.map((c) => c.id),
            hidden: [...profitabilityReferenceColumns],
          },
        },
        revision: null,
        stored: false,
      },
    ],
  }),
  putPreference: vi.fn(),
  deletePreference: vi.fn(),
};
const render = (ui: ReactElement) =>
  renderUi(<PreferencesProvider api={columnApi}>{ui}</PreferencesProvider>);
function Location() {
  return <output data-testid="current-path">{useLocation().pathname}</output>;
}
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api.getSharedCodes).mockResolvedValue({ items: [] });
  vi.mocked(api.getMenus).mockResolvedValue({ items: menus, diagnostics: [] });
  vi.mocked(api.getCurrentUser).mockResolvedValue({
    userId: "admin",
    name: "임시 관리자",
    roleIds: ["ADMIN"],
    developmentMode: true,
  });
  vi.mocked(api.getPeriodContext).mockResolvedValue(context);
  vi.mocked(api.getMonthClose).mockResolvedValue({
    items: [context.monthClose],
  });
  vi.mocked(api.getSharedPreferences).mockResolvedValue({ items: [] });
  vi.mocked(api.putSharedPreference).mockImplementation(
    async (type, key, value) => ({
      changed: true,
      preference: { type, key, value, stored: true, revision: "saved" },
    }),
  );
  vi.mocked(api.getCommonCodeHistory).mockResolvedValue({
    items: [],
    page: 1,
    size: 50,
    total: 0,
  });
});
describe("controlled 기간 선택", () => {
  it("UI-03만 내년 선택을 막고 이전 유효 YEAR를 revision과 함께 복원한다", async () => {
    vi.mocked(api.getPeriodContext).mockResolvedValue({
      ...context,
      cashBaseMonth: "2025-01",
    });
    vi.mocked(api.getSharedPreferences).mockResolvedValue({
      items: [
        {
          type: "PERIOD",
          key: "UI-03",
          value: { type: "YEAR", value: "2025" },
          stored: true,
          revision: "period-original",
        },
      ],
    });
    render(<ScreenPeriodBar screenId="UI-03" />);
    expect(await screen.findByLabelText("기간")).toHaveValue("2025");
    expect(
      screen.queryByRole("option", { name: "2027년" }),
    ).not.toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("기간"), {
      target: { value: "2026" },
    });
    await waitFor(() =>
      expect(api.putSharedPreference).toHaveBeenCalledWith(
        "PERIOD",
        "UI-03",
        { type: "YEAR", value: "2026" },
        "period-original",
      ),
    );
    await waitFor(() =>
      expect(screen.getByLabelText("다음 기간")).toBeDisabled(),
    );
    fireEvent.click(screen.getByLabelText("다음 기간"));
    expect(api.putSharedPreference).toHaveBeenCalledTimes(1);
  });
  it("UI-03의 저장된 내년은 안내와 임시 기본값만 표시하고 자동 수정하지 않는다", async () => {
    vi.mocked(api.getSharedPreferences).mockResolvedValue({
      items: [
        {
          type: "PERIOD",
          key: "UI-03",
          value: { type: "YEAR", value: "2027" },
          stored: true,
          revision: "future-period",
        },
      ],
    });
    render(<ScreenPeriodBar screenId="UI-03" />);
    expect(await screen.findByLabelText("기간")).toHaveValue("2026");
    expect(
      screen.getByText(/저장된 기간이 현재 허용 범위/),
    ).toBeInTheDocument();
    expect(api.putSharedPreference).not.toHaveBeenCalled();
    expect(api.deleteSharedPreference).not.toHaveBeenCalled();
  });
  it.each(["YEAR", "YEAR_MONTH"] as const)(
    "%s min/max/disabled는 이벤트를 만들지 않는다",
    (type) => {
      const change = vi.fn();
      const value = type === "YEAR" ? "2026" : "2026-01";
      const { rerender } = render(
        <PeriodSelector
          type={type}
          value={value}
          min={value}
          max={value}
          onChange={change}
        />,
      );
      fireEvent.click(screen.getByLabelText("이전 기간"));
      fireEvent.click(screen.getByLabelText("다음 기간"));
      expect(change).not.toHaveBeenCalled();
      rerender(
        <PeriodSelector
          type={type}
          value={value}
          min={value}
          max={type === "YEAR" ? "2027" : "2026-02"}
          disabled
          onChange={change}
        />,
      );
      fireEvent.click(screen.getByLabelText("다음 기간"));
      expect(change).not.toHaveBeenCalled();
    },
  );
  it.each(["YEAR", "YEAR_MONTH"] as const)(
    "%s 유효 선택은 한 번만 알린다",
    (type) => {
      const change = vi.fn();
      render(
        <PeriodSelector
          type={type}
          value={type === "YEAR" ? "2026" : "2026-01"}
          min={type === "YEAR" ? "2026" : "2026-01"}
          max={type === "YEAR" ? "2027" : "2026-02"}
          onChange={change}
        />,
      );
      fireEvent.click(screen.getByLabelText("다음 기간"));
      expect(change).toHaveBeenCalledTimes(1);
      expect(api.putSharedPreference).not.toHaveBeenCalled();
    },
  );
  it("YEAR는 시작연도부터 내년까지이고 명시 선택만 저장한다", async () => {
    render(<ScreenPeriodBar screenId="UI-01" />);
    await screen.findByLabelText("기간");
    expect(api.putSharedPreference).not.toHaveBeenCalled();
    expect(screen.getByRole("option", { name: "2027년" })).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("기간"), {
      target: { value: "2027" },
    });
    await screen.findByText("내년·계획");
    expect(api.putSharedPreference).toHaveBeenCalledTimes(1);
  });
  it("YEAR_MONTH는 운영 시작월부터 현재월까지다", async () => {
    render(<ScreenPeriodBar screenId="UI-16" />);
    await screen.findByLabelText("기간");
    expect(screen.getAllByRole("option")).toHaveLength(11);
    expect(
      screen.queryByRole("option", { name: "2026년 12월" }),
    ).not.toBeInTheDocument();
  });
  it.each(["UI-07", "UI-08", "UI-09"])(
    "%s 누계는 시작연도 이전 한 해를 허용하고 월을 새 연도로 제한한다",
    async (screenId) => {
      render(<ScreenPeriodBar screenId={screenId} />);
      await screen.findByLabelText("누계 연도");
      fireEvent.change(screen.getByLabelText("누계 연도"), {
        target: { value: "2025" },
      });
      await waitFor(() =>
        expect(api.putSharedPreference).toHaveBeenCalledWith(
          "PERIOD",
          screenId,
          { type: "YEAR_CUM", year: "2025", month: "2025-12" },
          null,
        ),
      );
    },
  );
  it("운영 시작 전에는 비활성이고 저장하지 않는다", async () => {
    vi.mocked(api.getPeriodContext).mockResolvedValue({
      ...context,
      cashBaseMonth: "2027-01",
    });
    render(<ScreenPeriodBar screenId="UI-16" />);
    await screen.findByText("운영 시작 전");
    expect(screen.getByLabelText("기간")).toBeDisabled();
    expect(api.putSharedPreference).not.toHaveBeenCalled();
  });
  it("저장 실패 시 이전 적용값과 오류를 유지한다", async () => {
    vi.mocked(api.putSharedPreference).mockRejectedValue(
      new Error("기간 저장 실패"),
    );
    render(<ScreenPeriodBar screenId="UI-01" />);
    await screen.findByLabelText("기간");
    fireEvent.change(screen.getByLabelText("기간"), {
      target: { value: "2027" },
    });
    await screen.findByText("기간 저장 실패");
    expect(screen.getByLabelText("기간")).toHaveValue("2026");
  });
  it.each([
    {
      screenId: "UI-01",
      value: { type: "YEAR", value: "2020" },
      label: "기간",
      fallback: "2026",
    },
    {
      screenId: "UI-16",
      value: { type: "YEAR_MONTH", value: "2025-12" },
      label: "기간",
      fallback: "2026-11",
    },
    {
      screenId: "UI-07",
      value: { type: "YEAR_CUM", year: "2024", month: "2024-01" },
      label: "누계 연도",
      fallback: "2026",
    },
  ])(
    "$screenId 범위 밖 저장기간은 안내하고 DB 설정을 자동 수정하지 않는다",
    async ({ screenId, value, label, fallback }) => {
      vi.mocked(api.getSharedPreferences).mockResolvedValue({
        items: [
          {
            type: "PERIOD",
            key: screenId,
            value,
            stored: true,
            revision: "existing-period",
          },
        ],
      });
      render(<ScreenPeriodBar screenId={screenId} />);
      await screen.findByText(
        "저장된 기간이 현재 허용 범위를 벗어나 기본 기간을 표시합니다. 저장된 설정은 변경하지 않았습니다.",
      );
      expect(screen.getByLabelText(label)).toHaveValue(fallback);
      expect(api.putSharedPreference).not.toHaveBeenCalled();
      expect(api.deleteSharedPreference).not.toHaveBeenCalled();
    },
  );
  it("범위 밖 기간 안내 뒤 명시 선택은 기존 revision으로만 저장한다", async () => {
    vi.mocked(api.getSharedPreferences).mockResolvedValue({
      items: [
        {
          type: "PERIOD",
          key: "UI-01",
          value: { type: "YEAR", value: "2020" },
          stored: true,
          revision: "existing-period",
        },
      ],
    });
    render(<ScreenPeriodBar screenId="UI-01" />);
    await screen.findByLabelText("기간");
    fireEvent.change(screen.getByLabelText("기간"), {
      target: { value: "2027" },
    });
    await waitFor(() =>
      expect(api.putSharedPreference).toHaveBeenCalledWith(
        "PERIOD",
        "UI-01",
        { type: "YEAR", value: "2027" },
        "existing-period",
      ),
    );
    expect(api.putSharedPreference).toHaveBeenCalledTimes(1);
    await screen.findByText("내년·계획");
    expect(
      screen.queryByText(/저장된 기간이 현재 허용 범위/),
    ).not.toBeInTheDocument();
  });
  it("UI-27에는 기간을 조회하지도 렌더하지도 않는다", () => {
    render(<ScreenPeriodBar screenId="UI-27" />);
    expect(screen.queryByLabelText("기간")).not.toBeInTheDocument();
    expect(api.getPeriodContext).not.toHaveBeenCalled();
  });
});
describe("공식 화면과 공통 틀", () => {
  it("UI-03 메뉴·작업탭·즐겨찾기 재진입은 동일 경로와 마운트된 화면을 유지한다", async () => {
    let favorites: api.SharedPreference = {
      type: "FAV",
      key: "*",
      value: [],
      stored: false,
      revision: null,
    };
    vi.mocked(api.getSharedPreferences).mockImplementation(async () => ({
      items: [favorites],
    }));
    vi.mocked(api.putSharedPreference).mockImplementation(
      async (type, key, value) => {
        const preference = {
          type,
          key,
          value,
          stored: true,
          revision: "saved",
        };
        if (type === "FAV") favorites = preference;
        return { changed: true, preference };
      },
    );
    render(
      <MemoryRouter initialEntries={["/UI-03"]}>
        <SharedApplication />
        <Location />
      </MemoryRouter>,
    );
    await screen.findByRole("heading", { name: "DB UI-03" });
    const root = screen.getByTestId("individual-profitability-screen");
    await userEvent.click(
      screen.getByRole("button", { name: "즐겨찾기 추가" }),
    );
    await waitFor(() =>
      expect(
        screen.getByRole("button", { name: "즐겨찾기 해제" }),
      ).toBeEnabled(),
    );
    await userEvent.click(
      within(screen.getByRole("navigation", { name: "열린 화면" })).getByRole(
        "button",
        { name: "DB UI-16" },
      ),
    );
    expect(screen.getByTestId("current-path")).toHaveTextContent("/UI-16");
    const sidebar = screen.getByRole("complementary", { name: "주 메뉴" });
    const links = within(sidebar).getAllByRole("link", { name: "DB UI-03" });
    expect(links[0]).toHaveAttribute("href", "/UI-03");
    await userEvent.click(links[0]);
    expect(screen.getByTestId("current-path")).toHaveTextContent("/UI-03");
    expect(screen.getByTestId("individual-profitability-screen")).toBe(root);
    await userEvent.click(
      within(screen.getByRole("navigation", { name: "열린 화면" })).getByRole(
        "button",
        { name: "DB UI-16" },
      ),
    );
    await userEvent.click(
      within(screen.getByRole("navigation", { name: "열린 화면" })).getByRole(
        "button",
        { name: "DB UI-03" },
      ),
    );
    expect(screen.getByTestId("current-path")).toHaveTextContent("/UI-03");
    expect(screen.getByTestId("individual-profitability-screen")).toBe(root);
  });
  // 실제 업무 화면의 직접 진입·입력·재조회는 router.test.tsx가 확인한다.
  const implementedPeopleScreens = ["UI-02", "UI-12", "UI-13", "UI-33"];
  // BASIC-139 replaces these follow-up placeholders with real financial screens.
  const financialScreenTestIds: Record<string, string> = {
    "UI-07": "profit-structure-screen",
    "UI-08": "operating-profit-screen",
    "UI-09": "net-profit-screen",
    "UI-18": "business-plan-performance",
  };
  it.each(
    Object.keys(screenRegistry).filter(
      (id) => !implementedPeopleScreens.includes(id),
    ),
  )("%s 직접 진입에서 DB 제목과 범위 내 본문을 렌더한다", async (id) => {
    render(
      <MemoryRouter initialEntries={["/" + id]}>
        <SharedApplication />
      </MemoryRouter>,
    );
    await screen.findByRole("heading", { name: `DB ${id}` });
    if (id === "UI-03") {
      expect(screen.getByTestId("profitability-overview")).toBeInTheDocument();
      expect(
        screen.getByRole("textbox", { name: "이름 검색" }),
      ).toBeInTheDocument();
      expect(
        screen.getByTestId("profitability-search-button"),
      ).toBeInTheDocument();
    } else if (id === "UI-10") {
      expect(screen.getByTestId("financial-editor")).toBeInTheDocument();
    } else if (id === "UI-04" || id === "UI-31") {
      expect(
        await screen.findByTestId(
          id === "UI-04" ? "project-cost-analysis" : "project-status-board",
        ),
      ).toBeInTheDocument();
    } else if (financialScreenTestIds[id]) {
      expect(
        await screen.findByTestId(financialScreenTestIds[id]),
      ).toBeVisible();
    } else if (id !== "UI-27")
      expect(
        screen
          .getAllByText("이 화면은 다음 단계에서 만듭니다.")
          .some((element) => !element.closest("[hidden]")),
      ).toBe(true);
    else
      expect(
        screen.getByRole("tab", { name: "변경 이력" }),
      ).toBeInTheDocument();
  });
  it("루트와 hash 진입을 같은 공식 화면으로 replace한다", async () => {
    render(
      <MemoryRouter initialEntries={["/#UI-01"]}>
        <SharedApplication />
      </MemoryRouter>,
    );
    await screen.findByRole("heading", { name: "DB UI-01" });
  });
  it("메뉴가 없으면 오류만 표시하고 이동을 반복하지 않는다", async () => {
    vi.mocked(api.getMenus).mockResolvedValue({ items: [], diagnostics: [] });
    render(
      <MemoryRouter initialEntries={["/"]}>
        <SharedApplication />
      </MemoryRouter>,
    );
    await screen.findByText("사용 가능한 메뉴가 없습니다.");
    expect(api.getMenus).toHaveBeenCalledTimes(1);
  });
  it("메뉴 조회 실패와 재시도를 제공한다", async () => {
    vi.mocked(api.getMenus).mockRejectedValueOnce(new Error("메뉴 조회 실패"));
    render(
      <MemoryRouter>
        <SharedApplication />
      </MemoryRouter>,
    );
    await screen.findByText("메뉴 조회 실패");
    await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
    await screen.findByRole("heading", { name: "DB UI-16" });
  });
  it("부모 이름은 이동하고 별도 토글은 접기만 한다", async () => {
    render(
      <MemoryRouter initialEntries={["/UI-01"]}>
        <SharedApplication />
      </MemoryRouter>,
    );
    await screen.findByRole("heading", { name: "DB UI-01" });
    await userEvent.click(
      screen.getByRole("button", { name: "묶음 하위 메뉴 접기" }),
    );
    expect(
      screen.getByRole("heading", { name: "DB UI-01" }),
    ).toBeInTheDocument();
    await userEvent.click(screen.getByRole("link", { name: "묶음" }));
    await screen.findByRole("heading", { name: "DB UI-16" });
  });
  it("route 등록은 UI-20을 만들지 않는다", () => {
    expect(screenRegistry["UI-20"]).toBeUndefined();
    expect(SharedRoutes).toBeDefined();
  });
});
describe("변경 이력", () => {
  it("빈 이력과 쓰기 버튼 부재를 표시한다", async () => {
    render(<ChangeHistoryPanel />);
    await screen.findByText("조회된 변경 이력이 없습니다.");
    expect(
      screen.queryByRole("button", { name: "삭제" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "저장" }),
    ).not.toBeInTheDocument();
  });
  it("500자 전후값을 펼치고 처리자와 항목을 표시한다", async () => {
    vi.mocked(api.getCommonCodeHistory).mockResolvedValue({
      items: [
        {
          histId: 9,
          occurredAt: "2000-01-01T00:00:00+09:00",
          target: "CODE",
          groupId: "JOB",
          entityKey: "JOB:A",
          type: "UPD",
          item: "CODE_NM",
          before: "가".repeat(500),
          after: "과거 이름",
          actor: "SYSTEM",
        },
      ],
      total: 1,
      page: 1,
      size: 50,
    });
    render(<ChangeHistoryPanel />);
    await screen.findByText("시스템");
    expect(screen.getByText("CODE_NM")).toBeInTheDocument();
    await userEvent.click(screen.getByText("긴 값 펼침"));
    expect(screen.getByText("가".repeat(500))).toBeVisible();
  });
  it("실패와 재시도를 표시한다", async () => {
    vi.mocked(api.getCommonCodeHistory).mockRejectedValueOnce(
      new Error("이력 조회 실패"),
    );
    render(<ChangeHistoryPanel />);
    await screen.findByText("이력 조회 실패");
    await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
    await screen.findByText("조회된 변경 이력이 없습니다.");
  });
});
