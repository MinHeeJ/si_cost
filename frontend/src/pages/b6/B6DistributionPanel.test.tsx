import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { B6DistributionPanel } from "./B6DistributionPanel";
import { b6Amount } from "./B6ComparisonPanel";
import { ApiError } from "../../shared/api";
import * as api from "./b6Api";
vi.mock("./b6Api");
const data: api.Distribution = {
  project: {
    id: "P-selected",
    name: "현재 프로젝트",
    contractType: "TK",
    status: "ING",
    startDate: "2026-06-01",
    endDate: "2026-07-31",
    contractAmount: "100",
  },
  viewedStep: "PLAN",
  selectedStep: "PLAN",
  months: ["2026-06", "2026-07"],
  rows: [
    {
      rowSn: 1,
      rowType: "DEV",
      developerId: "D-selected",
      managementNo: "000123",
      name: "중지 인력",
      itemName: null,
      jobNames: ["PM", "백엔드"],
      useYn: "N",
      cells: [
        { month: "2026-06", amount: "100" },
        { month: "2026-07", amount: "0" },
      ],
      totalAmount: "100",
    },
  ],
  monthlyTotals: [
    { month: "2026-06", amount: "100" },
    { month: "2026-07", amount: "0" },
  ],
  totalAmount: "100",
  revision: "current",
};
const comparison: api.B6Comparison = {
  selectedStep: "PLAN",
  contractAmount: "100",
  totalAmount: "100",
  differenceAmount: "0",
  mismatchCount: 1,
  items: [
    {
      developerId: "D-selected",
      managementNo: "000123",
      name: "중지 인력",
      month: "2026-06",
      type: "NO_ASSIGNMENT",
      amount: "100",
      actualMm: "0.00",
    },
  ],
  warnings: ["불일치 1건"],
};
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api.getDistribution).mockResolvedValue(data);
  vi.mocked(api.getDistributionComparison).mockResolvedValue(comparison);
  vi.mocked(api.getDistributionUploads).mockResolvedValue({ items: [] });
});
function draw(readOnly = false) {
  const onBack = vi.fn();
  render(
    <B6DistributionPanel
      projectId="P-selected"
      year={2026}
      customerName="현재 고객"
      readOnly={readOnly}
      onBack={onBack}
    />,
  );
  return onBack;
}
it("전체영역은 프로젝트정보·단계 조회탭·월행렬·합계·중지/직무·불일치N을 API에서 표시한다", async () => {
  const back = draw();
  await screen.findByText("턴키 배분표 · 현재 프로젝트");
  expect(screen.getByTestId("b6-project-info")).toHaveTextContent("현재 고객");
  expect(screen.getByText("PM·백엔드")).toBeInTheDocument();
  expect(screen.getByText("중지")).toHaveClass("sicost-tone-lock");
  expect(screen.getByText("배정 없음")).toBeInTheDocument();
  expect(screen.getByText(/선택표 총액/)).toHaveTextContent("불일치 1건");
  expect(screen.getByTestId("b6-month-total-2026-07")).toHaveTextContent("0");
  expect(screen.queryByRole("spinbutton")).not.toBeInTheDocument();
  await userEvent.click(screen.getByRole("tab", { name: "EST 견적" }));
  await waitFor(() =>
    expect(api.getDistribution).toHaveBeenLastCalledWith("P-selected", "EST"),
  );
  expect(screen.getByText(/계산에 쓰는 단계/)).toHaveTextContent("PLAN");
  expect(screen.queryByRole("switch")).not.toBeInTheDocument();
  await userEvent.click(screen.getByRole("button", { name: "목록" }));
  expect(back).toHaveBeenCalledTimes(1);
});
it("조회 탭 변경은 쓰기 요청 없이 선택된 조회단계 전체를 출력한다", async () => {
  draw();
  await screen.findByText("턴키 배분표 · 현재 프로젝트");
  await userEvent.click(screen.getByRole("tab", { name: "CTRT 최종 계약" }));
  await waitFor(() =>
    expect(api.getDistribution).toHaveBeenLastCalledWith("P-selected", "CTRT"),
  );
  await userEvent.selectOptions(screen.getByLabelText("금액 단위"), "백만원");
  await userEvent.click(
    screen.getByRole("button", { name: "배분표 다운로드" }),
  );
  expect(api.exportDistribution).toHaveBeenCalledWith(
    "P-selected",
    "CTRT",
    "백만원",
  );
  expect(api.saveB6Apply).not.toHaveBeenCalled();
});
it("미등록 표는 빈 안내를 보이고 업로드 진입만 제공한다", async () => {
  vi.mocked(api.getDistribution).mockResolvedValue({
    ...data,
    rows: [],
    selectedStep: null,
    totalAmount: "0",
  });
  draw();
  expect(
    await screen.findByText(/등록된 배분표가 없습니다/),
  ).toBeInTheDocument();
  expect(screen.queryByText("행 추가")).not.toBeInTheDocument();
  expect(screen.getByRole("button", { name: "엑셀 업로드" })).toBeEnabled();
});
it.each(["past", "LOSE"])(
  "%s는 업로드만 차단하고 조회/양식은 허용한다",
  async (state) => {
    if (state === "LOSE")
      vi.mocked(api.getDistribution).mockResolvedValue({
        ...data,
        project: { ...data.project, status: "LOSE" },
      });
    draw(state === "past");
    await screen.findByText("턴키 배분표 · 현재 프로젝트");
    expect(screen.getByRole("button", { name: "엑셀 업로드" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "양식 받기" })).toBeEnabled();
  },
);
it("실패는 문의번호·재조회로 복구하며 비TK는 진입 불가 안내다", async () => {
  vi.mocked(api.getDistribution).mockRejectedValueOnce(
    new ApiError(500, {
      success: false,
      meta: {},
      code: "INTERNAL_ERROR",
      message: "조회 실패",
      requestId: "b6-read",
    }),
  );
  draw();
  expect(await screen.findByRole("alert")).toHaveTextContent("b6-read");
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  await screen.findByText("턴키 배분표 · 현재 프로젝트");
});
it("최초 반영기록의 당시 단계/요약을 결과 API로 조회한다", async () => {
  vi.mocked(api.getDistributionUploads).mockResolvedValue({
    items: [
      {
        uploadId: 12,
        step: "EST",
        fileName: "old.xlsx",
        actor: "처리자",
        registeredAt: "2026-06-01 12:00",
        status: "APPLIED",
        appliedAt: "2026-06-01 12:01",
      },
    ],
  });
  vi.mocked(api.getUploadResult).mockResolvedValue({
    uploadId: 12,
    formType: "B6",
    status: "APPLIED",
    projectId: "P-selected",
    step: "EST",
    summary: {
      totalRows: 1,
      successRows: 1,
      warningRows: 0,
      errorRows: 0,
      fileWarningCount: 0,
      tableWarningCount: 0,
    },
    results: [],
    revision: "original",
    changed: true,
  });
  draw();
  await screen.findByText("old.xlsx");
  await userEvent.click(screen.getByRole("button", { name: "결과 보기" }));
  expect(await screen.findByTestId("b6-history-result")).toHaveTextContent(
    "당시 단계 EST",
  );
  expect(api.getUploadResult).toHaveBeenCalledWith(12);
});
it("큰 원합계와 음수 차이는 Number 변환 없이 단위별 정확히 표시한다", () => {
  expect(b6Amount("1999999999999998", "원")).toBe("1,999,999,999,999,998");
  expect(b6Amount("1999999999999998", "천원")).toBe("1,999,999,999,999.998");
  expect(b6Amount("-1", "백만원")).toBe("−0.000001");
  expect(b6Amount("0", "백만원")).toBe("0");
});
