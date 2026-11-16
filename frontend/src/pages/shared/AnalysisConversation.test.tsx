import { render, screen, within, fireEvent } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, it, expect, vi } from "vitest";
import type { AnalysisResult } from "./analysisClient";
import { AnalysisConversation } from "./AnalysisConversation";
const state = vi.hoisted(() => ({
  initialize: vi.fn(),
  refresh: vi.fn(),
  view: vi.fn(),
  askAgain: vi.fn(),
  send: vi.fn(),
  setQuestion: vi.fn(),
  setError: vi.fn(),
  question: "",
  pending: false,
  progress: undefined,
  history: [],
  historyError: "",
  error: "",
  lastQuestion: "",
  snapshot: "",
  cursor: null,
  qnaId: "unit-result",
  result: undefined as AnalysisResult | undefined,
}));
vi.mock("./AnalysisAssistantContext", () => ({
  useAnalysisAssistant: () => state,
}));
function fixture(values: (string | null)[]): AnalysisResult {
  const scope = {
    targets: [],
    filters: [],
    fromMonth: "2026-11",
    toMonth: "2026-11",
    baseDate: "2026-11-16",
    baseMonth: "2026-11",
  };
  return {
    schemaVersion: "1",
    conclusion: "단위 테스트용 저장 결과",
    tables: [
      {
        tableId: "t",
        title: "테스트 표",
        periodLabel: "2026-11",
        metricName: "금액",
        unit: "원",
        sourceState: "PROVISIONAL",
        scenario: "BASE",
        columns: [
          { key: "name", label: "대상", valueType: "TEXT", unit: "" },
          { key: "amount", label: "금액", valueType: "DECIMAL", unit: "원" },
        ],
        rows: values.map((v, i) => ({
          rowKey: String(i),
          target: null,
          cells: [
            {
              columnKey: "name",
              value: `row-${String(i).padStart(2, "0")}`,
              reason: null,
            },
            {
              columnKey: "amount",
              value: v,
              reason: v === null ? "단가 미확인" : null,
            },
          ],
          reason: null,
        })),
        totalRows: values.length,
        totals: [{ columnKey: "amount", value: null, reason: "미확인 포함" }],
        sort: [{ columnKey: "name", direction: "ASC" }],
        displayPageSize: 50,
        emptyReason: null,
      },
    ],
    assumptions: [],
    unknownReasons: [],
    usedData: [
      {
        queryName: "Unit source",
        scope,
        sourceState: "PROVISIONAL",
        calculationBasis: "제어된 테스트 자료",
        referenceIds: ["unit-revision"],
      },
    ],
    nextActions: [],
    context: { screenId: "UI-26" },
    appliedScope: scope,
    status: "OK",
    reason: null,
    screenLinks: [],
    interpreter: {
      mode: "SERVER_RULE",
      modelName: null,
      notice: "외부 미사용",
    },
    dataAsOf: "2026-11-16T10:00:00+09:00",
  };
}
beforeEach(() => {
  vi.clearAllMocks();
  state.question = "";
  state.pending = false;
  state.result = undefined;
});
it("keeps decimal strings exact, distinguishes zero/null/negative and changes display units only", () => {
  state.result = fixture(["9007199254740993", "-1000", "0", null, "245000000"]);
  const saved = JSON.stringify(state.result);
  const { container } = render(
    <MemoryRouter>
      <AnalysisConversation />
    </MemoryRouter>,
  );
  const table = screen.getByRole("table");
  expect(within(table).getByText("9,007,199,254,740.993")).toBeInTheDocument();
  expect(within(table).getByText("−1")).toHaveClass("sicost-number-negative");
  expect(within(table).getByText("0")).toBeInTheDocument();
  expect(
    within(table).getByText("단가 미확인", { exact: false }),
  ).toBeInTheDocument();
  expect(within(table).getByText("전체 합계")).toBeInTheDocument();
  fireEvent.change(screen.getByLabelText("답변 금액 단위"), {
    target: { value: "원" },
  });
  expect(within(table).getByText("9,007,199,254,740,993")).toBeInTheDocument();
  expect(within(table).getByText("245,000,000")).toBeInTheDocument();
  fireEvent.change(screen.getByLabelText("답변 금액 단위"), { target: { value: "백만원" } });
  expect(within(table).getByText("245")).toBeInTheDocument();
  expect(JSON.stringify(state.result)).toBe(saved);
  expect(container.querySelectorAll("img")).toHaveLength(0);
});
it("paginates 51 saved rows without dropping the whole-result count or footer", () => {
  state.result = fixture(Array.from({ length: 51 }, () => "0"));
  render(
    <MemoryRouter>
      <AnalysisConversation />
    </MemoryRouter>,
  );
  let table = screen.getByRole("table");
  expect(within(table).getAllByRole("row")).toHaveLength(52);
  expect(screen.queryByText("row-50")).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "다음 페이지" }));
  table = screen.getByRole("table");
  expect(within(table).getByText("row-50")).toBeInTheDocument();
  expect(within(table).getByText("전체 합계")).toBeInTheDocument();
  expect(screen.getByText("51-51 / 51건")).toBeInTheDocument();
});

