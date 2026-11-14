import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { PreferencesProvider } from "../me/usePreferences";
import type { Preference, PreferenceApi } from "../me/preferencesApi";
import { ApiError } from "../../shared/api";
import { getSharedCodes } from "../shared/sharedApi";
import { getProjects } from "../projects/projectsApi";
import {
  RateRenegotiationsScreen,
  renegotiationColumns,
} from "./RateRenegotiationsScreen";
import * as api from "./rateRenegotiationsApi";
vi.mock("./rateRenegotiationsApi", async (original) => ({
  ...(await original<typeof import("./rateRenegotiationsApi")>()),
  getRateRenegotiations: vi.fn(),
  downloadRenegotiations: vi.fn(),
}));
vi.mock("../shared/sharedApi");
vi.mock("../projects/projectsApi");
function response(): api.RenegotiationsResult {
  return {
    baseMonth: "2080-07",
    calculatedAt: "2080-07-15T09:00:00+09:00",
    appliedIncreaseRate: "5",
    targetMarginRate: "20",
    summary: {
      personCount: 1,
      assignmentCount: 2,
      weightedMarginRate: "10.0",
      annualGain: "7200000",
      targetAnnualGain: "18000000",
    },
    items: [0, 31].map((day, index) => ({
      assignmentId: `owned-${index}`,
      developerId: "actual-dev",
      name: "시험 인력",
      managementNo: "관리번호",
      employeeType: "FT",
      projectId: "actual-project",
      projectName: "시험 프로젝트",
      renewalDate: "2080-08-15",
      daysUntilRenewal: day,
      planned: index === 1,
      contractMm: "1.00",
      monthlyMm: "0.52",
      unitPrice: "6000000",
      monthlyRevenue: "3120000",
      monthlyLaborCost: "2808000",
      marginRate: "10.0",
      targetUnitPrice: "6750000",
      requiredIncreaseRate: "12.5",
      annualGain: "3600000",
      targetAnnualGain: "9000000",
      issues: [],
    })),
    pagination: { page: 1, size: 50, total: 2 },
    unevaluatedCount: 1,
    unevaluatedAssignments: [
      {
        code: "SOURCE_UNKNOWN",
        message: "노동비 근거 없음",
        field: "monthlyLaborCost",
        developerId: "missing-dev",
        assignmentId: "missing-assignment",
        projectId: "actual-project",
        month: "2080-07",
        source: "TB_CO_MON_COST",
        blocking: true,
      },
    ],
    issues: [],
  };
}
let pref: Preference;
let preferences: PreferenceApi;
function mount(onPerson = vi.fn()) {
  return {
    onPerson,
    ...render(
      <MemoryRouter>
        <PreferencesProvider api={preferences}>
          <RateRenegotiationsScreen onPerson={onPerson} />
        </PreferencesProvider>
      </MemoryRouter>,
    ),
  };
}
beforeEach(() => {
  vi.clearAllMocks();
  pref = {
    type: "COLS",
    key: "UI-32",
    value: null,
    revision: null,
    stored: false,
  };
  preferences = {
    getPreferences: vi.fn(async () => ({ items: [pref] })),
    putPreference: vi.fn(async (type, key, value) => {
      pref = { type, key, value, revision: "saved", stored: true };
      return { changed: true, preference: pref };
    }),
    deletePreference: vi.fn(),
  };
  vi.mocked(api.getRateRenegotiations).mockResolvedValue(response());
  vi.mocked(api.downloadRenegotiations).mockResolvedValue(undefined);
  vi.mocked(getSharedCodes).mockResolvedValue({
    items: [
      {
        code: "FT",
        name: "정규직",
        codeVal: null,
        unitNm: null,
        addVal1: null,
        addVal2: null,
        addVal3: null,
      },
    ],
  });
  vi.mocked(getProjects).mockResolvedValue({
    items: [{ id: "actual-project", name: "시험 프로젝트" }],
    total: 1,
    page: 1,
    size: 100,
  } as Awaited<ReturnType<typeof getProjects>>);
});
describe("단가 재협상 대상", () => {
  it("shows server month four whole-population summaries eleven columns distinct assignments and two MM values", async () => {
    mount();
    await screen.findByText(/^기준월 2080-07 · 계산시각/);
    expect(api.getRateRenegotiations).toHaveBeenCalledWith(
      api.defaultRenegotiationQuery(),
      expect.any(AbortSignal),
    );
    const table = screen.getByTestId("renegotiation-table");
    expect(within(table).getAllByRole("columnheader")).toHaveLength(11);
    expect(within(table).getAllByText("1.00")).toHaveLength(2);
    expect(
      screen.getAllByTitle("계약MM k=1.00 / 기준월 월MM m=0.52"),
    ).toHaveLength(2);
    expect(screen.getByText("D-0")).toHaveClass("sicost-tone-bad");
    expect(screen.getByText("D-31")).toHaveClass("sicost-tone-warn");
    expect(screen.getByText("예정")).toBeVisible();
    expect(
      screen.queryByRole("button", { name: /단가 적용|협상 저장|상태 변경/ }),
    ).not.toBeInTheDocument();
    expect(preferences.putPreference).not.toHaveBeenCalled();
  });
  it("retains last valid results when invalid assumption is committed without sending a request", async () => {
    mount();
    await screen.findByText(/^기준월 2080-07 · 계산시각/);
    fireEvent.change(screen.getByLabelText("인상률 %"), {
      target: { value: "1.234" },
    });
    fireEvent.click(screen.getByTestId("renegotiation-recalculate"));
    expect(
      await screen.findByText("인상률은 소수 2자리까지 입력해 주세요."),
    ).toBeVisible();
    expect(screen.getByTestId("renegotiation-person-owned-0")).toBeVisible();
    expect(api.getRateRenegotiations).toHaveBeenCalledTimes(1);
  });
  it("clamps simulation on commit and reset keeps the valid local assumption while restoring filters", async () => {
    vi.mocked(api.getRateRenegotiations).mockImplementation(async (q) => ({
      ...response(),
      appliedIncreaseRate: q.increaseRate,
    }));
    mount();
    await screen.findByText(/^기준월 2080-07 · 계산시각/);
    fireEvent.change(screen.getByLabelText("인상률 %"), {
      target: { value: "51" },
    });
    expect(api.getRateRenegotiations).toHaveBeenCalledTimes(1);
    fireEvent.blur(screen.getByLabelText("인상률 %"));
    await waitFor(() =>
      expect(screen.getByLabelText("인상률 %")).toHaveValue("50"),
    );
    fireEvent.change(screen.getByTestId("renegotiation-window"), {
      target: { value: "ALL" },
    });
    fireEvent.click(screen.getByTestId("renegotiation-reset"));
    await waitFor(() =>
      expect(api.getRateRenegotiations).toHaveBeenLastCalledWith(
        { ...api.defaultRenegotiationQuery(), increaseRate: "50" },
        expect.any(AbortSignal),
      ),
    );
  });
  it("failed request preserves data and retries GET without saving", async () => {
    mount();
    await screen.findByText(/^기준월 2080-07 · 계산시각/);
    vi.mocked(api.getRateRenegotiations).mockRejectedValueOnce(
      new ApiError(500, {
        success: false,
        meta: {},
        code: "INTERNAL_ERROR",
        message: "조회 실패",
        requestId: "owned-request",
      }),
    );
    fireEvent.click(screen.getByTestId("renegotiation-search"));
    await screen.findByText(/조회 실패/);
    expect(screen.getByTestId("renegotiation-person-owned-0")).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "다시 조회" }));
    await waitFor(() =>
      expect(api.getRateRenegotiations).toHaveBeenCalledTimes(3),
    );
    expect(preferences.putPreference).not.toHaveBeenCalled();
  });
  it("shows empty state and separate unevaluated evidence", async () => {
    vi.mocked(api.getRateRenegotiations).mockResolvedValue({
      ...response(),
      items: [],
      pagination: { page: 1, size: 50, total: 0 },
    });
    mount();
    await screen.findByText("조건에 맞는 재협상 대상이 없습니다");
    fireEvent.click(screen.getByText(/평가불가 1건/));
    expect(screen.getByText("노동비 근거 없음")).toBeVisible();
    expect(
      screen.getByTestId("renegotiation-issue-person-0"),
    ).toHaveTextContent("missing-dev");
  });
  it("uses actual developer and project identities and selected assignment history link", async () => {
    const { onPerson } = mount();
    await screen.findByText(/^기준월 2080-07 · 계산시각/);
    fireEvent.click(screen.getByTestId("renegotiation-person-owned-0"));
    expect(onPerson).toHaveBeenCalledWith("actual-dev");
    expect(screen.getByTestId("renegotiation-project-owned-0")).toHaveAttribute(
      "href",
      "/UI-01?projectId=actual-project&tab=basic",
    );
    expect(
      screen.getByTestId("renegotiation-assignment-owned-0"),
    ).toHaveAttribute("href", "/UI-12?assignmentId=owned-0&tab=history");
  });
  it("exports last successful applied parameters instead of unsubmitted filter or assumption drafts", async () => {
    mount();
    await screen.findByText(/^기준월 2080-07 · 계산시각/);
    fireEvent.change(screen.getByTestId("renegotiation-window"), {
      target: { value: "ALL" },
    });
    fireEvent.change(screen.getByLabelText("인상률 %"), {
      target: { value: "oops" },
    });
    fireEvent.click(screen.getByTestId("renegotiation-download"));
    await waitFor(() =>
      expect(api.downloadRenegotiations).toHaveBeenCalledWith(
        api.defaultRenegotiationQuery(),
        renegotiationColumns.map((c) => c.id),
        "천원",
      ),
    );
  });
  it("column draft cancellation preserves view and save only mutates explicit personal preference", async () => {
    mount();
    await screen.findByText(/^기준월 2080-07 · 계산시각/);
    fireEvent.click(screen.getByTestId("renegotiation-columns"));
    expect(screen.getByTestId("renegotiation-column-visible-0")).toBeDisabled();
    expect(screen.getByTestId("renegotiation-column-visible-2")).toBeDisabled();
    fireEvent.click(screen.getByTestId("renegotiation-column-visible-4"));
    fireEvent.click(screen.getByRole("button", { name: "취소" }));
    expect(preferences.putPreference).not.toHaveBeenCalled();
    fireEvent.click(screen.getByTestId("renegotiation-columns"));
    expect(screen.getByTestId("renegotiation-column-visible-4")).toBeChecked();
    fireEvent.click(screen.getByTestId("renegotiation-column-visible-4"));
    fireEvent.click(screen.getByRole("button", { name: "설정 저장" }));
    await waitFor(() =>
      expect(preferences.putPreference).toHaveBeenCalledWith(
        "COLS",
        "UI-32",
        {
          rateRenegotiations: {
            order: renegotiationColumns.map((c) => c.id),
            hidden: ["contractMm"],
          },
        },
        null,
      ),
    );
  });
  it("target unavailable explanation does not hide valid current margin or simulation", async () => {
    const result = response();
    result.summary.targetAnnualGain = null;
    result.items = result.items.map((row) => ({
      ...row,
      targetUnitPrice: null,
      requiredIncreaseRate: null,
      targetAnnualGain: null,
    }));
    result.issues = [
      {
        ...result.unevaluatedAssignments[0],
        code: "TARGET_UNAVAILABLE",
        message: "목표 이익률로 단가를 계산할 수 없습니다",
      },
    ];
    vi.mocked(api.getRateRenegotiations).mockResolvedValue(result);
    mount();
    await screen.findByText("목표 이익률로 단가를 계산할 수 없습니다");
    expect(screen.getAllByText("10.0%").length).toBeGreaterThan(0);
    expect(screen.getByTestId("renegotiation-table")).toHaveTextContent(
      "3,600",
    );
  });
});
