import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { ApiError } from "../../shared/api";
import { CandidateShortagesPanel } from "./CandidateShortagesPanel";
import { CandidatePositionsPanel } from "./CandidatePositionsPanel";
import * as api from "./candidateMatchingApi";

vi.mock("./candidateMatchingApi", () => ({
  getCandidateShortages: vi.fn(),
  getCandidatePositions: vi.fn(),
}));
const shortage: api.CandidateShortage = {
  jobCode: "DEV",
  firstShortageMonth: "2027-01",
  maximumShortage: "1.5",
  availableUntil: "2027-01-31",
  availableCandidateCount: 2,
  activeCandidateCount: 3,
};
const candidate = {
  id: "C-1",
  jobCode: "DEV",
  desiredRate: "750000",
  availableDate: "2027-01-16",
  contactStatus: "DONE",
};
const position: api.CandidatePosition = {
  opportunityId: "O-17",
  name: "실제 기회명",
  needSn: 2,
  probability: "80",
  jobCode: "DEV",
  gradeCode: "MD",
  startMonth: "2027-01",
  endMonth: "2027-03",
  availableDate: "2027-01-16",
  effectiveRate: "1000000",
  candidateCost: "850001",
  profitRate: "15.0",
  belowApprovalThreshold: true,
  gradeDistance: 0,
};
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api.getCandidateShortages).mockResolvedValue({
    data: {
      items: [shortage],
      total: 1,
      page: 1,
      size: 50,
      includeUnfilled: false,
      from: "2027-01",
      months: 6,
    },
    issues: [],
  });
  vi.mocked(api.getCandidatePositions).mockResolvedValue({
    data: { candidateId: candidate.id, items: [position], total: 1, limit: 4 },
    issues: [],
  });
});
it("shortage renders the specified opportunity-only basis, columns and exact candidate-view cutoff", async () => {
  const onView = vi.fn();
  render(<CandidateShortagesPanel onView={onView} jobLabel={() => "개발"} />);
  expect(screen.getByRole("status")).toHaveTextContent("불러오는 중");
  await screen.findByText("개발");
  expect(screen.getByText(/영업 기회 기준\(미충원 제외\)/)).toBeInTheDocument();
  expect(screen.getByText("1.5명")).toBeInTheDocument();
  expect(screen.getByText("2 / 3명")).toBeInTheDocument();
  await userEvent.click(screen.getByRole("button", { name: "후보 보기" }));
  expect(onView).toHaveBeenCalledWith(shortage);
  expect(
    screen.queryByRole("combobox", { name: /기간|연도/ }),
  ).not.toBeInTheDocument();
});
it("a real empty shortage is explicitly distinguished from loading", async () => {
  vi.mocked(api.getCandidateShortages).mockResolvedValue({
    data: {
      items: [],
      total: 0,
      page: 1,
      size: 50,
      from: "2027-01",
      months: 6,
      includeUnfilled: false,
    },
    issues: [],
  });
  render(<CandidateShortagesPanel onView={vi.fn()} />);
  expect(await screen.findByText("부족 직무가 없습니다.")).toBeInTheDocument();
});
it("unknown shortage standards show an issue rather than no shortages", async () => {
  vi.mocked(api.getCandidateShortages).mockResolvedValue({
    data: {
      items: [],
      total: 0,
      page: 1,
      size: 50,
      from: "2027-01",
      months: 6,
      includeUnfilled: false,
    },
    issues: [
      {
        field: "SHORT_PERSON",
        needSn: null,
        reason: "인력 부족 기준 미확인",
        status: "UNKNOWN",
      },
    ],
  });
  render(<CandidateShortagesPanel onView={vi.fn()} />);
  expect(await screen.findByText(/인력 부족 기준 미확인/)).toBeInTheDocument();
  expect(screen.queryByText("부족 직무가 없습니다.")).not.toBeInTheDocument();
});
it("shortage request error retains request id and retries explicitly", async () => {
  vi.mocked(api.getCandidateShortages).mockRejectedValueOnce(
    new ApiError(500, {
      success: false,
      meta: {},
      code: "INTERNAL_ERROR",
      message: "조회 오류",
      requestId: "matching-trace",
    }),
  );
  render(<CandidateShortagesPanel onView={vi.fn()} />);
  const alert = await screen.findByRole("alert");
  expect(alert).toHaveTextContent("matching-trace");
  await userEvent.click(
    within(alert).getByRole("button", { name: "다시 조회" }),
  );
  await screen.findByText("DEV");
  expect(api.getCandidateShortages).toHaveBeenCalledTimes(2);
});
it("shortage sorting uses approved server fields and resets page", async () => {
  render(<CandidateShortagesPanel onView={vi.fn()} />);
  await screen.findByText("DEV");
  await userEvent.click(screen.getByRole("button", { name: /최대 부족/ }));
  await waitFor(() =>
    expect(api.getCandidateShortages).toHaveBeenLastCalledWith(
      { page: 1, size: 50, sort: "maximumShortage", direction: "ASC" },
      expect.any(AbortSignal),
    ),
  );
});
it("positions use server threshold flag, not rounded display rate, and selected opportunity link", async () => {
  render(<CandidatePositionsPanel candidate={candidate} />);
  const link = await screen.findByRole("link", { name: "실제 기회명" });
  expect(link).toHaveAttribute("href", "/UI-29?opportunityId=O-17");
  expect(screen.getByText("15.0%")).toHaveClass("candidate-position-below");
  expect(screen.getByText("1,000,000")).toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: /등록|배정|가배정/ }),
  ).not.toBeInTheDocument();
});
it("equality at approval threshold is not red", async () => {
  vi.mocked(api.getCandidatePositions).mockResolvedValue({
    data: {
      candidateId: candidate.id,
      items: [{ ...position, belowApprovalThreshold: false }],
      total: 1,
      limit: 4,
    },
    issues: [],
  });
  render(<CandidatePositionsPanel candidate={candidate} />);
  expect(await screen.findByText("15.0%")).not.toHaveClass(
    "candidate-position-below",
  );
});
it.each(["HOLD", "CTRT"])(
  "%s candidate empty positions never expose write controls",
  async (contactStatus) => {
    vi.mocked(api.getCandidatePositions).mockResolvedValue({
      data: { candidateId: candidate.id, items: [], total: 0, limit: 4 },
      issues: [],
    });
    render(
      <CandidatePositionsPanel candidate={{ ...candidate, contactStatus }} />,
    );
    expect(
      await screen.findByText("조건에 맞는 자리가 없습니다."),
    ).toBeInTheDocument();
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  },
);
it("positions fail visibly and retry without mutating candidate", async () => {
  vi.mocked(api.getCandidatePositions).mockRejectedValueOnce(
    new Error("자리 조회 실패"),
  );
  render(<CandidatePositionsPanel candidate={candidate} />);
  expect(await screen.findByRole("alert")).toHaveTextContent("자리 조회 실패");
  expect(
    screen.queryByText("조건에 맞는 자리가 없습니다."),
  ).not.toBeInTheDocument();
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  await screen.findByRole("link", { name: "실제 기회명" });
});
it("missing cost is not shown as zero or empty matching", async () => {
  vi.mocked(api.getCandidatePositions).mockResolvedValue({
    data: { candidateId: candidate.id, items: [], total: 0, limit: 4 },
    issues: [
      {
        field: "TEAM_FEE",
        needSn: null,
        reason: "공통 기본경비 미확인",
        status: "UNKNOWN",
      },
    ],
  });
  render(<CandidatePositionsPanel candidate={candidate} />);
  expect(await screen.findByText(/공통 기본경비 미확인/)).toBeInTheDocument();
  expect(
    screen.queryByText("조건에 맞는 자리가 없습니다."),
  ).not.toBeInTheDocument();
});
it("actual candidate value changes requery and abort stale matching", async () => {
  let resolve!: (value: api.CandidateRead<api.CandidatePositions>) => void;
  vi.mocked(api.getCandidatePositions).mockReturnValueOnce(
    new Promise((yes) => {
      resolve = yes;
    }),
  );
  const view = render(<CandidatePositionsPanel candidate={candidate} />);
  const signal = vi.mocked(api.getCandidatePositions).mock.calls[0][1];
  view.rerender(
    <CandidatePositionsPanel
      candidate={{
        ...candidate,
        desiredRate: "800000",
        jobCode: "PM",
        availableDate: "2027-02-17",
      }}
      refreshKey={1}
    />,
  );
  await screen.findByRole("link", { name: "실제 기회명" });
  expect(signal?.aborted).toBe(true);
  await act(async () =>
    resolve({
      data: { candidateId: candidate.id, items: [], total: 0, limit: 4 },
      issues: [],
    }),
  );
  expect(screen.getByRole("link", { name: "실제 기회명" })).toBeInTheDocument();
  expect(api.getCandidatePositions).toHaveBeenCalledTimes(2);
});
it("inactive retained regions do not fetch and become fresh on activation", async () => {
  const view = render(
    <>
      <CandidatePositionsPanel candidate={candidate} active={false} />
      <CandidateShortagesPanel active={false} onView={vi.fn()} />
    </>,
  );
  expect(api.getCandidatePositions).not.toHaveBeenCalled();
  expect(api.getCandidateShortages).not.toHaveBeenCalled();
  view.rerender(
    <>
      <CandidatePositionsPanel candidate={candidate} />
      <CandidateShortagesPanel onView={vi.fn()} />
    </>,
  );
  await screen.findByRole("link", { name: "실제 기회명" });
  await screen.findByText("2 / 3명");
});
