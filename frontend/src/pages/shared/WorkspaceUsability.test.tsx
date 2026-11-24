import {
  fireEvent,
  render,
  screen,
  within,
} from "../../shared/testSupport/businessFixtures";
import userEvent from "@testing-library/user-event";
import { expect, it, vi } from "vitest";
import { WorkspaceUsability } from "./WorkspaceUsability";
const menu = {
  menuId: "m",
  parentId: null,
  name: "대시보드",
  screenId: "UI-16",
  pcOnly: false,
  sortOrder: 1,
  icon: null,
  children: [],
};
const props = () => ({
  menus: [menu],
  active: "UI-16",
  mode: null,
  onOpen: vi.fn(),
  onClose: vi.fn(),
});
it("메뉴명·번호 부분검색 후 Enter로 실제 메뉴를 연다", async () => {
  const p = props();
  render(<WorkspaceUsability {...p} mode="search" />);
  await userEvent.type(screen.getByLabelText("화면명·번호"), "16");
  await userEvent.keyboard("{Enter}");
  expect(p.onOpen).toHaveBeenCalledTimes(1);
  expect(p.onOpen).toHaveBeenCalledWith("UI-16");
});
it("활성 모달 저장만 한 번 실행하고 숨긴 업무 저장은 실행하지 않는다", () => {
  const save = vi.fn(),
    hidden = vi.fn();
  render(
    <>
      <WorkspaceUsability {...props()} />
      <div className="sicost-screen-host" hidden>
        <button onClick={hidden}>저장</button>
      </div>
      <div role="dialog">
        <button onClick={save}>저장</button>
      </div>
    </>,
  );
  fireEvent.keyDown(document.body, { key: "s", ctrlKey: true });
  expect(save).toHaveBeenCalledTimes(1);
  expect(hidden).not.toHaveBeenCalled();
});
it.each(["마감", "확인 후 다시알림", "오늘09시작업실행"])(
  "CtrlS로 %s 명령을 실행하지 않는다",
  (label) => {
    const command = vi.fn();
    render(
      <>
        <WorkspaceUsability {...props()} />
        <div className="sicost-screen-host">
          <button onClick={command}>{label}</button>
        </div>
      </>,
    );
    const event = new KeyboardEvent("keydown", {
      key: "s",
      ctrlKey: true,
      bubbles: true,
      cancelable: true,
    });
    document.body.dispatchEvent(event);
    expect(event.defaultPrevented).toBe(true);
    expect(command).not.toHaveBeenCalled();
  },
);
it("입력칸 Ctrl화살표와 IME·반복 키는 가로채지 않는다", () => {
  const next = vi.fn();
  render(
    <>
      <WorkspaceUsability {...props()} />
      <div className="sicost-screen-host">
        <input aria-label="입력" />
        <button aria-label="다음 페이지" onClick={next}>
          다음
        </button>
      </div>
    </>,
  );
  fireEvent.keyDown(screen.getByLabelText("입력"), {
    key: "ArrowRight",
    ctrlKey: true,
  });
  expect(next).not.toHaveBeenCalled();
  fireEvent.keyDown(document.body, {
    key: "ArrowRight",
    ctrlKey: true,
    isComposing: true,
  });
  expect(next).not.toHaveBeenCalled();
  fireEvent.keyDown(document.body, {
    key: "ArrowRight",
    ctrlKey: true,
    repeat: true,
  });
  expect(next).not.toHaveBeenCalled();
  fireEvent.keyDown(document.body, { key: "ArrowRight", ctrlKey: true });
  expect(next).toHaveBeenCalledTimes(1);
});
it("단축키 도움말에 모든 키와 닫기 버튼을 표시한다", async () => {
  const p = props();
  render(<WorkspaceUsability {...p} mode="help" />);
  const dialog = within(screen.getByRole("dialog", { name: "단축키 보기" }));
  for (const text of [/Enter/, /Ctrl\+S/, /Esc/, /Ctrl\+K/, /Ctrl\+←/])
    expect(dialog.getByText(text)).toBeVisible();
  expect(dialog.queryByRole("button", { name: "취소" })).not.toBeInTheDocument();
  await userEvent.click(dialog.getByRole("button", { name: "닫기", exact: true }));
  expect(p.onClose).toHaveBeenCalledOnce();
});
it("CSS로 가려진 PC전용 업무는 단축키로 저장하지 않는다", () => {
  const save = vi.fn();
  render(
    <>
      <style>{`.pc-hidden { display: none; }`}</style>
      <WorkspaceUsability {...props()} />
      <div className="sicost-screen-host">
        <div className="pc-hidden">
          <button onClick={save}>저장</button>
        </div>
      </div>
    </>,
  );
  fireEvent.keyDown(document.body, { key: "s", ctrlKey: true });
  expect(save).not.toHaveBeenCalled();
});