it("shows row and total unknown reasons alongside the affected values", () => {
  state.result = fixture([null, "0"]);
  state.result.tables[0].rows[0].reason = "행 전체의 계산 근거 부족";
  render(<MemoryRouter><AnalysisConversation /></MemoryRouter>);
  const table = screen.getByRole("table");
  expect(within(table).getByText("행 전체의 계산 근거 부족", { exact: false })).toBeInTheDocument();
  expect(within(table).getByText("미확인 포함", { exact: false })).toBeInTheDocument();
});

it("blocks blank/1001 character questions and locks submission while pending", () => {
  const { rerender } = render(<MemoryRouter><AnalysisConversation /></MemoryRouter>);
  expect(screen.getByRole("button", { name: "보내기" })).toBeDisabled();
  for (const question of ["   ", "가".repeat(1001)]) {
    state.question = question;
    rerender(<MemoryRouter><AnalysisConversation /></MemoryRouter>);
    expect(screen.getByRole("button", { name: "보내기" })).toBeDisabled();
    fireEvent.keyDown(screen.getByRole("textbox", { name: "질문" }), { key: "Enter" });
  }
  expect(state.send).not.toHaveBeenCalled();
  state.question = "가".repeat(1000);
  rerender(<MemoryRouter><AnalysisConversation /></MemoryRouter>);
  expect(screen.getByRole("button", { name: "보내기" })).toBeEnabled();
  state.pending = true;
  rerender(<MemoryRouter><AnalysisConversation /></MemoryRouter>);
  expect(screen.getByRole("button", { name: "처리 중…" })).toBeDisabled();
  fireEvent.keyDown(screen.getByRole("textbox", { name: "질문" }), { key: "Enter" });
  expect(state.send).not.toHaveBeenCalled();
});
it("sends Enter once and keeps Shift/IME Enter for editing", () => {
  state.question = "이번 달 매출";
  render(<MemoryRouter><AnalysisConversation /></MemoryRouter>);
  const input = screen.getByRole("textbox", { name: "질문" });
  fireEvent.keyDown(input, { key: "Enter", shiftKey: true });
  fireEvent.keyDown(input, { key: "Enter", isComposing: true });
  fireEvent.keyDown(input, { key: "Enter", keyCode: 229 });
  expect(state.send).not.toHaveBeenCalled();
  fireEvent.keyDown(input, { key: "Enter" });
  expect(state.send).toHaveBeenCalledTimes(1);
});

it.each([["명", "2.0", "−2.0"], ["%", "2.0", "−2.0"], ["%p", "2.0", "−2.0"], ["MM", "2.00", "−2.00"]])("preserves the server display scale for %s without recalculation", (unit, positive, negative) => {
  state.result = fixture(["2", "-2", "0", null]);
  state.result.tables[0].columns[1].unit = unit;
  render(<MemoryRouter><AnalysisConversation /></MemoryRouter>);
  const table = screen.getByRole("table");
  expect(within(table).getByText(positive)).toBeInTheDocument();
  expect(within(table).getByText(negative)).toBeInTheDocument();
  expect(within(table).getByText(unit === "MM" ? "0.00" : "0.0")).toBeInTheDocument();
  expect(within(table).getByText("단가 미확인", { exact: false }).closest("span")).toHaveTextContent("–");
});
