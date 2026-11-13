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
import { PreferencesProvider } from "../me/usePreferences";
import type { PreferenceApi } from "../me/preferencesApi";
import * as shared from "../shared/sharedApi";
import * as clients from "../clientsops/clientActionsApi";
import * as parts from "../salespartsops/partStandardsApi";
import { ApiError } from "../../shared/api";
import { IndividualProfitabilityScreen } from "./IndividualProfitabilityScreen";
import { IndividualProfitabilityScreen as RegisteredScreen } from "../shared/IndividualProfitabilityScreen";
import { ProfitabilityCalculationGuide } from "./ProfitabilityCalculationGuide";
import * as api from "./individualProfitabilityApi";
import {
  profitabilityColumns,
  profitabilityReferenceColumns,
} from "./profitabilityColumns";

vi.mock("./individualProfitabilityApi", async (original) => ({
  ...(await original<typeof import("./individualProfitabilityApi")>()),
  getIndividualProfitability: vi.fn(),
}));
vi.mock("../shared/sharedApi");
vi.mock("../clientsops/clientActionsApi");
vi.mock("../salespartsops/partStandardsApi");
vi.mock("../shared/FollowUpScreen", () => ({
  SharedScreenFrame: ({ children }: { children: React.ReactNode }) => (
    <section>{children}</section>
  ),
}));
function person(id = "owned", mm = "1.00"): api.ProfitabilityPerson {
  return {
    personId: id,
    name: `인력 ${id}`,
    managementNo: `관리-${id}`,
    companyName: null,
    companyId: null,
    personPartId: null,
    personPartName: null,
    position: null,
    employeeType: "FT",
    hireDate: null,
    jobCode: "DEV",
    gradeCode: null,
    metrics: {
      revenue: "0",
      pay: "1000001",
      allowance: "0",
      companyInsurance: "0",
      retirementReserve: "0",
      personalCost: "1000001",
      teamExpense: "0",
      projectExpense: "0",
      totalCost: "1000001",
      profit: "-1000001",
      selfInsurance: null,
      withholdingTax: null,
      incomeTax: null,
      netPay: null,
      mm,
      profitRate: null,
    },
    utilization: "100.0",
    workedMonths: 1,
    employmentMonths: 1,
    utilizedMonths: 1,
    turnkeyOnlyExcluded: false,
    monthBasis: [
      {
        month: "202601",
        assignmentMm: mm,
        assignmentCount: 1,
        averageMonthIncluded: true,
        employmentMonthIncluded: true,
        utilizedMonthIncluded: true,
        explanation: "직접 재직 운영월",
      },
    ],
    completeness: {
      costComplete: true,
      revenueComplete: true,
      estimated: false,
      allocationComplete: true,
      issues: [],
    },
  };
}
function response(items = [person()]): api.IndividualProfitabilityData {
  return {
    items,
    total: items.length,
    page: 1,
    size: 50,
    context: {
      period: {
        year: 2026,
        baseMonth: "202601",
        fromMonth: "202601",
        toMonth: "202601",
        calculatedAt: "2026-01-31T10:00:00+09:00",
        noData: false,
      },
      view: "CUMULATIVE",
      includeTurnkey: true,
    },
    summary: {
      companyHeadcount: 3,
      companyLeaveCount: 1,
      averageHeadcount: "3.0",
      averageUtilizedHeadcount: "1.0",
      averageEarnedHeadcount: "0.0",
      companyUtilization: "33.3",
      companyEarnedRate: "0.0",
      averageRevenue: "0",
      averageCost: "1000001",
      averageProfit: "-1000001",
      confirmedLossCount: 1,
      unknownCostCount: 0,
      unknownRevenueCount: 0,
      validRevenuePersonCount: 1,
    },
    totals: person().metrics,
    contributions: [
      {
        jobCode: "DEV",
        jobName: "개발",
        gradeCode: "UNCLASSIFIED",
        gradeName: "미분류",
        personCount: 1,
        unknownCount: 0,
        averageProfit: "-1000001",
      },
    ],
    unknownCount: 0,
    estimatedCount: 0,
    calculationBasis: ["서버 계산 근거"],
  };
}
function preferenceApi(): PreferenceApi {
  return {
    getPreferences: async () => ({
      items: [
        {
          type: "COLS",
          key: "UI-03",
          value: {
            individualProfitability: {
              order: profitabilityColumns.map((c) => c.id),
              hidden: profitabilityReferenceColumns,
            },
          },
          revision: null,
          stored: false,
        },
        {
          type: "PAGE_SIZE",
          key: "*",
          value: 50,
          revision: null,
          stored: false,
        },
        {
          type: "AMT_UNIT",
          key: "*",
          value: "천원",
          revision: null,
          stored: false,
        },
      ],
    }),
    putPreference: vi.fn(),
    deletePreference: vi.fn(),
  };
}
function mount(
  props: Parameters<typeof IndividualProfitabilityScreen>[0] = {},
) {
  return render(
    <PreferencesProvider api={preferenceApi()}>
      <IndividualProfitabilityScreen {...props} />
    </PreferencesProvider>,
  );
}
beforeEach(() => {
  vi.resetAllMocks();
  window.innerWidth = 1280;
  vi.mocked(api.getIndividualProfitability).mockImplementation(async (q) => ({
    ...response(),
    page: q.page,
    size: q.size,
    context: {
      ...response().context,
      view: q.view,
      includeTurnkey: q.includeTurnkey,
    },
  }));
  vi.mocked(shared.getSharedCodes).mockResolvedValue({
    items: [
      {
        code: "FT",
        name: "정규직",
        addVal1: null,
        addVal2: null,
        addVal3: null,
        codeVal: null,
        unitNm: null,
      },
    ],
  });
  vi.mocked(clients.getClientOptions).mockResolvedValue({
    items: [{ id: "owned-vendor", name: "협력사", type: "VNDR" }],
  });
  vi.mocked(parts.getSalesPartOptions).mockResolvedValue({
    items: [
      { id: "owned-hq", name: "본사", hqYn: "Y", firstEffectiveMonth: null },
    ],
  });
});
describe("개인 기간 조회 본문", () => {
  it("공개 응답의 nullable 표시명·관리번호·직무·등급을 유지하고 DEV_ID로 상세를 연다", async () => {
    const data = response();
    data.items[0].name = null;
    data.items[0].managementNo = null;
    data.items[0].jobCode = null;
    data.contributions[0].jobCode = null;
    data.contributions[0].gradeCode = null;
    const onPerson = vi.fn();
    vi.mocked(api.getIndividualProfitability).mockResolvedValue(data);
    mount({ onPerson });
    await userEvent.click(
      await screen.findByRole("button", { name: "owned (–)" }),
    );
    expect(onPerson).toHaveBeenCalledWith("owned");
    expect(screen.getByRole("button", { name: "owned 펼치기" })).toBeEnabled();
    await userEvent.click(
      screen.getByTestId("profitability-contribution-toggle"),
    );
    expect(
      screen.getByTestId("profitability-contribution-panel"),
    ).toHaveTextContent("개발");
    expect(
      screen.getByTestId("profitability-contribution-panel"),
    ).toHaveTextContent("미분류");
  });
  it("공통 FilterBar의 왼쪽 이름표와 오른쪽 초기화·조회 및 로딩 차단을 사용한다", async () => {
    mount();
    await screen.findByText("인력 owned (관리-owned)");
    const form = screen.getByTestId("profitability-filters");
    expect(form).toHaveClass("sicost-filterbar");
    expect(screen.getByRole("textbox", { name: "이름 검색" }).closest("label")).toHaveClass("sicost-field");
    const actions = form.querySelector(".sicost-filterbar-actions")!;
    expect(within(actions as HTMLElement).getAllByRole("button").map((b) => b.textContent)).toEqual(["초기화", "조회"]);
    expect(screen.getByTestId("profitability-search-button")).toHaveClass("sicost-primary");
    vi.mocked(api.getIndividualProfitability).mockImplementation(() => new Promise(() => {}));
    await userEvent.click(screen.getByTestId("profitability-search-button"));
    expect(screen.getByTestId("profitability-search-button")).toBeDisabled();
    expect(screen.getByTestId("profitability-reset-button")).toBeDisabled();
  });
  it("모바일 빈 카드 안에서 조건 초기화로 기본 조회를 복구한다", async () => {
    window.innerWidth = 390;
    vi.mocked(api.getIndividualProfitability).mockResolvedValue(response([]));
    mount();
    const reset = await screen.findByTestId("profitability-mobile-empty-reset-button");
    expect(reset.closest('[data-testid="profitability-mobile-cards"]')).not.toBeNull();
    const callsBeforeReset = vi.mocked(api.getIndividualProfitability).mock.calls.length;
    await userEvent.click(reset);
    await waitFor(() => expect(api.getIndividualProfitability).toHaveBeenCalledTimes(callsBeforeReset + 1));
    expect(api.getIndividualProfitability).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1, size: 20, includeTurnkey: true }), expect.any(AbortSignal));
  });
  it("등록 진입점은 실제 내보내기·계산안내와 서버 근거를 조립하고 응답 변경을 그대로 표시한다", async () => {
    const first = response();
    first.calculationBasis = [
      "급여 출처: 업로드 월급여",
      "분모: 서버가 확정한 근무월",
      "경비: 저장 프로젝트 금액",
      "TK: 개인 배분 참고액",
    ];
    vi.mocked(api.getIndividualProfitability).mockResolvedValue(first);
    render(
      <PreferencesProvider api={preferenceApi()}>
        <RegisteredScreen />
      </PreferencesProvider>,
    );
    await screen.findByText("인력 owned (관리-owned)");
    expect(
      screen.getByTestId("individual-profitability-screen"),
    ).toHaveAttribute("data-screen-id", "UI-03");
    expect(screen.getByTestId("profitability-export-button")).toBeEnabled();
    expect(
      screen.getByTestId("profitability-company-profit-note"),
    ).toHaveTextContent(
      "내부 프로젝트·대기 인력은 매출이 없어 영업이익이 −원가로 보입니다. 회사 전체 영업이익표에서는 내부 몫은 연구개발비(판관비), 대기 몫은 대기 원가로 따로 잡혀 이 표의 합계와 다를 수 있습니다.",
    );
    await userEvent.click(
      screen.getByTestId("profitability-calculation-toggle"),
    );
    expect(
      screen.getByTestId("profitability-calculation-panel"),
    ).toHaveAttribute("open");
    first.calculationBasis.forEach((basis, index) =>
      expect(
        screen.getByTestId(`profitability-calculation-basis-${index}`),
      ).toHaveTextContent(basis),
    );
    expect(
      screen.getByTestId("profitability-calculation-person-0"),
    ).toHaveTextContent("평균 근무월 N=1");
    expect(
      screen.getByTestId("profitability-calculation-month-0-202601"),
    ).toHaveTextContent(first.items[0].monthBasis[0].explanation);
    const changed = response();
    changed.calculationBasis = ["새 응답의 월평균 근거"];
    changed.items[0].workedMonths = 7;
    vi.mocked(api.getIndividualProfitability).mockResolvedValue(changed);
    fireEvent.click(screen.getByRole("button", { name: "월 평균" }));
    await screen.findByText("새 응답의 월평균 근거");
    await userEvent.click(
      screen.getByTestId("profitability-calculation-toggle"),
    );
    expect(
      screen.queryByText(first.calculationBasis[0]),
    ).not.toBeInTheDocument();
    expect(
      screen.getByTestId("profitability-calculation-person-0"),
    ).toHaveTextContent("평균 근무월 N=7");
    expect(
      screen.getByTestId("profitability-calculation-panel"),
    ).toHaveAttribute("open");
    expect(screen.getByTestId("profitability-table-totals")).toHaveTextContent(
      "−1,000.001",
    );
  });
  it("모바일 등록 화면에서도 계산 근거를 쓰고 내보내기는 렌더링하지 않는다", async () => {
    window.innerWidth = 767;
    render(
      <PreferencesProvider api={preferenceApi()}>
        <RegisteredScreen />
      </PreferencesProvider>,
    );
    await screen.findByTestId("profitability-calculation-guide");
    expect(
      screen.queryByTestId("profitability-export-button"),
    ).not.toBeInTheDocument();
    await userEvent.click(
      screen.getByTestId("profitability-calculation-toggle"),
    );
    expect(screen.getByText("서버 계산 근거")).toBeVisible();
  });
  it("근거가 없으면 예시 산식을 만들지 않고 자료 없음으로 표시한다", async () => {
    const data = response([]);
    data.calculationBasis = [];
    render(<ProfitabilityCalculationGuide data={data} />);
    await userEvent.click(
      screen.getByTestId("profitability-calculation-toggle"),
    );
    expect(
      screen.getByText("계산 근거 자료 없음 · 다시 조회해 주세요."),
    ).toBeVisible();
    expect(
      screen.queryByTestId("profitability-calculation-basis-0"),
    ).not.toBeInTheDocument();
  });
  it("투입 저장 commit 뒤 다음 응답으로 갱신하고 보기·필터는 보존한다", async () => {
    mount();
    await screen.findByText("인력 owned (관리-owned)");
    fireEvent.click(screen.getByRole("button", { name: "월 평균" }));
    await screen.findByRole("columnheader", { name: "월급여" });
    vi.mocked(api.getIndividualProfitability).mockResolvedValue(
      response([person("committed")]),
    );
    act(() =>
      window.dispatchEvent(
        new CustomEvent("sicost:assignments-committed", {
          detail: { year: 2026 },
        }),
      ),
    );
    await screen.findByText("인력 committed (관리-committed)");
    expect(api.getIndividualProfitability).toHaveBeenLastCalledWith(
      expect.objectContaining({ view: "MONTHLY_AVERAGE" }),
      expect.any(AbortSignal),
    );
    expect(screen.getByRole("button", { name: "월 평균" })).toHaveAttribute("aria-pressed", "true");
  });
  it("18개 기본열·4개 숨김열·누계급여 제목과 서버 전체합계/요약을 렌더링한다", async () => {
    mount();
    await screen.findByText("인력 owned (관리-owned)");
    const headers = within(
      screen.getByTestId("profitability-table"),
    ).getAllByRole("columnheader");
    expect(headers).toHaveLength(19);
    expect(headers[9]).toHaveTextContent("급여(누계)");
    expect(
      screen.queryByRole("columnheader", { name: "본인보험" }),
    ).not.toBeInTheDocument();
    expect(screen.getByTestId("profitability-table-totals")).toHaveTextContent(
      "−1,000.001",
    );
    expect(
      screen.getByTestId("profitability-summary-headcount"),
    ).toHaveTextContent("3명");
    expect(
      screen.getByTestId("profitability-summary-headcount"),
    ).toHaveTextContent("휴직 1명");
    expect(
      screen.queryByRole("button", { name: /삭제|등록|마감/ }),
    ).not.toBeInTheDocument();
  });
  it("필터는 초안이며 조회/Enter에만 trim하여 적용하고 초기화는 기본조건이다", async () => {
    mount();
    await screen.findByText("인력 owned (관리-owned)");
    const baseline = vi.mocked(api.getIndividualProfitability).mock.calls
      .length;
    await userEvent.type(
      screen.getByRole("textbox", { name: "이름 검색" }),
      "  검색  ",
    );
    expect(api.getIndividualProfitability).toHaveBeenCalledTimes(baseline);
    await userEvent.keyboard("{Enter}");
    await waitFor(() =>
      expect(api.getIndividualProfitability).toHaveBeenLastCalledWith(
        expect.objectContaining({ name: "검색", page: 1 }),
        expect.any(AbortSignal),
      ),
    );
    await waitFor(() =>
      expect(screen.getByTestId("profitability-reset-button")).toBeEnabled(),
    );
    await userEvent.click(screen.getByTestId("profitability-reset-button"));
    await waitFor(() =>
      expect(api.getIndividualProfitability).toHaveBeenLastCalledWith(
        expect.objectContaining({
          view: "CUMULATIVE",
          includeTurnkey: true,
          show: "ALL",
          page: 1,
        }),
        expect.any(AbortSignal),
      ),
    );
    expect(screen.getByRole("textbox", { name: "이름 검색" })).toHaveValue("");
  });
  it("보기/TK 제외/적자조건은 서버에 전송하고 프론트에서 금액을 재계산하지 않는다", async () => {
    mount();
    await screen.findByText("인력 owned (관리-owned)");
    fireEvent.click(screen.getByRole("button", { name: "월 평균" }));
    await screen.findByRole("columnheader", { name: "월급여" });
    await userEvent.click(
      screen.getByTestId("profitability-detail-filters-button"),
    );
    await userEvent.click(screen.getByTestId("profitability-turnkey-filter"));
    fireEvent.change(screen.getByTestId("profitability-show-filter"), {
      target: { value: "LOSS" },
    });
    await userEvent.click(screen.getByTestId("profitability-search-button"));
    await waitFor(() =>
      expect(api.getIndividualProfitability).toHaveBeenLastCalledWith(
        expect.objectContaining({
          view: "MONTHLY_AVERAGE",
          includeTurnkey: false,
          show: "LOSS",
        }),
        expect.any(AbortSignal),
      ),
    );
  });
  it("정렬/건수변경은 첫페이지·펼침해제이며 전체합계는 서버값이다", async () => {
    const ledger = vi.fn((p: api.ProfitabilityPerson) => (
      <p>{p.personId} 월 원본</p>
    ));
    mount({ renderLedger: ledger });
    await screen.findByText("인력 owned (관리-owned)");
    await userEvent.click(
      screen.getByRole("button", { name: "인력 owned 펼치기" }),
    );
    expect(screen.getByText("owned 월 원본")).toBeInTheDocument();
    fireEvent.change(screen.getByTestId("profitability-table-page-size"), {
      target: { value: "20" },
    });
    await waitFor(() =>
      expect(api.getIndividualProfitability).toHaveBeenLastCalledWith(
        expect.objectContaining({ size: 20, page: 1 }),
        expect.any(AbortSignal),
      ),
    );
    await waitFor(() =>
      expect(screen.queryByText("owned 월 원본")).not.toBeInTheDocument(),
    );
    await userEvent.click(screen.getByTestId("sort-profit"));
    await waitFor(() =>
      expect(api.getIndividualProfitability).toHaveBeenLastCalledWith(
        expect.objectContaining({ sort: "profit", direction: "ASC", page: 1 }),
        expect.any(AbortSignal),
      ),
    );
    expect(screen.getByTestId("profitability-table-totals")).toHaveTextContent(
      "−1,000.001",
    );
  });
  it("초과1.00MM만 배지·예정과 미확인을 구별하고 도움말에 문제월을 남긴다", async () => {
    const p = person("over", "1.01");
    p.completeness.estimated = true;
    const unknown = person("unknown");
    unknown.completeness.costComplete = false;
    unknown.completeness.issues = [
      {
        personId: unknown.personId,
        month: "202601",
        message: "급여 자료 없음",
        source: "TB_HR_MON_PAY",
        relatedScreen: "UI-02",
        sourceAmount: null,
      },
    ];
    vi.mocked(api.getIndividualProfitability).mockResolvedValue(
      response([p, unknown]),
    );
    mount();
    await screen.findByText("급여 미업로드·예정");
    expect(screen.getAllByText("1.0 초과")).toHaveLength(1);
    expect(screen.getByText("1.0 초과").parentElement).toHaveAttribute(
      "title",
      "202601: 1.01MM",
    );
    expect(screen.getByText("미확인").parentElement).toHaveAttribute(
      "title",
      expect.stringContaining("TB_HR_MON_PAY"),
    );
  });
  it("공헌이익은 기본접힘이고 펼치면 미분류·인원을 표시한다", async () => {
    mount();
    await screen.findByText("인력 owned (관리-owned)");
    expect(
      screen.queryByTestId("profitability-contribution-panel"),
    ).not.toBeInTheDocument();
    await userEvent.click(
      screen.getByTestId("profitability-contribution-toggle"),
    );
    expect(
      screen.getByTestId("profitability-contribution-panel"),
    ).toHaveTextContent("미분류");
    expect(
      screen.getByTestId("profitability-contribution-panel"),
    ).toHaveTextContent("1명");
  });
  it("767px는20건 카드/더보기·다운로드미호출이며 PC복귀시 공통50건을 복원한다", async () => {
    window.innerWidth = 767;
    vi.mocked(api.getIndividualProfitability).mockImplementation(async (q) => ({
      ...response(
        Array.from({ length: 20 }, (_, i) =>
          person(`p${(q.page - 1) * 20 + i}`),
        ),
      ),
      total: 40,
      page: q.page,
      size: q.size,
    }));
    const exported = vi.fn(() => (
      <button data-testid="test-export">엑셀</button>
    ));
    mount({ renderExport: exported });
    await screen.findByTestId("profitability-card-19");
    expect(api.getIndividualProfitability).toHaveBeenLastCalledWith(
      expect.objectContaining({ size: 20 }),
      expect.any(AbortSignal),
    );
    expect(exported).not.toHaveBeenCalled();
    expect(screen.queryByTestId("test-export")).not.toBeInTheDocument();
    await userEvent.click(screen.getByTestId("profitability-more-button"));
    await screen.findByTestId("profitability-card-39");
    expect(
      screen.queryByTestId("profitability-more-button"),
    ).not.toBeInTheDocument();
    act(() => {
      window.innerWidth = 1280;
      window.dispatchEvent(new Event("resize"));
    });
    await screen.findByTestId("profitability-table");
    await waitFor(() =>
      expect(api.getIndividualProfitability).toHaveBeenLastCalledWith(
        expect.objectContaining({ page: 1, size: 50 }),
        expect.any(AbortSignal),
      ),
    );
  });
  it("조회오류는빈목록으로위장하지않고필드메시지/초안보존·같은GET재시도한다", async () => {
    vi.mocked(api.getIndividualProfitability).mockRejectedValue(
      new ApiError(400, {
        success: false,
        meta: {},
        code: "INVALID_INPUT",
        message: "이름을 확인하세요",
        requestId: "owned",
        field: "name",
      }),
    );
    mount();
    await screen.findAllByText("이름을 확인하세요");
    expect(
      screen.queryByText("조건에 맞는 사람이 없습니다."),
    ).not.toBeInTheDocument();
    await userEvent.type(
      screen.getByRole("textbox", { name: "이름 검색" }),
      "유지",
    );
    vi.mocked(api.getIndividualProfitability).mockResolvedValue(response());
    await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
    await screen.findByText("인력 owned (관리-owned)");
    expect(screen.getByRole("textbox", { name: "이름 검색" })).toHaveValue(
      "유지",
    );
  });
  it("늦은이전연도응답은새연도본문을덮지않는다", async () => {
    const requests: {
      query: api.ProfitabilityQuery;
      resolve(value: api.IndividualProfitabilityData): void;
    }[] = [];
    vi.mocked(api.getIndividualProfitability).mockImplementation(
      (q) => new Promise((resolve) => requests.push({ query: q, resolve })),
    );
    mount();
    await waitFor(() => expect(requests.length).toBeGreaterThan(0));
    act(() =>
      window.dispatchEvent(
        new CustomEvent("sicost:screen-period-changed", {
          detail: {
            screenId: "UI-03",
            value: { type: "YEAR", value: "2025" },
            context: { currentMonth: "2026-01" },
          },
        }),
      ),
    );
    await waitFor(() =>
      expect(requests.some((r) => r.query.year === 2025)).toBe(true),
    );
    const recent = response([person("recent")]);
    recent.context.period.year = 2025;
    await act(async () => {
      requests
        .filter((r) => r.query.year === 2025)
        .forEach((r) => r.resolve(recent));
    });
    await screen.findByText("인력 recent (관리-recent)");
    await act(async () => {
      requests
        .filter((r) => r.query.year !== 2025)
        .forEach((r) => r.resolve(response([person("stale")])));
    });
    expect(
      screen.queryByText("인력 stale (관리-stale)"),
    ).not.toBeInTheDocument();
    expect(screen.getByTestId("profitability-context")).toHaveTextContent(
      "2025년",
    );
  });
});
