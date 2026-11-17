import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { MemoryRouter, useLocation } from "react-router-dom";
import { useEffect } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { AuthenticationBoundary } from "./AuthenticationBoundary";
import { SessionIndicator } from "./SessionIndicator";
import * as api from "./authenticationApi";
vi.mock("./authenticationApi", () => ({
  session: vi.fn(),
  capabilities: vi.fn(),
  authenticate: vi.fn(),
  clearAuthentication: vi.fn(),
  authenticationChannel: null,
}));
const authenticated = {
  nextStep: "AUTHENTICATED" as const,
  serverTime: "2026-11-17T03:00:00Z",
  expiresAt: "2026-11-17T03:30:00Z",
};
const empty = {
  screens: {},
  fields: {},
  scope: { all: false, partIds: [], projectIds: [] },
  sensitive: false,
};
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api.session).mockResolvedValue(authenticated);
  vi.mocked(api.capabilities).mockResolvedValue(empty);
});
afterEach(() => {
  cleanup();
  vi.useRealTimers();
  vi.unstubAllGlobals();
});
function application(
  content = <div data-testid="business">private business</div>,
) {
  return render(
    <MemoryRouter initialEntries={["/UI-23"]}>
      <AuthenticationBoundary>{content}</AuthenticationBoundary>
    </MemoryRouter>,
  );
}
describe("authentication consumer boundaries", () => {
  it("renders no business data and only logout when no menu is allowed", async () => {
    application();
    await screen.findByText(
      "볼 수 있는 화면이 없습니다. 관리자에게 권한을 요청해 주세요.",
    );
    expect(screen.queryByTestId("business")).toBeNull();
    expect(
      screen.getAllByRole("button").map((button) => button.textContent),
    ).toEqual(["로그아웃"]);
  });
  it("does not recursively fetch an expired session after a 401 session event", async () => {
    application();
    await screen.findByText(
      "볼 수 있는 화면이 없습니다. 관리자에게 권한을 요청해 주세요.",
    );
    const calls = vi.mocked(api.session).mock.calls.length;
    act(() =>
      window.dispatchEvent(
        new CustomEvent("sicost:authentication-required", {
          detail: { code: "SESSION_EXPIRED" },
        }),
      ),
    );
    await screen.findByRole("heading", { name: "로그인" });
    expect(api.session).toHaveBeenCalledTimes(calls);
  });
  it("unmounts old business content when refreshed capabilities are revoked", async () => {
    vi.mocked(api.capabilities)
      .mockResolvedValueOnce({ ...empty, screens: { "UI-23": ["INQ", "UPD"] } })
      .mockResolvedValue(empty);
    application();
    await screen.findByTestId("business");
    act(() =>
      window.dispatchEvent(
        new CustomEvent("sicost:permission-changed", {
          detail: { path: "/api/business-users" },
        }),
      ),
    );
    await screen.findByText(
      "볼 수 있는 화면이 없습니다. 관리자에게 권한을 요청해 주세요.",
    );
    expect(screen.queryByTestId("business")).toBeNull();
  });
  it("preserves an allowed consumer without replaying a denied same-capability GET", async () => {
    vi.mocked(api.capabilities).mockResolvedValue({
      ...empty,
      screens: { "UI-23": ["INQ"] },
    });
    const mounted = vi.fn();
    function Consumer() {
      useEffect(() => {
        mounted();
      }, []);
      return <div data-testid="business">permitted screen</div>;
    }
    application(<Consumer />);
    await screen.findByTestId("business");
    act(() =>
      window.dispatchEvent(
        new CustomEvent("sicost:permission-changed", {
          detail: { path: "/api/business-users" },
        }),
      ),
    );
    await waitFor(() => expect(api.capabilities).toHaveBeenCalledTimes(2));
    expect(screen.getByTestId("business")).toBeInTheDocument();
    expect(mounted).toHaveBeenCalledTimes(1);
  });
  it("selects a device-usable first screen instead of a PC-only first grant on mobile", async () => {
    vi.stubGlobal(
      "matchMedia",
      vi
        .fn()
        .mockReturnValue({
          matches: true,
          addEventListener: vi.fn(),
          removeEventListener: vi.fn(),
        }),
    );
    vi.mocked(api.capabilities).mockResolvedValue({
      ...empty,
      screens: { "UI-23": ["INQ"], "UI-33": ["INQ"] },
      pcOnlyScreens: ["UI-23"],
    });
    function Consumer() {
      return <div data-testid="route">{useLocation().pathname}</div>;
    }
    application(<Consumer />);
    await waitFor(() =>
      expect(screen.getByTestId("route")).toHaveTextContent("/UI-33"),
    );
  });
  it("rechecks the server instead of clearing other tabs on a stale zero countdown", async () => {
    vi.useFakeTimers();
    const base = Date.parse(authenticated.serverTime);
    vi.setSystemTime(base);
    vi.mocked(api.session)
      .mockResolvedValueOnce({
        ...authenticated,
        expiresAt: new Date(base + 1000).toISOString(),
      })
      .mockResolvedValue({
        ...authenticated,
        serverTime: new Date(base + 1000).toISOString(),
        expiresAt: new Date(base + 120000).toISOString(),
      });
    render(<SessionIndicator />);
    await act(async () => {});
    await act(async () => {
      await vi.advanceTimersByTimeAsync(1000);
    });
    expect(api.session).toHaveBeenCalledTimes(2);
    expect(api.clearAuthentication).not.toHaveBeenCalled();
  });
  it("clears consumers after a logout audit failure only when the server confirms discard", async () => {
    vi.stubGlobal("confirm", vi.fn().mockReturnValue(true));
    vi.mocked(api.session)
      .mockResolvedValueOnce(authenticated)
      .mockResolvedValue({
        nextStep: "LOGIN",
        serverTime: authenticated.serverTime,
      });
    vi.mocked(api.authenticate).mockRejectedValue(
      new Error("기록을 확인할 수 없습니다."),
    );
    render(<SessionIndicator />);
    await act(async () => {});
    fireEvent.click(screen.getByRole("button", { name: "로그아웃" }));
    await waitFor(() =>
      expect(api.clearAuthentication).toHaveBeenCalledTimes(1),
    );
    expect(api.authenticate).toHaveBeenCalledTimes(1);
    expect(api.authenticate).toHaveBeenCalledWith("logout");
    expect(api.session).toHaveBeenCalledTimes(2);
  });
  it("does not invent successful logout or replay a POST after an unconfirmed network error", async () => {
    vi.stubGlobal("confirm", vi.fn().mockReturnValue(true));
    vi.mocked(api.authenticate).mockRejectedValue(
      new Error("네트워크 응답을 확인할 수 없습니다."),
    );
    render(<SessionIndicator />);
    await act(async () => {});
    fireEvent.click(screen.getByRole("button", { name: "로그아웃" }));
    await screen.findByText("네트워크 응답을 확인할 수 없습니다.");
    await waitFor(() => expect(api.session).toHaveBeenCalledTimes(2));
    expect(api.clearAuthentication).not.toHaveBeenCalled();
    expect(api.authenticate).toHaveBeenCalledTimes(1);
  });
});
