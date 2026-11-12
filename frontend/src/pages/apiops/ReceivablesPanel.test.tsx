import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { ApiError } from "../../shared/api";
import { ReceivablesPanel } from "./ReceivablesPanel";
import * as api from "./billingReviewApi";
vi.mock("./billingReviewApi");
const row: api.Receivable = {
  billId: "selected-bill",
  projectId: "selected-project",
  projectName: "이월 프로젝트",
  unpaidAmount: "999999999999999",
  dueDate: "2025-12-31",
  dunning: {
    elapsedDays: 1,
    step: 0,
    nextStep: 1,
    nextDate: "2026-01-08",
    finalStep: false,
  },
  recipients: [
    {
      step: 1,
      recipients: [{ userId: "actual-user", name: "현재 담당", roles: ["PM"] }],
      unassignedReasons: ["영업파트 SALES_LDR 미지정"],
    },
  ],
  sentHistory: [],
  sendNotice: "발송 기능 연결 예정",
};
const result: api.ReceivablesPage = {
  asOfDate: "2026-01-01",
  items: [row],
  total: 1,
  tabCount: 1,
  page: 1,
  size: 50,
  cards: [
    { step: 1, thresholdDays: "7.5", count: 0, recipientRoles: ["PM"] },
    {
      step: 2,
      thresholdDays: "30.5",
      count: 0,
      recipientRoles: ["PM", "SALES_LDR"],
    },
    {
      step: 3,
      thresholdDays: "60.5",
      count: 0,
      recipientRoles: ["PM", "SALES_LDR", "EXEC", "FIN"],
    },
  ],
};
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api.getReceivables).mockResolvedValue(result);
});
function draw(year = 2026) {
  const detail = vi.fn(),
    count = vi.fn();
  render(
    <ReceivablesPanel
      year={year}
      currentYear={2026}
      onBillDetail={detail}
      onCount={count}
    />,
  );
  return { detail, count };
}
it("초기 연체도 숨기지 않고 실제 서버 기준일·정밀 금액·3카드를 표시한다", async () => {
  const { detail, count } = draw();
  await userEvent.click(
    await screen.findByRole("button", { name: "selected-bill" }),
  );
  expect(detail).toHaveBeenCalledWith("selected-bill");
  expect(count).toHaveBeenCalledWith(1);
  expect(screen.getByText("알림 전")).toHaveClass("sicost-tone-neutral");
  expect(screen.getByText("999,999,999,999.999")).toBeInTheDocument();
  expect(screen.getByText(/기준일 2026-01-01/)).toBeInTheDocument();
  expect(screen.getByTestId("receivable-step-card-1")).toHaveTextContent(
    "7.5일",
  );
});
it.each([1, 2, 3])(
  "%s단계 색상과 최종 여부를 그대로 표시한다",
  async (step) => {
    vi.mocked(api.getReceivables).mockResolvedValue({
      ...result,
      items: [
        { ...row, dunning: { ...row.dunning, step, finalStep: step === 3 } },
      ],
    });
    draw();
    expect(await screen.findByText(`${step}단계 · 1일`)).toHaveClass(
      `sicost-tone-${step === 1 ? "warn" : "bad"}`,
    );
    if (step === 3) expect(screen.getByText("최종 단계")).toBeInTheDocument();
  },
);
it("빈 이력은 발송 이력 없음이며 현재 대상/미지정과 구분한다", async () => {
  draw();
  expect(await screen.findByText("발송 이력 없음")).toBeInTheDocument();
  await userEvent.click(screen.getByText("현재 수신대상"));
  expect(screen.getByText(/현재 담당/)).toBeInTheDocument();
  expect(screen.getByText("영업파트 SALES_LDR 미지정")).toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: /발송|다시 알림/ }),
  ).not.toBeInTheDocument();
});
it("실제 과거 수신자·방법·다시 표식을 표시하고 현재 대상은 별도로 둔다", async () => {
  vi.mocked(api.getReceivables).mockResolvedValue({
    ...result,
    items: [
      {
        ...row,
        sentHistory: [
          {
            billId: row.billId,
            sn: 2,
            step: 1,
            sendDate: "2025-12-30",
            receiverDescription: "당시 담당",
            sendMethodCode: "EMAIL",
            resend: true,
          },
        ],
      },
    ],
  });
  draw();
  expect(await screen.findByText(/당시 수신자: 당시 담당/)).toHaveTextContent(
    "EMAIL (다시)",
  );
  expect(screen.getByText(/현재 담당/)).toBeInTheDocument();
});
it("로딩과 실제 연체 없음은 다른 상태다", async () => {
  let resolve!: (value: api.ReceivablesPage) => void;
  vi.mocked(api.getReceivables).mockReturnValue(
    new Promise((r) => {
      resolve = r;
    }),
  );
  draw();
  expect(screen.getByText("불러오는 중입니다.")).toBeInTheDocument();
  await act(async () =>
    resolve({ ...result, items: [], total: 0, tabCount: 0 }),
  );
  expect(
    screen.getByText("입금 예정일이 지난 청구가 없습니다."),
  ).toBeInTheDocument();
});
it("설정 오류는 가짜 단계 대신 오류와 명시적 재조회다", async () => {
  vi.mocked(api.getReceivables).mockRejectedValueOnce(
    new ApiError(500, {
      success: false,
      meta: {},
      code: "INTERNAL_ERROR",
      message: "미수 알림 기준일 설정 확인",
      requestId: "dunning-trace",
    }),
  );
  draw();
  expect(await screen.findByRole("alert")).toHaveTextContent("dunning-trace");
  expect(screen.queryByText("알림 전")).not.toBeInTheDocument();
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  await screen.findByText("알림 전");
});
it("페이지와 크기만 API에 보내고 크기 변경은 첫 페이지로 돌아간다", async () => {
  vi.mocked(api.getReceivables).mockResolvedValue({ ...result, total: 101 });
  draw();
  await screen.findByText("알림 전");
  await userEvent.click(screen.getByRole("button", { name: "다음 페이지" }));
  await waitFor(() =>
    expect(api.getReceivables).toHaveBeenLastCalledWith(
      { page: 2, size: 50 },
      expect.anything(),
    ),
  );
  await waitFor(() =>
    expect(screen.getByLabelText("한 화면 건수")).toBeEnabled(),
  );
  await userEvent.selectOptions(screen.getByLabelText("한 화면 건수"), "20");
  await waitFor(() =>
    expect(api.getReceivables).toHaveBeenLastCalledWith(
      { page: 1, size: 20 },
      expect.anything(),
    ),
  );
});
it.each([2025, 2027])("%s 연도에서는 조회를 하지 않는다", (year) => {
  draw(year);
  expect(
    screen.getByText("미수 알림은 올해만 제공합니다."),
  ).toBeInTheDocument();
  expect(api.getReceivables).not.toHaveBeenCalled();
});
