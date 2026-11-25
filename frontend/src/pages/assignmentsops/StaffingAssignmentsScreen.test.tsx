import {
  fireEvent,
  render as baseRender,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { MemoryRouter, useLocation } from "react-router-dom";
import type { ReactElement } from "react";
const render = (ui: ReactElement) =>
  baseRender(<MemoryRouter>{ui}</MemoryRouter>);
function CurrentLocation() {
  const location = useLocation();
  return (
    <output data-testid="current-location">
      {location.pathname}
      {location.search}
    </output>
  );
}
import {
  AssignmentsContent,
  StaffingAssignmentsScreen,
} from "./StaffingAssignmentsScreen";
import {
  AssignmentChangeDialog,
  AssignmentBulkDialog,
} from "./AssignmentDialogs";
import { AssignmentCreateForm } from "./AssignmentCreateForm";
import { AssignmentDrawer } from "./AssignmentDrawer";
import * as api from "./assignmentActionsApi";
import * as shared from "../shared/sharedApi";
import * as people from "../developers/peopleClient";
import { ApiError } from "../../shared/api";
import { PermissionContext } from "../../shared/permission/PermissionBoundary";
vi.mock("./assignmentActionsApi");
vi.mock("../shared/sharedApi");
vi.mock("../developers/peopleClient");
const context: shared.PeriodContext = {
  currentDate: "2026-07-01",
  currentMonth: "2026-07",
  cashBaseMonth: "2025-01",
  timezone: "Asia/Seoul",
  monthClose: { month: "2026-07", status: "작업중" },
};
const row: api.Assignment = {
  id: "A-101",
  projectId: "P-101",
  projectName: "실제 프로젝트",
  developerId: "D-101",
  developerName: "담당 인력",
  managementNo: "M-101",
  jobCode: "DEV",
  jobName: "개발",
  startDate: "2026-07-01",
  endDate: "2026-08-31",
  contractMm: "1.00",
  billingUnitPrice: "999999999999999",
  inputType: "NEW",
  contractDeveloperId: null,
  extensionPossibility: "H",
  extensionCount: 0,
  earlyWithdrawal: "N",
  assignmentStatus: "ACT",
  yearMm: "2.00",
  revision: "rev1",
  warnings: [],
  protectedMonths: [],
};
const selection: api.Selection = {
  items: [{ id: row.id, expectedRevision: row.revision }],
  total: 1,
  protectedMonths: [],
};
it("opens shared UI-30 project context only after confirming unsaved changes", async () => {
  const close = vi.fn();
  render(
    <>
      <AssignmentCreateForm
        year={2026}
        today="2026-07-01"
        onClose={close}
        onSaved={vi.fn()}
      />
      <CurrentLocation />
    </>,
  );
  const button = screen.getByTestId("assignment-ai-recommend");
  expect(button).toBeDisabled();
  expect(screen.getByTestId("assignment-ai-reason")).toHaveTextContent(
    "프로젝트와 직무",
  );
  await screen.findByRole("option", { name: /실제 프로젝트/ });
  fireEvent.change(screen.getByTestId("assignment-project"), {
    target: { value: row.projectId },
  });
  expect(button).toBeDisabled();
  fireEvent.change(screen.getByTestId("assignment-job"), {
    target: { value: "ETC" },
  });
  fireEvent.change(screen.getByTestId("assignment-create-start-date"), {
    target: { value: "2026-09-15" },
  });
  fireEvent.click(button);
  expect(close).not.toHaveBeenCalled();
  const guard = await screen.findByRole("dialog", {
    name: "작성 중인 내용을 나가시겠습니까?",
  });
  fireEvent.click(within(guard).getByRole("button", { name: "계속 작성" }));
  expect(screen.getByTestId("assignment-create-start-date")).toHaveValue(
    "2026-09-15",
  );
  expect(screen.getByTestId("current-location")).toHaveTextContent(/^\/$/);
  fireEvent.click(button);
  fireEvent.click(
    within(
      screen.getByRole("dialog", { name: "작성 중인 내용을 나가시겠습니까?" }),
    ).getByRole("button", { name: "나가기" }),
  );
  await waitFor(() =>
    expect(screen.getByTestId("current-location")).toHaveTextContent(
      "/UI-30?targetType=PROJECT&targetId=P-101&jobCode=ETC&startMonth=2026-09",
    ),
  );
  expect(close).toHaveBeenCalledOnce();
  expect(api.createAssignment).not.toHaveBeenCalled();
});
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api.getAssignments).mockResolvedValue({
    items: [row],
    total: 1,
    page: 1,
    size: 50,
  });
  vi.mocked(api.projectOptions).mockResolvedValue({
    items: [
      {
        id: row.projectId,
        name: row.projectName,
        contractType: "MO",
        status: "ING",
        startDate: row.startDate,
        endDate: row.endDate,
      },
    ],
  });
  vi.mocked(api.developerOptions).mockResolvedValue({
    items: [
      {
        id: row.developerId,
        name: row.developerName,
        managementNo: row.managementNo,
      },
    ],
    total: 1,
    page: 1,
    size: 50,
  });
  vi.mocked(api.getAssignment).mockResolvedValue(row);
  vi.mocked(api.getMonths).mockResolvedValue({
    items: [],
    total: 0,
    page: 1,
    size: 50,
  });
  vi.mocked(api.getHistory).mockResolvedValue({
    items: [],
    total: 0,
    page: 1,
    size: 50,
  });
  vi.mocked(api.selectAssignments).mockResolvedValue(selection);
  vi.mocked(api.preview).mockResolvedValue({
    warnings: [],
    affectedMonths: ["2026-07"],
    protectedMonths: [],
    months: [],
  });
  vi.mocked(api.changeAssignment).mockResolvedValue({
    assignment: row,
    changed: true,
    warnings: [],
    protectedMonths: [],
  });
  vi.mocked(api.createAssignment).mockResolvedValue({
    assignment: row,
    changed: true,
    warnings: [],
    protectedMonths: [],
  });
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
  vi.mocked(shared.getPeriodContext).mockResolvedValue(context);
  vi.mocked(shared.getSharedPreferences).mockResolvedValue({ items: [] });
  vi.mocked(people.getDeveloper).mockResolvedValue({
    priceHistory: [],
    jobCode: "ETC",
  } as unknown as people.DeveloperDetail);
});
const draw = (year = 2026) =>
  render(<AssignmentsContent year={year} context={context} />);
