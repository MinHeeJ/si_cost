import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { ApiError } from "../../shared/api";
import { ProjectDatesPreview } from "./ProjectDatesPreview";
import * as api from "../progress/projectProgressApi";
vi.mock("../progress/projectProgressApi", async (original) => ({
  ...(await original<typeof api>()),
  createProjectStepDatesPreview: vi.fn(),
  saveProjectStepDates: vi.fn(),
}));
const actor = { userId: "test-pm", userName: "시험 담당" };
const first: api.ProgressStep = {
  projectId: "DATE-PROJECT",
  stepSn: 1,
  stepCode: "TK_KICK",
  stepName: "착수",
  weight: "5.00",
  startDate: "2026-01-01",
  endDate: "2026-01-10",
  completedDate: null,
  status: "지연",
  registeredBy: actor,
  registeredAt: "2026-01-01T00:00:00+09:00",
  modifiedBy: null,
  modifiedAt: null,
};
const data: api.ProjectProgress = {
  projectId: first.projectId,
  year: 2026,
  progressRevision: "date-token",
  readOnly: false,
  readOnlyReasons: [],
  status: {
    projectId: first.projectId,
    projectName: "단계 시험",
    contractType: "TK",
    sourceStatus: "ING",
    partId: null,
    partName: null,
    hq: false,
    pm: [actor],
    light: "주의",
    reasons: [],
    costUnknown: true,
    planProgress: "0.0",
    actualProgress: "0.0",
    costUsage: null,
    scheduleGap: "0.0",
    costProgressGap: null,
    currentStep: first,
    allStepsCompleted: false,
    stepsInitialized: true,
    planBasis: "단계 기준",
    openIssueCount: 0,
    overdueIssueCount: 0,
    lastUpdatedDate: null,
    elapsedDays: 0,
    updateWarning: false,
    baseDate: "2026-07-01",
    costMonth: "2026-07",
    completeness: {
      costComplete: false,
      revenueComplete: false,
      estimated: false,
      unknownProjectCount: 1,
      rangeStartMonth: "2026-01",
      rangeEndMonth: "2026-07",
      missingBeforeMonth: null,
      issues: [],
    },
  },
  steps: [
    first,
    {
      ...first,
      stepSn: 2,
      stepName: "개발",
      weight: "95.00",
      startDate: "2026-01-11",
      endDate: "2026-12-31",
      status: "진행중",
    },
  ],
  memos: [],
  issues: [],
  initializationMessage: null,
  calculationNotice: "서버 기준",
};
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api.createProjectStepDatesPreview).mockResolvedValue({
    projectId: data.projectId,
    progressRevision: data.progressRevision,
    steps: [
      { ...first, endDate: "2026-01-20" },
      { ...data.steps[1], startDate: "2026-01-21" },
    ],
  });
});
function draw(value = data) {
  const props = {
    data: value,
    onSaved: vi.fn().mockResolvedValue(undefined),
    onClose: vi.fn(),
  };
  render(<ProjectDatesPreview {...props} />);
  return props;
}
it("uses existing date editor and server-derived contiguous dates without saving", async () => {
  draw();
  fireEvent.change(screen.getByLabelText("착수 계획 종료일"), {
    target: { value: "2026-01-20" },
  });
  expect(screen.getByLabelText("개발 계획 종료일")).toBeDisabled();
  await userEvent.click(screen.getByRole("button", { name: "미리보기" }));
  expect(await screen.findByText(/미리보기 · 저장되지 않음/)).toHaveTextContent(
    "개발 2026-01-21 ~ 2026-12-31",
  );
  expect(api.createProjectStepDatesPreview).toHaveBeenCalledWith(
    data.projectId,
    {
      year: 2026,
      steps: [
        { stepSn: 1, endDate: "2026-01-20" },
        { stepSn: 2, endDate: "2026-12-31" },
      ],
    },
  );
  expect(api.saveProjectStepDates).not.toHaveBeenCalled();
});
it("invalidates the prior server result immediately on editing", async () => {
  draw();
  await userEvent.click(screen.getByRole("button", { name: "미리보기" }));
  await screen.findByText(/미리보기 · 저장되지 않음/);
  fireEvent.change(screen.getByLabelText("착수 계획 종료일"), {
    target: { value: "2026-01-22" },
  });
  expect(
    screen.queryByText(/미리보기 · 저장되지 않음/),
  ).not.toBeInTheDocument();
});
it("locks completed dates and disables saving a readonly year", () => {
  draw({
    ...data,
    readOnly: true,
    steps: [{ ...first, completedDate: "2026-01-10" }, data.steps[1]],
  });
  expect(screen.getByLabelText("착수 계획 종료일")).toBeDisabled();
  expect(screen.getByRole("button", { name: "전체 저장" })).toBeDisabled();
});
it("keeps the draft, field error and request id on preview failure", async () => {
  vi.mocked(api.createProjectStepDatesPreview).mockRejectedValue(
    new ApiError(400, {
      success: false,
      meta: {},
      code: "VALIDATION_ERROR",
      message: "단계 날짜를 확인해 주세요.",
      field: "steps",
      requestId: "dates-test",
    }),
  );
  draw();
  fireEvent.change(screen.getByLabelText("착수 계획 종료일"), {
    target: { value: "2026-02-01" },
  });
  await userEvent.click(screen.getByRole("button", { name: "미리보기" }));
  await screen.findByText(/문의 번호: dates-test/);
  expect(screen.getByLabelText("착수 계획 종료일")).toHaveValue("2026-02-01");
  expect(screen.getByLabelText("착수 계획 종료일")).toHaveAttribute(
    "aria-invalid",
    "true",
  );
  expect(api.saveProjectStepDates).not.toHaveBeenCalled();
});
it("cancellation keeps dirty confirmation and does not save", async () => {
  const props = draw();
  fireEvent.change(screen.getByLabelText("착수 계획 종료일"), {
    target: { value: "2026-01-22" },
  });
  await userEvent.click(screen.getByRole("button", { name: "취소" }));
  expect(props.onClose).not.toHaveBeenCalled();
  await userEvent.click(screen.getByRole("button", { name: "나가기" }));
  expect(props.onClose).toHaveBeenCalled();
  expect(api.saveProjectStepDates).not.toHaveBeenCalled();
});
