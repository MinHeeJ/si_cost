import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import {
  ProjectStatusPanel,
  type ProjectStatusPanelProps,
} from "./ProjectStatusPanel";
import {
  ProjectDefaultsPreview,
  ProjectOptionSelect,
} from "./ProjectDefaultsPreview";
import { ApiError } from "../../shared/api";
import * as api from "./projectStatusApi";
import * as shared from "../shared/sharedApi";
vi.mock("./projectStatusApi");
vi.mock("../shared/sharedApi");
const period: shared.PeriodContext = {
  currentDate: "2026-07-01",
  currentMonth: "2026-07",
  timezone: "Asia/Seoul",
  cashBaseMonth: "2025-01",
  monthClose: { month: "2026-07", status: "작업중" },
};
const preview: api.ProjectLossPreview = {
  basisDate: "2026-07-01",
  currentPersonCount: 1,
  currentAssignmentCount: 2,
  futurePersonCount: 0,
  futureAssignmentCount: 0,
  currentAssignments: ["a1", "a2"].map((id) => ({
    assignmentId: id,
    developerId: "dev-pk",
    managementNo: "mng-no",
    name: "개발자",
    jobCode: "DEV",
    jobName: "개발",
    startDate: "2026-07-01",
    endDate: "2026-07-31",
    contractMm: "1.00",
    monthlyMmTotal: "1.00",
    status: "ACT",
  })),
  futureAssignments: [],
  previewToken: "opaque-token",
  revision: "preview-revision",
};
const value: api.ProjectValue = {
  id: "P-selected",
  name: "선택 프로젝트",
  customerId: null,
  contractType: "TK",
  status: "LOSE",
  startDate: "2026-01-01",
  endDate: "2027-01-01",
  workplace: null,
  feature: null,
  estimateAmount: null,
  originalContractAmount: "101",
  contractAmount: "101",
  budgetAmount: "80",
  teamExpenseAmount: "0",
  projectExpenseAmount: "0",
  budgetManual: "N",
  lossDate: "2026-07-01",
  lossReason: "ETC",
  lossMemo: null,
  opportunityId: null,
};
const saved: api.ProjectStatusResult = {
  value,
  revision: "after",
  changed: true,
};
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(shared.getPeriodContext).mockResolvedValue(period);
  vi.mocked(shared.getSharedCodes).mockResolvedValue({
    items: [
      {
        code: "ETC",
        name: "기타",
        addVal1: null,
        addVal2: null,
        addVal3: null,
        codeVal: null,
        unitNm: null,
      },
    ],
  });
  vi.mocked(api.getProjectLossPreview).mockResolvedValue(preview);
  vi.mocked(api.saveProjectLoss).mockResolvedValue(saved);
  vi.mocked(api.saveProjectProgress).mockResolvedValue({
    ...saved,
    value: { ...value, status: "ING" },
  });
});
function draw(override: Partial<ProjectStatusPanelProps> = {}) {
  const props: ProjectStatusPanelProps = {
    projectId: value.id,
    name: value.name,
    effectiveStatus: "PRE",
    revision: "before",
    year: 2026,
    onChanged: vi.fn(),
    onDirtyChange: vi.fn(),
    ...override,
  };
  render(<ProjectStatusPanel {...props} />);
  return props;
}
async function ready() {
  await waitFor(() =>
    expect(screen.getByRole("button", { name: "진행 처리" })).not.toBeDisabled(),
  );
}
async function openLoss() {
  await ready();
  await userEvent.click(screen.getByRole("button", { name: "실주 처리" }));
  await screen.findByText("기준일 2026-07-01 (서버 오늘)");
}
it("진행 확인 전에 쓰지 않고 확인 후 경로 id/year/revision만 보낸다", async () => {
  const props = draw();
  await ready();
  await userEvent.click(screen.getByRole("button", { name: "진행 처리" }));
  expect(screen.getByRole("dialog")).toHaveTextContent(
    "금액/예산/원계약금액/배정/영업기회는 그대로입니다.",
  );
  expect(api.saveProjectProgress).not.toHaveBeenCalled();
  await userEvent.click(screen.getByRole("button", { name: "진행 확인" }));
  await waitFor(() => expect(props.onChanged).toHaveBeenCalled());
  expect(api.saveProjectProgress).toHaveBeenCalledWith(value.id, {
    year: 2026,
    expectedRevision: "before",
  });
});
it("진행 취소는 요청하지 않는다", async () => {
  draw();
  await ready();
  await userEvent.click(screen.getByRole("button", { name: "진행 처리" }));
  await userEvent.click(screen.getByRole("button", { name: "취소" }));
  expect(api.saveProjectProgress).not.toHaveBeenCalled();
});
it.each(["ING", "END", "LOSE"] as const)(
  "%s에는 전환 버튼이 없고 실주 미리보기도 호출하지 않는다",
  async (effectiveStatus) => {
    draw({ effectiveStatus });
    await waitFor(() => expect(shared.getPeriodContext).toHaveBeenCalled());
    expect(
      screen.queryByRole("button", { name: "진행 처리" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "실주 처리" }),
    ).not.toBeInTheDocument();
    expect(api.getProjectLossPreview).not.toHaveBeenCalled();
  },
);
it("지난 연도 전환을 비활성화한다", async () => {
  draw({ year: 2025 });
  await screen.findByText("지난 연도는 조회만 할 수 있습니다.");
  expect(screen.getByRole("button", { name: "진행 처리" })).toBeDisabled();
});
it("인원수와 배정건수·관리번호·전체 영향 및 빈 미래 배정을 분리해 표시한다", async () => {
  draw();
  await openLoss();
  expect(
    screen.getByRole("heading", { name: "현재 투입 1명 / 2건" }),
  ).toBeInTheDocument();
  expect(screen.getAllByText("mng-no")).toHaveLength(2);
  expect(screen.getByText("미래 투입 배정이 없습니다.")).toBeInTheDocument();
  expect(screen.queryByLabelText("처리일")).not.toBeInTheDocument();
});
it("사유 필수와 500자 제한은 입력을 보존하고 저장0이다", async () => {
  draw();
  await openLoss();
  await userEvent.click(screen.getByRole("button", { name: "실주 확정" }));
  expect(screen.getByLabelText("실주 사유 *")).toHaveAttribute(
    "aria-invalid",
    "true",
  );
  fireEvent.change(screen.getByLabelText("실주 사유 *"), {
    target: { value: "ETC" },
  });
  fireEvent.change(screen.getByLabelText("메모"), {
    target: { value: "가".repeat(501) },
  });
  await userEvent.click(screen.getByRole("button", { name: "실주 확정" }));
  expect(screen.getByLabelText("메모")).toHaveAttribute("aria-invalid", "true");
  expect(api.saveProjectLoss).not.toHaveBeenCalled();
});
it("실주 확인은 최신 미리보기 revision/token과 사유만 보낸다", async () => {
  const props = draw();
  await openLoss();
  fireEvent.change(screen.getByLabelText("실주 사유 *"), {
    target: { value: "ETC" },
  });
  await userEvent.click(screen.getByRole("button", { name: "실주 확정" }));
  await waitFor(() => expect(props.onChanged).toHaveBeenCalledWith(saved));
  expect(api.saveProjectLoss).toHaveBeenCalledWith(value.id, {
    year: 2026,
    expectedRevision: "preview-revision",
    previewToken: "opaque-token",
    lossReason: "ETC",
    lossMemo: null,
  });
});
it("실패 요청ID·입력을 보존하고 stale이면 새 영향 확인 전 저장 금지", async () => {
  vi.mocked(api.saveProjectLoss).mockRejectedValue(
    new ApiError(409, {
      success: false,
      meta: {},
      code: "STALE_WRITE",
      message: "배정 변경",
      requestId: "loss-stale",
    }),
  );
  draw();
  await openLoss();
  fireEvent.change(screen.getByLabelText("실주 사유 *"), {
    target: { value: "ETC" },
  });
  await userEvent.click(screen.getByRole("button", { name: "실주 확정" }));
  await screen.findByText(/자동으로 재저장하지 않습니다/);
  expect(screen.getByRole("button", { name: "실주 확정" })).toBeDisabled();
  expect(screen.getByLabelText("실주 사유 *")).toHaveValue("ETC");
  expect(screen.getByText(/loss-stale/)).toBeInTheDocument();
  await userEvent.click(screen.getByRole("button", { name: "영향 미리보기" }));
  await screen.findByText("기준일 2026-07-01 (서버 오늘)");
  expect(api.saveProjectLoss).toHaveBeenCalledOnce();
});
it("미리보기 실패는 확정 불가하고 명시 재조회만 허용", async () => {
  vi.mocked(api.getProjectLossPreview).mockRejectedValueOnce(
    new Error("영향 조회 실패"),
  );
  draw();
  await ready();
  await userEvent.click(screen.getByRole("button", { name: "실주 처리" }));
  await screen.findByText("영향 조회 실패");
  expect(screen.getByRole("button", { name: "실주 확정" })).toBeDisabled();
  await userEvent.click(screen.getByRole("button", { name: "영향 미리보기" }));
  await screen.findByText("기준일 2026-07-01 (서버 오늘)");
  expect(api.saveProjectLoss).not.toHaveBeenCalled();
});
it("dirty는 shell로 전달하고 나가기 확인 전에는 메모 유지", async () => {
  const props = draw();
  await openLoss();
  fireEvent.change(screen.getByLabelText("메모"), {
    target: { value: "실주 메모" },
  });
  await waitFor(() =>
    expect(props.onDirtyChange).toHaveBeenLastCalledWith(true),
  );
  await userEvent.click(screen.getByRole("button", { name: "취소" }));
  await userEvent.click(screen.getByRole("button", { name: "계속 작성" }));
  expect(screen.getByLabelText("메모")).toHaveValue("실주 메모");
  await userEvent.click(screen.getByRole("button", { name: "취소" }));
  await userEvent.click(screen.getByRole("button", { name: "나가기" }));
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  expect(api.saveProjectLoss).not.toHaveBeenCalled();
});
it("저장 후 조회 실패는 재저장이 아니라 조회만 재시도", async () => {
  const props = draw({
    onChanged: vi
      .fn()
      .mockRejectedValueOnce(new Error("조회 실패"))
      .mockResolvedValue(undefined),
  });
  await ready();
  await userEvent.click(screen.getByRole("button", { name: "진행 처리" }));
  await userEvent.click(screen.getByRole("button", { name: "진행 확인" }));
  await screen.findByText(/저장은 완료됐지만/);
  await userEvent.click(screen.getByTestId("project-status-refresh"));
  await waitFor(() => expect(props.onChanged).toHaveBeenCalledTimes(2));
  expect(api.saveProjectProgress).toHaveBeenCalledOnce();
});
it("저장 중 이중 제출 차단", async () => {
  let resolve!: (value: api.ProjectStatusResult) => void;
  vi.mocked(api.saveProjectProgress).mockReturnValue(
    new Promise((r) => {
      resolve = r;
    }),
  );
  draw();
  await ready();
  await userEvent.click(screen.getByRole("button", { name: "진행 처리" }));
  const dialog = screen.getByRole("dialog");
  fireEvent.submit(dialog);
  fireEvent.submit(dialog);
  await waitFor(() => expect(api.saveProjectProgress).toHaveBeenCalledOnce());
  expect(within(dialog).getByRole("button", { name: "취소" })).toBeDisabled();
  resolve(saved);
  await waitFor(() =>
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument(),
  );
});
it("기본금액은 서버 조회와 정확한 문자열을 표시하고 없는 기준은 수기 안내", async () => {
  vi.mocked(api.getProjectDefaults).mockResolvedValue({
    contractAmount: "999999999999999",
    contractType: "TK",
    budgetAmount: null,
    targetMarginRate: null,
    teamExpenseAmount: "0",
    projectExpenseAmount: null,
    basisMonth: "2026-07",
    warnings: ["기준 없음"],
  });
  render(
    <ProjectDefaultsPreview
      contractAmount="999999999999999"
      contractType="TK"
    />,
  );
  await screen.findByText("기준 없음");
  expect(screen.getAllByText("수기 입력 필요")).toHaveLength(2);
  expect(api.getProjectDefaults).toHaveBeenCalledWith("999999999999999", "TK");
});
it("기본금액 실패 후 재조회하고 입력금액 변경 시 새 계산", async () => {
  vi.mocked(api.getProjectDefaults)
    .mockRejectedValueOnce(new Error("기준 조회 실패"))
    .mockResolvedValue({
      contractAmount: "101",
      contractType: "TK",
      budgetAmount: "80",
      targetMarginRate: "20",
      teamExpenseAmount: "0",
      projectExpenseAmount: "0",
      basisMonth: "2026-07",
      warnings: [],
    });
  const view = render(
    <ProjectDefaultsPreview contractAmount="101" contractType="TK" />,
  );
  await screen.findByText("기준 조회 실패");
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  await screen.findByText("80");
  view.rerender(
    <ProjectDefaultsPreview contractAmount="102" contractType="TK" />,
  );
  await waitFor(() =>
    expect(api.getProjectDefaults).toHaveBeenLastCalledWith("102", "TK"),
  );
});
it("옵션은 실제 응답 id로 선택하고 빈 목록을 가상 데이터로 채우지 않는다", async () => {
  vi.mocked(api.getProjectOptions)
    .mockResolvedValueOnce({
      items: [
        {
          id: value.id,
          name: value.name,
          contractType: "TK",
          status: "PRE",
          startDate: value.startDate,
          endDate: value.endDate,
          contractAmount: "101",
          customerName: null,
          personCount: 0,
          partName: null,
          distribution: null,
        },
      ],
    })
    .mockResolvedValue({ items: [] });
  const onChange = vi.fn();
  render(
    <ProjectOptionSelect purpose="BILLING" value="" onChange={onChange} />,
  );
  await screen.findByRole("option", { name: /선택 프로젝트/ });
  fireEvent.change(screen.getByLabelText("프로젝트"), {
    target: { value: value.id },
  });
  expect(onChange).toHaveBeenCalledWith(value.id);
  fireEvent.change(screen.getByLabelText("프로젝트 검색"), {
    target: { value: "없음" },
  });
  await screen.findByText("선택 가능한 진행·수주예정 프로젝트가 없습니다.");
  expect(api.getProjectOptions).toHaveBeenLastCalledWith("BILLING", "없음");
});
