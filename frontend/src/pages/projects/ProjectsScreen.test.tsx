import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { ProjectForm } from "./ProjectForm";
import { ProjectsScreen } from "./ProjectsScreen";
import { ProjectDetailPanel } from "./ProjectDetailPanel";
import { projectMoneyInput, projectAmount } from "./projectFormat";
import { ApiError } from "../../shared/api";
import * as api from "./projectsApi";
import * as shared from "../shared/sharedApi";
import * as clients from "../clientsops/clientActionsApi";
import * as clientDetail from "../clients/clientsApi";
import * as parts from "../salespartsops/partStandardsApi";
vi.mock("./projectsApi");
vi.mock("../shared/sharedApi");
vi.mock("../clientsops/clientActionsApi");
vi.mock("../clients/clientsApi");
vi.mock("../salespartsops/partStandardsApi");
vi.mock("../b9/B9UploadDialog", () => ({
  B9UploadActions: ({ readOnly }: { readOnly: boolean }) => (
    <button disabled={readOnly}>엑셀 업로드</button>
  ),
}));
vi.mock("../distribution/ProjectDistributionPanel", () => ({
  ProjectDistributionPanel: ({
    projectId,
    onBack,
  }: {
    projectId: string;
    onBack(): void;
  }) => (
    <section data-testid="distribution-region">
      배분표 {projectId}
      <button onClick={onBack}>목록</button>
    </section>
  ),
}));
vi.mock("../expenses/ProjectExpensesPanel", () => ({
  ProjectExpensesPanel: () => <div>추가 직접 경비</div>,
}));
vi.mock("../plans/ProjectPlansPanel", () => ({
  ProjectPlansPanel: ({
    readOnly,
    onDirtyChange,
  }: {
    readOnly: boolean;
    onDirtyChange?(dirty: boolean): void;
  }) => (
    <section data-testid="plan-region">
      <button disabled={readOnly} onClick={() => onDirtyChange?.(true)}>
        계획 입력
      </button>
    </section>
  ),
}));
vi.mock("../budget/ProjectBudgetDialog", () => ({
  ProjectBudgetDialog: () => <div>예산 변경 창</div>,
}));
const project: api.Project = {
  id: "P-0042",
  name: "검증 프로젝트",
  customerId: "C-42",
  contractType: "TK",
  status: "ING",
  startDate: "2025-01-01",
  endDate: "2027-12-31",
  workplace: "서울 전체 주소",
  feature: "검증 특징",
  estimateAmount: "777777",
  originalContractAmount: "100",
  contractAmount: "100",
  budgetAmount: "80",
  teamExpenseAmount: "0",
  projectExpenseAmount: "0",
  budgetManual: "N",
  lossDate: null,
  lossReason: null,
  lossMemo: null,
  opportunityId: null,
};
const item: api.ProjectListItem = {
  ...project,
  customerName: "검증 고객",
  personCount: 2,
  partName: "본사",
  distribution: {
    selectedStep: "PLAN",
    hasTable: true,
    totalAmount: "99",
    contractDifference: "1",
    mismatchCount: 3,
  },
};
const detail: api.ProjectDetail = {
  project,
  revision: "opaque-revision",
  effectiveStatus: "ING",
  readOnly: false,
  parts: [
    {
      effectiveMonth: "2025-01",
      partId: "HQ",
      partName: "본사",
      reason: "계약 시작",
      actor: "관리자",
      scheduled: false,
    },
    {
      effectiveMonth: "2027-01",
      partId: "PART-42",
      partName: "영업파트",
      reason: "예약 사유",
      actor: "변경자",
      scheduled: true,
    },
  ],
  pm: [
    { userId: "pm-a", name: "PM 가", scopeSn: 1 },
    { userId: "pm-b", name: "PM 나", scopeSn: 2 },
  ],
  plans: [],
  assignments: [],
  history: [],
  actualMmTotal: "0.00",
  directExpenseTotal: "0",
};
const defaults: api.ProjectDefaults = {
  contractAmount: "100",
  contractType: "TK",
  budgetAmount: "80",
  targetMarginRate: "20",
  teamExpenseAmount: "50000",
  projectExpenseAmount: "30000",
  basisMonth: "2026-07",
  warnings: [],
};
const result: api.SaveProjectResult = {
  project,
  revision: "new-revision",
  changed: true,
  defaults,
  warnings: ["최종 서버 기준 적용"],
};
const labels = (group: string, code: string) =>
  ({
    TK: "턴키",
    IN: "내부",
    ING: "진행",
    PRE: "수주예정",
    END: "종료",
    LOSE: "실주",
  })[code as "TK"] ?? code;
