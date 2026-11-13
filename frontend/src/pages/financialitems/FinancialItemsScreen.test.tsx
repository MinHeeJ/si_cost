import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { FinancialItemsEditor } from "./FinancialItemsScreen";
import { financialEditorFixture } from "./financialFixtures";
import type { FinancialEditor } from "./financialApi";

const fetcher = vi.fn<typeof fetch>();
let data: FinancialEditor;
const response = (value: unknown) =>
  new Response(JSON.stringify({ success: true, meta: {}, data: value }));
const failure = (
  message: string,
  code = "VALIDATION_ERROR",
  details?: unknown,
) =>
  new Response(
    JSON.stringify({
      success: false,
      meta: {},
      code,
      message,
      requestId: "finance-trace",
      details,
    }),
    { status: 400 },
  );
beforeEach(() => {
  vi.resetAllMocks();
  vi.stubGlobal("fetch", fetcher);
  data = financialEditorFixture();
  fetcher.mockImplementation(async (url, options) => {
    const path = String(url);
    if (path.startsWith("/api/codes/"))
      return response({ items: [{ code: "RENT", name: "임대료" }] });
    if (path.startsWith("/api/financial-items/year-status"))
      return response({
        year: data.month.slice(0, 4),
        months: Array.from({ length: 12 }, (_, index) => {
          const month = `${data.month.slice(0, 4)}-${String(index + 1).padStart(2, "0")}`;
          return {
            month,
            closeStatus: "WORK",
            itemCount: month <= data.today.slice(0, 7) ? 0 : null,
            selectable: month <= data.today.slice(0, 7),
            editable: true,
          };
        }),
      });
    if (path === "/api/corporate-taxes/initialize")
      return response({ changed: false });
    if (path === "/api/financial-items" && options?.method === "PUT")
      return response({
        changed: true,
        revision: "next",
        message: "저장했습니다.",
        editor: data,
        createdCards: [],
      });
    if (path.startsWith("/api/financial-items")) {
      const month = new URL(path, "https://test.invalid").searchParams.get(
        "month",
      );
      return response(month ? { ...data, month } : data);
    }
    return response({ items: [] });
  });
});
afterEach(() => vi.unstubAllGlobals());
async function ready() {
  render(<FinancialItemsEditor />);
  await screen.findByTestId("financial-items-panel");
}
function writes() {
  return fetcher.mock.calls.filter(
    ([url, options]) =>
      String(url) === "/api/financial-items" && options?.method === "PUT",
  );
}
async function addItem() {
  await userEvent.click(screen.getByTestId("financial-items-add"));
  await userEvent.type(
    screen.getByTestId("financial-items-new-1-item-name"),
    "임대료",
  );
  await userEvent.type(
    screen.getByTestId("financial-items-new-1-amount"),
    "1,000 원",
  );
}

