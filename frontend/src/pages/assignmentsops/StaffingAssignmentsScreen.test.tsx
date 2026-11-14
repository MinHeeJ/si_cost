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
it("11열·십진 원금 비파괴 단위와 API 값을 표시한다", async () => {
  draw();
  await ready();
  expect(screen.getByText("999,999,999,999.999")).toBeInTheDocument();
  fireEvent.change(screen.getByTestId("assignments-unit"), {
    target: { value: "원" },
  });
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
  await screen.findByText("조회된 투입 배정이 없습니다.");
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
  await userEvent.click(screen.getByTestId("assignments-refresh"));
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
  await screen.findByText("Y");
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
  await userEvent.click(within(screen.getByRole("dialog",{name:"신규 배정"})).getByRole("button",{name:"저장"}));
  await waitFor(() => expect(api.preview).toHaveBeenCalled());
  expect(api.preview).toHaveBeenCalledWith(
    expect.not.objectContaining({ billingUnitPrice: expect.anything() }),
  );
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
    target: { value: "EXTP" },
  });
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  await userEvent.click(
    await screen.findByRole("button", { name: "확인 후 저장" }),
  );
  await screen.findByText(/저장은 완료됐지만 목록을 다시 불러오지 못했습니다/);
  expect(api.changeAssignment).toHaveBeenCalledTimes(1);
});

it("UI-32 entry opens the selected assignment history while regular entry keeps the list", async () => {
  render(<AssignmentsContent year={2026} context={context} entryAssignmentId={row.id} entryHistory />);
  await screen.findByTestId("assignment-detail-drawer");
  await waitFor(() => expect(api.getAssignment).toHaveBeenCalledWith(row.id, 2026));
  expect(screen.getByTestId("assignment-history-tab")).toHaveAttribute("aria-selected", "true");
});
