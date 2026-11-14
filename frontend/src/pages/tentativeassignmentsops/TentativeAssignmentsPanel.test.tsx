import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { ApiError } from "../../shared/api";
import { TentativeAssignmentsPanel } from "./TentativeAssignmentsPanel";
import * as api from "./tentativeAssignmentsApi";
vi.mock("./tentativeAssignmentsApi");
const tentative: api.SalesTentative = {
  id: "101",
  opportunityId: "O-0101",
  needSn: 1,
  developerId: "D-101",
  startMonth: "2026-07",
  status: "ACT",
  sourceScreenId: "UI-29",
  processedAt: null,
};
const refresh = vi.fn();
beforeEach(() => {
  vi.resetAllMocks();
  refresh.mockResolvedValue(undefined);
  vi.mocked(api.releaseTentativeAssignment).mockResolvedValue({
    changed: true,
    revision: "next",
    warnings: [],
    opportunityId: tentative.opportunityId,
    tentativeId: tentative.id,
  });
});
function view(
  props: Partial<Parameters<typeof TentativeAssignmentsPanel>[0]> = {},
) {
  return render(
    <TentativeAssignmentsPanel
      items={[tentative]}
      revision="complete-revision"
      opportunityStatus="ING"
      developerNames={{ "D-101": "검토 인력" }}
      onRefresh={refresh}
      {...props}
    />,
  );
}
async function open() {
  await userEvent.click(screen.getByRole("button", { name: "해제" }));
  return screen.getByRole("dialog", { name: "가배정 해제" });
}
async function confirm() {
  await userEvent.click(
    within(screen.getByRole("dialog", { name: "가배정 해제" })).getByRole(
      "button",
      { name: "해제" },
    ),
  );
}
function failure(code: string, message: string, field?: string) {
  return new ApiError(code === "VALIDATION_ERROR" ? 400 : 409, {
    success: false,
    meta: {},
    code,
    message,
    requestId: "release-request",
    field,
  });
}
it("renders selected NEED and real developer with source, month and processing state", () => {
  view();
  expect(screen.getByText("검토 인력")).toBeVisible();
  expect(screen.getByText("2026-07")).toBeVisible();
  expect(screen.getByText("UI-29")).toBeVisible();
  expect(screen.getByText("ACT")).toHaveClass("sicost-tone-info");
  for (const label of [
    "필요행",
    "개발자",
    "시작월",
    "출처",
    "상태",
    "처리시각",
    "행동",
  ])
    expect(screen.getByRole("columnheader", { name: label })).toBeVisible();
  expect(api.releaseTentativeAssignment).not.toHaveBeenCalled();
});
it("requires confirmation then sends path TENT, complete revision and entered reason once", async () => {
  view();
  const dialog = await open();
  expect(
    within(dialog).getByText(/실제 투입 배정은 변경하지 않습니다/),
  ).toBeVisible();
  expect(api.releaseTentativeAssignment).not.toHaveBeenCalled();
  fireEvent.change(screen.getByLabelText("해제 사유 (선택)"), {
    target: { value: "인력 계획 변경" },
  });
  await confirm();
  await waitFor(() => expect(refresh).toHaveBeenCalledTimes(1));
  expect(api.releaseTentativeAssignment).toHaveBeenCalledTimes(1);
  expect(api.releaseTentativeAssignment).toHaveBeenCalledWith(
    "101",
    "complete-revision",
    "인력 계획 변경",
  );
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  expect(screen.getByText("저장했습니다.")).toBeVisible();
});
it("cancel leaves the tentative untouched", async () => {
  view();
  await open();
  await userEvent.click(screen.getByRole("button", { name: "취소" }));
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  expect(api.releaseTentativeAssignment).not.toHaveBeenCalled();
  expect(refresh).not.toHaveBeenCalled();
});
it.each(["REL", "CNVT", "DONE", "DROP"] as const)(
  "%s has no release action",
  (status) => {
    view({
      items: [{ ...tentative, status, processedAt: "2026-07-01T00:00:00" }],
    });
    expect(
      screen.queryByRole("button", { name: "해제" }),
    ).not.toBeInTheDocument();
    expect(screen.getByText("2026-07-01T00:00:00")).toBeVisible();
  },
);
it.each(["WIN", "LOSE", "STOP"] as const)(
  "%s opportunity is read-only even if a legacy ACT exists",
  (opportunityStatus) => {
    view({ opportunityStatus });
    expect(
      screen.queryByRole("button", { name: "해제" }),
    ).not.toBeInTheDocument();
    expect(screen.getByText(/종료된 기회의 가배정은 읽기 전용/)).toHaveClass(
      "sicost-tone-lock",
    );
  },
);
it("empty list uses the reference empty state", () => {
  view({ items: [] });
  expect(screen.getByText("가배정한 사람이 없습니다")).toHaveClass(
    "sicost-empty",
  );
  expect(screen.queryByRole("table")).not.toBeInTheDocument();
});
it("loading does not expose stale row controls", () => {
  view({ loading: true });
  expect(screen.getByText("가배정을 불러오는 중입니다.")).toBeVisible();
  expect(
    screen.queryByRole("button", { name: "해제" }),
  ).not.toBeInTheDocument();
});
it("failed list displays request id and supports read-only refresh", async () => {
  view({ error: failure("INTERNAL_ERROR", "조회 실패") });
  expect(screen.getByText(/조회 실패/)).toBeVisible();
  expect(screen.getByText(/release-request/)).toBeVisible();
  expect(screen.queryByRole("table")).not.toBeInTheDocument();
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  expect(refresh).toHaveBeenCalled();
  expect(api.releaseTentativeAssignment).not.toHaveBeenCalled();
});
it("overlong reason is visible and blocks save without truncation", async () => {
  view();
  await open();
  fireEvent.change(screen.getByLabelText("해제 사유 (선택)"), {
    target: { value: "가".repeat(301) },
  });
  expect(screen.getByRole("alert")).toHaveTextContent("300자");
  expect(
    within(screen.getByRole("dialog")).getByRole("button", { name: "해제" }),
  ).toBeDisabled();
  expect(screen.getByLabelText("해제 사유 (선택)")).toHaveValue(
    "가".repeat(301),
  );
});
it("reason at 300 code points is sent intact", async () => {
  view();
  await open();
  const reason = "😀".repeat(300);
  fireEvent.change(screen.getByLabelText("해제 사유 (선택)"), {
    target: { value: reason },
  });
  await confirm();
  await waitFor(() =>
    expect(api.releaseTentativeAssignment).toHaveBeenCalledWith(
      "101",
      "complete-revision",
      reason,
    ),
  );
});
it("server field errors retain the typed reason", async () => {
  vi.mocked(api.releaseTentativeAssignment).mockRejectedValue(
    failure("VALIDATION_ERROR", "해제 사유를 확인해 주세요.", "reason"),
  );
  view();
  await open();
  fireEvent.change(screen.getByLabelText("해제 사유 (선택)"), {
    target: { value: "검토" },
  });
  await confirm();
  await waitFor(() =>
    expect(screen.getByLabelText("해제 사유 (선택)")).toHaveAttribute(
      "aria-invalid",
      "true",
    ),
  );
  expect(screen.getByLabelText("해제 사유 (선택)")).toHaveValue("검토");
  expect(refresh).not.toHaveBeenCalled();
});
it("stale request blocks retries until cancel and explicit refresh", async () => {
  vi.mocked(api.releaseTentativeAssignment).mockRejectedValue(
    failure("STALE_WRITE", "다른 변경이 있습니다."),
  );
  view();
  await open();
  await confirm();
  await waitFor(() =>
    expect(
      within(screen.getByRole("dialog")).getByRole("button", { name: "해제" }),
    ).toBeDisabled(),
  );
  await userEvent.click(screen.getByRole("button", { name: "취소" }));
  expect(screen.getByRole("button", { name: "해제" })).toBeDisabled();
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  await waitFor(() =>
    expect(screen.getByRole("button", { name: "해제" })).toBeEnabled(),
  );
  expect(api.releaseTentativeAssignment).toHaveBeenCalledTimes(1);
});
it("invalid state and network failures are shown without automatic mutation retry", async () => {
  vi.mocked(api.releaseTentativeAssignment).mockRejectedValue(
    failure("INVALID_STATE", "종료된 가배정입니다."),
  );
  view();
  await open();
  await confirm();
  await waitFor(() =>
    expect(screen.getAllByText(/종료된 가배정입니다/).length).toBeGreaterThan(
      0,
    ),
  );
  expect(api.releaseTentativeAssignment).toHaveBeenCalledTimes(1);
  expect(refresh).not.toHaveBeenCalled();
  expect(screen.getByRole("dialog")).toBeVisible();
});
it("pending confirmation disables fields and prevents double submit", async () => {
  let finish!: (result: api.TentativeReleaseResult) => void;
  vi.mocked(api.releaseTentativeAssignment).mockReturnValue(
    new Promise((resolve) => {
      finish = resolve;
    }),
  );
  view();
  await open();
  await confirm();
  expect(screen.getByLabelText("해제 사유 (선택)")).toBeDisabled();
  expect(screen.getByRole("button", { name: "처리 중…" })).toBeDisabled();
  fireEvent.submit(screen.getByRole("dialog"));
  expect(api.releaseTentativeAssignment).toHaveBeenCalledTimes(1);
  finish({
    changed: true,
    revision: "next",
    warnings: [],
    opportunityId: tentative.opportunityId,
    tentativeId: tentative.id,
  });
  await waitFor(() => expect(refresh).toHaveBeenCalled());
});
it("committed but failed refresh offers only a read retry, not a mutation replay", async () => {
  refresh.mockRejectedValueOnce(new Error("조회 실패"));
  view();
  await open();
  await confirm();
  expect(
    await screen.findByText("저장됐지만 다시 불러오지 못했습니다"),
  ).toBeVisible();
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  expect(screen.getByRole("button", { name: "해제" })).toBeDisabled();
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  await waitFor(() => expect(refresh).toHaveBeenCalledTimes(2));
  expect(api.releaseTentativeAssignment).toHaveBeenCalledTimes(1);
});
