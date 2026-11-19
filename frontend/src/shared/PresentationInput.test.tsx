import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { useState } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { codeName, CodeName } from "./codeNames";
import { NumericInput } from "./NumericInput";
import { Dialog } from "./Dialog";
import { formatSummaryAmount } from "./format";

vi.mock("../pages/shared/sharedApi", () => ({
  getSharedCodes: vi.fn(async () => ({
    items: [{ code: "NEW", name: "새로 추가한 직무" }],
  })),
}));
afterEach(() => vi.restoreAllMocks());

describe("통합1 공통 표시와 입력 부품", () => {
  it.each([
    ["100000000", "1.0억 원"],
    ["345000000", "3.5억 원"],
    ["99994999", "9,999만 원"],
    ["99995000", "10,000만 원"],
    ["15000", "2만 원"],
    ["0", "0만 원"],
    ["-345000000", "−3.5억 원"],
    [null, "–"],
    [undefined, "–"],
  ])("요약 금액 %s", (value, expected) =>
    expect(formatSummaryAmount(value)).toBe(expected),
  );
  it("API가 추가한 코드 이름을 그대로 표시하고 없는 코드는 경고한다", async () => {
    render(<CodeName group="JOB" value="NEW" />);
    expect(await screen.findByText("새로 추가한 직무")).toBeInTheDocument();
    const warn = vi.spyOn(console, "warn").mockImplementation(() => {});
    expect(codeName({ JOB: [] }, "JOB", "MISSING")).toBe("–");
    expect(warn).toHaveBeenCalledOnce();
  });
  it.each([
    ["amount", "12a,3b4", "12,34"],
    ["decimal", "0.a5.2", "0.52"],
    ["decimal", "5x0", "50"],
    ["integer", "1a.2", "12"],
  ] as const)(
    "%s는 숫자 아닌 글자를 즉시 제거한다",
    (kind, input, expected) => {
      function Example() {
        const [value, setValue] = useState("");
        return (
          <NumericInput
            data-testid="number-input"
            aria-label="숫자"
            kind={kind}
            value={value}
            onChange={(e) => setValue(e.target.value)}
          />
        );
      }
      render(<Example />);
      fireEvent.change(screen.getByLabelText("숫자"), {
        target: { value: input },
      });
      expect(screen.getByLabelText("숫자")).toHaveValue(expected);
    },
  );
  it("위험 실행 버튼과 첫 오류 칸 포커스/스크롤을 제공한다", async () => {
    const scroll = vi.fn();
    const { rerender } = render(
      <Dialog
        open
        title="삭제할까요?"
        danger
        confirmLabel="삭제"
        onConfirm={() => {}}
        onCancel={() => {}}
      >
        <input aria-label="첫 칸" />
        <input aria-label="오류 칸" />
      </Dialog>,
    );
    screen.getByLabelText("오류 칸").scrollIntoView = scroll;
    rerender(
      <Dialog
        open
        title="삭제할까요?"
        danger
        confirmLabel="삭제"
        onConfirm={() => {}}
        onCancel={() => {}}
      >
        <input aria-label="첫 칸" />
        <input aria-label="오류 칸" aria-invalid="true" />
      </Dialog>,
    );
    await waitFor(() => expect(screen.getByLabelText("오류 칸")).toHaveFocus());
    expect(scroll).toHaveBeenCalled();
    expect(screen.getByRole("button", { name: "삭제" })).toHaveClass(
      "sicost-danger",
    );
  });
});
