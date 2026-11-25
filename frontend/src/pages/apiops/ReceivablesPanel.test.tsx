import { act, render, screen, waitFor } from "../../shared/testSupport/businessFixtures";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { ApiError } from "../../shared/api";
import { ReceivablesPanel } from "./ReceivablesPanel";
import * as api from "./billingReviewApi";
import * as delivery from "../receivablesops/dunningDeliveryApi";
vi.mock("./billingReviewApi");
vi.mock("../receivablesops/dunningDeliveryApi");
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
  vi.mocked(delivery.getReceivableDeliveryPreview).mockResolvedValue({
    billId: row.billId,
    projectId: row.projectId,
    projectName: row.projectName,
    unpaidAmount: row.unpaidAmount,
    dueDate: row.dueDate,
    currentStep: 1,
    recipients: [],
    unassignedReasons: [],
    sendMethodCode: "BOTH",
    mailConfigured: false,
    inProgress: false,
    canRemind: false,
    unavailableReasons: ["수신자 미지정"],
    revision: "selected-preview",
    observedAt: "2026-01-01T09:00:00+09:00",
  });
  vi.mocked(delivery.getReceivableDeliveries).mockResolvedValue({
    billId: row.billId,
    rounds: [],
    page: 1,
    size: 50,
    total: 0,
    hasMore: false,
    observedAt: "2026-01-01T09:00:00+09:00",
  });
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
it("초기 연체도 숨기지 않고 정밀 금액·3카드를 표시하고 안내 줄을 생략한다", async () => {
  const { detail, count } = draw();
  await userEvent.click(
    await screen.findByRole("button", { name: "selected-bill" }),
  );
  expect(detail).toHaveBeenCalledWith("selected-bill");
  expect(count).toHaveBeenCalledWith(1);
  expect(screen.getByText("알림 전")).toHaveClass("sicost-tone-neutral");
  expect(screen.getByText("999,999,999,999.999")).toBeInTheDocument();
  expect(
    screen.queryByText(/기준일 2026-01-01|이월 포함 전체 연체|단위 천원/),
  ).not.toBeInTheDocument();
  expect(screen.getByText("입금 예정일이 지난 청구 1건")).toBeInTheDocument();
  expect(screen.getByTestId("receivable-step-card-1")).toHaveTextContent(
    "7.5일",
  );
  expect(screen.getByTestId("receivable-step-card-1")).toHaveTextContent("영업 담당자");
  expect(screen.getByTestId("receivable-step-card-2")).toHaveTextContent("영업 담당자 · 영업 파트장");
  expect(screen.getByTestId("receivable-step-card-3")).toHaveTextContent("영업 담당자 · 영업 파트장 · 경영진 · 재무 담당");
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

it("목록 행의 다시알림은 선택한 청구를 미리보기만 하고 취소는 명령을 보내지 않는다", async () => {
  draw();
  await userEvent.click(
    await screen.findByRole("button", { name: "다시알림" }),
  );
  await screen.findByText("수신자 미지정");
  expect(delivery.getReceivableDeliveryPreview).toHaveBeenCalledWith(
    row.billId,
    expect.any(AbortSignal),
  );
  expect(
    screen.getByRole("button", { name: "확인 후 다시알림" }),
  ).toBeDisabled();
  await userEvent.click(screen.getByRole("button", { name: "취소" }));
  expect(delivery.createReceivableReminder).not.toHaveBeenCalled();
});

it("목록 행의 전달상태는 선택한 청구만 읽고 빈 회차도 성공으로 합성하지 않는다", async () => {
  draw();
  await userEvent.click(
    await screen.findByRole("button", { name: "전달상태" }),
  );
  await waitFor(() =>
    expect(delivery.getReceivableDeliveries).toHaveBeenCalledWith(
      row.billId,
      1,
      50,
      expect.any(AbortSignal),
    ),
  );
  expect(
    await screen.findByTestId("delivery-status-panel"),
  ).toBeInTheDocument();
  expect(delivery.createReceivableReminder).not.toHaveBeenCalled();
});

it("알림 저장 후 목록 재조회가 지연돼도 전달결과 패널을 유지한다", async () => {
  vi.mocked(delivery.getReceivableDeliveryPreview).mockResolvedValue({
    ...(await delivery.getReceivableDeliveryPreview(row.billId)), canRemind: true,
  });
  vi.mocked(delivery.createReceivableReminder).mockResolvedValue({ billId: row.billId, eventKey: "manual", operationId: "7047f01a-fab0-48cb-9208-99481bc962ff", reservedSn: 1, accepted: true, completed: false, tasks: [], history: null });
  draw();
  await screen.findByTestId("reminder-open-selected-bill");
  let finish!: (value: api.ReceivablesPage) => void;
  vi.mocked(api.getReceivables).mockImplementation(() => new Promise(resolve => { finish = resolve; }));
  await userEvent.click(screen.getByTestId("reminder-open-selected-bill"));
  await screen.findByTestId("reminder-confirm");
  await waitFor(() => expect(screen.getByTestId("reminder-confirm")).toBeEnabled());
  await userEvent.click(screen.getByTestId("reminder-confirm"));
  expect(await screen.findByRole("dialog", { name: /전달/ })).toBeVisible();
  await act(async () => finish(result));
  expect(screen.getByRole("dialog", { name: /전달/ })).toBeVisible();
});
