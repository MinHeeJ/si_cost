import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { ApiError } from "../../shared/api";
import { BillingChecksPanel } from "./BillingChecksPanel";
import * as api from "./billingReviewApi";
vi.mock("./billingReviewApi");
const row: api.BillingCheckRow = {
  projectId: "selected-project",
  projectName: "시험 프로젝트",
  contractType: "MO",
  personCount: 1,
  mm: "0.50",
  estimatedAmount: "1234567",
  billedAmount: "0",
  differenceAmount: "1234567",
  tolerance: "0.25",
  billCount: 0,
  verdict: "청구 없음",
  explanation: "E/B/D/T · 보호 스냅샷 · 예정 포함",
  sources: [],
  checkRevision: "check-revision",
  remedy: {
    mode: "CREATE",
    projectId: "selected-project",
    month: "2026-07",
    billId: null,
    proposedSupplyAmount: "1234567",
    expectedRevision: null,
    checkContext: {
      projectId: "selected-project",
      month: "2026-07",
      checkRevision: "check-revision",
    },
    reason: null,
    relatedBillIds: [],
  },
};
const result: api.BillingChecksPage = {
  month: "2026-07",
  availableMonths: ["2026-06", "2026-07"],
  defaultMonth: "2026-07",
  includesPlanned: true,
  summary: {
    projectCount: 1,
    matchedCount: 0,
    missingCount: 1,
    differenceCount: 0,
    missingSupplyAmount: "1234567",
    tabCount: 1,
  },
  items: [row],
  total: 1,
  page: 1,
  size: 50,
  warnings: [],
};
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api.getBillingChecks).mockResolvedValue(result);
});
function draw(year = 2026) {
  const remedy = vi.fn(),
    detail = vi.fn(),
    count = vi.fn();
  render(
    <BillingChecksPanel
      year={year}
      currentYear={2026}
      onRemedy={remedy}
      onBillDetail={detail}
      onCount={count}
    />,
  );
  return { remedy, detail, count };
}
it("API 카드를 같은 집계로 표시하고 고정 보완 문맥을 그대로 전달한다", async () => {
  const { remedy, count } = draw();
  await userEvent.click(await screen.findByRole("button", { name: "보완" }));
  expect(remedy).toHaveBeenCalledWith(row.remedy);
  expect(count).toHaveBeenCalledWith(1);
  expect(screen.getByTestId("billing-check-card-4")).toHaveTextContent(
    "1,234.567 천원",
  );
  expect(
    screen.getByText("청구 없음", { selector: ".sicost-badge" }),
  ).toHaveClass("sicost-tone-bad");
  expect(
    screen.getByText("예정 포함", { selector: ".sicost-badge" }),
  ).toHaveClass("sicost-tone-info");
});
it.each(["적게 청구", "많이 청구", "맞음"] as const)(
  "%s 판정의 배지와 보완 상태를 지킨다",
  async (verdict) => {
    vi.mocked(api.getBillingChecks).mockResolvedValue({
      ...result,
      items: [{ ...row, verdict, remedy: { ...row.remedy, mode: "NONE" } }],
    });
    draw();
    expect(
      await screen.findByText(verdict, { selector: ".sicost-badge" }),
    ).toHaveClass(
      `sicost-tone-${verdict === "맞음" ? "ok" : verdict === "적게 청구" ? "warn" : "bad"}`,
    );
    expect(
      screen.queryByRole("button", { name: "보완" }),
    ).not.toBeInTheDocument();
  },
);
it("마감 월은 추가/수정창 대신 사유와 관련 청구 상세만 제공한다", async () => {
  vi.mocked(api.getBillingChecks).mockResolvedValue({
    ...result,
    includesPlanned: false,
    items: [
      {
        ...row,
        remedy: {
          ...row.remedy,
          mode: "DETAIL_ONLY",
          proposedSupplyAmount: null,
          relatedBillIds: ["selected-bill"],
          reason: "마감 월 · 후속 UI-10 전월조정 안내",
        },
      },
    ],
  });
  const { detail, remedy } = draw();
  await userEvent.click(
    await screen.findByRole("button", { name: "관련 청구 상세 selected-bill" }),
  );
  expect(detail).toHaveBeenCalledWith("selected-bill");
  expect(remedy).not.toHaveBeenCalled();
  expect(
    screen.queryByRole("button", { name: "보완" }),
  ).not.toBeInTheDocument();
  expect(screen.getByText(/마감 월 · 후속/)).toHaveClass("sicost-tone-lock");
});
it("월 변경은 첫 페이지로 조회하고 공개 query에 year를 넣지 않는다", async () => {
  draw();
  await userEvent.click(await screen.findByRole("button", { name: "2026-06" }));
  await waitFor(() =>
    expect(api.getBillingChecks).toHaveBeenLastCalledWith(
      { month: "2026-06", page: 1, size: 50 },
      expect.anything(),
    ),
  );
});
it("로딩과 실제 빈 결과를 구분한다", async () => {
  let resolve!: (value: api.BillingChecksPage) => void;
  vi.mocked(api.getBillingChecks).mockReturnValue(
    new Promise((r) => {
      resolve = r;
    }),
  );
  draw();
  expect(screen.getByText("불러오는 중입니다.")).toBeInTheDocument();
  await act(async () => resolve({ ...result, items: [], total: 0 }));
  expect(screen.getByText("점검할 프로젝트가 없습니다.")).toBeInTheDocument();
});
it("설정오류는 추적번호와 명시적인 GET 재시도만 제공한다", async () => {
  vi.mocked(api.getBillingChecks).mockRejectedValueOnce(
    new ApiError(500, {
      success: false,
      meta: {},
      code: "INTERNAL_ERROR",
      message: "허용오차 설정 확인",
      requestId: "check-trace",
    }),
  );
  draw();
  expect(await screen.findByRole("alert")).toHaveTextContent("check-trace");
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  await screen.findByRole("button", { name: "보완" });
  expect(api.getBillingChecks).toHaveBeenCalledTimes(2);
});
it("운영 시작 전 안내는 빈 목록과 다르다", async () => {
  vi.mocked(api.getBillingChecks).mockResolvedValue({
    ...result,
    availableMonths: [],
    defaultMonth: null,
    items: [],
    total: 0,
    warnings: ["운영 시작 전입니다."],
  });
  draw();
  expect(await screen.findByText("운영 시작 전입니다.")).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "엑셀 다운로드" })).toBeDisabled();
});
it.each([2025, 2027])(
  "다른 연도 %s에는 탭 업무 API를 호출하지 않는다",
  (year) => {
    draw(year);
    expect(
      screen.getByText("청구 누락 점검은 올해만 제공합니다."),
    ).toBeInTheDocument();
    expect(api.getBillingChecks).not.toHaveBeenCalled();
  },
);
it("부모 저장 갱신 신호가 오면 선택 월의 점검을 다시 조회한다", async () => {
  const props = {
    year: 2026,
    currentYear: 2026,
    onRemedy: vi.fn(),
    onBillDetail: vi.fn(),
  };
  const { rerender } = render(<BillingChecksPanel {...props} refreshKey={1} />);
  await screen.findByRole("button", { name: "보완" });
  rerender(<BillingChecksPanel {...props} refreshKey={2} />);
  await waitFor(() => expect(api.getBillingChecks).toHaveBeenCalledTimes(2));
});
