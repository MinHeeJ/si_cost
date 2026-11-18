import { request } from "../../shared/api";

export type DeliveryRecipient = {
  userId: string;
  userName: string;
  emailAddress: string | null;
  emailMissing: boolean;
  qualificationCodes: string[];
};
export type DeliveryPreview = {
  billId: string;
  projectId: string;
  projectName: string;
  unpaidAmount: string;
  dueDate: string;
  currentStep: number;
  recipients: DeliveryRecipient[];
  unassignedReasons: string[];
  sendMethodCode: "BOTH";
  mailConfigured: boolean;
  inProgress: boolean;
  canRemind: boolean;
  unavailableReasons: string[];
  revision: string;
  observedAt: string;
};
export type DeliveryTask = {
  taskId: string;
  userId: string;
  userName: string;
  emailAddress: string | null;
  channelCode: "SYS" | "MAIL";
  state: "PENDING" | "SENDING" | "SENT" | "FAILED" | "UNKNOWN" | "SKIPPED";
  notificationId: string | null;
  messageId: string | null;
  sentAt: string | null;
  errorCode: string | null;
  errorMessage: string | null;
};
export type DeliveryHistory = {
  sn: number;
  step: number;
  sendDate: string;
  receiverDescription: string;
  sendMethodCode: string;
  resend: boolean;
};
export type DeliveryRound = {
  eventKey: string;
  reservedSn: number;
  step: number;
  resend: boolean;
  history: DeliveryHistory | null;
  tasks: DeliveryTask[];
  terminal: boolean;
};
export type DeliveryPage = {
  billId: string;
  rounds: DeliveryRound[];
  page: number;
  size: 20 | 50 | 100;
  total: number;
  hasMore: boolean;
  observedAt: string;
};
export type ReminderResult = {
  billId: string;
  eventKey: string;
  operationId: string;
  reservedSn: number;
  accepted: boolean;
  completed: boolean;
  tasks: DeliveryTask[];
  history: DeliveryHistory | null;
};
export type ReminderInput = { expectedRevision: string; operationId: string };
const base = (billId: string) =>
  `/api/receivables/${encodeURIComponent(billId)}`;
export const getReceivableDeliveryPreview = (
  billId: string,
  signal?: AbortSignal,
) => request<DeliveryPreview>(`${base(billId)}/delivery-preview`, { signal });
export const getReceivableDeliveries = (
  billId: string,
  page = 1,
  size: 20 | 50 | 100 = 50,
  signal?: AbortSignal,
  background = false,
) =>
  request<DeliveryPage>(
    `${base(billId)}/deliveries?${new URLSearchParams({ page: String(page), size: String(size), sort: "reservedSn,asc" })}`,
    {
      signal,
      ...(background ? { headers: { "X-Background-Request": "true" } } : {}),
    },
  );
export const createReceivableReminder = (
  billId: string,
  input: ReminderInput,
) =>
  request<ReminderResult>(`${base(billId)}/reminders`, {
    method: "POST",
    body: JSON.stringify(input),
  });
