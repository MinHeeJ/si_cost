import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, expect, it, vi } from "vitest";
import { ApiError } from "../../shared/api";
import { SalesPartsScreen } from "./SalesPartsScreen";
import * as api from "./salesPartsApi";
import * as options from "../businessusersops/businessUserOptionsApi";
vi.mock("./salesPartsApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("./salesPartsApi")>()),
  getSalesParts: vi.fn(),
  getSalesPart: vi.fn(),
  getSalesPartDefaults: vi.fn(),
  createSalesPart: vi.fn(),
  putSalesPart: vi.fn(),
  downloadSalesParts: vi.fn(),
}));
vi.mock("../businessusersops/businessUserOptionsApi");
const standard: api.SalesPartStandard = {
  effectiveMonth: "2026-01",
  endMonth: null,
  leaderUserId: "leader",
  leaderName: "파트장",
  profitRate: "40.00",
  salesExpenseAmount: "999999999999999",
  status: "CURRENT",
};
const part: api.SalesPart = {
  id: "PART-101",
  name: "영업파트",
  useYn: "Y",
  currentMonth: "2026-06",
  currentStandard: standard,
  scheduledChange: null,
  personCount: 2,
  projectCount: 1,
  revision: "full-revision",
};
const detail: api.SalesPartDetail = {
  part,
  standards: [standard],
  minMonth: "2026-01",
  revision: part.revision,
};
function show() {
  return render(
    <MemoryRouter>
      <SalesPartsScreen />
    </MemoryRouter>,
  );
}
function failure(code = "VALIDATION_ERROR", field = "name") {
  return new ApiError(409, {
    success: false,
    meta: {},
    code,
    field,
    message: "저장할 수 없습니다.",
    requestId: "request-123",
  });
}
beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(api.getSalesParts).mockResolvedValue({
    items: [part],
    total: 1,
    page: 1,
    size: 50,
  });
  vi.mocked(api.getSalesPart).mockResolvedValue(detail);
  vi.mocked(api.getSalesPartDefaults).mockResolvedValue({
    effectiveMonth: "2026-06",
    minMonth: "2026-01",
    profitRate: "40",
    salesExpenseAmount: "1500000",
    defaultErrors: [],
  });
  vi.mocked(api.putSalesPart).mockResolvedValue({
    changed: true,
    message: "저장했습니다.",
    part: detail,
  });
  vi.mocked(api.createSalesPart).mockResolvedValue({
    changed: true,
    message: "등록했습니다.",
    part: detail,
  });
  vi.mocked(options.getBusinessUserOptions).mockResolvedValue({
    items: [{ userId: "leader", name: "파트장", label: "파트장" }],
    roleOptions: [],
  });
});
it("초기 Y 필터와 고정 9열, 정밀 금액과 사용 배지를 표시한다", async () => {
  show();
  await screen.findByRole("button", { name: "영업파트" });
  expect(api.getSalesParts).toHaveBeenCalledWith(
    expect.objectContaining({ useYn: "Y", page: 1 }),
    expect.any(AbortSignal),
  );
  expect(screen.getAllByRole("columnheader")).toHaveLength(9);
  expect(screen.getByText("999,999,999,999.999")).toBeInTheDocument();
  expect(screen.getByText("사용", { selector: ".sicost-badge" })).toHaveClass(
    "sicost-tone-ok",
  );
});
it("전체는 useYn을 생략하고 Enter로 검색하며 정렬 변경은 첫 페이지다", async () => {
  show();
  await screen.findByRole("button", { name: "영업파트" });
  await userEvent.selectOptions(
    screen.getByTestId("sales-part-status-filter"),
    "",
  );
  fireEvent.change(screen.getByLabelText("파트명 검색"), {
    target: { value: " 영업 " },
  });
  await userEvent.click(screen.getByTestId("sales-part-search-button"));
  await waitFor(() =>
    expect(api.getSalesParts).toHaveBeenLastCalledWith(
      expect.objectContaining({ useYn: undefined, q: "영업", page: 1 }),
      expect.any(AbortSignal),
    ),
  );
  await userEvent.click(screen.getByTestId("sales-part-sort-profit-rate"));
  await waitFor(() =>
    expect(api.getSalesParts).toHaveBeenLastCalledWith(
      expect.objectContaining({ sort: "profitRate", page: 1 }),
      expect.any(AbortSignal),
    ),
  );
});
it("미래 기준을 현재값으로 표시하지 않는다", async () => {
  vi.mocked(api.getSalesParts).mockResolvedValue({
    items: [
      {
        ...part,
        currentStandard: null,
        scheduledChange: {
          ...standard,
          effectiveMonth: "2027-01",
          status: "FUTURE",
        },
      },
    ],
    total: 1,
    page: 1,
    size: 50,
  });
  show();
  expect(await screen.findByText("적용 전")).toHaveClass("sicost-tone-info");
  expect(screen.getByText("예정")).toHaveClass("sicost-tone-warn");
});
it("빈 목록은 조건 초기화를 제공하고 삭제 UI는 없다", async () => {
  vi.mocked(api.getSalesParts).mockResolvedValue({
    items: [],
    total: 0,
    page: 1,
    size: 50,
  });
  show();
  expect(
    await screen.findByText("조회된 자료가 없습니다."),
  ).toBeInTheDocument();
  expect(
    screen.getByRole("button", { name: "조건 초기화" }),
  ).toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: "삭제" }),
  ).not.toBeInTheDocument();
});
it("조회 실패는 빈 목록과 구분하고 수동 재시도한다", async () => {
  vi.mocked(api.getSalesParts).mockRejectedValueOnce(new Error("목록 오류"));
  show();
  expect(await screen.findByRole("alert")).toHaveTextContent("목록 오류");
  expect(screen.queryByText("조회된 자료가 없습니다.")).not.toBeInTheDocument();
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  await screen.findByRole("button", { name: "영업파트" });
  expect(api.getSalesParts).toHaveBeenCalledTimes(2);
});
it("상세 조회 실패는 저장하지 않고 상세만 재조회한다", async () => {
  vi.mocked(api.getSalesPart).mockRejectedValueOnce(new Error("상세 오류"));
  show();
  await userEvent.click(
    await screen.findByRole("button", { name: "영업파트" }),
  );
  expect(await screen.findByRole("alert")).toHaveTextContent("상세 오류");
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  await screen.findByTestId("sales-part-detail-dialog");
  expect(api.putSalesPart).not.toHaveBeenCalled();
});
it("이름 수정은 이름과 revision만 전송하고 성공 뒤 목록을 갱신한다", async () => {
  show();
  await userEvent.click(
    await screen.findByRole("button", { name: "영업파트" }),
  );
  await userEvent.click(
    await screen.findByRole("button", { name: "이름 수정" }),
  );
  fireEvent.change(screen.getByLabelText("파트명 수정"), {
    target: { value: "새 이름" },
  });
  await userEvent.click(screen.getByRole("button", { name: "이름 저장" }));
  await waitFor(() =>
    expect(api.putSalesPart).toHaveBeenCalledWith("PART-101", {
      name: "새 이름",
      expectedRevision: "full-revision",
    }),
  );
  await waitFor(() => expect(api.getSalesParts).toHaveBeenCalledTimes(2));
});
it("실패한 이름은 보존되고 필드 사유와 요청 번호를 표시한다", async () => {
  vi.mocked(api.putSalesPart).mockRejectedValueOnce(
    failure("DUPLICATE_PART_NAME"),
  );
  show();
  await userEvent.click(
    await screen.findByRole("button", { name: "영업파트" }),
  );
  await userEvent.click(
    await screen.findByRole("button", { name: "이름 수정" }),
  );
  fireEvent.change(screen.getByLabelText("파트명 수정"), {
    target: { value: "중복 이름" },
  });
  await userEvent.click(screen.getByRole("button", { name: "이름 저장" }));
  expect(await screen.findByText(/문의 번호: request-123/)).toBeInTheDocument();
  expect(screen.getByLabelText("파트명 수정")).toHaveValue("중복 이름");
  expect(api.putSalesPart).toHaveBeenCalledTimes(1);
});
it("중지 확인 취소는 요청 0건, 확인은 useYn만 전송한다", async () => {
  show();
  await userEvent.click(
    await screen.findByRole("button", { name: "영업파트" }),
  );
  await userEvent.click(await screen.findByTestId("sales-part-status-change"));
  await userEvent.click(
    within(screen.getByRole("dialog", { name: "영업 파트 중지" })).getByRole(
      "button",
      { name: "취소" },
    ),
  );
  expect(api.putSalesPart).not.toHaveBeenCalled();
  await userEvent.click(screen.getByTestId("sales-part-status-change"));
  await userEvent.click(screen.getByRole("button", { name: "확인 후 변경" }));
  await waitFor(() =>
    expect(api.putSalesPart).toHaveBeenCalledWith("PART-101", {
      useYn: "N",
      expectedRevision: "full-revision",
    }),
  );
});
it("중지된 파트도 전체 기준 이력과 이름 수정·재개를 제공한다", async () => {
  vi.mocked(api.getSalesPart).mockResolvedValue({
    ...detail,
    part: { ...part, useYn: "N" },
  });
  show();
  await userEvent.click(
    await screen.findByRole("button", { name: "영업파트" }),
  );
  expect(await screen.findByRole("button", { name: "재개" })).toBeEnabled();
  await userEvent.click(screen.getByRole("tab", { name: "기준 이력" }));
  expect(screen.getByText("적용 중")).toHaveClass("sicost-tone-ok");
  expect(screen.getByRole("button", { name: "이름 수정" })).toBeEnabled();
});
it("영업 파트 상세는 오른쪽 패널로 열리고 파트명·ID·사용 배지를 머리에 둔다", async () => {
  show();
  await userEvent.click(
    await screen.findByRole("button", { name: "영업파트" }),
  );
  const drawer = await screen.findByRole("dialog", { name: "영업 파트 상세" });
  expect(drawer.tagName).toBe("ASIDE");
  expect(drawer).toHaveClass("sicost-drawer");
  const header = drawer.querySelector("header") as HTMLElement;
  expect(within(header).getByRole("heading", { name: "영업파트" })).toBeInTheDocument();
  expect(within(header).getByText(part.id)).toBeInTheDocument();
  expect(within(header).getByText("사용")).toHaveClass("sicost-tone-ok");
  await userEvent.click(within(header).getByRole("button", { name: "닫기" }));
  expect(screen.queryByRole("dialog", { name: "영업 파트 상세" })).not.toBeInTheDocument();
});
it("미저장 이름의 하위 탭 전환은 계속 작성 또는 나가기를 확인한다", async () => {
  show();
  await userEvent.click(
    await screen.findByRole("button", { name: "영업파트" }),
  );
  await userEvent.click(
    await screen.findByRole("button", { name: "이름 수정" }),
  );
  fireEvent.change(screen.getByLabelText("파트명 수정"), {
    target: { value: "편집중" },
  });
  await userEvent.click(screen.getByRole("tab", { name: "기준 이력" }));
  expect(
    screen.getByRole("dialog", { name: "저장하지 않은 내용이 있습니다." }),
  ).toBeInTheDocument();
  await userEvent.click(screen.getByRole("button", { name: "계속 작성" }));
  expect(screen.getByLabelText("파트명 수정")).toHaveValue("편집중");
  expect(api.putSalesPart).not.toHaveBeenCalled();
});
it("등록은 빈 파트장으로 시작하고 유효 입력만 한 번 전송한다", async () => {
  show();
  await userEvent.click(screen.getByRole("button", { name: "파트 추가" }));
  await waitFor(() =>
    expect(screen.getByLabelText(/이익 배분율/)).toHaveValue("40"),
  );
  expect(
    screen.getByRole("button", { name: "파트장: 선택해 주세요" }),
  ).toBeInTheDocument();
  fireEvent.change(screen.getByTestId("sales-part-name-input"), {
    target: { value: "신규파트" },
  });
  await userEvent.click(screen.getByRole("button", { name: "등록" }));
  expect(
    await screen.findByText("파트장을 선택해 주세요."),
  ).toBeInTheDocument();
  expect(api.createSalesPart).not.toHaveBeenCalled();
  await userEvent.click(
    screen.getByRole("button", { name: "파트장: 선택해 주세요" }),
  );
  await userEvent.selectOptions(
    await screen.findByLabelText("파트장"),
    "leader",
  );
  fireEvent.change(screen.getByLabelText(/공통관리비 \(자사/), {
    target: { value: "999,999,999,999,999원" },
  });
  await userEvent.click(screen.getByRole("button", { name: "등록" }));
  await waitFor(() =>
    expect(api.createSalesPart).toHaveBeenCalledWith({
      name: "신규파트",
      effectiveMonth: "2026-06",
      leaderUserId: "leader",
      profitRate: "40",
      salesExpenseAmount: "999999999999999",
    }),
  );
});
it("월 변경 시 미편집 기본값만 갱신하고 수동율은 유지한다", async () => {
  show();
  await userEvent.click(screen.getByRole("button", { name: "파트 추가" }));
  await waitFor(() =>
    expect(screen.getByLabelText(/이익 배분율/)).toHaveValue("40"),
  );
  fireEvent.change(screen.getByLabelText(/이익 배분율/), {
    target: { value: "25.25" },
  });
  vi.mocked(api.getSalesPartDefaults).mockResolvedValueOnce({
    effectiveMonth: "2026-07",
    minMonth: "2026-01",
    profitRate: "50",
    salesExpenseAmount: "2000000",
    defaultErrors: [],
  });
  fireEvent.change(screen.getByLabelText(/적용 시작월/), {
    target: { value: "2026-07" },
  });
  await waitFor(() =>
    expect(screen.getByLabelText(/공통관리비 \(자사/)).toHaveValue("2000000"),
  );
  expect(screen.getByLabelText(/이익 배분율/)).toHaveValue("25.25");
});
it("기본값 오류는 빈칸·원천 사유를 표시하고 수동 입력은 가능하다", async () => {
  vi.mocked(api.getSalesPartDefaults).mockResolvedValueOnce({
    effectiveMonth: "2026-06",
    minMonth: "2026-01",
    profitRate: null,
    salesExpenseAmount: null,
    defaultErrors: [
      { source: "PART_DSTRB_RT", field: "profitRate", message: "기준값 없음" },
    ],
  });
  show();
  await userEvent.click(screen.getByRole("button", { name: "파트 추가" }));
  expect(
    await screen.findByText(/PART_DSTRB_RT: 기준값 없음/),
  ).toBeInTheDocument();
  expect(screen.getByLabelText(/이익 배분율/)).toHaveValue("");
  expect(screen.getByLabelText(/이익 배분율/)).not.toBeDisabled();
});
it("저장 뒤 목록 조회 실패는 재저장을 유도하지 않는다", async () => {
  vi.mocked(api.getSalesParts)
    .mockResolvedValueOnce({ items: [part], total: 1, page: 1, size: 50 })
    .mockRejectedValueOnce(new Error("조회 실패"));
  show();
  await userEvent.click(
    await screen.findByRole("button", { name: "영업파트" }),
  );
  await userEvent.click(
    await screen.findByRole("button", { name: "이름 수정" }),
  );
  fireEvent.change(screen.getByLabelText("파트명 수정"), {
    target: { value: "변경" },
  });
  await userEvent.click(screen.getByRole("button", { name: "이름 저장" }));
  expect(
    await screen.findByText("저장됐지만 다시 불러오지 못했습니다."),
  ).toBeInTheDocument();
  expect(api.putSalesPart).toHaveBeenCalledTimes(1);
});