const ready = async () => {
  await screen.findByRole("cell", { name: "실제 프로젝트" });
  await waitFor(() =>
    expect(screen.getByTestId("assignment-new")).not.toBeDisabled(),
  );
};
it("경고 종류를 구분하고 MM 합계와 미계약 선투입 필터를 제공한다", async () => {
  vi.mocked(api.getAssignments).mockResolvedValue({
    items: [
      {
        ...row,
        warnings: [
          {
            code: "UNCONTRACTED",
            message: "미계약 수주예정 프로젝트입니다.",
            months: [],
            assignmentIds: [row.id],
          },
          {
            code: "PART_DIFFERENCE",
            message: "영업 파트가 다릅니다.",
            months: [],
            assignmentIds: [row.id],
          },
          {
            code: "MM_OVER",
            message: "2026-07 전프로젝트 월MM 1.50 > 1.0",
            months: ["2026-07"],
            assignmentIds: [row.id],
          },
        ],
      },
    ],
    total: 1,
    page: 1,
    size: 50,
  });
  draw();
  await ready();
  expect(screen.getByTestId("assignment-warning-a-101-0")).toHaveTextContent(
    "미계약 선투입",
  );
  expect(screen.getByTestId("assignment-warning-a-101-1")).toHaveTextContent(
    "영업 파트 다름",
  );
  expect(
    screen.getByTitle("2026-07 전프로젝트 월MM 1.50 > 1.0"),
  ).toHaveTextContent("⚠");
  await userEvent.click(screen.getByTestId("assignment-warning-a-101-2"));
  expect(await screen.findByRole("tooltip")).toHaveTextContent("월MM 1.50");
  await userEvent.selectOptions(screen.getByLabelText("경고"), "UNCONTRACTED");
  await userEvent.click(screen.getByTestId("assignments-query"));
  await waitFor(() =>
    expect(api.getAssignments).toHaveBeenLastCalledWith(
      expect.objectContaining({ warning: "UNCONTRACTED" }),
    ),
  );
  expect(
    screen.getByRole("option", { name: "미계약 선투입" }),
  ).toBeInTheDocument();
});
it("신규 배정의 인력 목록은 휴직 우선·현재 마지막 철수일·대기를 표시한다", async () => {
  vi.mocked(api.developerOptions).mockResolvedValue({
    items: [
      {
        id: "D-1",
        name: "투입자",
        managementNo: "M-1",
        status: "WTHDR",
        withdrawalDate: "2027-02-28",
      },
      {
        id: "D-2",
        name: "대기자",
        managementNo: "M-2",
        status: "WORK",
        withdrawalDate: null,
      },
      {
        id: "D-3",
        name: "휴직자",
        managementNo: "M-3",
        status: "LEAVE",
        withdrawalDate: "2027-03-01",
      },
    ],
    total: 3,
    page: 1,
    size: 50,
  });
  render(
    <AssignmentCreateForm
      year={2026}
      today={context.currentDate}
      onClose={vi.fn()}
      onSaved={vi.fn()}
    />,
  );
  expect(
    await screen.findByRole("option", {
      name: "투입자 (M-1) · 투입 중 ~2027-02-28",
    }),
  ).toBeInTheDocument();
  expect(
    screen.getByRole("option", { name: "대기자 (M-2) · 대기" }),
  ).toBeInTheDocument();
  expect(
    screen.getByRole("option", { name: "휴직자 (M-3) · 휴직" }),
  ).toBeInTheDocument();
});
it("시작월 월 단가가 없을 때만 청구 단가 칸 아래 안내한다", async () => {
  vi.mocked(people.getDeveloper)
    .mockResolvedValueOnce({
      priceHistory: [],
      jobCode: "DEV",
    } as unknown as people.DeveloperDetail)
    .mockResolvedValueOnce({
      priceHistory: [
        { effectiveMonth: "2026-08", endMonth: null, monthlyRate: "100" },
      ],
      jobCode: "DEV",
    } as unknown as people.DeveloperDetail);
  render(
    <PermissionContext.Provider
      value={{
        screens: { "UI-02": ["INQ"] },
        fields: { "UI-12": ["billingUnitPrice"] },
        sensitive: true,
        features: {},
        scope: { all: true, partIds: [], projectIds: [] },
      }}
    >
      <AssignmentCreateForm
        year={2026}
        today={context.currentDate}
        onClose={vi.fn()}
        onSaved={vi.fn()}
      />
    </PermissionContext.Provider>,
  );
  await screen.findByRole("option", { name: `${row.projectName} · MO · ING` });
  fireEvent.change(screen.getByTestId("assignment-project"), {
    target: { value: row.projectId },
  });
  fireEvent.change(screen.getByTestId("assignment-developer"), {
    target: { value: row.developerId },
  });
  expect(
    await screen.findByTestId("assignment-missing-monthly-rate"),
  ).toHaveTextContent("⚠ 개발자 풀에 월 단가가 없는 사람");
  fireEvent.change(screen.getByTestId("assignment-create-start-date"), {
    target: { value: "2026-08-01" },
  });
  await waitFor(() =>
    expect(
      screen.getByTestId("assignment-create-billing-unit-price"),
    ).toHaveValue("100"),
  );
  expect(
    screen.queryByTestId("assignment-missing-monthly-rate"),
  ).not.toBeInTheDocument();
});
it.each([
  ["턴키", null, "NEW", false],
  ["내부", null, "NEW", false],
  ["무상지원", "0", "FREE", false],
  ["월단위", "100", "NEW", true],
  ["파견", "100", "NEW", true],
] as const)(
  "%s 배정의 단가 변경 노출은 청구 단가 유무를 따른다",
  async (_, billingUnitPrice, inputType, visible) => {
    render(
      <AssignmentChangeDialog
        assignment={{ ...row, billingUnitPrice, inputType }}
        year={2026}
        today={context.currentDate}
        onClose={vi.fn()}
        onChanged={vi.fn()}
      />,
    );
    const option = screen.queryByTestId("assignment-change-type-option-uprc");
    if (visible) expect(option).toBeInTheDocument();
    else expect(option).not.toBeInTheDocument();
    expect(
      screen.getByTestId("assignment-change-type-option-rate"),
    ).toBeInTheDocument();
  },
);
it("변경 기록의 같은 연장 가능성 거부는 창을 유지하고 미정으로 다시 저장할 수 있다", async () => {
  const onClose = vi.fn(),
    onChanged = vi.fn().mockResolvedValue(undefined);
  vi.mocked(api.changeAssignment)
    .mockRejectedValueOnce(
      new ApiError(400, {
        success: false,
        meta: {},
        code: "SAME_VALUE",
        message: "지금 연장 가능성과 같습니다. 다른 값을 골라 주세요.",
        field: "extensionPossibility",
        requestId: "same-value",
      }),
    )
    .mockResolvedValueOnce({
      assignment: { ...row, extensionPossibility: null },
      changed: true,
      warnings: [],
      protectedMonths: [],
    });
  render(
    <AssignmentChangeDialog
      assignment={row}
      year={2026}
      today={context.currentDate}
      onClose={onClose}
      onChanged={onChanged}
    />,
  );
  fireEvent.change(screen.getByTestId("assignment-change-type"), {
    target: { value: "EXTP" },
  });
  fireEvent.change(screen.getByTestId("assignment-change-value"), {
    target: { value: "H" },
  });
  fireEvent.click(screen.getByRole("button", { name: "저장" }));
  expect(
    (
      await screen.findAllByText(
        "지금 연장 가능성과 같습니다. 다른 값을 골라 주세요.",
      )
    ).length,
  ).toBeGreaterThan(0);
  expect(onClose).not.toHaveBeenCalled();
  fireEvent.change(screen.getByTestId("assignment-change-value"), {
    target: { value: "" },
  });
  fireEvent.click(screen.getByRole("button", { name: "저장" }));
  await waitFor(() => expect(onClose).toHaveBeenCalledTimes(1));
  expect(api.changeAssignment).toHaveBeenLastCalledWith(
    row.id,
    expect.objectContaining({ extensionPossibility: null }),
  );
  expect(api.preview).not.toHaveBeenCalled();
});
it("11열·십진 원금 비파괴 단위와 API 값을表示한다", async () => {
  draw();
  await ready();
  expect(screen.queryByTestId("assignments-unit")).not.toBeInTheDocument();
  expect(screen.getByText("999,999,999,999,999")).toBeInTheDocument();
  expect(screen.getAllByRole("columnheader")).toHaveLength(12);
  expect(api.changeAssignment).not.toHaveBeenCalled();
});
it("로딩·빈목록·재조회 실패를 구분한다", async () => {
  vi.mocked(api.getAssignments).mockResolvedValueOnce({
    items: [],
    total: 0,
    page: 1,
    size: 50,
  });
  draw();
  expect(screen.getByText("불러오는 중입니다.")).toBeInTheDocument();
  await screen.findByText("조건에 맞는 결과가 없습니다.");
});
it("조회 실패는 재조회만 제공한다", async () => {
  vi.mocked(api.getAssignments).mockRejectedValueOnce(
    new ApiError(500, {
      success: false,
      meta: {},
      code: "INTERNAL_ERROR",
      message: "조회 실패",
      requestId: "assignment-read",
    }),
  );
  draw();
  await screen.findByText(/assignment-read/);
  await userEvent.click(
    within(
      screen
        .getByText(/assignment-read/)
        .closest("[role=alert]") as HTMLElement,
    ).getByRole("button", { name: "다시 조회" }),
  );
  await ready();
  expect(api.createAssignment).not.toHaveBeenCalled();
});
it("지난해는 신규·선택·일괄 금지다", async () => {
  draw(2025);
  await screen.findByText(/지난 해·읽기 전용/);
  expect(screen.getByTestId("assignment-new")).toBeDisabled();
  expect(
    screen.queryByTestId("assignments-all-selection"),
  ).not.toBeInTheDocument();
  expect(
    screen.queryByRole("checkbox", { name: "이 페이지 전체 선택" }),
  ).not.toBeInTheDocument();
});
it("검색은 조회시에만 trim 적용하고 조건변경시 page와 선택을 초기화한다", async () => {
  draw();
  await ready();
  fireEvent.change(screen.getByTestId("assignments-search"), {
    target: { value: "  name_%  " },
  });
  expect(api.getAssignments).toHaveBeenCalledTimes(1);
  await userEvent.click(screen.getByTestId("assignments-query"));
  await waitFor(() =>
    expect(api.getAssignments).toHaveBeenLastCalledWith(
      expect.objectContaining({ q: "name_%", year: 2026, page: 1 }),
    ),
  );
});
it("조회전체는 서버 snapshot을 고정하고 개별 제외는 bulk 대상에서 빠진다", async () => {
  vi.mocked(api.selectAssignments).mockResolvedValue({
    items: [...selection.items, { id: "A-102", expectedRevision: "rev2" }],
    total: 2,
    protectedMonths: ["2026-07"],
  });
  draw();
  await ready();
  // 선택 도구줄은 행을 선택했을 때만 나타난다
  expect(
    screen.queryByTestId("assignments-all-selection"),
  ).not.toBeInTheDocument();
  await userEvent.click(screen.getAllByRole("checkbox")[1]);
  await userEvent.click(screen.getByTestId("assignments-all-selection"));
  await screen.findByText("실제 선택 2건");
  await userEvent.click(screen.getByTestId("assignment-bulk-open"));
  await userEvent.click(screen.getByTestId("bulk-exclude-a-101"));
  vi.mocked(api.saveBulk).mockResolvedValue({
    requested: 1,
    applied: 1,
    noChange: 0,
    rejected: 0,
    items: [
      {
        id: "A-102",
        outcome: "applied",
        revision: "after",
        error: null,
        protectedMonths: [],
      },
    ],
  });
  await userEvent.click(screen.getByRole("button", { name: "확인 후 처리" }));
  await waitFor(() =>
    expect(api.saveBulk).toHaveBeenCalledWith(
      expect.objectContaining({
        items: [{ id: "A-102", expectedRevision: "rev2" }],
      }),
    ),
  );
});
it("변경종류별 payload는 typed 필드만 보내고 서버 경고 확인 뒤 1회 저장한다", async () => {
  vi.mocked(api.preview).mockResolvedValueOnce({
    affectedMonths: ["2026-07"],
    protectedMonths: ["2026-07"],
    months: [],
    warnings: [
      {
        code: "CLOSE_CROSS",
        message: "마감한 달 값은 유지하고 미마감 달부터 반영합니다.",
        months: ["2026-07"],
        assignmentIds: [row.id],
      },
      {
        code: "MM_OVER",
        message: "2026-07 전프로젝트 월MM 1.10 > 1.00",
        months: ["2026-07"],
        assignmentIds: [row.id],
      },
    ],
  });
  const refresh = vi.fn().mockResolvedValue(undefined);
  render(
    <AssignmentChangeDialog
      assignment={row}
      year={2026}
      today={context.currentDate}
      onClose={vi.fn()}
      onChanged={refresh}
    />,
  );
  fireEvent.change(screen.getByLabelText("변경 종류"), {
    target: { value: "RATE" },
  });
  fireEvent.change(screen.getByLabelText("새 계약MM"), {
    target: { value: "0.50" },
  });
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  await screen.findByText("영향월 2026-07");
  const dialog = screen.getByRole("dialog", { name: "보호월 영향 확인" });
  const notices = within(dialog).getByTestId("assignment-warnings");
  expect(notices.parentElement?.previousElementSibling?.tagName).toBe("HEADER");
  expect(within(notices).getAllByRole("alert")).toHaveLength(2);
  expect(within(notices).getAllByText("마감된 월의 투입값 유지")).toHaveLength(1);
  expect(notices).toHaveTextContent("월 MM 한도 초과");
  expect(notices).not.toHaveTextContent("CLOSE_CROSS");
  expect(within(dialog).getByRole("button", { name: "입력 수정" }).parentElement?.tagName).toBe("FOOTER");
  expect(api.changeAssignment).not.toHaveBeenCalled();
  await userEvent.click(screen.getByRole("button", { name: "확인 후 저장" }));
  await waitFor(() => expect(refresh).toHaveBeenCalledOnce());
  expect(api.changeAssignment).toHaveBeenCalledWith(row.id, {
    year: 2026,
    expectedRevision: "rev1",
    type: "RATE",
    applyMonth: "2026-07",
    newContractMm: "0.50",
  });
});
it("적용월 입력은 현재월을 기본값으로 표시하고 투입 기간 밖의 월도 서버에 전달한다", async () => {
  render(
    <AssignmentChangeDialog
      assignment={row}
      year={2026}
      today="2026-11-25"
      onClose={vi.fn()}
      onChanged={vi.fn()}
    />,
  );
  fireEvent.change(screen.getByLabelText("변경 종류"), { target: { value: "RATE" } });
  const month = screen.getByLabelText("적용월");
  expect(month).toHaveValue("2026-11");
  expect(month).not.toHaveAttribute("min");
  expect(month).not.toHaveAttribute("max");
  fireEvent.change(month, { target: { value: "2026-09" } });
  fireEvent.change(screen.getByLabelText("새 계약MM"), { target: { value: "0.50" } });
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  await waitFor(() => expect(api.preview).toHaveBeenCalledWith({
    id: row.id,
    change: expect.objectContaining({ type: "RATE", applyMonth: "2026-09" }),
  }));
});
it("필드 오류 입력보존과 stale 자동재저장 금지", async () => {
  vi.mocked(api.preview).mockRejectedValue(
    new ApiError(409, {
      success: false,
      meta: {},
      code: "STALE_WRITE",
      message: "충돌",
      requestId: "stale",
    }),
  );
  render(
    <AssignmentChangeDialog
      assignment={row}
      year={2026}
      today={context.currentDate}
      onClose={vi.fn()}
      onChanged={vi.fn()}
    />,
  );
  fireEvent.change(screen.getByLabelText("새 종료일"), {
    target: { value: "2026-09-30" },
  });
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  await screen.findByText(/자동으로 재저장하지 않습니다/);
  expect(screen.getByLabelText("새 종료일")).toHaveValue("2026-09-30");
  expect(screen.getByRole("button", { name: "저장" })).toBeDisabled();
  expect(api.changeAssignment).not.toHaveBeenCalled();
});
it("dirty 취소는 계속 작성과 나가기를 구분한다", async () => {
  const close = vi.fn(),
    dirty = vi.fn();
  render(
    <AssignmentChangeDialog
      assignment={row}
      year={2026}
      today={context.currentDate}
      onClose={close}
      onChanged={vi.fn()}
      onDirtyChange={dirty}
    />,
  );
  await userEvent.click(screen.getByRole("button", { name: "취소" }));
  await userEvent.click(screen.getByRole("button", { name: "계속 작성" }));
  expect(close).not.toHaveBeenCalled();
  expect(dirty).toHaveBeenCalledWith(true);
  await userEvent.click(screen.getByRole("button", { name: "취소" }));
  await userEvent.click(screen.getByRole("button", { name: "나가기" }));
  expect(close).toHaveBeenCalledOnce();
});
it("고정월은 조회전용이고 취소 배정은 변경 버튼이 없다", async () => {
  vi.mocked(api.getAssignment).mockResolvedValue({
    ...row,
    assignmentStatus: "CNCL",
  });
  vi.mocked(api.getMonths).mockResolvedValue({
    items: [
      {
        month: "2026-07",
        projectId: row.projectId,
        developerId: row.developerId,
        inputDays: 31,
        contractMm: "1.00",
        monthlyMm: "1.00",
        appliedUnitPrice: "100",
        salesAmount: "100",
        fixed: "Y",
      },
    ],
    total: 1,
    page: 1,
    size: 50,
  });
  render(
    <AssignmentDrawer
      id={row.id}
      year={2026}
      readonly={false}
      onClose={vi.fn()}
      onChange={vi.fn()}
    />,
  );
  await screen.findByText("예");
  expect(
    screen.queryByTestId("assignment-change-open"),
  ).not.toBeInTheDocument();
  expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
});
it("일괄 항목별 결과와 보호 영향 안내를 표시한다", async () => {
  vi.mocked(api.saveBulk).mockResolvedValue({
    requested: 1,
    applied: 0,
    noChange: 0,
    rejected: 1,
    items: [
      {
        id: row.id,
        outcome: "rejected",
        revision: null,
        error: {
          success: false,
          meta: {},
          code: "STALE_WRITE",
          message: "새로 조회하세요",
          requestId: "bulk",
        },
        protectedMonths: ["2026-07"],
      },
    ],
  });
  render(
    <AssignmentBulkDialog
      year={2026}
      selection={{ ...selection, protectedMonths: ["2026-07"] }}
      onClose={vi.fn()}
      onChanged={vi.fn().mockResolvedValue(undefined)}
    />,
  );
  await screen.findByText(/보호 영향월 2026-07/);
  await userEvent.click(screen.getByRole("button", { name: "확인 후 처리" }));
  await screen.findByText(/요청 1 = applied 0 \+ noChange 0 \+ rejected 1/);
  expect(screen.getByTestId("bulk-result-a-101")).toHaveTextContent(
    "새로 조회하세요",
  );
});
it("기간바 변경 이벤트의 year를 실제 조회에 전달한다", async () => {
  render(<StaffingAssignmentsScreen />);
  await ready();
  fireEvent(
    window,
    new CustomEvent("sicost:screen-period-changed", {
      detail: {
        screenId: "UI-12",
        value: { type: "YEAR", value: "2027" },
        context,
      },
    }),
  );
  await waitFor(() =>
    expect(api.getAssignments).toHaveBeenLastCalledWith(
      expect.objectContaining({ year: 2027 }),
    ),
  );
});
it("신규폼 SUB 계약상 인력은 실제 변경시 지우며 숨긴 단가는 제출하지 않는다", async () => {
  render(
    <AssignmentCreateForm
      year={2026}
      today={context.currentDate}
      onClose={vi.fn()}
      onSaved={vi.fn()}
    />,
  );
  await screen.findByRole("option", { name: "실제 프로젝트 · MO · ING" });
  fireEvent.change(screen.getByTestId("assignment-project"), {
    target: { value: "P-101" },
  });
  fireEvent.change(screen.getByTestId("assignment-kind"), {
    target: { value: "FREE" },
  });
  expect(
    screen.queryByTestId("assignment-create-billing-unit-price"),
  ).not.toBeInTheDocument();
  await userEvent.click(
    within(screen.getByRole("dialog", { name: "신규 배정" })).getByRole(
      "button",
      { name: "저장" },
    ),
  );
  await waitFor(() => expect(api.preview).toHaveBeenCalled());
  expect(api.preview).toHaveBeenCalledWith(
    expect.not.objectContaining({ billingUnitPrice: expect.anything() }),
  );
});
it.each([
  ["projectId", "프로젝트를 입력해 주세요.", 1],
  ["jobCode", "직무를 입력해 주세요.", 1],
  ["year", "입력 항목의 형식과 길이를 확인해 주세요.", 1],
])(
  "신규폼 서버 거절 %s: 칸 아래에 보이면 위쪽 띠에 다시 안 나오고, 칸 문구가 없으면 띠에만 나온다",
  async (field, message, count) => {
    vi.mocked(api.preview).mockRejectedValue(
      new ApiError(400, {
        success: false,
        meta: {},
        code: "VALIDATION_ERROR",
        message,
        field,
        requestId: `create-${field}`,
      }),
    );
    render(
      <AssignmentCreateForm
        year={2026}
        today={context.currentDate}
        onClose={vi.fn()}
        onSaved={vi.fn()}
      />,
    );
    await screen.findByRole("option", { name: "실제 프로젝트 · MO · ING" });
    await userEvent.click(
      within(screen.getByRole("dialog", { name: "신규 배정" })).getByRole(
        "button",
        { name: "저장" },
      ),
    );
    expect(await screen.findAllByText(message)).toHaveLength(count);
    if (field === "year")
      expect(screen.getByText(/create-year/)).toBeInTheDocument();
    else
      expect(
        screen.queryByText(new RegExp(`create-${field}`)),
      ).not.toBeInTheDocument();
  },
);
it("신규폼 빈 저장의 여러 칸 거절은 칸마다 한 번씩만 보이고 위쪽 띠·문의 번호가 없다", async () => {
  vi.mocked(api.preview).mockRejectedValue(
    new ApiError(400, {
      success: false,
      meta: {},
      code: "INVALID_INPUT",
      message: "프로젝트를 입력해 주세요.",
      field: "projectId",
      requestId: "create-many",
      details: {
        violations: [
          { field: "projectId", message: "프로젝트를 입력해 주세요." },
          { field: "developerId", message: "개발자를 골라 주세요" },
          { field: "jobCode", message: "직무를 입력해 주세요." },
        ],
      },
    } as ConstructorParameters<typeof ApiError>[1]),
  );
  render(
    <AssignmentCreateForm
      year={2026}
      today={context.currentDate}
      onClose={vi.fn()}
      onSaved={vi.fn()}
    />,
  );
  await screen.findByRole("option", { name: "실제 프로젝트 · MO · ING" });
  await userEvent.click(
    within(screen.getByRole("dialog", { name: "신규 배정" })).getByRole(
      "button",
      { name: "저장" },
    ),
  );
  for (const text of [
    "프로젝트를 입력해 주세요.",
    "개발자를 골라 주세요",
    "직무를 입력해 주세요.",
  ])
    expect(await screen.findAllByText(text)).toHaveLength(1);
  expect(screen.queryByText(/create-many/)).not.toBeInTheDocument();
});
it("저장후 재조회 실패는 POST 재실행 없이 조회만 허용한다", async () => {
  const refreshed = vi.fn().mockRejectedValue(new Error("조회 실패"));
  render(
    <AssignmentChangeDialog
      assignment={row}
      year={2026}
      today={context.currentDate}
      onClose={vi.fn()}
      onChanged={refreshed}
    />,
  );
  fireEvent.change(screen.getByLabelText("변경 종류"), {
    target: { value: "EXT" },
  });
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  await userEvent.click(
    await screen.findByRole("button", { name: "확인 후 저장" }),
  );
  await screen.findByText(/저장은 완료됐지만 목록을 다시 불러오지 못했습니다/);
  expect(api.changeAssignment).toHaveBeenCalledTimes(1);
});

