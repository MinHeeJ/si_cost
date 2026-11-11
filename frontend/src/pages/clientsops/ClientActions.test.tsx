import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { ClientAccountPanel } from "./ClientAccountPanel";
import { ClientAssignmentsPanel } from "./ClientAssignmentsPanel";
import { ClientSelect } from "./ClientSelect";
import { ClientSingleStatus, ClientStatusToolbar } from "./ClientStatusToolbar";
import { useClientSelection } from "./useClientSelection";
import * as api from "./clientActionsApi";
vi.mock("./clientActionsApi", async (original) => ({
  ...(await original<typeof api>()),
  getClientOptions: vi.fn(),
  getClientAccount: vi.fn(),
  getClientAssignments: vi.fn(),
  getClientSelection: vi.fn(),
  saveClientBulkStatus: vi.fn(),
  notifyClientsCommitted: vi.fn(),
}));
const account: api.ClientAccount = {
  id: "C-901",
  bankCode: "BANK",
  bankName: "은행",
  accountNumber: "001-23-45678",
  depositor: "예금주",
};
const row = {
  id: "C-901",
  name: "검증 거래처",
  revision: "current",
  useYn: "Y" as const,
};
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api.getClientAccount).mockResolvedValue(account);
  vi.mocked(api.getClientOptions).mockResolvedValue({ items: [] });
  vi.mocked(api.getClientAssignments).mockResolvedValue({
    items: [],
    total: 0,
    page: 1,
    size: 50,
    basisDate: "2026-06-16",
  });
  vi.mocked(api.getClientSelection).mockResolvedValue({ items: [], total: 0 });
  vi.mocked(api.saveClientBulkStatus).mockResolvedValue({
    items: [{ id: row.id, name: row.name, result: "applied" }],
    applied: 1,
    noChange: 0,
    rejected: 0,
  });
});
it("account starts masked and reveals only after explicit request, preserving zeros and separators", async () => {
  render(
    <ClientAccountPanel clientId={row.id} maskedAccount="은행 ****5678" />,
  );
  expect(screen.getByText("은행 ****5678")).toBeInTheDocument();
  expect(api.getClientAccount).not.toHaveBeenCalled();
  await userEvent.click(
    screen.getByRole("button", { name: "전체 계좌번호 보기" }),
  );
  expect(await screen.findByText("은행 001-23-45678")).toBeInTheDocument();
  expect(api.getClientAccount).toHaveBeenCalledWith(
    row.id,
    expect.any(AbortSignal),
  );
  await userEvent.click(screen.getByRole("button", { name: "숨기기" }));
  expect(screen.queryByText(/001-23-45678/)).not.toBeInTheDocument();
  await userEvent.click(
    screen.getByRole("button", { name: "전체 계좌번호 보기" }),
  );
  await screen.findByText(/001-23-45678/);
  expect(api.getClientAccount).toHaveBeenCalledTimes(2);
});
it("account close and reopen destroy plaintext rather than keeping a shared cache", async () => {
  const view = render(
    <ClientAccountPanel clientId={row.id} maskedAccount="은행 ****5678" />,
  );
  await userEvent.click(
    screen.getByRole("button", { name: "전체 계좌번호 보기" }),
  );
  await screen.findByText(/001-23-45678/);
  view.rerender(
    <ClientAccountPanel
      clientId={row.id}
      maskedAccount="은행 ****5678"
      open={false}
    />,
  );
  expect(screen.queryByText(/001-23-45678/)).not.toBeInTheDocument();
  view.rerender(
    <ClientAccountPanel clientId={row.id} maskedAccount="은행 ****5678" />,
  );
  expect(screen.getByText("은행 ****5678")).toBeInTheDocument();
  expect(api.getClientAccount).toHaveBeenCalledTimes(1);
});
it("switching client or retained workspace deactivation clears plaintext", async () => {
  const view = render(
    <ClientAccountPanel clientId={row.id} maskedAccount="은행 ****5678" />,
  );
  await userEvent.click(
    screen.getByRole("button", { name: "전체 계좌번호 보기" }),
  );
  await screen.findByText(/001-23-45678/);
  view.rerender(
    <ClientAccountPanel clientId="C-902" maskedAccount="다른 은행 ****1234" />,
  );
  expect(screen.queryByText(/001-23-45678/)).not.toBeInTheDocument();
  view.rerender(
    <ClientAccountPanel
      clientId={row.id}
      maskedAccount="은행 ****5678"
      active={false}
    />,
  );
  view.rerender(
    <ClientAccountPanel clientId={row.id} maskedAccount="은행 ****5678" />,
  );
  expect(screen.queryByText(/001-23-45678/)).not.toBeInTheDocument();
});
it("late reveal after hide cannot restore a secret", async () => {
  let resolve!: (value: api.ClientAccount) => void;
  vi.mocked(api.getClientAccount).mockReturnValue(
    new Promise((yes) => {
      resolve = yes;
    }),
  );
  render(
    <ClientAccountPanel clientId={row.id} maskedAccount="은행 ****5678" />,
  );
  await userEvent.click(
    screen.getByRole("button", { name: "전체 계좌번호 보기" }),
  );
  await userEvent.click(screen.getByRole("button", { name: "숨기기" }));
  await act(async () => resolve(account));
  expect(screen.queryByText(/001-23-45678/)).not.toBeInTheDocument();
});
it("failed ACCT_VIEW never shows the number, success notice, or automatic retry", async () => {
  vi.mocked(api.getClientAccount).mockRejectedValue(
    new Error("접근기록을 저장하지 못했습니다."),
  );
  render(
    <ClientAccountPanel clientId={row.id} maskedAccount="은행 ****5678" />,
  );
  await userEvent.click(
    screen.getByRole("button", { name: "전체 계좌번호 보기" }),
  );
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "접근기록을 저장하지 못했습니다.",
  );
  expect(screen.queryByText(/001-23-45678/)).not.toBeInTheDocument();
  expect(api.getClientAccount).toHaveBeenCalledTimes(1);
});
it("missing account is readonly and cannot request a plaintext view", () => {
  render(<ClientAccountPanel clientId={row.id} maskedAccount={null} />);
  expect(
    screen.getByRole("button", { name: "전체 계좌번호 보기" }),
  ).toBeDisabled();
});
it("assignment empty state and supplied basis date render without fabricated rows", async () => {
  render(<ClientAssignmentsPanel clientId={row.id} basisDate="2026-06-16" />);
  expect(
    await screen.findByText("현재 투입 내역이 없습니다."),
  ).toBeInTheDocument();
  expect(
    screen.getByRole("heading", { name: "투입 내역 (0)" }),
  ).toBeInTheDocument();
});
it("assignment fields are read-only and project/management ordering is server-owned", async () => {
  vi.mocked(api.getClientAssignments).mockResolvedValue({
    items: [
      {
        assignmentId: "A-901",
        projectId: "P-901",
        projectName: "프로젝트",
        managementNo: "M-901",
        developerName: "개발자",
        jobCode: "DEV",
        jobName: "개발",
        inputBeginDate: "2026-06-01",
        inputEndDate: "2026-06-30",
      },
    ],
    total: 1,
    page: 1,
    size: 50,
    basisDate: "2026-06-16",
  });
  render(<ClientAssignmentsPanel clientId={row.id} basisDate="2026-06-16" />);
  await screen.findByText("M-901");
  expect(screen.getByText("P-901")).toBeInTheDocument();
  expect(screen.getByText("2026-06-01 ~ 2026-06-30")).toBeInTheDocument();
  expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
});
it("basis date rollover requests parent list refresh", async () => {
  const refresh = vi.fn();
  render(
    <ClientAssignmentsPanel
      clientId={row.id}
      basisDate="2026-06-15"
      onBasisDateChanged={refresh}
    />,
  );
  await screen.findByText("현재 투입 내역이 없습니다.");
  expect(refresh).toHaveBeenCalledTimes(1);
});
it("assignment errors require explicit retry and are not an empty list", async () => {
  vi.mocked(api.getClientAssignments).mockRejectedValueOnce(
    new Error("투입 조회 실패"),
  );
  render(<ClientAssignmentsPanel clientId={row.id} basisDate="2026-06-16" />);
  expect(await screen.findByRole("alert")).toHaveTextContent("투입 조회 실패");
  expect(
    screen.queryByText("현재 투입 내역이 없습니다."),
  ).not.toBeInTheDocument();
  expect(api.getClientAssignments).toHaveBeenCalledTimes(1);
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  await screen.findByText("현재 투입 내역이 없습니다.");
  expect(api.getClientAssignments).toHaveBeenCalledTimes(2);
});
it("options requery on every open and distinguish same names by ID", async () => {
  vi.mocked(api.getClientOptions).mockResolvedValueOnce({
    items: [
      { id: "C-901", name: "동명", type: "CUST" },
      { id: "C-902", name: "동명", type: "BOTH" },
    ],
  });
  const change = vi.fn();
  render(<ClientSelect purpose="CUSTOMER" value="" onChange={change} />);
  expect(api.getClientOptions).not.toHaveBeenCalled();
  await userEvent.click(screen.getByRole("button", { name: /고객사:/ }));
  await screen.findByRole("option", { name: "동명 (C-902)" });
  await userEvent.selectOptions(screen.getByRole("combobox"), "C-902");
  expect(change).toHaveBeenCalledWith("C-902");
  await userEvent.click(screen.getByRole("button", { name: /고객사:/ }));
  await userEvent.click(screen.getByRole("button", { name: /고객사:/ }));
  await screen.findByText("선택할 수 있는 고객사가 없습니다.");
  expect(api.getClientOptions).toHaveBeenCalledTimes(2);
});
it("inactive stored client is displayed but cannot be selected as new option", async () => {
  vi.mocked(api.getClientOptions).mockResolvedValue({
    items: [{ id: "C-902", name: "신규", type: "VNDR" }],
  });
  render(
    <ClientSelect
      purpose="VENDOR"
      value="C-901"
      selectedLabel="기존 중지 거래처"
      onChange={vi.fn()}
    />,
  );
  await userEvent.click(screen.getByRole("button", { name: /협력사:/ }));
  expect(
    await screen.findByRole("option", { name: "기존 중지 거래처" }),
  ).toBeDisabled();
});
it("option failure is not empty and retries only by action", async () => {
  vi.mocked(api.getClientOptions).mockRejectedValueOnce(
    new Error("거래처 조회 실패"),
  );
  render(<ClientSelect purpose="VENDOR" value="" onChange={vi.fn()} />);
  await userEvent.click(screen.getByRole("button", { name: /협력사:/ }));
  await screen.findByRole("alert");
  expect(
    screen.queryByText("선택할 수 있는 협력사가 없습니다."),
  ).not.toBeInTheDocument();
  expect(api.getClientOptions).toHaveBeenCalledTimes(1);
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  await screen.findByText("선택할 수 있는 협력사가 없습니다.");
});
it("disabled client selector does not read options", () => {
  render(
    <ClientSelect purpose="VENDOR" value="C-901" onChange={vi.fn()} disabled />,
  );
  expect(screen.getByRole("button")).toBeDisabled();
  expect(api.getClientOptions).not.toHaveBeenCalled();
});
function SelectionHost({
  filters = { useYn: "Y" as const },
  size = 50,
  page = 1,
  committed = vi.fn(),
  refresh = vi.fn(),
}: {
  filters?: api.ClientFilters;
  size?: number;
  page?: number;
  committed?: () => Promise<void> | void;
  refresh?: () => Promise<void> | void;
}) {
  const selection = useClientSelection(filters, size);
  return (
    <>
      <ClientStatusToolbar
        selection={selection}
        rows={[row]}
        total={123}
        onCommitted={committed}
        onRefresh={refresh}
      />
      <span>페이지 {page}</span>
      <input
        type="checkbox"
        aria-label="거래처 선택"
        checked={selection.items.has(row.id)}
        onChange={(event) => selection.toggle(row, event.target.checked)}
      />
    </>
  );
}
it("full 123 snapshot minus one retains 122 targets across page changes", async () => {
  const items = Array.from({ length: 123 }, (_, i) => ({
    id: `C-${901 + i}`,
    revision: `revision-${i}`,
  }));
  vi.mocked(api.getClientSelection).mockResolvedValue({ items, total: 123 });
  const view = render(<SelectionHost />);
  await userEvent.click(
    screen.getByRole("button", { name: "조회된 123건 모두 선택" }),
  );
  await screen.findByText("선택 123건");
  await userEvent.click(screen.getByRole("checkbox", { name: "거래처 선택" }));
  expect(screen.getByText("선택 122건")).toBeInTheDocument();
  view.rerender(<SelectionHost page={2} />);
  expect(screen.getByText("선택 122건")).toBeInTheDocument();
  view.rerender(<SelectionHost page={1} />);
  expect(screen.getByText("선택 122건")).toBeInTheDocument();
});
it.each(["filter", "sort", "size"])(
  "%s changes clear selection synchronously",
  async (kind) => {
    const view = render(<SelectionHost />);
    await userEvent.click(screen.getByRole("checkbox"));
    expect(screen.getByText("선택 1건")).toBeInTheDocument();
    view.rerender(
      <SelectionHost
        filters={
          kind === "filter"
            ? { useYn: "N" }
            : kind === "sort"
              ? { useYn: "Y", sort: "businessNo" }
              : { useYn: "Y" }
        }
        size={kind === "size" ? 20 : 50}
      />,
    );
    expect(screen.getByText("선택 0건")).toBeInTheDocument();
  },
);
it("single status uses approved bulk endpoint after confirmation, cancellation never writes", async () => {
  const committed = vi.fn();
  render(
    <ClientSingleStatus
      row={row}
      onCommitted={committed}
      onRefresh={vi.fn()}
    />,
  );
  await userEvent.click(screen.getByRole("button", { name: "사용 중지" }));
  await userEvent.click(screen.getByRole("button", { name: "취소" }));
  expect(api.saveClientBulkStatus).not.toHaveBeenCalled();
  await userEvent.click(screen.getByRole("button", { name: "사용 중지" }));
  await userEvent.click(screen.getByRole("button", { name: "확인" }));
  expect(
    await screen.findByText("적용 1건 · 변경 없음 0건 · 거부 0건"),
  ).toBeInTheDocument();
  expect(api.saveClientBulkStatus).toHaveBeenCalledWith("N", [
    { id: row.id, expectedRevision: row.revision },
  ]);
  expect(committed).toHaveBeenCalledTimes(1);
});
it("stopped row offers resume only", () => {
  render(
    <ClientSingleStatus
      row={{ ...row, useYn: "N" }}
      onCommitted={vi.fn()}
      onRefresh={vi.fn()}
    />,
  );
  expect(screen.getByRole("button", { name: "다시 사용" })).toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: "사용 중지" }),
  ).not.toBeInTheDocument();
});
it("network failure never resubmits and requires manual reload before new revision request", async () => {
  vi.mocked(api.saveClientBulkStatus).mockRejectedValue(
    new Error("통신이 끊겼습니다."),
  );
  const refresh = vi.fn();
  render(
    <ClientSingleStatus row={row} onCommitted={vi.fn()} onRefresh={refresh} />,
  );
  await userEvent.click(screen.getByRole("button", { name: "사용 중지" }));
  await userEvent.click(screen.getByRole("button", { name: "확인" }));
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "통신이 끊겼습니다.",
  );
  expect(screen.getByRole("button", { name: "사용 중지" })).toBeDisabled();
  expect(api.saveClientBulkStatus).toHaveBeenCalledTimes(1);
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  expect(refresh).toHaveBeenCalledTimes(1);
  expect(api.saveClientBulkStatus).toHaveBeenCalledTimes(1);
});
it("save success plus refresh failure offers requery, never another save", async () => {
  render(
    <ClientSingleStatus
      row={row}
      onCommitted={() => Promise.reject(new Error("reload failed"))}
      onRefresh={vi.fn()}
    />,
  );
  await userEvent.click(screen.getByRole("button", { name: "사용 중지" }));
  await userEvent.click(screen.getByRole("button", { name: "확인" }));
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "저장됐지만 다시 불러오지 못했습니다",
  );
  await userEvent.click(screen.getByRole("button", { name: "재조회" }));
  expect(api.saveClientBulkStatus).toHaveBeenCalledTimes(1);
});
it("mixed results show each error and distinct state colors without reporting all applied", async () => {
  vi.mocked(api.saveClientBulkStatus).mockResolvedValue({
    applied: 0,
    noChange: 0,
    rejected: 1,
    items: [
      {
        id: row.id,
        name: row.name,
        result: "rejected",
        error: {
          success: false,
          meta: {},
          code: "REFERENCE_IN_USE",
          message: "소속 개발자가 있습니다.",
          requestId: "request",
          details: { references: [{ type: "DEVELOPER", count: 1 }] },
        },
      },
    ],
  });
  render(
    <ClientSingleStatus row={row} onCommitted={vi.fn()} onRefresh={vi.fn()} />,
  );
  await userEvent.click(screen.getByRole("button", { name: "사용 중지" }));
  await userEvent.click(screen.getByRole("button", { name: "확인" }));
  expect(await screen.findByText("거부")).toHaveClass("client-result-rejected");
  expect(screen.getByText("개발자 1건")).toBeInTheDocument();
  expect(api.notifyClientsCommitted).not.toHaveBeenCalled();
});
it("untrustworthy bulk totals never produce a success view", async () => {
  vi.mocked(api.saveClientBulkStatus).mockResolvedValue({
    applied: 2,
    noChange: 0,
    rejected: 0,
    items: [],
  });
  render(
    <ClientSingleStatus row={row} onCommitted={vi.fn()} onRefresh={vi.fn()} />,
  );
  await userEvent.click(screen.getByRole("button", { name: "사용 중지" }));
  await userEvent.click(screen.getByRole("button", { name: "확인" }));
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "처리 결과를 확인할 수 없습니다.",
  );
  expect(api.notifyClientsCommitted).not.toHaveBeenCalled();
});
it("late all-selection cannot restore a prior filter snapshot", async () => {
  let resolve!: (value: api.ClientSelection) => void;
  vi.mocked(api.getClientSelection).mockReturnValue(
    new Promise((yes) => {
      resolve = yes;
    }),
  );
  const view = render(<SelectionHost />);
  await userEvent.click(
    screen.getByRole("button", { name: "조회된 123건 모두 선택" }),
  );
  view.rerender(<SelectionHost filters={{ useYn: "N" }} />);
  await act(async () => resolve({ items: [row], total: 1 }));
  expect(screen.getByText("선택 0건")).toBeInTheDocument();
});
it("selection failure does not falsely select every result", async () => {
  vi.mocked(api.getClientSelection).mockRejectedValue(
    new Error("전체 선택 실패"),
  );
  render(<SelectionHost />);
  await userEvent.click(
    screen.getByRole("button", { name: "조회된 123건 모두 선택" }),
  );
  await waitFor(() =>
    expect(screen.getByRole("alert")).toHaveTextContent("전체 선택 실패"),
  );
  expect(screen.getByText("선택 0건")).toBeInTheDocument();
});
