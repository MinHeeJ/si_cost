import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, afterEach, expect, it, vi } from "vitest";
import { ApiError } from "../../shared/api";
import { ReminderDialog } from "./ReminderDialog";
import { DeliveryStatusPanel } from "./DeliveryStatusPanel";
import { ReceivableDeliveryActions } from "./ReceivableDeliveryActions";
import * as api from "./dunningDeliveryApi";
vi.mock("./dunningDeliveryApi", async (original) => ({
  ...(await original<typeof api>()),
  getReceivableDeliveryPreview: vi.fn(),
  getReceivableDeliveries: vi.fn(),
  createReceivableReminder: vi.fn(),
}));
const preview: api.DeliveryPreview = {
  billId: "TEST-BILL",
  projectId: "TEST-PROJECT",
  projectName: "시험 프로젝트",
  unpaidAmount: "999999999999999",
  dueDate: "2026-06-01",
  currentStep: 2,
  recipients: [
    {
      userId: "pm",
      userName: "담당자",
      emailAddress: null,
      emailMissing: true,
      qualificationCodes: ["PM"],
    },
  ],
  unassignedReasons: ["영업파트 SALES_LDR 미지정"],
  sendMethodCode: "BOTH",
  mailConfigured: false,
  inProgress: false,
  canRemind: true,
  unavailableReasons: [],
  revision: "delivery-token",
  observedAt: "2026-07-15T09:00:00+09:00",
};
const result: api.ReminderResult = {
  billId: preview.billId,
  eventKey: "test-event",
  operationId: "7047f01a-fab0-48cb-9208-99481bc962ff",
  reservedSn: 2,
  accepted: true,
  completed: false,
  tasks: [],
  history: null,
};
const task: api.DeliveryTask = {
  taskId: "test-task",
  userId: "former",
  userName: "당시 이름",
  emailAddress: "old@example.invalid",
  channelCode: "MAIL",
  state: "UNKNOWN",
  notificationId: null,
  messageId: "<fixed@example.invalid>",
  sentAt: null,
  errorCode: "MAIL_ACCEPTANCE_UNKNOWN",
  errorMessage: "접수 확인 필요",
};
const page: api.DeliveryPage = {
  billId: preview.billId,
  rounds: [
    {
      eventKey: "",
      reservedSn: 1,
      step: 1,
      resend: false,
      history: {
        sn: 1,
        step: 1,
        sendDate: "2026-06-02",
        receiverDescription: "과거 수신자",
        sendMethodCode: "SYS",
        resend: false,
      },
      tasks: [],
      terminal: true,
    },
    {
      eventKey: "test-event",
      reservedSn: 2,
      step: 2,
      resend: true,
      history: null,
      tasks: [task],
      terminal: true,
    },
  ],
  page: 1,
  size: 50,
  total: 2,
  hasMore: false,
  observedAt: preview.observedAt,
};
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api.getReceivableDeliveryPreview).mockResolvedValue(preview);
  vi.mocked(api.createReceivableReminder).mockResolvedValue(result);
  vi.mocked(api.getReceivableDeliveries).mockResolvedValue(page);
});
afterEach(() => {
  vi.useRealTimers();
});
function draw() {
  const props = {
    billId: preview.billId,
    onClose: vi.fn(),
    onAccepted: vi.fn().mockResolvedValue(undefined),
  };
  render(<ReminderDialog {...props} />);
  return props;
}
it("shows actual readonly balance, recipients, method and truthful missing-mail warning", async () => {
  draw();
  expect(await screen.findByText("999,999,999,999,999")).toBeInTheDocument();
  expect(screen.getByText("담당자")).toBeInTheDocument();
  expect(screen.getByText("시스템+메일")).toBeInTheDocument();
  expect(screen.getByText("누락 · 메일 실패")).toHaveClass("sicost-tone-bad");
  expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
});
it("sends only revision and confirmation UUID and prevents duplicate submission", async () => {
  vi.mocked(api.createReceivableReminder).mockReturnValue(
    new Promise(() => {}),
  );
  draw();
  await screen.findByText("담당자");
  fireEvent.submit(screen.getByRole("dialog"));
  fireEvent.submit(screen.getByRole("dialog"));
  expect(api.createReceivableReminder).toHaveBeenCalledTimes(1);
  expect(api.createReceivableReminder).toHaveBeenCalledWith(preview.billId, {
    expectedRevision: "delivery-token",
    operationId: expect.stringMatching(/^[a-f0-9-]{36}$/),
  });
  expect(screen.getByRole("button", { name: "취소" })).toBeDisabled();
});
it.each([
  "취소된 청구입니다.",
  "현재 미수가 없습니다.",
  "알림 단계에 도달하지 않았습니다.",
  "수신자가 지정되지 않았습니다.",
  "전달 처리 중입니다.",
])(
  "blocks confirmation for server state %s without invented role gate",
  async (reason) => {
    vi.mocked(api.getReceivableDeliveryPreview).mockResolvedValue({
      ...preview,
      canRemind: false,
      unavailableReasons: [reason],
    });
    draw();
    await screen.findByText(reason);
    expect(
      screen.getByRole("button", { name: "확인 후 다시알림" }),
    ).toBeDisabled();
    expect(api.createReceivableReminder).not.toHaveBeenCalled();
  },
);
it("cancels without a command", async () => {
  const props = draw();
  await screen.findByText("담당자");
  await userEvent.click(screen.getByRole("button", { name: "취소" }));
  expect(props.onClose).toHaveBeenCalledOnce();
  expect(api.createReceivableReminder).not.toHaveBeenCalled();
});
it("stale confirmation preserves evidence and needs a new preview before a second explicit click", async () => {
  vi.mocked(api.createReceivableReminder)
    .mockRejectedValueOnce(
      new ApiError(409, {
        success: false,
        meta: {},
        code: "STALE_WRITE",
        message: "확인한 내용이 바뀌었습니다.",
        requestId: "test-request",
      }),
    )
    .mockResolvedValue(result);
  draw();
  await screen.findByText("담당자");
  await userEvent.click(
    screen.getByRole("button", { name: "확인 후 다시알림" }),
  );
  await screen.findByText("확인한 내용이 바뀌었습니다.");
  expect(
    screen.getByRole("button", { name: "확인 후 다시알림" }),
  ).toBeDisabled();
  const first = vi.mocked(api.createReceivableReminder).mock.calls[0][1]
    .operationId;
  await userEvent.click(
    screen.getByRole("button", { name: "최신 미리보기 다시 확인" }),
  );
  await waitFor(() =>
    expect(
      screen.getByRole("button", { name: "확인 후 다시알림" }),
    ).toBeEnabled(),
  );
  expect(api.createReceivableReminder).toHaveBeenCalledTimes(1);
  await userEvent.click(
    screen.getByRole("button", { name: "확인 후 다시알림" }),
  );
  expect(
    vi.mocked(api.createReceivableReminder).mock.calls[1][1].operationId,
  ).toBe(first);
});
it("failed preview and empty recipients do not allow a command", async () => {
  vi.mocked(api.getReceivableDeliveryPreview).mockRejectedValue(
    new Error("조회 실패"),
  );
  draw();
  await screen.findByText("조회 실패");
  expect(
    screen.getByRole("button", { name: "확인 후 다시알림" }),
  ).toBeDisabled();
});
it("accepted request refresh failure offers GET/result refresh only", async () => {
  const props = draw();
  props.onAccepted.mockRejectedValueOnce(new Error("failed refresh"));
  await screen.findByText("담당자");
  await userEvent.click(
    screen.getByRole("button", { name: "확인 후 다시알림" }),
  );
  await screen.findByText("요청은 저장됐지만 전달결과를 불러오지 못했습니다.");
  await userEvent.click(
    screen.getByRole("button", { name: "전달결과 재조회" }),
  );
  expect(api.createReceivableReminder).toHaveBeenCalledOnce();
  expect(props.onAccepted).toHaveBeenCalledTimes(2);
});
it("legacy and failed rounds keep frozen evidence and do not fabricate success or resend", async () => {
  render(<DeliveryStatusPanel billId={preview.billId} onClose={vi.fn()} />);
  await screen.findByText("당시주소 미기록 · 기존 발송이력");
  expect(screen.getByText("old@example.invalid")).toBeInTheDocument();
  expect(screen.getByText("발송완료 이력 없음")).toBeInTheDocument();
  expect(screen.getByText("확인필요")).toHaveClass("sicost-tone-warn");
  expect(api.createReceivableReminder).not.toHaveBeenCalled();
  expect(api.getReceivableDeliveryPreview).not.toHaveBeenCalled();
});
it.each([
  ["PENDING", "대기", "neutral"],
  ["SENDING", "처리중", "info"],
  ["FAILED", "실패", "bad"],
  ["SKIPPED", "건너뜀", "lock"],
  ["SENT", "메일 성공", "ok"],
] as const)(
  "uses channel-specific %s status and token",
  async (state, label, tone) => {
    vi.mocked(api.getReceivableDeliveries).mockResolvedValue({
      ...page,
      rounds: [{ ...page.rounds[1], tasks: [{ ...task, state }] }],
    });
    render(<DeliveryStatusPanel billId={preview.billId} onClose={vi.fn()} />);
    expect(await screen.findByText(label)).toHaveClass(`sicost-tone-${tone}`);
  },
);
it("empty deliveries and failed GET are distinct", async () => {
  vi.mocked(api.getReceivableDeliveries)
    .mockResolvedValueOnce({ ...page, rounds: [], total: 0 })
    .mockRejectedValueOnce(new Error("이력 조회 실패"));
  render(<DeliveryStatusPanel billId={preview.billId} onClose={vi.fn()} />);
  await screen.findByText("발송 이력이 없습니다.");
  await userEvent.click(screen.getByRole("button", { name: "재조회" }));
  await screen.findByText("이력 조회 실패");
  expect(api.createReceivableReminder).not.toHaveBeenCalled();
});
it("uses the selected bill in both host actions and opens delivery round after acceptance", async () => {
  render(<ReceivableDeliveryActions billId={preview.billId} />);
  await userEvent.click(screen.getByRole("button", { name: "다시알림" }));
  await screen.findByText("담당자");
  await userEvent.click(
    screen.getByRole("button", { name: "확인 후 다시알림" }),
  );
  expect(
    await screen.findByTestId("delivery-status-panel"),
  ).toBeInTheDocument();
  expect(api.getReceivableDeliveries).toHaveBeenCalledWith(
    preview.billId,
    1,
    50,
    expect.any(AbortSignal),
  );
});
