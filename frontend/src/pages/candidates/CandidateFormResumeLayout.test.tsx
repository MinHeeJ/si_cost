import { render, screen, waitFor } from "@testing-library/react";
import { expect, it, vi } from "vitest";
import { CandidateForm } from "./CandidateForm";

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