it("UI-32 entry opens the selected assignment history while regular entry keeps the list", async () => {
  render(
    <AssignmentsContent
      year={2026}
      context={context}
      entryAssignmentId={row.id}
      entryHistory
    />,
  );
  await screen.findByTestId("assignment-detail-drawer");
  await waitFor(() =>
    expect(api.getAssignment).toHaveBeenCalledWith(row.id, 2026),
  );
  expect(screen.getByTestId("assignment-history-tab")).toHaveAttribute(
    "aria-selected",
    "true",
  );
});

it.each(["ADD", "FREE"])(
  "추천 배정의 투입구분 %s 변경도 같은 사람·프로젝트의 추천 참조를 유지한다",
  async (kind) => {
    render(
      <AssignmentCreateForm
        year={2026}
        today={context.currentDate}
        onClose={vi.fn()}
        onSaved={vi.fn()}
        initialValues={{
          year: 2026,
          projectId: row.projectId,
          developerId: row.developerId,
          jobCode: row.jobCode,
          startDate: row.startDate,
          endDate: row.endDate,
          contractMm: "1.00",
          billingUnitPrice: "10000000",
          inputType: "NEW",
          recommendationRef: { rcmdId: "123", rank: 2 },
        }}
      />,
    );
    await screen.findByRole("option", { name: "실제 프로젝트 · MO · ING" });
    fireEvent.change(screen.getByTestId("assignment-kind"), {
      target: { value: kind },
    });
    await userEvent.click(
      within(screen.getByRole("dialog", { name: "신규 배정" })).getByRole(
        "button",
        { name: "저장" },
      ),
    );
    await waitFor(() => expect(api.preview).toHaveBeenCalled());
    expect(api.preview).toHaveBeenCalledWith(
      expect.objectContaining({
        inputType: kind,
        recommendationRef: { rcmdId: "123", rank: 2 },
      }),
    );
  },
);

