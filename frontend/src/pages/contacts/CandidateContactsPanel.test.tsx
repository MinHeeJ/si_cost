import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import {
  CandidateContactsPanel,
  CandidateContactStatusBadge,
} from "./CandidateContactsPanel";
import { ApiError } from "../../shared/api";
import * as api from "./peopleClient";
vi.mock("./peopleClient", () => ({
  getCandidateContacts: vi.fn(),
  createCandidateContact: vi.fn(),
  getCandidateContactContext: vi.fn(),
}));
const candidate: api.CandidateContactContext = {
  id: "CAND-900001",
  name: "시험 후보",
  contactStatus: "BEFORE",
  revision: "current",
};
const contact: api.CandidateContact = {
  sn: 1,
  status: "BEFORE",
  memo: "등록",
  contactDate: "2026-06-15",
  actorId: "admin",
  actorName: "관리자",
};
const empty: api.CandidateContactsPage = {
  items: [],
  total: 0,
  page: 1,
  size: 50,
};
beforeEach(() => {
  vi.resetAllMocks();
  vi.stubGlobal(
    "matchMedia",
    vi.fn(() => ({
      matches: false,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })),
  );
  vi.mocked(api.getCandidateContacts).mockResolvedValue(empty);
  vi.mocked(api.getCandidateContactContext).mockResolvedValue({
    ...candidate,
    revision: "after-contact",
    contactStatus: "DONE",
  });
  vi.mocked(api.createCandidateContact).mockResolvedValue({
    contact,
    contactStatus: "BEFORE",
    lastContactDate: "2026-06-16",
  });
});
async function begin() {
  await userEvent.click(screen.getByRole("button", { name: "연락 등록" }));
}
async function fillAndSave(text = "새 연락") {
  await begin();
  await userEvent.type(screen.getByLabelText("연락 메모 *"), text);
  await userEvent.click(screen.getByRole("button", { name: "연락 저장" }));
}
it("renders loading then empty without fabricated contacts or forbidden operations", async () => {
  let resolve!: (page: api.CandidateContactsPage) => void;
  vi.mocked(api.getCandidateContacts).mockReturnValue(
    new Promise((yes) => {
      resolve = yes;
    }),
  );
  render(<CandidateContactsPanel candidate={candidate} />);
  expect(screen.getByText("불러오는 중입니다.")).toBeInTheDocument();
  await act(async () => resolve(empty));
  expect(await screen.findByText("연락 기록이 없습니다.")).toBeInTheDocument();
  expect(
    screen.queryByRole("button", {
      name: /삭제|수정|개발자 전환|동기화|추출|엑셀|일괄/,
    }),
  ).not.toBeInTheDocument();
});
it("renders date/status/memo/actor as read-only in server order", async () => {
  vi.mocked(api.getCandidateContacts).mockResolvedValue({
    ...empty,
    items: [{ ...contact, sn: 3, memo: "최근 연락" }, contact],
    total: 2,
  });
  render(<CandidateContactsPanel candidate={candidate} />);
  await screen.findByText("최근 연락");
  const rows = screen.getAllByRole("row");
  expect(rows[1]).toHaveTextContent("최근 연락");
  expect(rows[2]).toHaveTextContent("등록");
  expect(screen.getAllByText("관리자 (admin)")).toHaveLength(2);
  expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
});
it("failed load is an error with request id and requires explicit read retry", async () => {
  vi.mocked(api.getCandidateContacts).mockRejectedValueOnce(
    new ApiError(500, {
      success: false,
      meta: {},
      code: "INTERNAL_ERROR",
      message: "조회 실패",
      requestId: "contacts-read",
    }),
  );
  render(<CandidateContactsPanel candidate={candidate} />);
  expect(await screen.findByRole("alert")).toHaveTextContent("조회 실패");
  expect(screen.getByRole("alert")).toHaveTextContent("contacts-read");
  expect(screen.queryByText("연락 기록이 없습니다.")).not.toBeInTheDocument();
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  await screen.findByText("연락 기록이 없습니다.");
  expect(api.getCandidateContacts).toHaveBeenCalledTimes(2);
  expect(api.createCandidateContact).not.toHaveBeenCalled();
});
it("pagination and size changes request the selected candidate and reset to page one", async () => {
  vi.mocked(api.getCandidateContacts).mockResolvedValue({
    ...empty,
    total: 101,
    items: [contact],
  });
  render(<CandidateContactsPanel candidate={candidate} />);
  await screen.findByText("등록");
  await userEvent.click(screen.getByRole("button", { name: "다음 페이지" }));
  await waitFor(() =>
    expect(api.getCandidateContacts).toHaveBeenLastCalledWith(
      candidate.id,
      2,
      50,
      expect.any(AbortSignal),
    ),
  );
  await userEvent.selectOptions(
    screen.getByRole("combobox", { name: "한 화면 건수" }),
    "20",
  );
  await waitFor(() =>
    expect(api.getCandidateContacts).toHaveBeenLastCalledWith(
      candidate.id,
      1,
      20,
      expect.any(AbortSignal),
    ),
  );
});
it("focuses the first Korean validation error and never posts a blank memo", async () => {
  render(<CandidateContactsPanel candidate={candidate} />);
  await screen.findByText("연락 기록이 없습니다.");
  await begin();
  await userEvent.click(screen.getByRole("button", { name: "연락 저장" }));
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "연락 메모는 1~200자로 입력해 주세요.",
  );
  expect(screen.getByLabelText("연락 메모 *")).toHaveFocus();
  expect(api.createCandidateContact).not.toHaveBeenCalled();
});
it("rejects 201 characters without silently cutting text", async () => {
  render(<CandidateContactsPanel candidate={candidate} />);
  await begin();
  await userEvent.click(screen.getByLabelText("연락 메모 *"));
  await userEvent.paste("가".repeat(201));
  await userEvent.click(screen.getByRole("button", { name: "연락 저장" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("1~200자");
  expect(screen.getByLabelText("연락 메모 *")).toHaveValue("가".repeat(201));
  expect(api.createCandidateContact).not.toHaveBeenCalled();
});
it("cancel preserves a dirty draft and revision without a write", async () => {
  const dirty = vi.fn();
  const view = render(
    <CandidateContactsPanel candidate={candidate} onDirtyChange={dirty} />,
  );
  await begin();
  await userEvent.type(screen.getByLabelText("연락 메모 *"), "보존할 메모");
  await userEvent.click(screen.getByRole("button", { name: "취소" }));
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  view.rerender(
    <CandidateContactsPanel
      candidate={{ ...candidate, revision: "someone-else" }}
      onDirtyChange={dirty}
    />,
  );
  await begin();
  expect(screen.getByLabelText("연락 메모 *")).toHaveValue("보존할 메모");
  expect(api.createCandidateContact).not.toHaveBeenCalled();
  await userEvent.click(screen.getByRole("button", { name: "연락 저장" }));
  await waitFor(() =>
    expect(api.createCandidateContact).toHaveBeenCalledWith(candidate.id, {
      contactStatus: "BEFORE",
      memo: "보존할 메모",
      expectedRevision: "current",
    }),
  );
});
it("retained tab changes preserve the form and never submit", async () => {
  const view = render(<CandidateContactsPanel candidate={candidate} />);
  await begin();
  await userEvent.type(screen.getByLabelText("연락 메모 *"), "탭 보존");
  view.rerender(
    <CandidateContactsPanel candidate={candidate} active={false} />,
  );
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  view.rerender(<CandidateContactsPanel candidate={candidate} active />);
  expect(screen.getByLabelText("연락 메모 *")).toHaveValue("탭 보존");
  expect(api.createCandidateContact).not.toHaveBeenCalled();
});
it("explicit leave requires confirmation, while cancelling confirmation keeps the draft", async () => {
  render(<CandidateContactsPanel candidate={candidate} />);
  await begin();
  await userEvent.type(screen.getByLabelText("연락 메모 *"), "작성 중");
  await userEvent.click(
    screen.getByRole("button", { name: "입력을 버리고 나가기" }),
  );
  const confirm = screen.getByRole("dialog", { name: "입력 내용 버리기" });
  await userEvent.click(within(confirm).getByRole("button", { name: "취소" }));
  expect(screen.getByLabelText("연락 메모 *")).toHaveValue("작성 중");
  await userEvent.click(
    screen.getByRole("button", { name: "입력을 버리고 나가기" }),
  );
  await userEvent.click(
    within(screen.getByRole("dialog", { name: "입력 내용 버리기" })).getByRole(
      "button",
      { name: "버리고 나가기" },
    ),
  );
  await begin();
  expect(screen.getByLabelText("연락 메모 *")).toHaveValue("");
  expect(api.createCandidateContact).not.toHaveBeenCalled();
});
it("same status submits a new event, trims memo, closes and immediately refreshes contacts and parent summary", async () => {
  const refresh = vi.fn();
  render(
    <CandidateContactsPanel candidate={candidate} onCommitted={refresh} />,
  );
  await screen.findByText("연락 기록이 없습니다.");
  await fillAndSave(" 새 연락 ");
  await waitFor(() =>
    expect(api.createCandidateContact).toHaveBeenCalledWith(candidate.id, {
      contactStatus: "BEFORE",
      memo: "새 연락",
      expectedRevision: "current",
    }),
  );
  await waitFor(() =>
    expect(api.getCandidateContacts).toHaveBeenCalledTimes(2),
  );
  expect(refresh).toHaveBeenCalledTimes(1);
  expect(api.getCandidateContactContext).toHaveBeenCalledWith(
    candidate.id,
    expect.any(AbortSignal),
  );
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  await begin();
  expect(screen.getByLabelText("연락 상태 *")).toHaveValue("DONE");
  await userEvent.type(screen.getByLabelText("연락 메모 *"), "다음 연락");
  await userEvent.click(screen.getByRole("button", { name: "연락 저장" }));
  await waitFor(() =>
    expect(api.createCandidateContact).toHaveBeenLastCalledWith(candidate.id, {
      contactStatus: "DONE",
      memo: "다음 연락",
      expectedRevision: "after-contact",
    }),
  );
});
it("commit followed by contact read failure offers read-only retry, not a second POST", async () => {
  vi.mocked(api.getCandidateContacts)
    .mockResolvedValueOnce(empty)
    .mockRejectedValueOnce(new Error("연락 조회 실패"))
    .mockResolvedValue(empty);
  render(<CandidateContactsPanel candidate={candidate} />);
  await screen.findByText("연락 기록이 없습니다.");
  await fillAndSave();
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "연락은 저장되었습니다.",
  );
  expect(screen.getByRole("button", { name: "연락 등록" })).toBeDisabled();
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  await waitFor(() =>
    expect(screen.getByRole("button", { name: "연락 등록" })).toBeEnabled(),
  );
  expect(api.createCandidateContact).toHaveBeenCalledTimes(1);
  expect(api.getCandidateContacts).toHaveBeenCalledTimes(3);
});
it("commit followed by parent refresh failure likewise retries only reads", async () => {
  const refresh = vi
    .fn()
    .mockRejectedValueOnce(new Error("요약 실패"))
    .mockResolvedValue(undefined);
  render(
    <CandidateContactsPanel candidate={candidate} onCommitted={refresh} />,
  );
  await fillAndSave();
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "연락은 저장되었습니다.",
  );
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  await waitFor(() => expect(refresh).toHaveBeenCalledTimes(2));
  expect(api.createCandidateContact).toHaveBeenCalledTimes(1);
});
it.each(["STALE_WRITE", "SERIAL_LIMIT"])(
  "%s keeps modal and draft with request id, no automatic resave",
  async (code) => {
    vi.mocked(api.createCandidateContact).mockRejectedValue(
      new ApiError(409, {
        success: false,
        meta: {},
        code,
        message: "다시 조회해 주세요.",
        requestId: "contact-conflict",
      }),
    );
    render(<CandidateContactsPanel candidate={candidate} />);
    await fillAndSave("입력 보존");
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "contact-conflict",
    );
    expect(screen.getByLabelText("연락 메모 *")).toHaveValue("입력 보존");
    expect(api.createCandidateContact).toHaveBeenCalledTimes(1);
  },
);
it("server field error focuses memo and displays Korean reason", async () => {
  vi.mocked(api.createCandidateContact).mockRejectedValue(
    new ApiError(400, {
      success: false,
      meta: {},
      code: "VALIDATION_ERROR",
      message: "연락 메모를 확인해 주세요.",
      field: "memo",
      requestId: "contact-validation",
    }),
  );
  render(<CandidateContactsPanel candidate={candidate} />);
  await fillAndSave();
  await waitFor(() =>
    expect(screen.getByLabelText("연락 메모 *")).toHaveFocus(),
  );
  expect(screen.getByLabelText("연락 메모 *")).toHaveAttribute(
    "aria-invalid",
    "true",
  );
});
it("in-flight save disables duplicate submission", async () => {
  let resolve!: (result: api.CandidateContactCreated) => void;
  vi.mocked(api.createCandidateContact).mockReturnValue(
    new Promise((yes) => {
      resolve = yes;
    }),
  );
  render(<CandidateContactsPanel candidate={candidate} />);
  await fillAndSave();
  expect(api.createCandidateContact).toHaveBeenCalledTimes(1);
  expect(screen.getByRole("button", { name: "처리 중…" })).toBeDisabled();
  expect(screen.getByRole("button", { name: "취소" })).toBeDisabled();
  await act(async () =>
    resolve({
      contact,
      contactStatus: "BEFORE",
      lastContactDate: "2026-06-16",
    }),
  );
});
it("mobile branch requests twenty and loads more while keeping single contact registration", async () => {
  vi.stubGlobal("matchMedia", () => ({
    matches: true,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
  }));
  vi.mocked(api.getCandidateContacts)
    .mockResolvedValueOnce({ ...empty, size: 20, total: 2, items: [contact] })
    .mockResolvedValueOnce({
      ...empty,
      page: 2,
      size: 20,
      total: 2,
      items: [{ ...contact, sn: 2, memo: "추가 연락" }],
    });
  render(<CandidateContactsPanel candidate={candidate} />);
  await screen.findByTestId("candidate-contact-1");
  expect(api.getCandidateContacts).toHaveBeenCalledWith(
    candidate.id,
    1,
    20,
    expect.any(AbortSignal),
  );
  await userEvent.click(screen.getByRole("button", { name: "20개 더보기" }));
  await screen.findByTestId("candidate-contact-2");
  expect(screen.getAllByRole("article")).toHaveLength(2);
  expect(api.getCandidateContacts).toHaveBeenLastCalledWith(
    candidate.id,
    2,
    20,
    expect.any(AbortSignal),
  );
  await fillAndSave();
  expect(api.createCandidateContact).toHaveBeenCalledTimes(1);
});
it.each(["BEFORE", "DONE", "ITVW", "HOLD", "CTRT"] as const)(
  "keeps %s visually distinct with shared badge tokens",
  (status) => {
    const { container } = render(
      <CandidateContactStatusBadge status={status} />,
    );
    const tone = {
      BEFORE: "neutral",
      DONE: "info",
      ITVW: "warn",
      HOLD: "lock",
      CTRT: "ok",
    }[status];
    expect(container.querySelector(".sicost-badge")).toHaveClass(
      `sicost-tone-${tone}`,
    );
  },
);
