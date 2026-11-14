import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { TentativeAssignmentSelector } from "./TentativeAssignmentSelector";
import * as api from "./tentativeSelectionApi";
import { developerOptions } from "../assignmentsops/assignmentActionsApi";
vi.mock("./tentativeSelectionApi", () => ({
  createTentativeAssignment: vi.fn(),
}));
vi.mock("../assignmentsops/assignmentActionsApi", () => ({
  developerOptions: vi.fn(),
}));
const selection = {
  opportunityId: "O-1",
  needSn: 1,
  startMonth: "2026-07",
  sourceScreenId: "UI-29" as const,
  expectedRevision: "r",
};
const saved = {
  changed: true,
  revision: "new",
  warnings: ["이전 가배정을 해제하고 이동했습니다."],
  opportunityId: "O-1",
  tentativeId: "101",
};
beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(developerOptions).mockResolvedValue({
    items: [{ id: "D-1", name: "개발자", managementNo: "M-1" }],
    total: 1,
    page: 1,
    size: 50,
  });
  vi.mocked(api.createTentativeAssignment).mockResolvedValue(saved);
});
it("uses existing developer search and submits real DEV_ID with need start and revision", async () => {
  const refresh = vi.fn().mockResolvedValue(undefined);
  render(
    <TentativeAssignmentSelector
      selection={selection}
      selectedCount={0}
      needCount={2}
      onSaved={refresh}
    />,
  );
  await userEvent.type(screen.getByLabelText("개발자 검색"), "이름");
  await userEvent.click(screen.getByRole("button", { name: "개발자 조회" }));
  await userEvent.click(await screen.findByRole("button", { name: "선택" }));
  await waitFor(() =>
    expect(api.createTentativeAssignment).toHaveBeenCalledWith({
      ...selection,
      developerId: "D-1",
    }),
  );
  expect(refresh).toHaveBeenCalledWith(saved);
  expect(screen.getByRole("note")).toHaveTextContent(saved.warnings[0]);
});
it("capacity prevents selecting another person", async () => {
  render(
    <TentativeAssignmentSelector
      selection={selection}
      selectedCount={2}
      needCount={2}
      onSaved={vi.fn()}
    />,
  );
  await userEvent.click(screen.getByRole("button", { name: "개발자 조회" }));
  expect(await screen.findByRole("button", { name: "선택" })).toBeDisabled();
  expect(api.createTentativeAssignment).not.toHaveBeenCalled();
});
it("read-only state disables search without creating a write", () => {
  render(
    <TentativeAssignmentSelector
      selection={selection}
      selectedCount={0}
      needCount={2}
      readOnly
      onSaved={vi.fn()}
    />,
  );
  expect(screen.getByRole("button", { name: "개발자 조회" })).toBeDisabled();
  expect(screen.getByLabelText("개발자 검색")).toBeDisabled();
});
it("postcommit refresh failure is not retried as a mutation", async () => {
  render(
    <TentativeAssignmentSelector
      selection={selection}
      selectedCount={0}
      needCount={2}
      onSaved={vi.fn().mockRejectedValue(new Error("조회 실패"))}
    />,
  );
  await userEvent.click(screen.getByRole("button", { name: "개발자 조회" }));
  await userEvent.click(await screen.findByRole("button", { name: "선택" }));
  expect(await screen.findByRole("status")).toHaveTextContent(
    "저장됐지만 다시 불러오지 못했습니다",
  );
  expect(api.createTentativeAssignment).toHaveBeenCalledTimes(1);
});
it("failed developer search remains an error rather than an empty success", async () => {
  vi.mocked(developerOptions).mockRejectedValue(new Error("검색 실패"));
  render(
    <TentativeAssignmentSelector
      selection={selection}
      selectedCount={0}
      needCount={2}
      onSaved={vi.fn()}
    />,
  );
  await userEvent.click(screen.getByRole("button", { name: "개발자 조회" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("검색 실패");
  expect(
    screen.queryByText("선택 가능한 개발자가 없습니다."),
  ).not.toBeInTheDocument();
});
