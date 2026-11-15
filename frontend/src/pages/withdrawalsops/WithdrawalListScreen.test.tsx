import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, expect, it, vi } from "vitest";
import { WithdrawalContent } from "./WithdrawalListScreen";
import { AssignmentChangeDialog } from "../assignmentsops/AssignmentDialogs";
import * as api from "../apiops/withdrawalsApi";
import * as assignmentApi from "../assignmentsops/assignmentActionsApi";
import { getSharedCodes } from "../shared/sharedApi";
import { getDeveloper } from "../developers/peopleClient";
vi.mock("../apiops/withdrawalsApi");
vi.mock("../assignmentsops/assignmentActionsApi");
vi.mock("../shared/sharedApi");
vi.mock("../developers/peopleClient");
const row: api.WithdrawalRow = {
  assignmentId: "A-001",
  developer: {
    developerId: "D-001",
    name: "철수 인력",
    managementNumber: "001",
    jobCode: "BE",
    subJobCode: null,
    gradeCode: "MD",
    employeeTypeCode: "FT",
    detailId: "D-001",
  },
  projectId: "P-001",
  projectName: "원프로젝트",
  jobCode: "BE",
  withdrawalDate: "2026-11-20",
  remainingDays: 5,
  extensionCount: 2,
  earlyWithdrawal: false,
  extensionPossibility: "H",
  extensionEditable: true,
  nextAssignments: [],
  recommendationCount: 0,
  canRecommend: false,
  canChangeTentative: false,
  revision: "revision",
};
const data: api.WithdrawalsData = {
  items: [row],
  year: 2026,
  page: 1,
  size: 50,
  totalCount: 1,
  serverDate: "2026-11-15",
  baseMonth: "2025-01",
  guidance: {
    visible: true,
    withinThreeMonthsPeople: 1,
    withoutNextAssignmentPeople: 0,
    withMatchingPositionsPeople: 0,
    message: "철수 예정 안내",
  },
};
const assignment: assignmentApi.Assignment = {
  id: row.assignmentId,
  projectId: row.projectId,
  projectName: row.projectName,
  developerId: row.developer.developerId,
  developerName: row.developer.name,
  managementNo: "001",
  jobCode: "BE",
  jobName: "백엔드 개발",
  startDate: "2026-09-01",
  endDate: row.withdrawalDate,
  contractMm: "1.00",
  billingUnitPrice: null,
  inputType: "NEW",
  contractDeveloperId: null,
  extensionPossibility: "H",
  extensionCount: 2,
  earlyWithdrawal: "N",
  assignmentStatus: "ACT",
  yearMm: "3.00",
  revision: "revision",
  warnings: [],
  protectedMonths: ["2026-10"],
};
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(getSharedCodes).mockResolvedValue({ items: [] });
  vi.mocked(getDeveloper).mockResolvedValue({ skills: ["Java"] } as Awaited<
    ReturnType<typeof getDeveloper>
  >);
  vi.mocked(api.getWithdrawals).mockResolvedValue(data);
  vi.mocked(api.saveWithdrawalExtensionPossibility).mockResolvedValue({
    changed: true,
    revision: "new-revision",
  });
  vi.mocked(assignmentApi.changeAssignment).mockResolvedValue({
    assignment,
    changed: true,
    warnings: [],
    protectedMonths: [],
  });
});
it("reuses the merged API-backed table and submits null with the selected ASGN revision", async () => {
  render(
    <MemoryRouter>
      <WithdrawalContent year={2026} />
    </MemoryRouter>,
  );
  await screen.findByText("001");
  fireEvent.change(screen.getByTestId("withdrawal-extension-a-001"), {
    target: { value: "" },
  });
  await waitFor(() =>
    expect(api.saveWithdrawalExtensionPossibility).toHaveBeenCalledWith(
      row,
      2026,
      null,
    ),
  );
  expect(api.createNextPositionRecommendation).not.toHaveBeenCalled();
});
it("past year cannot change extension or create recommendations", async () => {
  vi.mocked(api.getWithdrawals).mockResolvedValue({
    ...data,
    items: [{ ...row, extensionEditable: false, remainingDays: -365 }],
    guidance: { ...data.guidance, visible: false },
  });
  render(
    <MemoryRouter>
      <WithdrawalContent year={2025} />
    </MemoryRouter>,
  );
  expect(
    await screen.findByTestId("withdrawal-extension-a-001"),
  ).toBeDisabled();
  expect(screen.queryByTestId("withdrawal-guidance")).not.toBeInTheDocument();
  expect(
    screen.queryByTestId("withdrawal-recommend-a-001"),
  ).not.toBeInTheDocument();
});
it("UI-12 EXTP hides date and reason and saves immediately without a protected-month preview", async () => {
  const changed = vi.fn().mockResolvedValue(undefined);
  render(
    <AssignmentChangeDialog
      assignment={assignment}
      year={2026}
      today="2026-11-15"
      onClose={vi.fn()}
      onChanged={changed}
    />,
  );
  fireEvent.change(screen.getByTestId("assignment-change-type"), {
    target: { value: "EXTP" },
  });
  expect(
    screen.queryByTestId("assignment-change-applyDate"),
  ).not.toBeInTheDocument();
  expect(screen.queryByTestId("assignment-reason")).not.toBeInTheDocument();
  expect(screen.queryByTestId("assignment-memo")).not.toBeInTheDocument();
  fireEvent.change(screen.getByTestId("assignment-change-value"), {
    target: { value: "L" },
  });
  fireEvent.click(screen.getByRole("button", { name: "저장" }));
  await waitFor(() =>
    expect(assignmentApi.changeAssignment).toHaveBeenCalledWith(
      row.assignmentId,
      {
        year: 2026,
        expectedRevision: "revision",
        type: "EXTP",
        extensionPossibility: "L",
      },
    ),
  );
  expect(assignmentApi.preview).not.toHaveBeenCalled();
  await waitFor(() => expect(changed).toHaveBeenCalledTimes(1));
});
it("failed NEXT request stays explicit and rerender does not generate another round", async () => {
  vi.mocked(api.getWithdrawals).mockResolvedValue({
    ...data,
    items: [
      {
        ...row,
        extensionPossibility: "L",
        canRecommend: true,
        recommendationCount: 1,
      },
    ],
  });
  vi.mocked(api.createNextPositionRecommendation).mockRejectedValue(
    new Error("다시 추천해 주세요."),
  );
  const view = render(
    <MemoryRouter>
      <WithdrawalContent year={2026} />
    </MemoryRouter>,
  );
  fireEvent.click(await screen.findByTestId("withdrawal-recommend-a-001"));
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "다시 추천해 주세요.",
  );
  view.rerender(
    <MemoryRouter>
      <WithdrawalContent year={2026} />
    </MemoryRouter>,
  );
  expect(api.createNextPositionRecommendation).toHaveBeenCalledTimes(1);
});
