import {
  act,
  render,
  screen,
  waitFor,
  within,
} from "../../shared/testSupport/businessFixtures";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { CandidatesScreen } from "../candidates/CandidatesScreen";
import * as people from "../candidates/peopleClient";
import * as matching from "./candidateMatchingApi";
vi.mock("../candidates/peopleClient", () => ({
  getCandidates: vi.fn(),
  getCandidate: vi.fn(),
  getCandidateCodes: vi.fn(),
  getCandidateDate: vi.fn(),
  downloadCandidates: vi.fn(),
}));
vi.mock("./candidateMatchingApi", async (original) => ({
  ...(await original<typeof import("./candidateMatchingApi")>()),
  getCandidateShortages: vi.fn(),
  getCandidatePositions: vi.fn(),
}));
vi.mock("./CandidateSummaryPanel", () => ({
  CandidateSummaryPanel: () => (
    <section data-testid="preserved-summary">기존 후보 요약</section>
  ),
}));
vi.mock("../contacts/CandidateContactsPanel", () => ({
  CandidateContactStatusBadge: ({ status }: { status: string }) => (
    <span>{status}</span>
  ),
  CandidateContactsPanel: () => null,
}));
vi.mock("../candidates/CandidateForm", () => ({
  CandidateForm: ({
    onSaved,
  }: {
    onSaved(id: string, message: string, created: boolean): void;
  }) => (
    <button
      data-testid="existing-save"
      onClick={() => onSaved("C-1", "수정 완료", false)}
    >
      기존 저장
    </button>
  ),
}));
const candidate: people.CandidateDetail = {
  id: "C-1",
  name: "상세 후보",
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
  desiredRate: "750000",
  availableDate: "2027-06-30",
  source: "사이트",
  sourceCode: "SITE",
  contactStatus: "DONE",
  lastContactDate: "2026-12-01",
  memo: null,
  skills: ["Java"],
  revision: "r",
};
beforeEach(() => {
  vi.resetAllMocks();
  vi.stubGlobal("scrollTo", vi.fn());
  vi.stubGlobal(
    "matchMedia",
    vi.fn(() => ({
      matches: false,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })),
  );
  vi.mocked(people.getCandidateDate).mockResolvedValue({
    currentDate: "2026-12-01",
  });
  vi.mocked(people.getCandidateCodes).mockResolvedValue({
    items: [{ code: "DEV", name: "개발" }],
  });
  vi.mocked(people.getCandidates).mockResolvedValue({
    items: [candidate],
    total: 1,
    page: 1,
    size: 50,
  });
  vi.mocked(people.getCandidate).mockResolvedValue(candidate);
  vi.mocked(matching.getCandidateShortages).mockResolvedValue({
    data: {
      items: [
        {
          jobCode: "DEV",
          firstShortageMonth: "2027-06",
          maximumShortage: "2.0",
          availableUntil: "2027-06-30",
          availableCandidateCount: 1,
          activeCandidateCount: 2,
        },
      ],
      total: 1,
      page: 1,
      size: 50,
      from: "2027-01",
      months: 6,
      includeUnfilled: false,
    },
    issues: [],
  });
  vi.mocked(matching.getCandidatePositions).mockResolvedValue({
    data: { candidateId: candidate.id, items: [], total: 0, limit: 4 },
    issues: [],
  });
});
it("places shortages under existing summary and exact cutoff view resets without inventing API parameters", async () => {
  render(<CandidatesScreen />);
  await screen.findByRole("button", { name: "후보 보기" });
  expect(
    screen
      .getByTestId("preserved-summary")
      .compareDocumentPosition(
        screen.getByTestId("candidate-shortages-panel"),
      ) & Node.DOCUMENT_POSITION_FOLLOWING,
  ).toBeTruthy();
  await userEvent.click(screen.getByRole("button", { name: "후보 보기" }));
  const toast = await screen.findByText(/2027-06-30까지 투입 가능한 후보를 조회합니다/);
  expect(toast).toHaveAttribute("role", "status");
  expect(toast).toHaveTextContent("보류·계약 완료는 제외합니다.");
  expect(toast).toHaveTextContent("[초기화]를 누르면 조회 조건이 해제됩니다.");
  expect(screen.getByTestId("candidate-filters")).not.toContainElement(toast);
  expect(screen.getByTestId("candidate-filter-job-code")).toHaveValue("DEV");
  await waitFor(() =>
    expect(people.getCandidates).toHaveBeenCalledWith(
      {
        jobCode: "DEV",
        page: 1,
        size: 50,
        availability: undefined,
        availableUntil: "2027-06-30",
        activeOnly: true,
      },
      expect.any(AbortSignal),
    ),
  );
  expect(screen.getByTestId("candidate-filter-availability")).toHaveValue("");
  expect(screen.getByTestId("candidate-export")).toBeEnabled();
  await userEvent.click(screen.getByTestId("candidate-export"));
  expect(people.downloadCandidates).toHaveBeenCalledWith(
    {
      jobCode: "DEV",
      page: 1,
      size: 50,
      availability: undefined,
      availableUntil: "2027-06-30",
      activeOnly: true,
    },
    expect.arrayContaining(["name"]),
    "원",
  );
  await userEvent.click(screen.getByTestId("candidate-reset"));
  await waitFor(() =>
    expect(
      screen.queryByText(/2027-06-30까지 투입 가능한 후보를 조회합니다/),
    ).not.toBeInTheDocument(),
  );
  expect(screen.getByTestId("candidate-filter-job-code")).toHaveValue("");
  expect(people.getCandidates).toHaveBeenLastCalledWith(
    { page: 1, size: 50 },
    expect.any(AbortSignal),
  );
});
it("injects matching into real basic panel and re-reads after the existing save callback", async () => {
  render(<CandidatesScreen />);
  await userEvent.click(
    await screen.findByRole("button", { name: candidate.name }),
  );
  const basic = await screen.findByTestId("candidate-basic-panel");
  expect(
    within(basic).getByRole("region", { name: "맞는 자리" }),
  ).toBeInTheDocument();
  await within(basic).findByText("조건에 맞는 자리가 없습니다.");
  const before = vi.mocked(matching.getCandidatePositions).mock.calls.length;
  await userEvent.click(screen.getByTestId("candidate-edit"));
  vi.mocked(people.getCandidate).mockResolvedValue({
    ...candidate,
    desiredRate: "800000",
    availableDate: "2027-07-16",
  });
  await userEvent.click(screen.getByTestId("existing-save"));
  await waitFor(() =>
    expect(
      vi.mocked(matching.getCandidatePositions).mock.calls.length,
    ).toBeGreaterThan(before),
  );
  expect(await screen.findByText("2027-07-16")).toBeInTheDocument();
  expect(candidate.lastContactDate).toBe("2026-12-01");
});

it("다른 기회 저장 뒤 기존 후보의 부족직무·맞는자리를 다시 조회한다", async () => {
  render(<CandidatesScreen />);
  await userEvent.click(
    await screen.findByRole("button", { name: candidate.name }),
  );
  await screen.findByTestId("candidate-positions-panel");
  const shortages = vi.mocked(matching.getCandidateShortages).mock.calls.length;
  const positions = vi.mocked(matching.getCandidatePositions).mock.calls.length;
  act(() => window.dispatchEvent(new Event("sicost:sales-committed")));
  await waitFor(() =>
    expect(matching.getCandidateShortages).toHaveBeenCalledTimes(shortages + 1),
  );
  await waitFor(() =>
    expect(matching.getCandidatePositions).toHaveBeenCalledTimes(positions + 1),
  );
});