it("EXTP는 사유·적용일·미리보기 없이 메타정보 변경만 한 번 저장한다", async () => {
  render(
    <AssignmentChangeDialog
      assignment={row}
      year={2026}
      today={context.currentDate}
      onClose={vi.fn()}
      onChanged={vi.fn()}
    />,
  );
  fireEvent.change(screen.getByLabelText("변경 종류"), {
    target: { value: "EXTP" },
  });
  expect(screen.queryByLabelText(/변경 사유/)).not.toBeInTheDocument();
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  await waitFor(() => expect(api.changeAssignment).toHaveBeenCalledTimes(1));
  expect(api.preview).not.toHaveBeenCalled();
  expect(api.changeAssignment).toHaveBeenCalledWith(row.id, {
    year: 2026,
    expectedRevision: row.revision,
    type: "EXTP",
    extensionPossibility: null,
  });
});
it("uses close-check year and MM warning without changing saved period", async () => {
  render(
    <StaffingAssignmentsScreen
      initialMonth="2026-05"
      initialWarning="MM_OVER"
    />,
  );
  await waitFor(() =>
    expect(api.getAssignments).toHaveBeenCalledWith(
      expect.objectContaining({
        year: 2026,
        month: "2026-05",
        warning: "MM_OVER",
      }),
    ),
  );
  expect(shared.putSharedPreference).not.toHaveBeenCalled();
});

