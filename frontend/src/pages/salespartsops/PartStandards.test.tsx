import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, expect, it, vi } from "vitest";
import { SalesPartStandardDialog } from "./SalesPartStandardDialog";
import { SalesPartDefaultsFields } from "./SalesPartDefaultsFields";
import { SalesPartSelect } from "./SalesPartSelect";
import { ApiError } from "../../shared/api";
import * as api from "./partStandardsApi";
import * as userApi from "../businessusersops/businessUserOptionsApi";
vi.mock("./partStandardsApi");
vi.mock(
  "../businessusersops/businessUserOptionsApi",
  async (importOriginal) => ({
    ...(await importOriginal<typeof userApi>()),
    getBusinessUserOptions: vi.fn(),
  }),
);
const detail: api.PartDetail = {
  part: {
    id: "PART-test",
    name: "시험 영업파트",
    useYn: "N",
    currentMonth: "2026-06",
    currentStandard: null,
    scheduledChange: null,
    personCount: 0,
    projectCount: 0,
    revision: "revision",
  },
  minMonth: "2026-02",
  revision: "revision",
  standards: [
    {
      effectiveMonth: "2026-01",
      endMonth: null,
      leaderUserId: "leader",
      leaderName: "시험 파트장",
      profitRate: "40",
      salesExpenseAmount: "1500000",
      status: "CURRENT",
    },
  ],
};
const defaults: api.PartDefaults = {
  effectiveMonth: "2026-06",
  minMonth: "2026-02",
  profitRate: "40",
  salesExpenseAmount: "1500000",
  defaultErrors: [],
};
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api.getSalesPartDefaults).mockResolvedValue(defaults);
  vi.mocked(api.getPartStandardDetail).mockResolvedValue(detail);
  vi.mocked(api.createSalesPartStandard).mockResolvedValue({
    changed: true,
    message: "저장했습니다.",
    part: detail,
  });
  vi.mocked(userApi.getBusinessUserOptions).mockResolvedValue({
    items: [],
    roleOptions: [],
  });
});
function draw(
  onSaved = vi.fn().mockResolvedValue(undefined),
  onClose = vi.fn(),
) {
  render(
    <MemoryRouter>
      <SalesPartStandardDialog
        partId="PART-test"
        onSaved={onSaved}
        onClose={onClose}
      />
    </MemoryRouter>,
  );
  return { onSaved, onClose };
}
it("중지 파트 기준 변경을 허용하고 소수 문자열과 revision을 전송한다", async () => {
  draw();
  await screen.findByText("시험 영업파트");
  expect(screen.getByText("중지 · 기준 변경 가능")).toHaveClass(
    "sicost-tone-lock",
  );
  fireEvent.change(screen.getByLabelText(/이익 배분율/), {
    target: { value: "40.25" },
  });
  fireEvent.change(screen.getByLabelText(/공통관리비/), {
    target: { value: "999,999,999,999,999원" },
  });
  await userEvent.click(screen.getByRole("button", { name: "변경 확인" }));
  await userEvent.click(screen.getByRole("button", { name: "확인 후 저장" }));
  expect(api.createSalesPartStandard).toHaveBeenCalledWith("PART-test", {
    effectiveMonth: "2026-06",
    leaderUserId: "leader",
    profitRate: "40.25",
    salesExpenseAmount: "999999999999999",
    expectedRevision: "revision",
  });
});
it("중복월과 최소월 이전을 필드에서 차단한다", async () => {
  draw();
  await screen.findByText("시험 영업파트");
  fireEvent.change(screen.getByLabelText(/적용 시작월/), {
    target: { value: "2026-01" },
  });
  fireEvent.submit(screen.getByRole("dialog"));
  await waitFor(() =>
    expect(screen.getByLabelText(/적용 시작월/)).toHaveAttribute(
      "aria-invalid",
      "true",
    ),
  );
  expect(api.createSalesPartStandard).not.toHaveBeenCalled();
});
it("소수 세자리 입력은 자동 반올림하거나 저장하지 않는다", async () => {
  draw();
  await screen.findByText("시험 영업파트");
  fireEvent.change(screen.getByLabelText(/이익 배분율/), {
    target: { value: "40.001" },
  });
  fireEvent.submit(screen.getByRole("dialog"));
  await waitFor(() =>
    expect(screen.getByLabelText(/이익 배분율/)).toHaveAttribute(
      "aria-invalid",
      "true",
    ),
  );
  expect(screen.getByLabelText(/이익 배분율/)).toHaveValue("40.001");
  expect(api.createSalesPartStandard).not.toHaveBeenCalled();
});
it("stale 오류는 입력을 보존하고 자동 재전송하지 않는다", async () => {
  vi.mocked(api.createSalesPartStandard).mockRejectedValue(
    new ApiError(409, {
      success: false,
      meta: {},
      code: "STALE_WRITE",
      message: "다른 사용자가 변경했습니다.",
      requestId: "part-stale",
    }),
  );
  draw();
  await screen.findByText("시험 영업파트");
  fireEvent.change(screen.getByLabelText(/이익 배분율/), {
    target: { value: "45" },
  });
  await userEvent.click(screen.getByRole("button", { name: "변경 확인" }));
  await userEvent.click(screen.getByRole("button", { name: "확인 후 저장" }));
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "다른 사용자가 변경했습니다.",
  );
  expect(screen.getByLabelText(/이익 배분율/)).toHaveValue("45");
  expect(api.createSalesPartStandard).toHaveBeenCalledTimes(1);
});
it("저장 후 갱신 실패는 저장하지 않고 조회만 다시 수행한다", async () => {
  const onSaved = vi
    .fn()
    .mockRejectedValueOnce(new Error("조회 실패"))
    .mockResolvedValue(undefined);
  draw(onSaved);
  await screen.findByText("시험 영업파트");
  await userEvent.click(screen.getByRole("button", { name: "변경 확인" }));
  await userEvent.click(screen.getByRole("button", { name: "확인 후 저장" }));
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "저장됐지만 다시 불러오지 못했습니다",
  );
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  expect(onSaved).toHaveBeenCalledTimes(2);
  expect(api.createSalesPartStandard).toHaveBeenCalledTimes(1);
});
it("dirty 취소는 계속 작성 또는 나가기를 확인한다", async () => {
  const { onClose } = draw();
  await screen.findByText("시험 영업파트");
  fireEvent.change(screen.getByLabelText(/이익 배분율/), {
    target: { value: "45" },
  });
  await userEvent.click(screen.getByRole("button", { name: "취소" }));
  expect(
    screen.getByText("저장하지 않은 내용이 있습니다."),
  ).toBeInTheDocument();
  await userEvent.click(screen.getByRole("button", { name: "계속 작성" }));
  expect(onClose).not.toHaveBeenCalled();
  expect(screen.getByLabelText(/이익 배분율/)).toHaveValue("45");
});
it("조회 실패시 재시도는 조회만 하고 저장은 차단한다", async () => {
  vi.mocked(api.getPartStandardDetail).mockRejectedValue(
    new Error("연결 실패"),
  );
  draw();
  expect(await screen.findByRole("alert")).toHaveTextContent("연결 실패");
  expect(screen.getByRole("button", { name: "변경 확인" })).toBeDisabled();
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  expect(api.getPartStandardDetail).toHaveBeenCalledTimes(2);
  expect(api.createSalesPartStandard).not.toHaveBeenCalled();
});
it("추가 기본월은 서버값을 쓰며 파트장은 빈 선택으로 유지한다", async () => {
  vi.mocked(api.getSalesPartDefaults).mockResolvedValue({
    ...defaults,
    effectiveMonth: "2029-01",
    minMonth: "2029-01",
    profitRate: null,
    defaultErrors: [
      {
        source: "PART_DSTRB_RT",
        field: "profitRate",
        message: "직접 입력해 주세요.",
      },
    ],
  });
  const onChange = vi.fn();
  render(
    <MemoryRouter>
      <SalesPartDefaultsFields onChange={onChange} />
    </MemoryRouter>,
  );
  await waitFor(() =>
    expect(screen.getByLabelText(/적용 시작월/)).toHaveValue("2029-01"),
  );
  expect(
    screen.getByRole("button", { name: "파트장: 선택해 주세요" }),
  ).toBeInTheDocument();
  expect(screen.getByLabelText(/이익 배분율/)).toHaveValue("");
  expect(screen.getByRole("alert")).toHaveClass("sicost-tone-warn");
  fireEvent.change(screen.getByLabelText(/이익 배분율/), {
    target: { value: "45" },
  });
  await waitFor(() =>
    expect(onChange).toHaveBeenLastCalledWith({
      effectiveMonth: "2029-01",
      leaderUserId: "",
      profitRate: "45",
      salesExpenseAmount: "1500000",
    }),
  );
});
it("파트 옵션은 본사와 미래 일반파트를 구분하고 다음 열기 최신 조회한다", async () => {
  vi.mocked(api.getSalesPartOptions).mockResolvedValue({
    items: [
      { id: "HQ", name: "본사", hqYn: "Y", firstEffectiveMonth: null },
      {
        id: "PART-test",
        name: "미래파트",
        hqYn: "N",
        firstEffectiveMonth: "2029-01",
      },
    ],
  });
  const change = vi.fn();
  render(<SalesPartSelect value="" onChange={change} />);
  await userEvent.click(screen.getByRole("button"));
  await screen.findByRole("option", { name: "미래파트 · 최초 시작 2029-01" });
  fireEvent.change(screen.getByLabelText("영업 파트 선택"), {
    target: { value: "PART-test" },
  });
  expect(change).toHaveBeenCalledWith("PART-test");
  expect(screen.getByRole("group", { name: "본사" })).toBeInTheDocument();
  await userEvent.click(screen.getByRole("button"));
  await userEvent.click(screen.getByRole("button"));
  await waitFor(() => expect(api.getSalesPartOptions).toHaveBeenCalledTimes(2));
});
it("파트 옵션 빈 목록과 실패는 다른 상태를 표시한다", async () => {
  vi.mocked(api.getSalesPartOptions)
    .mockRejectedValueOnce(new Error("파트 조회 실패"))
    .mockResolvedValue({ items: [] });
  render(<SalesPartSelect value="" onChange={vi.fn()} />);
  await userEvent.click(screen.getByRole("button"));
  expect(await screen.findByRole("alert")).toHaveTextContent("파트 조회 실패");
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  expect(
    await screen.findByText("선택할 수 있는 파트가 없습니다."),
  ).toHaveClass("sicost-empty");
});