beforeEach(() => {
  vi.resetAllMocks();
  window.scrollTo = vi.fn();
  Object.defineProperty(window, "matchMedia", {
    configurable: true,
    value: vi.fn().mockReturnValue({
      matches: false,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    }),
  });
  vi.mocked(shared.getSharedCodes).mockImplementation(async (group) => ({
    items: (group === "CTRT_TYPE"
      ? [
          { code: "TK", name: "턴키" },
          { code: "IN", name: "내부" },
        ]
      : group === "PROJ_STAT"
        ? [
            { code: "ING", name: "진행" },
            { code: "PRE", name: "수주예정" },
            { code: "END", name: "종료" },
            { code: "LOSE", name: "실주" },
          ]
        : []) as shared.SharedCodeLookup[],
  }));
  vi.mocked(clients.getClientOptions).mockResolvedValue({
    items: [{ id: "C-42", name: "검증 고객", type: "CUST" }],
  });
  vi.mocked(parts.getSalesPartOptions).mockResolvedValue({
    items: [{ id: "HQ", name: "본사", hqYn: "Y", firstEffectiveMonth: null }],
  });
  vi.mocked(api.getProjectDefaults).mockResolvedValue(defaults);
  vi.mocked(api.getProjects).mockImplementation(async (filters) => ({
    items: [item],
    total: 120,
    unassignedCount: 7,
    year: filters.year ?? 2026,
    page: filters.page,
    size: filters.size,
  }));
  vi.mocked(api.getProject).mockResolvedValue(detail);
  vi.mocked(api.createProject).mockResolvedValue(result);
  vi.mocked(api.putProject).mockResolvedValue(result);
});
const props = { year: 2026, currentYear: 2026, currentDate: "2026-07-01" };
async function formReady() {
  await waitFor(() =>
    expect(screen.getByTestId("project-form-save")).toBeEnabled(),
  );
}
async function fillNew() {
  await formReady();
  fireEvent.change(screen.getByTestId("project-name"), {
    target: { value: "새 프로젝트" },
  });
  await userEvent.selectOptions(screen.getByLabelText("계약유형"), "TK");
  await userEvent.selectOptions(screen.getByLabelText("고객사"), "C-42");
  await userEvent.selectOptions(screen.getByLabelText("영업 파트"), "HQ");
  fireEvent.change(screen.getByLabelText(/계약 시작일/), {
    target: { value: "2026-01-01" },
  });
  fireEvent.change(screen.getByLabelText(/계약 종료일/), {
    target: { value: "2027-12-31" },
  });
  fireEvent.change(screen.getByLabelText(/계약금액/), {
    target: { value: "0" },
  });
}
function panel(
  overrides: Partial<Parameters<typeof ProjectDetailPanel>[0]> = {},
) {
  return render(
    <ProjectDetailPanel
      {...props}
      id={project.id}
      labels={labels}
      onClose={vi.fn()}
      onEdit={vi.fn()}
      onDistribution={vi.fn()}
      onChanged={vi.fn()}
      {...overrides}
    />,
  );
}
it("uses exact money normalization and unit division without Number", () => {
  expect(projectMoneyInput(" 999,999,999,999,999\u00a0원 ")).toBe(
    "999999999999999",
  );
  for (const input of ["1.1", "-1", "1e3", "+1", "1%", "9999999999999999"])
    expect(projectMoneyInput(input)).toBeNull();
  expect(projectAmount("999999999999999", "백만원")).toBe("999,999,999.999999");
  expect(projectAmount(null, "원")).toBe("–");
});
it("list renders all ten columns and server banner total with distinct badge tones", async () => {
  render(<ProjectsScreen {...props} />);
  await screen.findByText(project.name);
  for (const text of [
    "프로젝트ID",
    "프로젝트명",
    "고객사",
    "계약유형",
    "계약 기간",
    "계약금액",
    "투입 인원",
    "영업 파트",
    "상태",
    "배분표",
  ])
    expect(
      screen.getByRole("columnheader", { name: new RegExp(text) }),
    ).toBeInTheDocument();
  expect(screen.getByTestId("project-unassigned-banner")).toHaveTextContent(
    "7건",
  );
  expect(
    screen.getAllByText("진행").find((node) => node.tagName === "SPAN"),
  ).toHaveClass("sicost-tone-info");
  expect(
    screen.getByTestId(`project-distribution-${project.id.toLowerCase()}`),
  ).toHaveTextContent("PLAN 합계 경고 불일치 3");
});
it("unassigned CTA only changes part filter and resets page preserving year search status and type", async () => {
  render(<ProjectsScreen {...props} />);
  await screen.findByText(project.name);
  await userEvent.selectOptions(screen.getByLabelText("계약유형"), "TK");
  await userEvent.selectOptions(screen.getByLabelText("상태"), "PRE");
  fireEvent.change(screen.getByPlaceholderText("프로젝트명 또는 고객사"), {
    target: { value: "  A%_  " },
  });
  await userEvent.click(screen.getByText("조회"));
  await userEvent.click(screen.getByRole("button", { name: "다음 페이지" }));
  await waitFor(() =>
    expect(api.getProjects).toHaveBeenLastCalledWith(
      expect.objectContaining({ page: 2 }),
    ),
  );
  await userEvent.click(screen.getByText("이 건만 보기"));
  await waitFor(() =>
    expect(api.getProjects).toHaveBeenLastCalledWith(
      expect.objectContaining({
        year: 2026,
        q: "A%_",
        status: "PRE",
        contractType: "TK",
        partId: "UNASSIGNED",
        page: 1,
      }),
    ),
  );
});
it("query Enter and reset share real filters while sort and size reset page1", async () => {
  render(<ProjectsScreen {...props} />);
  await screen.findByText(project.name);
  fireEvent.change(screen.getByPlaceholderText("프로젝트명 또는 고객사"), {
    target: { value: "검색" },
  });
  fireEvent.submit(screen.getByTestId("project-filter-form"));
  await waitFor(() =>
    expect(api.getProjects).toHaveBeenLastCalledWith(
      expect.objectContaining({ q: "검색" }),
    ),
  );
  await userEvent.click(screen.getByRole("button", { name: "다음 페이지" }));
  await userEvent.click(screen.getByTestId("sort-name"));
  await waitFor(() =>
    expect(api.getProjects).toHaveBeenLastCalledWith(
      expect.objectContaining({ sort: "name", page: 1 }),
    ),
  );
  await userEvent.selectOptions(screen.getByLabelText("한 화면 건수"), "20");
  await waitFor(() =>
    expect(api.getProjects).toHaveBeenLastCalledWith(
      expect.objectContaining({ size: 20, page: 1 }),
    ),
  );
  await userEvent.click(screen.getByText("초기화"));
  await waitFor(() =>
    expect(api.getProjects).toHaveBeenLastCalledWith(
      expect.objectContaining({
        q: undefined,
        partId: undefined,
        year: 2026,
        page: 1,
      }),
    ),
  );
});
it("past year disables business creation and upload but keeps settings available", async () => {
  const settings = vi.fn();
  render(<ProjectsScreen {...props} year={2025} onColumnsChange={settings} />);
  await screen.findByText(project.name);
  expect(screen.getByTestId("project-create")).toBeDisabled();
  expect(screen.getByRole("button", { name: "엑셀 업로드" })).toBeDisabled();
  await userEvent.click(screen.getByText("열 설정"));
  expect(settings).toHaveBeenCalledOnce();
});
it("next-year context queries with year and allows planned editing", async () => {
  render(<ProjectsScreen {...props} year={2027} />);
  await screen.findByText(project.name);
  expect(screen.getByTestId("project-create")).toBeEnabled();
  expect(api.getProjects).toHaveBeenCalledWith(
    expect.objectContaining({ year: 2027 }),
  );
});
it("empty and failed states are distinct and retry only reads", async () => {
  vi.mocked(api.getProjects).mockRejectedValueOnce(
    new ApiError(500, {
      success: false,
      meta: {},
      code: "INTERNAL_ERROR",
      message: "조회 오류",
      requestId: "req-list",
    }),
  );
  vi.mocked(api.getProjects).mockResolvedValue({
    items: [],
    total: 0,
    unassignedCount: 0,
    year: 2026,
    page: 1,
    size: 50,
  });
  render(<ProjectsScreen {...props} />);
  await screen.findByText(/조회 오류/);
  expect(screen.getByText(/req-list/)).toBeInTheDocument();
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  await screen.findByText("조회된 자료가 없습니다.");
  expect(api.createProject).not.toHaveBeenCalled();
});
it("TK distribution replaces list region and back restores filters without route", async () => {
  render(<ProjectsScreen {...props} />);
  await screen.findByText(project.name);
  await userEvent.click(
    screen.getByTestId(`project-distribution-${project.id.toLowerCase()}`),
  );
  expect(screen.queryByTestId("project-filter-form")).not.toBeInTheDocument();
  expect(screen.getByTestId("distribution-region")).toHaveTextContent(
    project.id,
  );
  await userEvent.click(screen.getByText("목록"));
  expect(screen.getByTestId("project-filter-form")).toBeInTheDocument();
});
it("mobile uses twenty-card more and hides desktop-only actions", async () => {
  vi.mocked(window.matchMedia).mockReturnValue({
    matches: true,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
  } as unknown as MediaQueryList);
  render(<ProjectsScreen {...props} />);
  await screen.findByTestId(`project-card-${project.id.toLowerCase()}`);
  expect(api.getProjects).toHaveBeenCalledWith(
    expect.objectContaining({ size: 20 }),
  );
  expect(screen.queryByTestId("project-export")).not.toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: "엑셀 업로드" }),
  ).not.toBeInTheDocument();
  await userEvent.click(screen.getByText("20개 더보기"));
  await waitFor(() =>
    expect(api.getProjects).toHaveBeenLastCalledWith(
      expect.objectContaining({ page: 2, size: 20 }),
    ),
  );
});
it("create validates before POST and focuses first invalid field", async () => {
  render(<ProjectForm year={2026} onClose={vi.fn()} onSaved={vi.fn()} />);
  await formReady();
  await userEvent.click(screen.getByTestId("project-form-save"));
  expect(api.createProject).not.toHaveBeenCalled();
  expect(screen.getByTestId("project-name")).toHaveFocus();
});
it("new create sends zero and initial identity only with exact year and clears dirty after success", async () => {
  const saved = vi.fn(),
    dirty = vi.fn();
  render(
    <ProjectForm
      year={2027}
      onClose={vi.fn()}
      onSaved={saved}
      onDirtyChange={dirty}
    />,
  );
  await fillNew();
  await userEvent.click(screen.getByTestId("project-form-save"));
  await waitFor(() => expect(saved).toHaveBeenCalledWith(result));
  expect(api.createProject).toHaveBeenCalledWith(
    expect.objectContaining({
      year: 2027,
      contractType: "TK",
      initialStatus: "ING",
      partId: "HQ",
      contractAmount: "0",
    }),
  );
  expect(dirty).toHaveBeenLastCalledWith(false);
});
it("IN switching hides and clears customer part workplace budget while retaining estimate", async () => {
  render(<ProjectForm year={2026} onClose={vi.fn()} onSaved={vi.fn()} />);
  await fillNew();
  fireEvent.change(screen.getByLabelText("견적 금액"), {
    target: { value: "99" },
  });
  fireEvent.change(screen.getByLabelText("근무지"), {
    target: { value: "삭제할 주소" },
  });
  await userEvent.selectOptions(screen.getByLabelText("계약유형"), "IN");
  for (const name of ["고객사", "영업 파트", "근무지", "예산 입력 방식"])
    expect(screen.queryByLabelText(name)).not.toBeInTheDocument();
  await userEvent.click(screen.getByTestId("project-form-save"));
  await waitFor(() =>
    expect(api.createProject).toHaveBeenCalledWith(
      expect.objectContaining({
        contractType: "IN",
        customerId: null,
        partId: null,
        workplace: null,
        estimateAmount: "99",
      }),
    ),
  );
  const payload = vi.mocked(api.createProject).mock.calls[0][0];
  expect(payload).not.toHaveProperty("budgetAmount");
  expect(payload).not.toHaveProperty("budgetMode");
});
it("update omits immutable contract status part original loss audit fields", async () => {
  render(
    <ProjectForm
      year={2026}
      detail={detail}
      onClose={vi.fn()}
      onSaved={vi.fn()}
    />,
  );
  await formReady();
  expect(screen.queryByLabelText("계약유형")).not.toBeInTheDocument();
  expect(screen.getByText(/계약유형은 등록 후/)).toBeInTheDocument();
  expect(screen.getByText(/영업 파트는 여기서/)).toBeInTheDocument();
  await userEvent.click(screen.getByTestId("project-form-save"));
  await waitFor(() => expect(api.putProject).toHaveBeenCalled());
  const body = vi.mocked(api.putProject).mock.calls[0][1];
  expect(body.expectedRevision).toBe(detail.revision);
  for (const key of [
    "id",
    "contractType",
    "status",
    "partId",
    "initialStatus",
    "originalContractAmount",
    "lossDate",
    "opportunityId",
    "modUserId",
  ])
    expect(body).not.toHaveProperty(key);
});
it("existing manual budget cannot switch to AUTO and explicit equal amount remains manual", async () => {
  render(
    <ProjectForm
      year={2026}
      detail={{ ...detail, project: { ...project, budgetManual: "Y" } }}
      onClose={vi.fn()}
      onSaved={vi.fn()}
    />,
  );
  await formReady();
  expect(
    within(screen.getByLabelText("예산 입력 방식")).queryByRole("option", {
      name: "자동",
    }),
  ).not.toBeInTheDocument();
  await userEvent.click(screen.getByTestId("project-form-save"));
  await waitFor(() =>
    expect(api.putProject).toHaveBeenCalledWith(
      project.id,
      expect.objectContaining({ budgetMode: "MANUAL", budgetAmount: "80" }),
    ),
  );
});
it("unavailable automatic preview permits explicit manual zero and blanks retain server defaults", async () => {
  vi.mocked(api.getProjectDefaults).mockResolvedValue({
    ...defaults,
    budgetAmount: null,
    targetMarginRate: null,
    warnings: ["수기로 입력"],
  });
  render(<ProjectForm year={2026} onClose={vi.fn()} onSaved={vi.fn()} />);
  await fillNew();
  await screen.findByText(/수기로 입력/);
  await userEvent.selectOptions(
    screen.getByLabelText("예산 입력 방식"),
    "MANUAL",
  );
  fireEvent.change(screen.getByLabelText(/예산\(목표 원가\)/), {
    target: { value: "0" },
  });
  await userEvent.click(screen.getByTestId("project-form-save"));
  await waitFor(() =>
    expect(api.createProject).toHaveBeenCalledWith(
      expect.objectContaining({
        budgetMode: "MANUAL",
        budgetAmount: "0",
        teamExpenseAmount: null,
      }),
    ),
  );
});
it("inactive existing customer name is preserved rather than replaced with ID", async () => {
  vi.mocked(clients.getClientOptions).mockResolvedValue({ items: [] });
  vi.mocked(clientDetail.getClient).mockResolvedValue({
    id: "C-42",
    name: "기존 고객",
    type: "CUST",
    useYn: "N",
  } as clientDetail.Client);
  render(
    <ProjectForm
      year={2026}
      detail={detail}
      onClose={vi.fn()}
      onSaved={vi.fn()}
    />,
  );
  await formReady();
  expect(
    screen.getByRole("option", { name: "기존 고객 (중지)" }),
  ).toHaveProperty("selected", true);
});
it("server field error retains input and request id without automatic resubmit", async () => {
  vi.mocked(api.putProject).mockRejectedValue(
    new ApiError(400, {
      success: false,
      meta: {},
      code: "INVALID_INPUT",
      message: "이름 오류",
      field: "name",
      requestId: "req-form",
    }),
  );
  render(
    <ProjectForm
      year={2026}
      detail={detail}
      onClose={vi.fn()}
      onSaved={vi.fn()}
    />,
  );
  await formReady();
  fireEvent.change(screen.getByTestId("project-name"), {
    target: { value: "유지할 이름" },
  });
  await userEvent.click(screen.getByTestId("project-form-save"));
  await screen.findAllByText("이름 오류");
  expect(screen.getByTestId("project-name")).toHaveValue("유지할 이름");
  expect(screen.getByText(/req-form/)).toBeInTheDocument();
  expect(api.putProject).toHaveBeenCalledOnce();
});
it("saved refresh failure cannot resave and explicit retry only refreshes", async () => {
  const refresh = vi
    .fn()
    .mockRejectedValueOnce(new Error("read failed"))
    .mockResolvedValueOnce(undefined);
  render(
    <ProjectForm
      year={2026}
      detail={detail}
      onClose={vi.fn()}
      onSaved={refresh}
    />,
  );
  await formReady();
  await userEvent.click(screen.getByTestId("project-form-save"));
  await screen.findByText(/저장은 완료됐지만 조회하지 못했습니다/);
  expect(screen.getByTestId("project-form-save")).toBeDisabled();
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  await waitFor(() => expect(refresh).toHaveBeenCalledTimes(2));
  expect(api.putProject).toHaveBeenCalledOnce();
});
it("dirty cancel keeps inputs on stay and leaves without API on discard", async () => {
  const close = vi.fn();
  render(<ProjectForm year={2026} onClose={close} onSaved={vi.fn()} />);
  await formReady();
  fireEvent.change(screen.getByTestId("project-name"), {
    target: { value: "작성 중" },
  });
  await userEvent.click(screen.getByTestId("project-form-cancel"));
  await userEvent.click(screen.getByRole("button", { name: "계속 작성" }));
  expect(screen.getByTestId("project-name")).toHaveValue("작성 중");
  await userEvent.click(screen.getByTestId("project-form-cancel"));
  await userEvent.click(screen.getByRole("button", { name: "나가기" }));
  expect(close).toHaveBeenCalledOnce();
  expect(api.createProject).not.toHaveBeenCalled();
});
it("detail excludes estimate and fake progress, displays four areas, all PMs and scheduled reason", async () => {
  panel();
  await screen.findByText(project.name);
  expect(screen.queryByText("777777")).not.toBeInTheDocument();
  expect(screen.getAllByRole("tab")).toHaveLength(4);
  expect(screen.getByText(/PM 가/)).toBeInTheDocument();
  expect(screen.getByText(/PM 나/)).toBeInTheDocument();
  expect(screen.getByTitle("사유: 예약 사유 · 변경자")).toBeInTheDocument();
  expect(screen.getByText("(예정)")).toHaveClass("sicost-tone-info");
  expect(screen.getByText(/진행현황·이슈는 후속 기능/)).toBeInTheDocument();
});
it("cancelled assignment toggle sends only includeCancelled and no year", async () => {
  panel();
  await screen.findByText(project.name);
  await userEvent.click(screen.getByRole("tab", { name: "투입 인력" }));
  await userEvent.click(screen.getByLabelText("취소 포함"));
  await waitFor(() =>
    expect(api.getProject).toHaveBeenLastCalledWith(project.id, true),
  );
});
it("LOSE detail and past-year details disable edit and budget but keep reads", async () => {
  vi.mocked(api.getProject).mockResolvedValue({
    ...detail,
    project: {
      ...project,
      status: "LOSE",
      lossDate: "2026-07-01",
      lossReason: "PRICE",
      lossMemo: "실주 메모",
    },
    effectiveStatus: "LOSE",
    readOnly: true,
  });
  panel();
  await screen.findByText(/실주 2026-07-01/);
  expect(screen.getByTestId("project-detail-edit")).toBeDisabled();
  expect(screen.getByTestId("project-budget-open")).toBeDisabled();
  expect(screen.getByText(/실주 메모/)).toBeInTheDocument();
});
it("past-year plan gets readOnly state and header cannot edit", async () => {
  panel({ year: 2025 });
  await screen.findByText(project.name);
  expect(screen.getByTestId("project-detail-edit")).toBeDisabled();
  await userEvent.click(screen.getByRole("tab", { name: "인원 계획" }));
  expect(screen.getByRole("button", { name: "계획 입력" })).toBeDisabled();
});
it("dirty child prevents close and tab switch until explicit discard", async () => {
  const close = vi.fn(),
    dirty = vi.fn();
  panel({ onClose: close, onDirtyChange: dirty });
  await screen.findByText(project.name);
  await userEvent.click(screen.getByRole("tab", { name: "인원 계획" }));
  await userEvent.click(screen.getByRole("button", { name: "계획 입력" }));
  expect(dirty).toHaveBeenLastCalledWith(true);
  expect(screen.getByRole("tab", { name: "기본 정보" })).toBeDisabled();
  await userEvent.click(screen.getByTestId("detail-drawer-close"));
  await userEvent.click(screen.getByRole("button", { name: "계속 작성" }));
  expect(close).not.toHaveBeenCalled();
  await userEvent.click(screen.getByTestId("detail-drawer-close"));
  await userEvent.click(screen.getByRole("button", { name: "나가기" }));
  expect(close).toHaveBeenCalledOnce();
});

it("등록 모달 뒤 목록을 유지하고 Escape 취소에서 미저장 확인을 제공한다", async () => {
  render(<ProjectsScreen {...props} />);
  await screen.findByTestId("project-open-p-0042");
  await userEvent.click(screen.getByTestId("project-create"));
  const dialog=screen.getByRole("dialog", {name: "프로젝트 등록"});
  expect(screen.getByTestId("project-open-p-0042")).toBeVisible();
  expect(dialog).toContainElement(screen.getByTestId("project-form"));
  await formReady();
  fireEvent.change(screen.getByTestId("project-name"), {target: {value: "모달 초안"}});
  fireEvent.keyDown(dialog, {key: "Escape"});
  await userEvent.click(screen.getByRole("button", {name: "계속 작성"}));
  expect(screen.getByTestId("project-name")).toHaveValue("모달 초안");
  fireEvent.keyDown(dialog, {key: "Escape"});
  await userEvent.click(screen.getByRole("button", {name: "나가기"}));
  expect(screen.queryByTestId("project-form")).not.toBeInTheDocument();
  expect(api.createProject).not.toHaveBeenCalled();
});