it("마감 선택월을 조회·전체선택에 유지하고 초기화하면 연간 전체로 돌아간다", async () => {
  render(
    <StaffingAssignmentsScreen
      initialMonth="2026-05"
      initialWarning="MM_OVER"
    />,
  );
  await screen.findByRole("cell", { name: row.projectName });
  expect(screen.getByTestId("assignments-month-filter")).toHaveValue("2026-05");
  fireEvent.change(screen.getByTestId("assignments-search"), {
    target: { value: "담당" },
  });
  await userEvent.click(screen.getByTestId("assignments-query"));
  await waitFor(() =>
    expect(api.getAssignments).toHaveBeenLastCalledWith(
      expect.objectContaining({
        month: "2026-05",
        warning: "MM_OVER",
        q: "담당",
      }),
    ),
  );
  await userEvent.click(screen.getByTestId("assignments-reset"));
  await waitFor(() =>
    expect(api.getAssignments).toHaveBeenLastCalledWith(
      expect.not.objectContaining({ month: expect.anything() }),
    ),
  );
  expect(screen.getByTestId("assignments-month-filter")).toHaveValue("");
});

it("변경 이력의 입력 시각은 ISO 원문 대신 월일 시분으로 보인다", async () => {
  vi.mocked(api.getHistory).mockResolvedValue({
    items: [
      {
        sequence: 1,
        type: "UPRC",
        applyDate: "2026-11-16",
        before: "8000000",
        after: "8500000",
        reasonCode: "NEGO",
        memo: null,
        newUnitPrice: "8500000",
        newContractMm: null,
        retroactive: "N",
        closeCross: "N",
        corrected: "N",
        correctionTargetSequence: null,
        actor: "admin",
        registeredAt: "2026-11-16T17:05:14.951915",
      },
    ],
    total: 1,
    page: 1,
    size: 50,
  });
  render(
    <AssignmentDrawer
      id={row.id}
      year={2026}
      readonly={false}
      onClose={vi.fn()}
      onChange={vi.fn()}
    />,
  );
  await userEvent.click(await screen.findByTestId("assignment-history-tab"));
  expect(await screen.findByText("admin / 11-16 17:05")).toBeInTheDocument();
  expect(screen.queryByText(/2026-11-16T17:05/)).not.toBeInTheDocument();
});