it("loads server current month without preferences and renders empty states, shared tables and future disabled", async () => {
  await ready();
  expect(
    fetcher.mock.calls.some(([url]) => String(url) === "/api/financial-items"),
  ).toBe(true);
  expect(
    fetcher.mock.calls.some(([url]) => String(url).includes("preferences")),
  ).toBe(false);
  expect(screen.getByLabelText("선택월")).toHaveValue("2026-07");
  expect(screen.getByTestId("financial-month-2026-08")).toBeDisabled();
  expect(screen.getAllByText("등록된 항목이 없습니다.").length).toBeGreaterThan(
    0,
  );
  expect(screen.getByTestId("financial-save")).toBeDisabled();
  expect(screen.getByTestId("financial-adjustments-add")).toBeDisabled();
});
it("retains both tab drafts and sends exactly one atomic add/update/delete request without audit fields", async () => {
  await ready();
  await addItem();
  await userEvent.click(screen.getByTestId("financial-tab-cash"));
  await userEvent.click(screen.getByTestId("financial-loans-add"));
  await userEvent.type(screen.getByTestId("financial-loans-new-2-amount"), "2");
  await userEvent.click(screen.getByTestId("financial-tab-financial"));
  expect(screen.getByTestId("financial-items-new-1-item-name")).toHaveValue(
    "임대료",
  );
  await userEvent.click(screen.getByTestId("financial-save"));
  await waitFor(() => expect(writes()).toHaveLength(1));
  const body = JSON.parse(String(writes()[0][1]?.body));
  expect(body.expectedRevision).toBe("revision-current");
  expect(body.items.add[0]).toMatchObject({
    amount: "1000",
    itemName: "임대료",
    sgaItemCode: "RENT",
  });
  expect(body.loans.add[0]).toMatchObject({ amount: "2", direction: "IN" });
  expect(JSON.stringify(body)).not.toMatch(/regUserId|audit|autoFill|uploadId/);
  await screen.findByText("저장했습니다.");
});
it("cancel restores saved rows and does not regenerate automatic drafts or send writes", async () => {
  data = financialEditorFixture({
    automaticDrafts: [
      {
        sourceMonth: "2026-06",
        sourceItemSn: "2",
        finClass: "SGA",
        itemName: "반복",
        amount: "10",
        vatAmount: "0",
        ioDate: null,
        sgaItemCode: null,
        repeat: "Y",
        remark: null,
        autoFill: "Y",
        errors: [],
      },
    ],
  });
  await ready();
  expect(screen.getByTestId("financial-items-repeat-0-item-name")).toHaveValue(
    "반복",
  );
  await userEvent.click(screen.getByTestId("financial-cancel"));
  expect(
    screen.queryByTestId("financial-items-repeat-0-item-name"),
  ).not.toBeInTheDocument();
  expect(writes()).toHaveLength(0);
  expect(screen.getByTestId("financial-save")).toBeDisabled();
});
it("read-only close disables inputs, upload, all add and save actions without inventing a role denial", async () => {
  data = financialEditorFixture({
    editable: false,
    closeStatus: "CLOSE",
    readOnlyReason: "마감된 월은 읽기 전용입니다.",
    items: [
      {
        itemSn: "1",
        finClass: "SGA",
        itemName: "경비",
        amount: "10",
        vatAmount: "0",
        ioDate: null,
        repeat: "N",
        sgaItemCode: null,
        remark: null,
      },
    ],
  });
  await ready();
  expect(screen.getByTestId("financial-read-only")).toHaveTextContent(
    "마감된 월",
  );
  expect(screen.getByTestId("financial-items-1-amount")).toBeDisabled();
  expect(screen.getByTestId("financial-upload-button")).toBeDisabled();
  expect(screen.getByTestId("financial-items-add")).toBeDisabled();
  expect(screen.getByTestId("financial-save")).toBeDisabled();
  expect(
    fetcher.mock.calls.some(([url]) => String(url).includes("initialize")),
  ).toBe(false);
});
it("initial read failure exposes retry and no fabricated rows", async () => {
  const implementation = fetcher.getMockImplementation()!;
  let failed = false;
  fetcher.mockImplementation(async (url, options) => {
    if (!failed && String(url) === "/api/financial-items") {
      failed = true;
      return failure("조회 실패");
    }
    return implementation(url, options);
  });
  render(<FinancialItemsEditor />);
  expect(await screen.findByRole("alert")).toHaveTextContent("조회 실패");
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  await screen.findByTestId("financial-items-panel");
  expect(writes()).toHaveLength(0);
});
it("invalid decimals and missing required fields retain drafts without sending a write", async () => {
  await ready();
  await userEvent.click(screen.getByTestId("financial-items-add"));
  await userEvent.type(
    screen.getByTestId("financial-items-new-1-amount"),
    "1e3",
  );
  await userEvent.click(screen.getByTestId("financial-save"));
  expect(await screen.findByRole("alert")).toHaveTextContent("원 정수");
  expect(writes()).toHaveLength(0);
  expect(screen.getByTestId("financial-items-new-1-amount")).toHaveValue("1e3");
});
it("hidden-tab server row errors reveal that tab and keep all inputs", async () => {
  await ready();
  await addItem();
  const implementation = fetcher.getMockImplementation()!;
  fetcher.mockImplementation(async (url, options) =>
    options?.method === "PUT"
      ? failure("대여금 오류", "VALIDATION_ERROR", {
          financial: {
            violations: [
              {
                tab: "CARD_TRANSFER_LOAN",
                collection: "loans",
                rowId: "row",
                field: "amount",
                message: "필수 금액",
              },
            ],
          },
        })
      : implementation(url, options),
  );
  await userEvent.click(screen.getByTestId("financial-save"));
  await waitFor(() =>
    expect(screen.getByTestId("financial-tab-cash")).toHaveAttribute(
      "aria-pressed",
      "true",
    ),
  );
  expect(
    screen
      .getAllByRole("alert")
      .some((node) => node.textContent?.includes("finance-trace")),
  ).toBe(true);
  await userEvent.click(screen.getByTestId("financial-tab-financial"));
  expect(screen.getByTestId("financial-items-new-1-amount")).toHaveValue(
    "1,000 원",
  );
});
it("period movement offers stay, discard and save, with failed save leaving period unchanged", async () => {
  await ready();
  await addItem();
  await userEvent.click(screen.getByTestId("financial-month-2026-06"));
  expect(await screen.findByRole("dialog")).toHaveTextContent(
    "저장 안 한 내용이 있습니다",
  );
  await userEvent.click(screen.getByRole("button", { name: "계속 편집" }));
  expect(screen.getByLabelText("선택월")).toHaveValue("2026-07");
  const implementation = fetcher.getMockImplementation()!;
  fetcher.mockImplementation(async (url, options) =>
    options?.method === "PUT"
      ? failure("저장 실패")
      : implementation(url, options),
  );
  await userEvent.click(screen.getByTestId("financial-month-2026-06"));
  await userEvent.click(
    within(screen.getByRole("dialog")).getByRole("button", {
      name: "저장 후 이동",
    }),
  );
  await within(screen.getByRole("dialog")).findByText("저장 실패");
  expect(screen.getByLabelText("선택월")).toHaveValue("2026-07");
  await userEvent.click(screen.getByTestId("financial-navigation-discard"));
  await waitFor(() =>
    expect(screen.getByLabelText("선택월")).toHaveValue("2026-06"),
  );
});
it("shell navigation and tab-close events are deferred until a successful resolution", async () => {
  await ready();
  await addItem();
  const proceed = vi.fn();
  let allowed = true;
  act(() => {
    allowed = window.dispatchEvent(
      new CustomEvent("sicost:screen-navigation-request", {
        cancelable: true,
        detail: { screenId: "UI-10", label: "작업 탭 닫기", proceed },
      }),
    );
  });
  expect(allowed).toBe(false);
  expect(proceed).not.toHaveBeenCalled();
  await userEvent.click(screen.getByTestId("financial-navigation-discard"));
  await waitFor(() => expect(proceed).toHaveBeenCalledTimes(1));
  expect(writes()).toHaveLength(0);
});
it("native beforeunload uses the browser warning only for actual changes", async () => {
  await ready();
  const clean = new Event("beforeunload", { cancelable: true });
  window.dispatchEvent(clean);
  expect(clean.defaultPrevented).toBe(false);
  await addItem();
  const dirty = new Event("beforeunload", { cancelable: true });
  window.dispatchEvent(dirty);
  expect(dirty.defaultPrevented).toBe(true);
});
it("card same sum direct input uses MANUAL, and explicit AUTO removes the amount property", async () => {
  data = financialEditorFixture({
    cards: [
      { cardId: "K-01", cardName: "법인카드", last4: "0001", useYn: "Y" },
    ],
    cardPayments: [{ cardId: "K-01", amount: "10" }],
    cardMonth: {
      exists: true,
      cardSumAmount: "10",
      cashAmount: "10",
      editYn: "N",
      difference: "0",
    },
  });
  await ready();
  await userEvent.click(screen.getByTestId("financial-tab-cash"));
  fireEvent.change(screen.getByTestId("financial-card-cash-override"), {
    target: { value: "010" },
  });
  await userEvent.click(screen.getByTestId("financial-save"));
  await waitFor(() => expect(writes()).toHaveLength(1));
  expect(JSON.parse(String(writes()[0][1]?.body)).cards.cashOverride).toEqual({
    mode: "MANUAL",
    amount: "10",
  });
  await userEvent.click(screen.getByTestId("financial-card-auto"));
  await userEvent.click(screen.getByTestId("financial-save"));
  await waitFor(() => expect(writes()).toHaveLength(2));
  expect(JSON.parse(String(writes()[1][1]?.body)).cards.cashOverride).toEqual({
    mode: "AUTO",
  });
});
it("stopped cards hide zero rows by default, show existing positive and restore without changing historical amounts", async () => {
  data = financialEditorFixture({
    cards: [
      { cardId: "K-01", cardName: "숨은 카드", last4: "0001", useYn: "N" },
      { cardId: "K-02", cardName: "기존 양수", last4: "0002", useYn: "N" },
    ],
    cardPayments: [{ cardId: "K-02", amount: "10" }],
  });
  await ready();
  await userEvent.click(screen.getByTestId("financial-tab-cash"));
  expect(screen.queryByText("숨은 카드")).not.toBeInTheDocument();
  expect(screen.getByText("기존 양수")).toBeInTheDocument();
  await userEvent.click(screen.getByTestId("financial-show-stopped"));
  await userEvent.click(screen.getByTestId("financial-card-K-01-state"));
  await userEvent.click(screen.getByTestId("financial-save"));
  await waitFor(() => expect(writes()).toHaveLength(1));
  expect(JSON.parse(String(writes()[0][1]?.body)).cards).toMatchObject({
    states: [{ cardId: "K-01", useYn: "Y" }],
    payments: [],
  });
});
it("released adjustment target is visible but immutable except explicit delete", async () => {
  data = financialEditorFixture({
    adjustments: [
      {
        adjustmentId: "1",
        targetMonth: "2026-06",
        itemCode: "COST",
        amount: "-10",
        reason: "차액",
      },
    ],
  });
  await ready();
  expect(
    screen.getByTestId("financial-adjustments-1-target-month"),
  ).toBeDisabled();
  expect(screen.getByTestId("financial-adjustments-1-amount")).toBeDisabled();
  expect(screen.getByTestId("financial-adjustments-1-delete")).toBeEnabled();
  await userEvent.click(screen.getByTestId("financial-adjustments-1-delete"));
  await userEvent.click(screen.getByTestId("financial-save"));
  await waitFor(() => expect(writes()).toHaveLength(1));
  expect(
    JSON.parse(String(writes()[0][1]?.body)).adjustments.deleteIds,
  ).toEqual(["1"]);
});
it("refresh-only failure after commit never retries a write", async () => {
  await ready();
  await addItem();
  const implementation = fetcher.getMockImplementation()!;
  let committed = false;
  fetcher.mockImplementation(async (url, options) => {
    if (options?.method === "PUT") {
      committed = true;
      return implementation(url, options);
    }
    if (committed && String(url).startsWith("/api/financial-items?"))
      return failure("再조회 실패");
    return implementation(url, options);
  });
  await userEvent.click(screen.getByTestId("financial-save"));
  await screen.findByTestId("financial-refresh-only");
  await userEvent.click(screen.getByTestId("financial-refresh-only"));
  expect(writes()).toHaveLength(1);
});
it("confirmed tax rows have no editable schedule and status badges retain distinct colours", async () => {
  data = financialEditorFixture({
    taxPayments: [
      {
        year: "2026",
        payType: "MID",
        dueDate: "2026-07-01",
        paidDate: "2026-07-02",
        payAmount: "0",
        payYn: "Y",
        displayAmount: "0",
        automaticAmount: "0",
        manuallyEdited: true,
        status: "납부",
        confirmationRevision: "payment",
        audit: {
          regUserId: "admin",
          regDttm: "2026-07-01T09:00:00",
          modUserId: null,
          modDttm: null,
        },
      },
    ],
  });
  await ready();
  expect(
    screen.queryByTestId("financial-tax-2026-mid-amount"),
  ).not.toBeInTheDocument();
  expect(
    screen.queryByTestId("financial-tax-2026-mid-confirm"),
  ).not.toBeInTheDocument();
  expect(screen.getByText("납부")).toHaveClass("corporate-tax-ok");
});
