import {
  act,
  fireEvent,
  render,
  renderHook,
  screen,
  waitFor,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import { CodeSelect } from "./CodeSelect";
import { DataTable } from "./DataTable";
import { Decimal } from "./Decimal";
import { Dialog } from "./Dialog";
import { ErrorBanner, StatusBadge } from "./Feedback";
import { Text } from "./Text";
import { COMMITTED_REFRESH_FAILED, useCommitRefresh } from "./useCommitRefresh";

describe("공통 부품 계약", () => {
  it("화면이 제공한 열·렌더러·페이지·선택 이벤트를 사용한다", async () => {
    const page = vi.fn(),
      selection = vi.fn();
    render(
      <DataTable
        rows={[{ code: "A", name: "독립 시험" }]}
        rowKey={(row) => row.code}
        columns={[{ id: "name", label: "이름", render: (row) => row.name }]}
        page={1}
        total={51}
        onPageChange={page}
        onSelectionChange={selection}
        selectedKeys={[]}
      />,
    );
    expect(
      screen.getByRole("columnheader", { name: "이름" }),
    ).toBeInTheDocument();
    expect(screen.getByText("독립 시험")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "다음 페이지" }));
    expect(page).toHaveBeenCalledWith(2);
    await userEvent.click(screen.getByRole("checkbox"));
    expect(selection).toHaveBeenCalledWith(["A"]);
  });
  it("불러오는 중·빈 목록·실패를 구분한다", async () => {
    const props = {
      rows: [],
      rowKey: () => "none",
      columns: [{ id: "name", label: "이름", render: () => "" }],
    };
    const retry = vi.fn();
    const { rerender } = render(<DataTable {...props} loading />);
    expect(screen.getByRole("status")).toHaveTextContent("불러오는 중");
    rerender(<DataTable {...props} empty="등록된 표기가 없습니다." />);
    expect(screen.getByText("등록된 표기가 없습니다.")).toBeInTheDocument();
    rerender(
      <DataTable {...props} error="자료를 읽지 못했습니다." onRetry={retry} />,
    );
    expect(screen.getByRole("alert")).toHaveTextContent(
      "자료를 읽지 못했습니다.",
    );
    expect(retry).not.toHaveBeenCalled();
    await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
    expect(retry).toHaveBeenCalledTimes(1);
  });
  it("통제 입력·읽기전용·오류·코드 선택을 제공한다", async () => {
    function Form() {
      const [value, setValue] = useState("");
      return (
        <>
          <Text
            label="이름"
            value={value}
            onChange={setValue}
            error="이름을 입력하세요."
            maxLength={50}
          />
          <Decimal label="값" value="1.2300" onChange={vi.fn()} readOnly />
          <CodeSelect
            label="코드"
            value=""
            onChange={vi.fn()}
            options={[{ code: "A", name: "선택 값" }]}
            disabled
          />
        </>
      );
    }
    render(<Form />);
    await userEvent.type(screen.getByLabelText("이름"), "보존");
    expect(screen.getByLabelText("이름")).toHaveValue("보존");
    expect(screen.getByLabelText("이름")).toHaveAttribute(
      "aria-invalid",
      "true",
    );
    expect(screen.getByLabelText("값")).toHaveAttribute("readonly");
    expect(screen.getByLabelText("값")).toHaveValue("1.2300");
    expect(screen.getByLabelText("코드")).toBeDisabled();
  });
  it("모달 포커스를 가두고 닫은 뒤 열기 버튼으로 복원한다", async () => {
    function Form() {
      const [open, setOpen] = useState(false);
      return (
        <>
          <button onClick={() => setOpen(true)}>열기</button>
          <Dialog
            title="확인"
            open={open}
            onCancel={() => setOpen(false)}
            onConfirm={() => setOpen(false)}
          >
            <Text label="이름" value="" onChange={vi.fn()} />
          </Dialog>
        </>
      );
    }
    render(<Form />);
    const user = userEvent.setup();
    await user.click(screen.getByRole("button", { name: "열기" }));
    expect(screen.getByLabelText("이름")).toHaveFocus();
    await user.tab({ shift: true });
    expect(screen.getByRole("button", { name: "확인" })).toHaveFocus();
    await user.tab();
    expect(screen.getByLabelText("이름")).toHaveFocus();
    await user.keyboard("{Escape}");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "열기" })).toHaveFocus();
  });
  it("첫 오류에 포커스하고 pending 취소·중복 제출을 차단한다", async () => {
    const confirm = vi.fn(),
      cancel = vi.fn();
    const { rerender } = render(
      <Dialog open title="입력" onConfirm={confirm} onCancel={cancel}>
        <Text label="이름" value="보존" onChange={vi.fn()} error="확인" />
      </Dialog>,
    );
    expect(screen.getByLabelText("이름")).toHaveFocus();
    rerender(
      <Dialog open title="입력" pending onConfirm={confirm} onCancel={cancel}>
        <p>처리 중</p>
      </Dialog>,
    );
    await userEvent.click(screen.getByRole("button", { name: "처리 중…" }));
    fireEvent.keyDown(screen.getByRole("dialog"), { key: "Escape" });
    expect(confirm).not.toHaveBeenCalled();
    expect(cancel).not.toHaveBeenCalled();
  });
  it("pending prop 갱신 전 빠른 중복 클릭도 한 번만 제출한다", async () => {
    let finish!: () => void;
    const confirm = vi.fn(
      () =>
        new Promise<void>((resolve) => {
          finish = resolve;
        }),
    );
    render(
      <Dialog open title="확인" onConfirm={confirm} onCancel={vi.fn()}>
        <p>계속할까요?</p>
      </Dialog>,
    );
    const button = screen.getByRole("button", { name: "확인" });
    fireEvent.click(button);
    fireEvent.click(button);
    expect(confirm).toHaveBeenCalledTimes(1);
    await act(async () => finish());
  });
  it("같은 문구도 상태 종류에 따라 색 토큰 클래스를 유지한다", () => {
    render(
      <>
        <StatusBadge tone="warn">예정</StatusBadge>
        <StatusBadge tone="lock">예정</StatusBadge>
      </>,
    );
    expect(screen.getAllByText("예정")[0]).toHaveClass("sicost-tone-warn");
    expect(screen.getAllByText("예정")[1]).toHaveClass("sicost-tone-lock");
  });
  it("실패 입력을 보존하고 자동 저장·재시도하지 않는다", async () => {
    const save = vi.fn().mockRejectedValue(new Error("실패"));
    const refresh = vi.fn();
    const { result } = renderHook(() => useCommitRefresh());
    await act(async () => {
      await expect(result.current.commit(save, refresh)).rejects.toThrow(
        "실패",
      );
    });
    expect(save).toHaveBeenCalledTimes(1);
    expect(refresh).not.toHaveBeenCalled();
    expect(result.current.pending).toBe(false);
    expect(result.current.message).toBe("실패");
  });
  it("commit 후 재조회 실패는 저장 실패와 다른 문구로 표시한다", async () => {
    const save = vi.fn().mockResolvedValue(undefined),
      refresh = vi.fn().mockRejectedValue(new Error("조회 실패"));
    const { result } = renderHook(() => useCommitRefresh());
    await act(async () => {
      await result.current.commit(save, refresh);
    });
    expect(result.current.message).toBe(COMMITTED_REFRESH_FAILED);
    expect(save).toHaveBeenCalledTimes(1);
    expect(refresh).toHaveBeenCalledTimes(1);
  });
});
