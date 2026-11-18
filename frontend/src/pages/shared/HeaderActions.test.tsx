import { useEffect } from "react";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { expect, it, vi } from "vitest";
import { PermissionContext } from "../../shared/permission/PermissionBoundary";
import { HeaderActions } from "./HeaderActions";

const lifecycle = vi.hoisted(() => ({ mounted: vi.fn(), unmounted: vi.fn() }));
vi.mock("../authentication/SessionIndicator", () => ({
  SessionIndicator: () => {
    useEffect(() => {
      lifecycle.mounted();
      return lifecycle.unmounted;
    }, []);
    return <button>연장</button>;
  },
}));
vi.mock("../apiops/ActivityCenter", () => ({
  ActivityCenter: () => <span>알림</span>,
}));
vi.mock("./AnalysisConversation", () => ({
  AnalysisConversation: () => <p>대화 본문</p>,
}));

it("keeps the session consumer mounted while its compact menu opens/closes and still hides unauthorized AI", async () => {
  render(<HeaderActions />);
  const indicator = screen.getByRole("button", { name: "연장", hidden: true });
  expect(
    screen.queryByRole("button", { name: "AI에게 묻기" }),
  ).not.toBeInTheDocument();
  await userEvent.click(screen.getByTestId("workspace-session-toggle"));
  expect(screen.getByRole("button", { name: "연장" })).toBe(indicator);
  await userEvent.click(screen.getByTestId("workspace-session-toggle"));
  expect(screen.getByRole("button", { name: "연장", hidden: true })).toBe(
    indicator,
  );
  expect(lifecycle.mounted).toHaveBeenCalledOnce();
  expect(lifecycle.unmounted).not.toHaveBeenCalled();
});

it("retains permission-backed AI opening, Escape dismissal and close action", async () => {
  render(
    <PermissionContext.Provider
      value={{
        screens: {},
        scope: { all: false, partIds: [], projectIds: [] },
        sensitive: false,
        features: { analysisPanel: true },
      }}
    >
      <HeaderActions />
    </PermissionContext.Provider>,
  );
  await userEvent.click(screen.getByRole("button", { name: "AI에게 묻기" }));
  expect(
    screen.getByRole("dialog", { name: "AI에게 묻기 대화창" }),
  ).toBeInTheDocument();
  await userEvent.keyboard("{Escape}");
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  await userEvent.click(screen.getByRole("button", { name: "AI에게 묻기" }));
  await userEvent.click(screen.getByRole("button", { name: "AI 대화창 닫기" }));
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
});
