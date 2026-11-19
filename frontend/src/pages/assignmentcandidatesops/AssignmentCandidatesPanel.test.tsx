import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "../../shared/testSupport/businessFixtures";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { AssignmentCandidatesPanel } from "./AssignmentCandidatesPanel";
import * as api from "./assignmentCandidatesApi";
import * as assignments from "../assignmentsops/assignmentActionsApi";
import * as shared from "../shared/sharedApi";
import * as people from "../developers/peopleClient";
import { ApiError } from "../../shared/api";
vi.mock("./assignmentCandidatesApi");
vi.mock("../assignmentsops/assignmentActionsApi");
vi.mock("../shared/sharedApi");
vi.mock("../developers/peopleClient");
const candidate: api.AssignmentCandidate = {
  id: "101",
  opportunityId: "O-0101",
  projectId: "P-101",
  projectName: "수주 프로젝트",
  developerId: "D-101",
  developerName: "검토 인력",
  jobCode: "BE",
  startDate: "2026-07-10",
  endDate: "2026-08-31",
  contractMm: "0.50",
  billingUnitPrice: "101",
  revision: "revision-one",
};
const onConfirmed = vi.fn(),
  onChanged = vi.fn();
beforeEach(() => {
  vi.resetAllMocks();
  onChanged.mockResolvedValue(undefined);
  onConfirmed.mockResolvedValue(undefined);
  vi.mocked(api.getAssignmentCandidates).mockResolvedValue({
    items: [candidate],
    total: 1,
    page: 1,
    size: 50,
  });
  vi.mocked(api.confirmAssignmentCandidate).mockResolvedValue({
    changed: true,
    revision: "next",
    warnings: [],
    opportunityId: candidate.opportunityId,
    tentativeId: candidate.id,
    assignmentId: "A-0102",
  });
  vi.mocked(api.dropAssignmentCandidate).mockResolvedValue({
    changed: true,
    revision: "next",
    warnings: [],
    opportunityId: candidate.opportunityId,
    tentativeId: candidate.id,
  });
  vi.mocked(assignments.projectOptions).mockResolvedValue({
    items: [
      {
        id: candidate.projectId,
        name: candidate.projectName,
        contractType: "MO",
        status: "ING",
        startDate: candidate.startDate,
        endDate: candidate.endDate,
      },
    ],
  });
  vi.mocked(assignments.developerOptions).mockResolvedValue({
    items: [
      {
        id: candidate.developerId,
        name: candidate.developerName,
        managementNo: "M-101",
      },
    ],
    total: 1,
    page: 1,
    size: 50,
  });
  vi.mocked(shared.getSharedCodes).mockResolvedValue({
    items: [
      {
        code: "BE",
        name: "백엔드",
        addVal1: null,
        addVal2: null,
        addVal3: null,
        codeVal: null,
        unitNm: null,
      },
    ],
  });
  vi.mocked(people.getDeveloper).mockResolvedValue({
    jobCode: "BE",
    priceHistory: [
      { effectiveMonth: "2026-01", endMonth: null, monthlyRate: "101" },
    ],
  } as Awaited<ReturnType<typeof people.getDeveloper>>);
  vi.mocked(assignments.preview).mockResolvedValue({
    warnings: [],
    affectedMonths: ["2026-07"],
    protectedMonths: [],
    months: [],
  });
});
function view(readOnly = false) {
  return render(
    <AssignmentCandidatesPanel
      year={2026}
      today="2026-07-01"
      readOnly={readOnly}
      onConfirmed={onConfirmed}
      onChanged={onChanged}
    />,
  );
}
async function open() {
  view();
  await userEvent.click(await screen.findByRole("button", { name: /^확정$/ }));
  await screen.findByRole("dialog", { name: "신규 배정 (수주 후보)" });
}
async function inspectAndSave() {
  await userEvent.click(screen.getByRole("button", { name: /^저장$/ }));
  await userEvent.click(
    await screen.findByRole("button", { name: "확인 후 저장" }),
  );
}
it("renders API-backed CNVT region and the reference columns without hiding amounts by role", async () => {
  view();
  expect(await screen.findByText("수주 프로젝트")).toBeVisible();
  expect(screen.getByText("CNVT")).toHaveClass("sicost-tone-info");
  for (const name of ["프로젝트", "사람", "직무", "예정기간", "상태", "행동"])
    expect(screen.getByRole("columnheader", { name })).toBeVisible();
  expect(screen.getByRole("heading", { name: "배정 후보 1" })).toBeVisible();
});
it("opens the existing form with API defaults, fixed identity, NEW and developer effective price", async () => {
  await open();
  expect(screen.getByTestId("assignment-create-start-date")).toHaveValue(
    candidate.startDate,
  );
  expect(screen.getByTestId("assignment-create-end-date")).toHaveValue(
    candidate.endDate,
  );
  expect(screen.getByTestId("assignment-create-contract-mm")).toHaveValue(
    "0.50",
  );
  await waitFor(() =>
    expect(
      screen.getByTestId("assignment-create-billing-unit-price"),
    ).toHaveValue("101"),
  );
  for (const id of [
    "assignment-project",
    "assignment-developer",
    "assignment-job",
    "assignment-kind",
  ])
    expect(screen.getByTestId(id)).toBeDisabled();
  expect(screen.getByTestId("assignment-kind")).toHaveValue("NEW");
  expect(api.confirmAssignmentCandidate).not.toHaveBeenCalled();
});
it("submits reviewed date, MM and price only through candidate confirm and opens the real result id", async () => {
  await open();
  await waitFor(() =>
    expect(
      screen.getByTestId("assignment-create-billing-unit-price"),
    ).toHaveValue("101"),
  );
  fireEvent.change(screen.getByTestId("assignment-create-contract-mm"), {
    target: { value: "0.75" },
  });
  fireEvent.change(screen.getByTestId("assignment-create-billing-unit-price"), {
    target: { value: "123" },
  });
  await inspectAndSave();
  await waitFor(() => expect(onConfirmed).toHaveBeenCalledWith("A-0102"));
  expect(api.confirmAssignmentCandidate).toHaveBeenCalledWith(
    candidate,
    expect.objectContaining({
      contractMm: "0.75",
      billingUnitPrice: "123",
      inputType: "NEW",
      contractDeveloperId: null,
    }),
  );
  expect(assignments.createAssignment).not.toHaveBeenCalled();
  expect(onChanged).toHaveBeenCalled();
});
it("cancel after dirty confirmation leaves CNVT and sends no mutation", async () => {
  await open();
  await userEvent.click(
    within(
      screen.getByRole("dialog", { name: "신규 배정 (수주 후보)" }),
    ).getByRole("button", { name: "취소" }),
  );
  await userEvent.click(screen.getByRole("button", { name: "나가기" }));
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  expect(api.confirmAssignmentCandidate).not.toHaveBeenCalled();
  expect(api.dropAssignmentCandidate).not.toHaveBeenCalled();
  expect(screen.getByText("CNVT")).toBeVisible();
});
it("drop requires explicit confirmation and refreshes without creating assignment", async () => {
  view();
  await userEvent.click(await screen.findByRole("button", { name: /^빼기$/ }));
  expect(api.dropAssignmentCandidate).not.toHaveBeenCalled();
  await userEvent.click(
    within(screen.getByRole("dialog", { name: "배정 후보 빼기" })).getByRole(
      "button",
      { name: "빼기" },
    ),
  );
  await waitFor(() =>
    expect(api.dropAssignmentCandidate).toHaveBeenCalledWith(candidate),
  );
  expect(onChanged).toHaveBeenCalled();
  expect(api.confirmAssignmentCandidate).not.toHaveBeenCalled();
});
it("cancel drop does not mutate", async () => {
  view();
  await userEvent.click(await screen.findByRole("button", { name: "빼기" }));
  await userEvent.click(screen.getByRole("button", { name: "취소" }));
  expect(api.dropAssignmentCandidate).not.toHaveBeenCalled();
});
it("past year has no candidate controls or candidate request", () => {
  view(true);
  expect(
    screen.queryByTestId("assignment-candidates-panel"),
  ).not.toBeInTheDocument();
  expect(api.getAssignmentCandidates).not.toHaveBeenCalled();
});
it("empty API result displays the empty-list message", async () => {
  vi.mocked(api.getAssignmentCandidates).mockResolvedValue({
    items: [],
    total: 0,
    page: 1,
    size: 50,
  });
  view();
  expect(await screen.findByText("배정 후보가 없습니다.")).toBeVisible();
  expect(
    screen.queryByRole("button", { name: "확정" }),
  ).not.toBeInTheDocument();
});
it("loading is shown while the actual request is pending", () => {
  vi.mocked(api.getAssignmentCandidates).mockReturnValue(
    new Promise(() => undefined),
  );
  view();
  expect(screen.getByText("배정 후보를 불러오는 중입니다.")).toBeVisible();
});
it("failed list shows request id and a read-only retry", async () => {
  vi.mocked(api.getAssignmentCandidates).mockRejectedValue(
    new ApiError(500, {
      success: false,
      meta: {},
      code: "INTERNAL_ERROR",
      message: "후보 조회 실패",
      requestId: "request-list",
    }),
  );
  view();
  expect(await screen.findByText(/후보 조회 실패/)).toBeVisible();
  expect(screen.getByText(/request-list/)).toBeVisible();
  expect(api.confirmAssignmentCandidate).not.toHaveBeenCalled();
});
it("state lock keeps the form and does not automatically retry confirm", async () => {
  vi.mocked(api.confirmAssignmentCandidate).mockRejectedValue(
    new ApiError(409, {
      success: false,
      meta: {},
      code: "INVALID_STATE",
      message: "종료 프로젝트는 저장할 수 없습니다.",
      requestId: "state-request",
    }),
  );
  await open();
  await inspectAndSave();
  await waitFor(() =>
    expect(
      screen.getAllByText(/종료 프로젝트는 저장할 수 없습니다/).length,
    ).toBeGreaterThan(0),
  );
  expect(api.confirmAssignmentCandidate).toHaveBeenCalledTimes(1);
  expect(onConfirmed).not.toHaveBeenCalled();
  expect(screen.getByTestId("assignment-create-contract-mm")).toHaveValue(
    "0.50",
  );
});
it("field validation renders beneath the editable input and retains entered value", async () => {
  vi.mocked(api.confirmAssignmentCandidate).mockRejectedValue(
    new ApiError(400, {
      success: false,
      meta: {},
      code: "VALIDATION_ERROR",
      field: "contractMm",
      message: "월MM을 확인해 주세요.",
      requestId: "field-request",
    }),
  );
  await open();
  await inspectAndSave();
  await waitFor(() =>
    expect(screen.getByTestId("assignment-create-contract-mm")).toHaveAttribute(
      "aria-invalid",
      "true",
    ),
  );
  expect(onConfirmed).not.toHaveBeenCalled();
});
it("successful save followed by failed refresh offers no automatic mutation replay", async () => {
  await open();
  onChanged.mockRejectedValue(new Error("조회 실패"));
  await inspectAndSave();
  expect(
    await screen.findByText("저장됐지만 다시 불러오지 못했습니다"),
  ).toBeVisible();
  expect(api.confirmAssignmentCandidate).toHaveBeenCalledTimes(1);
  expect(
    screen.queryByRole("dialog", { name: "신규 배정 (수주 후보)" }),
  ).not.toBeInTheDocument();
});
