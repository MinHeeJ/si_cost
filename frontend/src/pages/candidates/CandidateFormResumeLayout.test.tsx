import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from "../../shared/testSupport/businessFixtures";
import { expect, it, vi } from "vitest";
import { CandidateForm } from "./CandidateForm";
import { extractResume } from "../../shared/resumeClient";
vi.mock("../../shared/resumeClient", async (original) => ({
  ...(await original<typeof import("../../shared/resumeClient")>()),
  extractResume: vi.fn(),
}));

vi.mock("./peopleClient", () => ({
  getCandidateCodes: vi.fn(async () => ({ items: [] })),
  getCandidateDate: vi.fn(async () => ({ currentDate: "2026-11-16" })),
  createCandidate: vi.fn(),
  putCandidate: vi.fn(),
}));
vi.mock("../../shared/api", async (original) => ({
  ...(await original<typeof import("../../shared/api")>()),
  request: vi.fn(async () => ({ items: [] })),
}));

it("keeps consent immediately after contact inputs in the DOM and keyboard order", async () => {
  render(<CandidateForm open onClose={vi.fn()} onSaved={vi.fn()} />);
  await waitFor(() =>
    expect(screen.getByTestId("candidate-available-date")).toHaveValue(
      "2026-12-16",
    ),
  );
  const email = screen.getByTestId("candidate-email"),
    consent = screen.getByTestId("candidate-consent"),
    job = screen.getByLabelText("주 직무 *");
  expect(
    email.compareDocumentPosition(consent) & Node.DOCUMENT_POSITION_FOLLOWING,
  ).toBeTruthy();
  expect(
    consent.compareDocumentPosition(job) & Node.DOCUMENT_POSITION_FOLLOWING,
  ).toBeTruthy();
  expect(consent).not.toBeChecked();
  expect(consent.closest(".candidate-form-wide")).not.toBeNull();
  expect(screen.getByRole("button", { name: "이력서 등록" })).toBeEnabled();
  expect(screen.getByRole("button", { name: "등록" })).toBeEnabled();
});

it("discards a new resume-assisted draft on cancel, including an upload still pending", async () => {
  for (const pending of [false, true]) {
    const onSaved = vi.fn(),
      onClose = vi.fn();
    const { unmount } = render(
      <CandidateForm open onClose={onClose} onSaved={onSaved} />,
    );
    await waitFor(() =>
      expect(screen.getByTestId("candidate-available-date")).toHaveValue(
        "2026-12-16",
      ),
    );
    let resolve!: (
      value: import("../../shared/resumeClient").ResumeExtraction,
    ) => void;
    vi.mocked(extractResume).mockReturnValue(
      new Promise((r) => {
        resolve = r;
      }),
    );
    fireEvent.change(screen.getByLabelText("이력서 파일 선택"), {
      target: { files: [new File(["name"], "resume.txt")] },
    });
    if (!pending) {
      await act(async () =>
        resolve({
          targetType: "CANDIDATE",
          values: { name: "추출 이름" },
          fields: [
            {
              field: "name",
              valid: true,
              confidence: "EXPLICIT",
              notice: "가져옴",
              fragment: "",
              matchedWords: [],
              candidates: [],
            },
          ],
          warnings: [],
          careerReference: null,
          notice: "",
        }),
      );
      expect(screen.getByTestId("candidate-name")).toHaveValue("추출 이름");
    }
    fireEvent.click(screen.getByRole("button", { name: "취소" }));
    expect(onSaved).toHaveBeenCalledWith("", "", false);
    expect(onClose).not.toHaveBeenCalled();
    unmount();
  }
});
