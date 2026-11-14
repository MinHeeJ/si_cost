import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { DetailDrawer } from "./DetailDrawer";

function mount(props: Partial<React.ComponentProps<typeof DetailDrawer>> = {}) {
  const onClose = vi.fn();
  render(
    <DetailDrawer
      label="시험 상세"
      title="제목"
      subtitle="보조 줄"
      actions={<button type="button">수정</button>}
      onClose={onClose}
      {...props}
    >
      <p>본문</p>
    </DetailDrawer>,
  );
  return onClose;
}

describe("DetailDrawer", () => {
  it("오른쪽 패널(aside)로 제목·보조 줄·작업·닫기·본문을 표시하고 닫기에 초점을 둔다", () => {
    mount();
    const drawer = screen.getByRole("dialog", { name: "시험 상세" });
    expect(drawer.tagName).toBe("ASIDE");
    expect(drawer).toHaveAttribute("aria-modal", "true");
    expect(screen.getByRole("heading", { name: "제목" })).toBeInTheDocument();
    expect(screen.getByText("보조 줄")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "수정" })).toBeInTheDocument();
    expect(screen.getByText("본문")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "닫기" })).toHaveFocus();
  });

  it("작업 버튼 뒤에 닫기 버튼이 마지막에 놓인다", () => {
    mount();
    const buttons = screen.getAllByRole("button").map((b) => b.textContent);
    expect(buttons.slice(-2)).toEqual(["수정", "닫기"]);
  });

  it("닫기 버튼·Escape·바깥(어두운 배경) 클릭이 각각 한 번씩 닫는다", async () => {
    const onClose = mount();
    await userEvent.click(screen.getByRole("button", { name: "닫기" }));
    expect(onClose).toHaveBeenCalledTimes(1);
    fireEvent.keyDown(screen.getByRole("dialog"), { key: "Escape" });
    expect(onClose).toHaveBeenCalledTimes(2);
    fireEvent.click(screen.getByTestId("detail-drawer-scrim"));
    expect(onClose).toHaveBeenCalledTimes(3);
  });

  it("처리 중에는 어떤 방법으로도 닫지 않는다", async () => {
    const onClose = mount({ pending: true });
    expect(screen.getByRole("button", { name: "닫기" })).toBeDisabled();
    fireEvent.keyDown(screen.getByRole("dialog"), { key: "Escape" });
    fireEvent.click(screen.getByTestId("detail-drawer-scrim"));
    expect(onClose).not.toHaveBeenCalled();
  });

  it("라벨이 없으면 제목으로 이름을 붙인다", () => {
    mount({ label: undefined });
    expect(screen.getByRole("dialog", { name: "제목" })).toBeInTheDocument();
  });
});

it("숨김 옵션은 패널과 배경을 함께 숨기고 내부 입력을 보존한다", () => {
  const onClose=vi.fn();
  const {rerender}=render(<DetailDrawer title="작업 상세" hidden={false} onClose={onClose}><input aria-label="초안" defaultValue="보존" /></DetailDrawer>);
  const input=screen.getByRole("textbox",{name:"초안"});
  rerender(<DetailDrawer title="작업 상세" hidden onClose={onClose}><input aria-label="초안" defaultValue="보존" /></DetailDrawer>);
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  expect(screen.getByTestId("detail-drawer-scrim")).not.toBeVisible();
  rerender(<DetailDrawer title="작업 상세" onClose={onClose}><input aria-label="초안" defaultValue="보존" /></DetailDrawer>);
  expect(screen.getByRole("textbox",{name:"초안"})).toBe(input);
  expect(input).toHaveValue("보존");
});
