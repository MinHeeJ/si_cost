import { act, fireEvent, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { useState } from "react";
import { HelpTooltip } from "./HelpTooltip";
import { SegmentedTabs } from "./SegmentedTabs";
import { SectionCard, KpiStrip, KpiItem } from "./Surface";
import { FormSection } from "./FormSection";
import { Dialog } from "./Dialog";
import { DetailDrawer } from "./DetailDrawer";
import { Text } from "./Text";
import { chartCoordinate, seriesOptions } from "./charts/chartAdapters";

afterEach(() => {
  vi.useRealTimers();
});
describe("리디자인 표현 계약", () => {
  it("닫힌 도움말을 DOM에 남기지 않고 포커스로 body 포털을 연다", () => {
    render(
      <div style={{ overflow: "hidden" }}>
        <HelpTooltip label="공급가">부가세 제외</HelpTooltip>
      </div>,
    );
    expect(screen.queryByRole("tooltip")).not.toBeInTheDocument();
    const trigger = screen.getByRole("button", { name: "공급가 도움말" });
    fireEvent.focus(trigger);
    const tooltip = screen.getByRole("tooltip");
    expect(tooltip.parentElement).toBe(document.body);
    expect(trigger).toHaveAttribute("aria-describedby", tooltip.id);
    expect(trigger).toHaveAttribute("aria-expanded", "true");
    fireEvent.keyDown(trigger, { key: "Escape" });
    expect(screen.queryByRole("tooltip")).not.toBeInTheDocument();
  });
  it("클릭 고정·다시 클릭·바깥 누르기·한 번에 하나 열기를 지원한다", async () => {
    render(
      <>
        <HelpTooltip label="첫째">첫 안내</HelpTooltip>
        <HelpTooltip label="둘째">둘째 안내</HelpTooltip>
      </>,
    );
    const first = screen.getByRole("button", { name: "첫째 도움말" });
    await userEvent.click(first);
    fireEvent.pointerLeave(first);
    expect(screen.getByRole("tooltip")).toHaveTextContent("첫 안내");
    await userEvent.click(screen.getByRole("button", { name: "둘째 도움말" }));
    expect(screen.getAllByRole("tooltip")).toHaveLength(1);
    expect(screen.getByRole("tooltip")).toHaveTextContent("둘째 안내");
    fireEvent.pointerDown(document.body);
    expect(screen.queryByRole("tooltip")).not.toBeInTheDocument();
    await userEvent.click(first);
    await userEvent.click(first);
    expect(screen.queryByRole("tooltip")).not.toBeInTheDocument();
  });
  it("도움말은 입력 라벨·검증·폼 제출을 바꾸지 않는다", async () => {
    const submit = vi.fn();
    function Form() {
      const [value, setValue] = useState("");
      return (
        <form
          onSubmit={(e) => {
            e.preventDefault();
            submit(value);
          }}
        >
          <Text
            label="계약금액"
            value={value}
            onChange={setValue}
            help="부가세 제외"
            error="필수값"
          />
          <button>저장</button>
        </form>
      );
    }
    render(<Form />);
    await userEvent.click(
      screen.getByRole("button", { name: "계약금액 도움말" }),
    );
    expect(submit).not.toHaveBeenCalled();
    await userEvent.type(screen.getByLabelText("계약금액"), "0");
    expect(screen.getByLabelText("계약금액")).toHaveAttribute(
      "aria-invalid",
      "true",
    );
    await userEvent.click(screen.getByRole("button", { name: "저장" }));
    expect(submit).toHaveBeenCalledWith("0");
  });
  it("분절 탭은 원래 key·패널·testid를 유지하고 방향키에서 disabled를 건너뛴다", async () => {
    function Tabs() {
      const [value, setValue] = useState("EST");
      return (
        <SegmentedTabs
          label="조회 단계"
          testId="stage-tabs"
          value={value}
          onChange={setValue}
          tabs={[
            {
              key: "EST",
              label: "견적",
              panelId: "stage",
              testId: "stage-est",
            },
            {
              key: "CTRT",
              label: "계약",
              panelId: "stage",
              testId: "stage-ctrt",
              disabled: true,
            },
            {
              key: "PLAN",
              label: "계획",
              panelId: "stage",
              testId: "stage-plan",
            },
          ]}
        />
      );
    }
    render(<Tabs />);
    screen.getByTestId("stage-est").focus();
    await userEvent.keyboard("{ArrowRight}");
    expect(screen.getByTestId("stage-plan")).toHaveAttribute(
      "aria-selected",
      "true",
    );
    expect(screen.getByTestId("stage-plan")).toHaveFocus();
    expect(screen.getByTestId("stage-plan")).toHaveAttribute(
      "aria-controls",
      "stage",
    );
    await userEvent.keyboard("{Home}");
    expect(screen.getByTestId("stage-est")).toHaveFocus();
    const ids = screen.getAllByRole("tab").map((node) => node.id);
    expect(new Set(ids).size).toBe(ids.length);
  });
  it("표현 부품은 음수·0·미확인·단위를 계산하거나 덮지 않는다", () => {
    render(
      <SectionCard title="요약" testId="summary">
        <KpiStrip testId="metrics">
          <KpiItem
            label="이익"
            value="−1,234,567,890"
            unit="원"
            testId="negative"
          />
          <KpiItem
            label="원가"
            value="미확인"
            source="급여 미업로드"
            testId="unknown"
          />
          <KpiItem label="예산" value="0" unit="원" testId="zero" />
        </KpiStrip>
        <FormSection title="기본 정보" testId="basic">
          <Text
            label="프로젝트명"
            value="긴 프로젝트 이름"
            onChange={() => {}}
          />
        </FormSection>
      </SectionCard>,
    );
    expect(screen.getByTestId("negative")).toHaveTextContent(
      "−1,234,567,890원",
    );
    expect(screen.getByTestId("unknown")).toHaveTextContent(
      "미확인급여 미업로드",
    );
    expect(screen.getByTestId("zero")).toHaveTextContent("0원");
    expect(screen.getByLabelText("프로젝트명")).toHaveValue("긴 프로젝트 이름");
  });
  it("차트 좌표만 변환하며 null과 단일 계열·0·음수와 원문 정밀도를 보존한다", () => {
    expect(chartCoordinate(null)).toBeNull();
    expect(chartCoordinate("0")).toBe(0);
    expect(chartCoordinate("-1000000")).toBe(-1);
    const raw = ["9007199254740993", null, "0", "-10"];
    const option = seriesOptions(
      ["1월", "2월", "3월", "4월"],
      [{ name: "실적", values: raw, dashed: true }],
      "원",
    );
    const series = (
      option.series as { data: unknown[]; connectNulls: boolean }[]
    )[0];
    expect(series.data[1]).toBeNull();
    expect(series.connectNulls).toBe(false);
    const formatter = (option.tooltip as { formatter: (p: unknown) => string })
      .formatter;
    expect(formatter([{ dataIndex: 0 }])).toContain("9,007,199,254,740,993");
    expect(formatter([{ dataIndex: 1 }])).toContain("–");
    expect(raw).toEqual(["9007199254740993", null, "0", "-10"]);
  });
  it("중첩 창의 Escape·Tab·초점 복귀와 마지막 창까지 스크롤 잠금을 보존한다", async () => {
    function Nested() {
      const [open, setOpen] = useState(false);
      return (
        <DetailDrawer title="청구 상세" onClose={() => {}}>
          <button onClick={() => setOpen(true)}>입금 등록</button>
          <Dialog
            open={open}
            title="입금 확인"
            onConfirm={() => {}}
            onCancel={() => setOpen(false)}
          >
            <Text label="금액" value="0" onChange={() => {}} />
          </Dialog>
        </DetailDrawer>
      );
    }
    const before = document.body.style.overflow;
    const result = render(<Nested />);
    expect(document.body.style.overflow).toBe("hidden");
    const launch = screen.getByRole("button", { name: "입금 등록" });
    await userEvent.click(launch);
    const dialog = screen.getByRole("dialog", { name: "입금 확인" });
    const confirm = within(dialog).getByRole("button", { name: "확인" });
    confirm.focus();
    await userEvent.tab();
    expect(screen.getByLabelText("금액")).toHaveFocus();
    await userEvent.keyboard("{Escape}");
    expect(
      screen.queryByRole("dialog", { name: "입금 확인" }),
    ).not.toBeInTheDocument();
    expect(launch).toHaveFocus();
    expect(document.body.style.overflow).toBe("hidden");
    result.unmount();
    expect(document.body.style.overflow).toBe(before);
  });
});
