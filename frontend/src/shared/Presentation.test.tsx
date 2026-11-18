import { useState } from "react";
import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { SegmentedTabs } from "./SegmentedTabs";
import { HelpTooltip } from "./HelpTooltip";
import { KpiItem, KpiStrip } from "./Surface";
import { DetailDrawer } from "./DetailDrawer";
import { Dialog } from "./Dialog";

describe("controlled presentation", () => {
  it("moves tab focus/selection with arrows and Home/End, skips disabled tabs and keeps caller-owned drafts", async () => {
    function Workspace() {
      const [tab, select] = useState("first");
      return (
        <>
          <SegmentedTabs
            label="시험 탭"
            testId="test-tabs"
            value={tab}
            onChange={select}
            items={[
              { key: "first", label: "첫째", panelId: "first-panel" },
              { key: "locked", label: "잠김", disabled: true },
              { key: "last", label: "마지막", panelId: "last-panel" },
            ]}
          />
          <div id="first-panel" hidden={tab !== "first"}>
            <input aria-label="초안" defaultValue="" />
          </div>
          <div id="last-panel" hidden={tab !== "last"}>
            두 번째 본문
          </div>
        </>
      );
    }
    render(<Workspace />);
    const input = screen.getByRole("textbox");
    await userEvent.type(input, "유지할 초안");
    const first = screen.getByRole("tab", { name: "첫째" });
    first.focus();
    await userEvent.keyboard("{ArrowRight}");
    expect(screen.getByRole("tab", { name: "마지막" })).toHaveFocus();
    expect(screen.getByRole("tab", { name: "마지막" })).toHaveAttribute(
      "aria-selected",
      "true",
    );
    await userEvent.keyboard("{Home}");
    expect(first).toHaveFocus();
    expect(input).toHaveValue("유지할 초안");
    await userEvent.keyboard("{End}{ArrowRight}");
    expect(first).toHaveFocus();
  });

  it("renders exact negative, zero and unknown KPI values without doing arithmetic", () => {
    render(
      <KpiStrip testId="values" label="값">
        <KpiItem
          testId="negative"
          label="정밀 금액"
          value="-9,007,199,254,740,993"
          unit="원"
        />
        <KpiItem testId="zero" label="영" value="0" />
        <KpiItem
          testId="unknown"
          label="미확인"
          value="–"
          source={<p>급여 미업로드</p>}
        />
      </KpiStrip>,
    );
    expect(screen.getByTestId("negative")).toHaveTextContent(
      "-9,007,199,254,740,993원",
    );
    expect(screen.getByTestId("zero")).toHaveTextContent("0");
    expect(screen.getByText("급여 미업로드")).toBeVisible();
  });

  it("opens help with focus/click, keeps API text literal, dismisses on Escape and outside click", async () => {
    render(
      <>
        <HelpTooltip label="계산 근거" testId="help">
          {"<img src=x onerror=alert(1)>"}
        </HelpTooltip>
        <button>외부</button>
      </>,
    );
    await userEvent.click(screen.getByRole("button", { name: "계산 근거" }));
    expect(screen.getByRole("tooltip")).toHaveTextContent(
      "<img src=x onerror=alert(1)>",
    );
    expect(screen.queryByRole("img")).not.toBeInTheDocument();
    await userEvent.keyboard("{Escape}");
    expect(screen.queryByRole("tooltip")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "계산 근거" })).toHaveFocus();
    await userEvent.click(screen.getByRole("button", { name: "계산 근거" }));
    await userEvent.click(screen.getByRole("button", { name: "외부" }));
    expect(screen.queryByRole("tooltip")).not.toBeInTheDocument();
  });
});

it("traps focus in the topmost confirmation, preserves pending, restores drawer focus and the original scroll lock", async () => {
  const closeDrawer = vi.fn();
  function Layers() {
    const [confirmation, setConfirmation] = useState(false),
      [pending, setPending] = useState(false);
    return (
      <DetailDrawer title="상세" onClose={closeDrawer}>
        <button onClick={() => setConfirmation(true)}>하위 확인</button>
        <Dialog
          open={confirmation}
          title="확정"
          pending={pending}
          onCancel={() => setConfirmation(false)}
          onConfirm={() => setPending(true)}
        >
          <input aria-label="변경 사유" />
        </Dialog>
      </DetailDrawer>
    );
  }
  const original = document.body.style.overflow;
  document.body.style.overflow = "clip";
  const { unmount } = render(<Layers />);
  expect(document.body.style.overflow).toBe("hidden");
  await userEvent.click(screen.getByRole("button", { name: "하위 확인" }));
  expect(screen.getByRole("textbox", { name: "변경 사유" })).toHaveFocus();
  await userEvent.keyboard("{Shift>}{Tab}{/Shift}");
  expect(screen.getByRole("button", { name: /^확인$/ })).toHaveFocus();
  await userEvent.keyboard("{Escape}");
  expect(
    screen.queryByRole("dialog", { name: "확정" }),
  ).not.toBeInTheDocument();
  expect(screen.getByRole("button", { name: "하위 확인" })).toHaveFocus();
  expect(closeDrawer).not.toHaveBeenCalled();
  await userEvent.click(screen.getByRole("button", { name: "하위 확인" }));
  await userEvent.click(screen.getByRole("button", { name: /^확인$/ }));
  fireEvent.keyDown(document, { key: "Escape" });
  expect(screen.getByRole("dialog", { name: "확정" })).toBeInTheDocument();
  expect(closeDrawer).not.toHaveBeenCalled();
  unmount();
  expect(document.body.style.overflow).toBe("clip");
  document.body.style.overflow = original;
});
