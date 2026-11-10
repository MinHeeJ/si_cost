import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { StandardHistoryDialog } from "./StandardHistoryDialog";
import {
  getOperatingStandardHistory,
  type OperatingStandard,
} from "./operatingStandardsApi";
vi.mock("./operatingStandardsApi");
const standard: OperatingStandard = {
  key: "VAT_RT",
  name: "부가세율",
  value: null,
  effectiveMonth: null,
  description: "기준 설명",
  unitNm: "%",
  keyRevision: "key-token",
};
beforeEach(() => vi.resetAllMocks());
it("서버 순서대로 모든 월과 현재·예정·지난의 서로 다른 색을 표시한다", async () => {
  vi.mocked(getOperatingStandardHistory).mockResolvedValue({
    keyRevision: "key-token",
    items: ["예정", "현재", "지난"].map((status, i) => ({
      key: standard.key,
      value: String(12 - i),
      effectiveMonth: ["2026-07", "2026-06", "2026-01"][i],
      unitNm: "%",
      status,
      revision: `row-${i}`,
    })),
  });
  render(<StandardHistoryDialog standard={standard} onClose={vi.fn()} />);
  expect(await screen.findByText("예정")).toHaveClass("sicost-tone-info");
  expect(screen.getByText("현재")).toHaveClass("sicost-tone-ok");
  expect(screen.getByText("지난")).toHaveClass("sicost-tone-lock");
  expect(screen.getAllByRole("row")[1]).toHaveTextContent("2026-07");
  expect(screen.queryByLabelText("한 화면 건수")).not.toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: "저장" }),
  ).not.toBeInTheDocument();
});
it("값이 없으면 기준값 없음이며 0 기본값·저장 제어가 없다", async () => {
  vi.mocked(getOperatingStandardHistory).mockResolvedValue({
    keyRevision: "key-token",
    items: [],
  });
  render(<StandardHistoryDialog standard={standard} onClose={vi.fn()} />);
  expect(await screen.findByText("기준값 없음")).toBeInTheDocument();
  expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
  expect(screen.queryByText("0")).not.toBeInTheDocument();
});
it("조회 오류는 수동 재시도하며 닫기는 원래 화면으로 돌아간다", async () => {
  vi.mocked(getOperatingStandardHistory)
    .mockRejectedValueOnce(new Error("이력 조회 실패"))
    .mockResolvedValueOnce({ keyRevision: "token", items: [] });
  const close = vi.fn();
  render(<StandardHistoryDialog standard={standard} onClose={close} />);
  expect(await screen.findByRole("alert")).toHaveTextContent("이력 조회 실패");
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  await screen.findByText("기준값 없음");
  await userEvent.click(screen.getByRole("button", { name: "닫기" }));
  expect(close).toHaveBeenCalledOnce();
  expect(getOperatingStandardHistory).toHaveBeenCalledTimes(2);
});
