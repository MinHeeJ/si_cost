import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { CandidatesScreen } from "./CandidatesScreen";
import { CandidateForm } from "./CandidateForm";
import * as api from "./peopleClient";
import { ApiError, request } from "../../shared/api";
import { getCandidateSummary } from "../candidatesops/candidateSummaryApi";
import { getCandidateContacts } from "../contacts/peopleClient";
vi.mock("./peopleClient", () => ({
  getCandidates: vi.fn(),
  getCandidate: vi.fn(),
  createCandidate: vi.fn(),
  putCandidate: vi.fn(),
  getCandidateCodes: vi.fn(),
  getCandidateDate: vi.fn(),
  downloadCandidates: vi.fn(),
}));
vi.mock("../../shared/api", async (original) => ({
  ...(await original<typeof import("../../shared/api")>()),
  request: vi.fn(async () => ({ items: [] })),
}));
vi.mock("../candidatesops/candidateSummaryApi", () => ({
  getCandidateSummary: vi.fn(async () => ({
    total: 0,
    inProgress: 0,
    interviewScheduled: 0,
    availableByNextMonthStart: 0,
    overdueContact: 0,
  })),
}));
vi.mock("../contacts/peopleClient", () => ({
  getCandidateContacts: vi.fn(async () => ({
    items: [],
    total: 0,
    page: 1,
    size: 50,
  })),
  createCandidateContact: vi.fn(),
  getCandidateContactContext: vi.fn(),
}));
const detail: api.CandidateDetail = {
  id: "CAND-900001",
  name: "시험 후보",
  employeeType: "FL",
  companyId: null,
  companyName: null,
  phone: null,
  email: null,
  consent: false,
  consentDate: null,
  consentUserId: null,
  consentUserName: null,
  job: "개발",
  jobCode: "DEV",
  subJobCode: null,
  grade: "중급",
  gradeCode: "MD",
  homeAreaCode: null,
  homeAddress: null,
  addressPath: { items: [], homeAddress: null },
  desiredRate: "1",
  availableDate: "2026-07-16",
  source: "구인 사이트",
  sourceCode: "SITE",
  contactStatus: "BEFORE",
  lastContactDate: "2026-05-01",
  memo: "자체 메모",
  skills: ["Java"],
  revision: "current",
};
const empty: api.CandidatesPage = { items: [], total: 0, page: 1, size: 50 };
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(request).mockResolvedValue({ items: [] });
  vi.mocked(getCandidateSummary).mockResolvedValue({
    total: 0,
    inProgress: 0,
    interviewScheduled: 0,
    availableByNextMonthStart: 0,
    overdueContact: 0,
  });
  vi.mocked(getCandidateContacts).mockResolvedValue({
    items: [],
    total: 0,
    page: 1,
    size: 50,
  });
  vi.stubGlobal(
    "matchMedia",
    vi.fn(() => ({
      matches: false,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })),
  );
  vi.stubGlobal("scrollTo", vi.fn());
  vi.mocked(api.getCandidates).mockResolvedValue(empty);
  vi.mocked(api.getCandidate).mockResolvedValue(detail);
  vi.mocked(api.getCandidateDate).mockResolvedValue({
    currentDate: "2026-06-16",
  });
  vi.mocked(api.getCandidateCodes).mockImplementation(async (group) => ({
    items:
      group === "JOB"
        ? [{ code: "DEV", name: "개발" }]
        : group === "GRADE"
          ? [{ code: "MD", name: "중급" }]
          : group === "CAND_SRC"
            ? [{ code: "SITE", name: "구인 사이트" }]
            : group === "EMP_TYPE"
              ? [
                  { code: "FL", name: "프리랜서" },
                  { code: "OS", name: "외주" },
                ]
              : [
                  { code: "BEFORE", name: "연락 전" },
                  { code: "HOLD", name: "보류" },
                ],
  }));
  vi.mocked(api.createCandidate).mockResolvedValue({ id: detail.id });
  vi.mocked(api.putCandidate).mockResolvedValue({ changed: true });
});
async function begin() {
  await userEvent.click(screen.getByRole("button", { name: "후보 등록" }));
  await screen.findByDisplayValue("2026-07-16");
}
async function fill() {
  await userEvent.type(screen.getByLabelText("이름 *"), "새 후보");
  await userEvent.selectOptions(screen.getByLabelText("주 직무 *"), "DEV");
  await userEvent.type(screen.getByLabelText("보유 스킬"), "Java, SQL");
  await userEvent.type(screen.getByLabelText("희망 단가 (원/월) *"), "1");
}
it("renders loading then real empty state without out-of-scope controls", async () => {
  let resolve!: (page: api.CandidatesPage) => void;
  vi.mocked(api.getCandidates).mockReturnValue(
    new Promise((yes) => {
      resolve = yes;
    }),
  );
  render(<CandidatesScreen />);
  expect(screen.getByText("불러오는 중입니다.")).toBeInTheDocument();
  await act(async () => resolve(empty));
  expect(
    await screen.findByText("조회된 후보가 없습니다."),
  ).toBeInTheDocument();
  expect(
    screen.queryByRole("button", {
      name: /추천|맞는 자리|부족 직무|삭제|전환|일괄/,
    }),
  ).not.toBeInTheDocument();
});
it("request failure shows requestId and retries reads only", async () => {
  vi.mocked(api.getCandidates).mockRejectedValueOnce(
    new ApiError(500, {
      success: false,
      meta: {},
      code: "INTERNAL_ERROR",
      message: "목록 실패",
      requestId: "read-request",
    }),
  );
  render(<CandidatesScreen />);
  expect(await screen.findByText("목록 실패")).toBeInTheDocument();
  expect(screen.getByText(/read-request/)).toBeInTheDocument();
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  await screen.findByText("조회된 후보가 없습니다.");
  expect(api.createCandidate).not.toHaveBeenCalled();
});
it("starts with FL MD SITE server today plus thirty and never auto-checks consent", async () => {
  render(<CandidatesScreen />);
  await begin();
  expect(screen.getByLabelText("구분 *")).toHaveValue("FL");
  expect(screen.getByLabelText("등급 *")).toHaveValue("MD");
  expect(screen.getByLabelText("출처 *")).toHaveValue("SITE");
  await userEvent.type(screen.getByLabelText("전화번호"), "010-1234-5678");
  await userEvent.type(screen.getByLabelText("이메일"), "s@test.com");
  expect(screen.getByTestId("candidate-consent")).not.toBeChecked();
});
it("focuses Korean first error without posting an invalid form", async () => {
  render(<CandidatesScreen />);
  await begin();
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  expect(
    await screen.findByText("이름은 1~50자로 입력해 주세요."),
  ).toBeInTheDocument();
  expect(screen.getByLabelText("이름 *")).toHaveFocus();
  expect(api.createCandidate).not.toHaveBeenCalled();
});
it("requires deliberate consent when either contact exists", async () => {
  render(<CandidatesScreen />);
  await begin();
  await fill();
  await userEvent.type(screen.getByLabelText("이메일"), "s@test.com");
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  expect(
    await screen.findByText("연락처가 있으면 개인정보 동의를 확인해 주세요."),
  ).toBeInTheDocument();
  expect(screen.getByTestId("candidate-consent")).not.toBeChecked();
  expect(api.createCandidate).not.toHaveBeenCalled();
});
it("cancel and retained screen changes preserve dirty input until explicit discard", async () => {
  const dirty = vi.fn(),
    view = render(<CandidatesScreen onDirtyChange={dirty} />);
  await begin();
  await userEvent.type(screen.getByLabelText("이름 *"), "보존");
  await userEvent.click(screen.getByRole("button", { name: "취소" }));
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  await begin();
  expect(screen.getByLabelText("이름 *")).toHaveValue("보존");
  view.rerender(<CandidatesScreen active={false} onDirtyChange={dirty} />);
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  view.rerender(<CandidatesScreen active onDirtyChange={dirty} />);
  expect(screen.getByLabelText("이름 *")).toHaveValue("보존");
  expect(api.createCandidate).not.toHaveBeenCalled();
});
it("successful registration refreshes immediately, closes modal, highlights once then returns to normal", async () => {
  vi.mocked(api.getCandidates).mockResolvedValue({
    ...empty,
    items: [detail],
    total: 1,
  });
  render(<CandidatesScreen />);
  await begin();
  await fill();
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  await waitFor(() =>
    expect(api.createCandidate).toHaveBeenCalledWith(
      expect.objectContaining({
        employeeType: "FL",
        gradeCode: "MD",
        sourceCode: "SITE",
        consent: false,
        desiredRate: "1",
        skills: ["Java", "SQL"],
        availableDate: "2026-07-16",
      }),
    ),
  );
  await waitFor(() =>
    expect(
      screen.queryByRole("dialog", { name: "외부 인력 후보 등록" }),
    ).not.toBeInTheDocument(),
  );
  expect(api.getCandidate).toHaveBeenCalledWith(detail.id);
  const name = await screen.findByTestId("candidate-name-cand-900001");
  expect(name).toHaveClass("candidate-new");
  await waitFor(() => expect(name).not.toHaveClass("candidate-new"), {
    timeout: 3000,
  });
});
it("committed refresh failure offers only re-query and never repeats POST", async () => {
  vi.mocked(api.getCandidate).mockRejectedValueOnce(new Error("조회 실패"));
  render(<CandidatesScreen />);
  await begin();
  await fill();
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  await screen.findByText(/저장은 완료됐습니다/);
  expect(api.createCandidate).toHaveBeenCalledTimes(1);
  expect(screen.getByRole("button", { name: "후보 등록" })).toBeDisabled();
  await userEvent.click(
    screen.getByRole("button", { name: "저장된 후보 다시 조회" }),
  );
  await screen.findByRole("dialog", { name: "시험 후보" });
  expect(api.createCandidate).toHaveBeenCalledTimes(1);
});
it("edit uses retrieved revision and consent, preserves recent contact in read-only detail", async () => {
  vi.mocked(api.getCandidates).mockResolvedValue({
    ...empty,
    items: [detail],
    total: 1,
  });
  render(<CandidatesScreen />);
  await userEvent.click(
    await screen.findByTestId("candidate-name-cand-900001"),
  );
  await userEvent.click(await screen.findByRole("button", { name: "수정" }));
  expect(screen.getByLabelText("자체 메모")).toHaveValue("자체 메모");
  await userEvent.type(screen.getByLabelText("자체 메모"), " 수정");
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  await waitFor(() =>
    expect(api.putCandidate).toHaveBeenCalledWith(
      detail.id,
      expect.objectContaining({
        expectedRevision: "current",
        consent: false,
        memo: "자체 메모 수정",
      }),
    ),
  );
  const payload = vi.mocked(api.putCandidate).mock.calls[0][1];
  expect(payload).not.toHaveProperty("lastContactDate");
  expect(payload).not.toHaveProperty("consentUserId");
  expect(payload).not.toHaveProperty("contactStatus");
});
it("form preserves existing consent metadata and dates instead of applying create defaults", async () => {
  render(
    <CandidateForm
      candidate={{
        ...detail,
        consent: true,
        availableDate: "2025-01-01",
        consentDate: "2025-01-01",
        consentUserId: "original",
      }}
      open
      onClose={vi.fn()}
      onSaved={vi.fn()}
    />,
  );
  expect(screen.getByTestId("candidate-consent")).toBeChecked();
  expect(screen.getByLabelText("투입 가능일 *")).toHaveValue("2025-01-01");
  await screen.findByLabelText("주 직무 *");
  expect(api.getCandidateDate).not.toHaveBeenCalled();
});
it("filter and sort changes reset page and preserve approved query shape", async () => {
  vi.mocked(api.getCandidates).mockResolvedValue({
    ...empty,
    items: [detail],
    total: 101,
  });
  render(<CandidatesScreen />);
  await screen.findByTestId("candidate-name-cand-900001");
  await userEvent.click(screen.getByRole("button", { name: "다음 페이지" }));
  await waitFor(() =>
    expect(api.getCandidates).toHaveBeenLastCalledWith(
      { page: 2, size: 50 },
      expect.any(AbortSignal),
    ),
  );
  await userEvent.selectOptions(
    screen.getByLabelText("투입 가능"),
    "NEXT_MONTH",
  );
  await userEvent.click(screen.getByRole("button", { name: "조회" }));
  await waitFor(() =>
    expect(api.getCandidates).toHaveBeenLastCalledWith(
      { availability: "NEXT_MONTH", page: 1, size: 50 },
      expect.any(AbortSignal),
    ),
  );
  await userEvent.selectOptions(
    screen.getByTestId("candidate-sort"),
    "availableDate",
  );
  await waitFor(() =>
    expect(api.getCandidates).toHaveBeenLastCalledWith(
      expect.objectContaining({ sort: "availableDate", page: 1 }),
      expect.any(AbortSignal),
    ),
  );
});
it("mobile uses twenty-card load more and hides export and bulk selection", async () => {
  vi.mocked(window.matchMedia).mockReturnValue({
    matches: true,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
  } as unknown as MediaQueryList);
  vi.mocked(api.getCandidates).mockResolvedValue({
    ...empty,
    items: [detail],
    total: 21,
    size: 20,
  });
  render(<CandidatesScreen />);
  await screen.findByTestId("candidate-card-cand-900001");
  expect(
    screen.queryByRole("button", { name: "엑셀 다운로드" }),
  ).not.toBeInTheDocument();
  expect(screen.queryByRole("checkbox")).not.toBeInTheDocument();
  await userEvent.click(screen.getByRole("button", { name: "20개 더보기" }));
  await waitFor(() =>
    expect(api.getCandidates).toHaveBeenLastCalledWith(
      { page: 2, size: 20 },
      expect.any(AbortSignal),
    ),
  );
});
it("HOLD does not get overdue warning while active candidates do", async () => {
  vi.mocked(api.getCandidates).mockResolvedValue({
    ...empty,
    items: [
      detail,
      {
        ...detail,
        id: "CAND-900002",
        name: "보류 후보",
        contactStatus: "HOLD",
      },
    ],
    total: 2,
  });
  render(<CandidatesScreen />);
  await screen.findByText("보류 후보");
  const rows = screen.getAllByRole("row");
  expect(within(rows[1]).getByText("46일 전")).toBeInTheDocument();
  expect(within(rows[2]).queryByText("46일 전")).not.toBeInTheDocument();
});
