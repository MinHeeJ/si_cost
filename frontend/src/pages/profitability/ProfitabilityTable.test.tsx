import { useState, type Key } from "react";
import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { PreferencesProvider } from "../me/usePreferences";
import { useTablePreferences } from "../me/ColumnPreferencesDialog";
import { PreferenceSettings } from "../me/PreferenceSettings";
import type { Preference, PreferenceApi } from "../me/preferencesApi";
import { IndividualProfitabilityScreen } from "../shared/IndividualProfitabilityScreen";
import { SharedApplication } from "../shared/SharedApplication";
import * as shared from "../shared/sharedApi";
import { ProfitabilityTable } from "./ProfitabilityTable";
import { ProfitabilityValue } from "./ProfitabilityValue";
import {
  profitabilityColumns,
  profitabilityReferenceColumns,
} from "./profitabilityColumns";

vi.mock("../shared/sharedApi");
const context = {
  currentDate: "2026-11-10",
  currentMonth: "2026-11",
  timezone: "Asia/Seoul",
  cashBaseMonth: "2025-01",
  monthClose: { month: "2026-11", status: "작업중" },
};
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(shared.getMenus).mockResolvedValue({
    items: ["UI-03", "UI-16"].map((screenId, sortOrder) => ({
      menuId: screenId,
      screenId,
      name: screenId === "UI-03" ? "개인 원가·수익표" : "대시보드",
      parentId: null,
      sortOrder,
      icon: null,
      pcOnly: false,
      children: [],
    })),
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
  vi.mocked(shared.getSharedPreferences).mockResolvedValue({ items: [] });
  vi.mocked(shared.putSharedPreference).mockImplementation(
    async (type, key, value) => ({
      changed: true,
      preference: { type, key, value, revision: "period-saved", stored: true },
    }),
  );
});
function preferenceApi() {
  let items: Preference[] = [
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
    {
      type: "COLS",
      key: "UI-27",
      value: {
        codes: {
          order: ["code", "name", "addVal1", "addVal2", "sortOrder", "useYn"],
          hidden: ["addVal1"],
        },
      },
      revision: "other-screen",
      stored: true,
    },
    { type: "PAGE_SIZE", key: "*", value: 50, revision: null, stored: false },
    { type: "DENSE", key: "*", value: "넓게", revision: null, stored: false },
    {
      type: "AMT_UNIT",
      key: "*",
      value: "천원",
      revision: null,
      stored: false,
    },
  ];
  const api: PreferenceApi = {
    getPreferences: vi.fn(async () => ({ items: structuredClone(items) })),
    putPreference: vi.fn(async (type, key, value) => {
      const preference = {
        type,
        key,
        value: structuredClone(value),
        revision: `revision-${type}`,
        stored: true,
      };
      items = items.map((item) =>
        item.type === type && item.key === key ? preference : item,
      );
      return { changed: true, preference };
    }),
    deletePreference: vi.fn(),
  };
  return api;
}
const row = Object.freeze({
  personId: "owned-person",
  name: "독립 시험",
  amount: "-1000001",
});
function Body() {
  const table = useTablePreferences(
    "UI-03",
    "individualProfitability",
    profitabilityColumns,
  );
  const [expandedKeys, setExpandedKeys] = useState<Key[]>([]);
  const unit =
    table.amountUnit === "원" || table.amountUnit === "백만원"
      ? table.amountUnit
      : "천원";
  return (
    <ProfitabilityTable
      rows={[row]}
      rowKey={(item) => item.personId}
      columns={profitabilityColumns.map((column) => ({
        ...column,
        render: (item: typeof row) =>
          column.id === "name" ? (
            item.name
          ) : (
            <ProfitabilityValue value={item.amount} unit={unit} />
          ),
      }))}
      page={1}
      total={101}
      onPageChange={vi.fn()}
      stickyColumns={[{ id: "name", width: 180 }]}
      footer={{
        label: "필터 전체 합계",
        render: (column) =>
          column.id === "name" ? "전체 합계" : "서버 전체합계",
      }}
      expandableRow={{
        expandedKeys,
        onExpandedChange: setExpandedKeys,
        label: (item) => item.name,
        render: () => <p>월별 원본금액(월평균 적용 전)</p>,
      }}
    />
  );
}
function mount(api: PreferenceApi) {
  return render(
    <MemoryRouter initialEntries={["/UI-03"]}>
      <PreferencesProvider api={api}>
        <SharedApplication
          screens={{
            "UI-03": (
              <IndividualProfitabilityScreen>
                <Body />
              </IndividualProfitabilityScreen>
            ),
          }}
        />
      </PreferencesProvider>
    </MemoryRouter>,
  );
}
describe("UI-03 공통 표시 경계", () => {
  it("실제 화면 안에서 공통 표의 열·행키·펼침·전체합계·고정열 옵션을 사용하고 작업탭 왕복에 보존한다", async () => {
    const api = preferenceApi();
    mount(api);
    const name = await screen.findByText("독립 시험");
    const root = screen.getByTestId("individual-profitability-screen");
    await waitFor(() =>
      expect(screen.getAllByRole("columnheader")).toHaveLength(19),
    ); // 18 visible business columns + expansion
    expect(name.closest("td")).toHaveStyle({
      position: "sticky",
      left: "44px",
    });
    expect(screen.getByTestId("profitability-table-totals")).toHaveTextContent(
      "서버 전체합계",
    );
    await userEvent.click(
      screen.getByRole("button", { name: "독립 시험 펼치기" }),
    );
    expect(
      screen.getByRole("region", { name: "독립 시험 상세" }),
    ).toHaveTextContent("월별 원본금액");
    const tabs = screen.getByRole("navigation", { name: "열린 화면" });
    await userEvent.click(
      within(tabs).getByRole("button", { name: "대시보드" }),
    );
    expect(root).not.toBeVisible();
    await userEvent.click(
      within(tabs).getByRole("button", { name: "개인 원가·수익표" }),
    );
    expect(screen.getByTestId("individual-profitability-screen")).toBe(root);
    expect(
      screen.getByRole("button", { name: "독립 시험 접기" }),
    ).toHaveAttribute("aria-expanded", "true");
    expect(api.putPreference).not.toHaveBeenCalled();
  });
  it("열설정 순서/숨김을 저장·재조회하고 다른 화면 값은 유지하며 취소는 쓰지 않는다", async () => {
    const api = preferenceApi();
    mount(api);
    await screen.findByText("독립 시험");
    const other = (await api.getPreferences()).items.find(
      (item) => item.key === "UI-27",
    );
    await userEvent.click(screen.getByRole("button", { name: "열 설정" }));
    expect(screen.getByTestId("column-preferences-panel")).toHaveAttribute(
      "data-screen-id",
      "SCR-UI-03-M1",
    );
    await userEvent.click(screen.getByRole("button", { name: "소속 위로" }));
    await userEvent.click(screen.getByRole("checkbox", { name: "직위 숨김" }));
    for (const label of ["이름", "개인 단가", "원가합계", "영업이익"])
      expect(
        screen.getByRole("checkbox", { name: `${label} 숨김` }),
      ).toBeDisabled();
    await userEvent.click(screen.getByRole("button", { name: "적용" }));
    await waitFor(() =>
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument(),
    );
    expect(
      screen.queryByRole("columnheader", { name: "직위" }),
    ).not.toBeInTheDocument();
    expect(screen.getAllByRole("columnheader")[1]).toHaveTextContent("소속");
    const saved = (await api.getPreferences()).items.find(
      (item) => item.key === "UI-03",
    );
    expect(saved?.value).toEqual({
      individualProfitability: {
        order: [
          "companyName",
          "name",
          ...profitabilityColumns.slice(2).map((c) => c.id),
        ],
        hidden: [...profitabilityReferenceColumns, "position"],
      },
    });
    expect(
      (await api.getPreferences()).items.find((item) => item.key === "UI-27"),
    ).toEqual(other);
    await userEvent.click(screen.getByRole("button", { name: "열 설정" }));
    await userEvent.click(screen.getByRole("checkbox", { name: "소속 숨김" }));
    await userEvent.click(screen.getByRole("button", { name: "취소" }));
    expect(api.putPreference).toHaveBeenCalledTimes(1);
    expect(
      screen.getByRole("columnheader", { name: "소속" }),
    ).toBeInTheDocument();
  });
  it("금액단위·밀도·건수는 기존 공통 설정으로 저장·복원하며 원값은 변하지 않는다", async () => {
    const api = preferenceApi();
    function Page() {
      return (
        <>
          <PreferenceSettings open onClose={vi.fn()} />
          <Body />
        </>
      );
    }
    render(
      <PreferencesProvider api={api}>
        <Page />
      </PreferencesProvider>,
    );
    await screen.findByLabelText("금액 단위");
    expect(screen.getAllByText("−1,000.001").length).toBeGreaterThan(0);
    fireEvent.change(screen.getByLabelText("금액 단위"), {
      target: { value: "백만원" },
    });
    await screen.findAllByText("−1.000001");
    await waitFor(() =>
      expect(screen.getByLabelText("목록 밀도")).toBeEnabled(),
    );
    fireEvent.change(screen.getByLabelText("목록 밀도"), {
      target: { value: "좁게" },
    });
    await waitFor(() =>
      expect(
        screen.getByTestId("profitability-table").querySelector("table"),
      ).toHaveClass("sicost-dense"),
    );
    await waitFor(() =>
      expect(screen.getByTestId("preference-select-page-size")).toBeEnabled(),
    );
    fireEvent.change(screen.getByTestId("preference-select-page-size"), {
      target: { value: "20" },
    });
    await waitFor(() =>
      expect(api.putPreference).toHaveBeenCalledWith(
        "PAGE_SIZE",
        "*",
        20,
        null,
      ),
    );
    const saved = await api.getPreferences();
    expect(saved.items.find((item) => item.type === "AMT_UNIT")?.value).toBe(
      "백만원",
    );
    expect(saved.items.find((item) => item.type === "DENSE")?.value).toBe(
      "좁게",
    );
    expect(row.amount).toBe("-1000001");
  });
  it("유효0/미확인NULL·음수색·MM·율·날짜를 렌더링한다", () => {
    render(
      <div>
        <ProfitabilityValue value={null} />
        <ProfitabilityValue value="0" />
        <ProfitabilityValue value="-1" unit="원" />
        <ProfitabilityValue value="1" kind="mm" />
        <ProfitabilityValue value="0" kind="rate" />
        <ProfitabilityValue value="2026-01-02" kind="date" />
      </div>,
    );
    expect(screen.getByText("–")).not.toHaveClass("sicost-number-negative");
    expect(screen.getByText("0")).not.toHaveClass("sicost-number-negative");
    expect(screen.getByText("−1")).toHaveClass("sicost-number-negative");
    expect(screen.getByText("1.00")).toBeInTheDocument();
    expect(screen.getByText("0.0%")).toBeInTheDocument();
    expect(screen.getByText("2026-01-02")).toBeInTheDocument();
  });
});
