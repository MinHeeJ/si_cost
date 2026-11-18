import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { LoginForm } from "./LoginForm";
import { MfaForm } from "./MfaForm";
import { ApiError } from "../../shared/api";
import * as api from "./authenticationApi";
vi.mock("./authenticationApi", () => ({
  authenticate: vi.fn(),
  session: vi.fn(),
}));
const pending: api.AuthenticationSession = {
  nextStep: "MFA",
  serverTime: "2026-11-17T03:00:00Z",
  stageExpiresAt: "2026-11-17T03:04:00Z",
  maskedEmail: "s***@example.test",
};
const failure = (code: string, status = 503) =>
  new ApiError(status, {
    success: false,
    meta: {},
    code,
    message:
      code === "MFA_INVALID"
        ? "인증번호가 맞지 않습니다. (1/5)"
        : "인증 메일 발송에 실패했습니다. 다시 보내기를 이용해 주세요.",
    requestId: "synthetic-request",
  });
beforeEach(() => vi.resetAllMocks());
afterEach(cleanup);
describe("recoverable MFA", () => {
  it("keeps a committed initial MFA proof recoverable after SMTP failure", async () => {
    vi.mocked(api.authenticate).mockRejectedValue(
      failure("MFA_DELIVERY_FAILED"),
    );
    vi.mocked(api.session).mockResolvedValue(pending);
    const next = vi.fn();
    render(<LoginForm onAuthenticated={next} onRecovery={vi.fn()} />);
    fireEvent.change(screen.getByLabelText("아이디"), {
      target: { value: "synthetic" },
    });
    fireEvent.change(screen.getByLabelText("비밀번호"), {
      target: { value: "synthetic-only" },
    });
    fireEvent.submit(screen.getByLabelText("아이디").closest("form")!);
    await waitFor(() =>
      expect(next).toHaveBeenCalledWith(
        pending,
        expect.stringContaining("다시 보내기"),
      ),
    );
    expect(screen.getByLabelText("비밀번호")).toHaveValue("");
    expect(api.session).toHaveBeenCalledTimes(1);
  });
  it("uses the latest committed expiry even when resend delivery fails", async () => {
    vi.mocked(api.authenticate).mockRejectedValue(
      failure("MFA_DELIVERY_FAILED"),
    );
    vi.mocked(api.session).mockResolvedValue({
      ...pending,
      serverTime: "2026-11-17T03:01:00Z",
      stageExpiresAt: "2026-11-17T03:06:00Z",
    });
    const next = vi.fn();
    render(<MfaForm state={pending} onAuthenticated={next} />);
    fireEvent.click(screen.getByRole("button", { name: "다시 보내기" }));
    await screen.findByText("남은 시간 5:00");
    expect(next).not.toHaveBeenCalled();
    expect(
      screen.getByRole("button", { name: "처음으로" }),
    ).toBeInTheDocument();
    expect(screen.getByText(/인증 메일 발송에 실패/)).toBeInTheDocument();
  });
  it("keeps a well-formed wrong-code 401 local to the pending MFA form", async () => {
    vi.mocked(api.authenticate).mockRejectedValue(failure("MFA_INVALID", 401));
    const next = vi.fn();
    render(<MfaForm state={pending} onAuthenticated={next} />);
    fireEvent.change(screen.getByLabelText("인증번호"), {
      target: { value: "123456" },
    });
    fireEvent.submit(screen.getByLabelText("인증번호").closest("form")!);
    await screen.findByText("인증번호가 맞지 않습니다. (1/5)");
    expect(next).not.toHaveBeenCalled();
    expect(screen.getByLabelText("인증번호")).toHaveValue("");
  });
});

it("blocks repeated resend and verification while a resend is pending, then allows retry", async () => {
  let finish!: (value: api.AuthenticationSession) => void;
  vi.mocked(api.authenticate).mockReturnValueOnce(new Promise(resolve => { finish = resolve; }));
  render(<MfaForm state={pending} onAuthenticated={vi.fn()} />);
  const resend = screen.getByRole("button", { name: "다시 보내기" });
  // Two events in the same React batch must still issue exactly one request.
  const { act } = await import("@testing-library/react");
  act(() => { resend.click(); resend.click(); });
  expect(api.authenticate).toHaveBeenCalledTimes(1);
  expect(resend).toBeDisabled();
  expect(screen.getByRole("button", { name: "인증 확인" })).toBeDisabled();
  await act(async () => finish(pending));
  expect(resend).toBeEnabled();
  vi.mocked(api.authenticate).mockResolvedValue(pending);
  fireEvent.click(resend);
  await waitFor(() => expect(api.authenticate).toHaveBeenCalledTimes(2));
});
