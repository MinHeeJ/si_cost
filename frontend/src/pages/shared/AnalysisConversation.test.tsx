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
beforeEach(() => vi.clearAllMocks());
it("keeps decimal strings exact, distinguishes zero/null/negative and changes display units only", () => {
  state.result = fixture(["9007199254740993", "-1000", "0", null]);
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
