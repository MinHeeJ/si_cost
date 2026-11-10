import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ApiError } from "../../shared/api";
import { beforeEach, expect, it, vi } from "vitest";
import { OperatingStandardsScreen } from "./OperatingStandardsScreen";
import * as api from "./operatingStandardsApi";
vi.mock("./operatingStandardsApi");
const standard: api.OperatingStandard = {
  key: "VAT_RT",
  name: "부가세율",
  value: "10",
  unitNm: "%",
  effectiveMonth: "2026-01",
  description: "계산 기준",
  keyRevision: "current",
};
const cashMonth: api.OperatingStandard = {
  ...standard,
  key: "CASH_BASE_YM",
  name: "자금수지 시작 월",
  value: "202601",
  unitNm: "연월",
};
const cashAmount: api.OperatingStandard = {
  ...standard,
  key: "CASH_BASE_AMT",
  name: "기초 잔액",
  value: "1000",
  unitNm: "원",
};
function data(
  items: api.OperatingStandard[] = [standard, cashMonth, cashAmount],
) {
  vi.mocked(api.getOperatingStandards).mockResolvedValue({ items });
  return items;
}
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api.getOperatingStandards).mockResolvedValue({ items: [] });
  vi.mocked(api.getStandardsPeriodContext).mockResolvedValue({
    currentMonth: "2026-06",
    cashBaseMonth: "2026-01",
  });
  vi.mocked(api.getStandardsMonthClose).mockResolvedValue({ items: [] });
  vi.mocked(api.isCashStandard).mockImplementation(
    (key) => key === "CASH_BASE_YM" || key === "CASH_BASE_AMT",
  );
  vi.mocked(api.nextStandardMonth).mockImplementation((month) =>
    month === "2026-05" ? "2026-06" : "2026-02",
  );
  vi.mocked(api.getOperatingStandardHistory).mockImplementation(
    async (key) => ({
      keyRevision: "history-token",
      items: [
        {
          key,
          value: "10",
          unitNm: "%",
          effectiveMonth: "2026-01",
          status: "현재",
          revision: "row",
        },
      ],
    }),
  );
  vi.mocked(api.saveOperatingStandards).mockResolvedValue({
    changed: true,
    items: [standard, cashMonth, cashAmount],
  });
});
it("빈 목록을 비페이지 공통 표로 표시한다", async () => {
  render(<OperatingStandardsScreen />);
  expect(
    await screen.findByText("등록된 운영 기준값이 없습니다."),
  ).toBeInTheDocument();
  expect(
    screen.queryByRole("navigation", { name: "페이지" }),
  ).not.toBeInTheDocument();
});
it("메타데이터는 읽기 전용이고 유효행 없는 값은 0 대신 기준값 없음이다", async () => {
  data([{ ...standard, value: null, effectiveMonth: null }]);
  render(<OperatingStandardsScreen />);
  await screen.findByText("부가세율");
  expect(screen.getAllByText("기준값 없음")).toHaveLength(2);
  expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: "삭제" }),
  ).not.toBeInTheDocument();
});
it("일반 값 변경은 선택키 이력 토큰과 기본월로 확인 후 저장하고 재조회한다", async () => {
  data();
  render(<OperatingStandardsScreen />);
  await screen.findByText("부가세율");
  await userEvent.click(screen.getByRole("button", { name: "값 변경 VAT_RT" }));
  const dialog = screen.getByRole("dialog");
  const input = await within(dialog).findByLabelText(/VAT_RT 값/);
  expect(within(dialog).getByLabelText(/적용 시작월/)).toHaveValue("2026-06");
  fireEvent.change(input, { target: { value: "11" } });
  await userEvent.click(
    within(dialog).getByRole("button", { name: "변경 확인" }),
  );
  expect(within(dialog).getByText(/VAT_RT: 10 → 11/)).toBeInTheDocument();
  expect(api.saveOperatingStandards).not.toHaveBeenCalled();
  await userEvent.click(
    within(dialog).getByRole("button", { name: "확인 후 저장" }),
  );
  await waitFor(() =>
    expect(api.saveOperatingStandards).toHaveBeenCalledWith({
      effectiveMonth: "2026-06",
      changes: [
        { key: "VAT_RT", value: "11", expectedRevision: "history-token" },
      ],
    }),
  );
  await waitFor(() =>
    expect(api.getOperatingStandards).toHaveBeenCalledTimes(2),
  );
  expect(api.notifyOperatingStandardsCommitted).toHaveBeenCalledWith(false);
});
it("조회 실패는 수동 조회만 재시도하며 자동 저장하지 않는다", async () => {
  vi.mocked(api.getOperatingStandards).mockRejectedValue(
    new Error("조회 연결 실패"),
  );
  render(<OperatingStandardsScreen />);
  expect(await screen.findByRole("alert")).toHaveTextContent("조회 연결 실패");
  expect(
    screen.queryByText("등록된 운영 기준값이 없습니다."),
  ).not.toBeInTheDocument();
  expect(api.getOperatingStandards).toHaveBeenCalledTimes(1);
  data();
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  await screen.findByText("부가세율");
  expect(api.saveOperatingStandards).not.toHaveBeenCalled();
});
it("처리중 상태와 빈 목록을 구분한다", async () => {
  let resolve!: (value: { items: api.OperatingStandard[] }) => void;
  vi.mocked(api.getOperatingStandards).mockReturnValue(
    new Promise((yes) => {
      resolve = yes;
    }),
  );
  render(<OperatingStandardsScreen />);
  expect(screen.getByRole("status")).toHaveTextContent("불러오는 중");
  await act(async () => resolve({ items: [] }));
  expect(
    await screen.findByText("등록된 운영 기준값이 없습니다."),
  ).toBeInTheDocument();
});
it("기존 CASH 시작월 마감이면 두 입력과 저장은 읽기 전용이다", async () => {
  data();
  vi.mocked(api.getStandardsMonthClose).mockResolvedValue({
    items: [{ month: "2026-01", status: "마감" }],
  });
  render(<OperatingStandardsScreen />);
  expect(await screen.findByLabelText("자금수지 시작 월")).toHaveAttribute(
    "readonly",
  );
  expect(screen.getByLabelText("기초 잔액")).toHaveAttribute("readonly");
  expect(screen.getByRole("button", { name: "자금 기준 저장" })).toBeDisabled();
  expect(api.saveOperatingStandards).not.toHaveBeenCalled();
});
it("자금 저장에는 적용월을 보내지 않고 날짜를 6자리로 보낸다", async () => {
  data();
  const committed = vi.fn();
  render(<OperatingStandardsScreen onCommitted={committed} />);
  const input = await screen.findByLabelText("자금수지 시작 월");
  expect(input).toHaveValue("2026-01");
  fireEvent.change(input, { target: { value: "2026-02" } });
  await userEvent.click(screen.getByRole("button", { name: "자금 기준 저장" }));
  const dialog = screen.getByRole("dialog");
  expect(
    within(dialog).queryByLabelText(/적용 시작월/),
  ).not.toBeInTheDocument();
  await userEvent.click(
    within(dialog).getByRole("button", { name: "확인 후 저장" }),
  );
  await waitFor(() =>
    expect(api.saveOperatingStandards).toHaveBeenCalledWith({
      changes: [
        { key: "CASH_BASE_YM", value: "202602", expectedRevision: "current" },
      ],
    }),
  );
  expect(committed).toHaveBeenCalledWith(true);
  expect(api.notifyOperatingStandardsCommitted).toHaveBeenCalledWith(true);
  await waitFor(() =>
    expect(api.getStandardsPeriodContext).toHaveBeenCalledTimes(2),
  );
});
it("실패한 자금 입력은 보존하고 자동 재시도하지 않는다", async () => {
  data();
  vi.mocked(api.saveOperatingStandards).mockRejectedValue(
    new ApiError(409, {
      success: false,
      meta: {},
      code: "CLOSED_MONTH",
      message: "기존 시작월 마감",
      requestId: "reference",
    }),
  );
  render(<OperatingStandardsScreen />);
  fireEvent.change(await screen.findByLabelText("기초 잔액"), {
    target: { value: "1234.5678" },
  });
  await userEvent.click(screen.getByRole("button", { name: "자금 기준 저장" }));
  await userEvent.click(
    within(screen.getByRole("dialog")).getByRole("button", {
      name: "확인 후 저장",
    }),
  );
  await waitFor(() =>
    expect(api.saveOperatingStandards).toHaveBeenCalledTimes(1),
  );
  expect(screen.getByLabelText("기초 잔액")).toHaveValue("1234.5678");
  expect(api.getOperatingStandards).toHaveBeenCalledTimes(1);
});
it("성공 뒤 조회 실패는 저장 실패와 구분하며 재저장하지 않는다", async () => {
  data();
  vi.mocked(api.getOperatingStandards)
    .mockResolvedValueOnce({ items: [standard, cashMonth, cashAmount] })
    .mockRejectedValueOnce(new Error("조회 실패"));
  render(<OperatingStandardsScreen />);
  fireEvent.change(await screen.findByLabelText("기초 잔액"), {
    target: { value: "2" },
  });
  await userEvent.click(screen.getByRole("button", { name: "자금 기준 저장" }));
  await userEvent.click(
    within(screen.getByRole("dialog")).getByRole("button", {
      name: "확인 후 저장",
    }),
  );
  expect(
    await screen.findByText("저장됐지만 다시 불러오지 못했습니다."),
  ).toBeInTheDocument();
  expect(api.saveOperatingStandards).toHaveBeenCalledTimes(1);
});
it("이력 창은 수정·저장없이 전체 적용월과 상태를 조회한다", async () => {
  data();
  render(<OperatingStandardsScreen />);
  await screen.findByText("부가세율");
  await userEvent.click(screen.getByRole("button", { name: "이력 VAT_RT" }));
  const dialog = screen.getByRole("dialog");
  expect(await within(dialog).findByText("현재")).toHaveClass("sicost-tone-ok");
  expect(
    within(dialog).queryByRole("button", { name: "저장" }),
  ).not.toBeInTheDocument();
  expect(api.getOperatingStandardHistory).toHaveBeenCalledWith("VAT_RT");
  expect(api.saveOperatingStandards).not.toHaveBeenCalled();
});
it("변경 취소는 API 저장없이 모달만 닫는다", async () => {
  data();
  render(<OperatingStandardsScreen />);
  await screen.findByText("부가세율");
  await userEvent.click(screen.getByRole("button", { name: "값 변경 VAT_RT" }));
  await userEvent.click(
    within(screen.getByRole("dialog")).getByRole("button", { name: "취소" }),
  );
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  expect(api.saveOperatingStandards).not.toHaveBeenCalled();
});
