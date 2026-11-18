import { useState } from "react";
import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { SegmentedTabList, SegmentedTabs } from "./SegmentedTabs";
import { HelpTooltip } from "./HelpTooltip";
import { decimalCoordinate, seriesChartOptions } from "./charts/chartAdapters";
import { ChartHost } from "./charts/ChartHost";
import { DetailDrawer } from "./DetailDrawer";
import { Dialog } from "./Dialog";

describe("v22 presentation contracts", () => {
  it("preserves an existing named navigation landmark and its controlled tab handlers", async () => {
    function Example() {
      const [active, setActive] = useState("codes");
      return (
        <SegmentedTabList as="nav" aria-label="공통코드 하위 탭">
          <button
            aria-current={active === "codes" ? "page" : undefined}
            onClick={() => setActive("codes")}
          >
            코드
          </button>
          <button disabled>잠김</button>
          <button
            aria-current={active === "history" ? "page" : undefined}
            onClick={() => setActive("history")}
          >
            이력
          </button>
        </SegmentedTabList>
      );
    }
    render(<Example />);
    expect(
      screen.getByRole("navigation", { name: "공통코드 하위 탭" }),
    ).toBeInTheDocument();
    screen.getByRole("button", { name: "코드" }).focus();
    await userEvent.keyboard("{ArrowRight}");
    expect(screen.getByRole("button", { name: "이력" })).toHaveFocus();
    expect(screen.getByRole("button", { name: "이력" })).toHaveAttribute(
      "aria-current",
      "page",
    );
  });
  it("moves controlled tabs with arrow keys, skips disabled tabs, and preserves panel IDs", async () => {
    function Example() {
      const [value, setValue] = useState("first");
      return (
        <SegmentedTabs
          label="업무 영역"
          value={value}
          onChange={setValue}
          tabs={[
            { key: "first", label: "기본", panelId: "basic-panel" },
            { key: "locked", label: "잠금", disabled: true },
            { key: "last", label: "이력", panelId: "history-panel" },
          ]}
        />
      );
    }
    render(<Example />);
    const first = screen.getByRole("tab", { name: "기본" }),
      last = screen.getByRole("tab", { name: "이력" });
    first.focus();
    await userEvent.keyboard("{ArrowRight}");
    expect(last).toHaveFocus();
    expect(last).toHaveAttribute("aria-selected", "true");
    expect(last).toHaveAttribute("aria-controls", "history-panel");
    await userEvent.keyboard("{Home}");
    expect(first).toHaveFocus();
    expect(first).toHaveAttribute("tabindex", "0");
  });
  it("exposes help on keyboard focus and dismisses only the popover with Escape", async () => {
    render(
      <HelpTooltip label="계산 기준">
        서버에서 받은 기준을 표시합니다.
      </HelpTooltip>,
    );
    const trigger = screen.getByRole("button", { name: "계산 기준" });
    await userEvent.tab();
    expect(await screen.findByRole("tooltip")).toHaveTextContent(
      "서버에서 받은 기준",
    );
    await userEvent.keyboard("{Escape}");
    expect(screen.queryByRole("tooltip")).not.toBeInTheDocument();
    expect(trigger).toHaveFocus();
  });
  it("keeps null gaps, exact tooltip strings and zero/negative/very large coordinates distinct", () => {
    expect(decimalCoordinate(null)).toBeNull();
    expect(decimalCoordinate("0")).toBe(0);
    expect(decimalCoordinate("-123.45", 2)).toBe(-1.2345);
    expect(Number.isFinite(decimalCoordinate("9".repeat(400), 400)!)).toBe(
      true,
    );
    const raw = "900719925474099312345",
      option = seriesChartOptions({
        categories: ["<script>unsafe</script>"],
        series: [{ name: "<img src=x>", values: [raw, null, "0"] }],
        formatValue: (value) => value ?? "미확인",
      });
    const tooltip = option.tooltip as {
      formatter(parameters: unknown): HTMLElement;
    };
    const node = tooltip.formatter([{ dataIndex: 0 }]);
    expect(node.textContent).toContain(raw);
    expect(node.querySelector("script,img")).toBeNull();
    const series = option.series as {
      connectNulls: boolean;
      data: (number | null)[];
    }[];
    expect(series[0].connectNulls).toBe(false);
    expect(series[0].data[1]).toBeNull();
    expect(series[0].data[2]).toBe(0);
  });
  it("closes an interactive source popover with Escape without reopening it on restored focus", async () => {
    render(
      <HelpTooltip label="원천 안내" interactive testId="source-help">
        <a href="/UI-08" data-testid="source-link">
          손익 원천
        </a>
      </HelpTooltip>,
    );
    const user = userEvent.setup();
    await user.tab();
    expect(
      screen.getByRole("dialog", { name: "원천 안내" }),
    ).toBeInTheDocument();
    await user.tab();
    expect(screen.getByRole("link", { name: "손익 원천" })).toHaveFocus();
    await user.keyboard("{Escape}");
    expect(
      screen.queryByRole("dialog", { name: "원천 안내" }),
    ).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "원천 안내" })).toHaveFocus();
  });
  it("retains the accessible original chart when SVG chart initialization is unavailable", () => {
    render(
      <ChartHost option={{}}>
        <svg role="img" aria-label="기존 숫자 근거">
          <title>정확한 원천값</title>
        </svg>
      </ChartHost>,
    );
    expect(
      screen.getByRole("img", { name: "기존 숫자 근거" }),
    ).toBeInTheDocument();
  });
  it("keeps a nested confirmation inside the drawer focus cycle and restores scrolling only after both close", async () => {
    function Example() {
      const [drawer, setDrawer] = useState(true),
        [dialog, setDialog] = useState(false);
      return drawer ? (
        <DetailDrawer title="청구 상세" onClose={() => setDrawer(false)}>
          <button onClick={() => setDialog(true)}>입금 등록</button>
          <Dialog
            open={dialog}
            title="입금 확인"
            onConfirm={() => {}}
            onCancel={() => setDialog(false)}
          >
            <input aria-label="입금액" />
          </Dialog>
        </DetailDrawer>
      ) : (
        <p>닫힘</p>
      );
    }
    const original = document.body.style.overflow;
    render(<Example />);
    const opener = screen.getByRole("button", { name: "입금 등록" });
    await userEvent.click(opener);
    expect(screen.getByRole("textbox", { name: "입금액" })).toHaveFocus();
    expect(document.body.style.overflow).toBe("hidden");
    await userEvent.keyboard("{Escape}");
    expect(
      screen.queryByRole("dialog", { name: "입금 확인" }),
    ).not.toBeInTheDocument();
    expect(opener).toHaveFocus();
    expect(document.body.style.overflow).toBe("hidden");
    fireEvent.keyDown(screen.getByRole("dialog", { name: "청구 상세" }), {
      key: "Escape",
    });
    expect(screen.getByText("닫힘")).toBeInTheDocument();
    expect(document.body.style.overflow).toBe(original);
  });
  it("arbitrates an initially mounted nested dialog above its drawer regardless of child-first effect ordering", async () => {
    function Example() {
      const [open, setOpen] = useState(true);
      return (
        <DetailDrawer
          title="원천 상세"
          onClose={() => {
            throw new Error("아래 상세가 닫히면 안 됩니다");
          }}
        >
          <Dialog
            open={open}
            title="원천 확인"
            onCancel={() => setOpen(false)}
            onConfirm={() => {}}
          >
            확인 내용
          </Dialog>
        </DetailDrawer>
      );
    }
    render(<Example />);
    await userEvent.keyboard("{Escape}");
    expect(
      screen.queryByRole("dialog", { name: "원천 확인" }),
    ).not.toBeInTheDocument();
    expect(
      screen.getByRole("dialog", { name: "원천 상세" }),
    ).toBeInTheDocument();
    expect(document.body.style.overflow).toBe("hidden");
  });
});
