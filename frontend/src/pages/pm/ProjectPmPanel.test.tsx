import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, expect, it, vi } from "vitest";
import { ApiError } from "../../shared/api";
import * as api from "../businessusersops/userProjectsApi";
import { ProjectPmPanel } from "./ProjectPmPanel";
vi.mock("../businessusersops/userProjectsApi", async (original) => ({
  ...(await original<typeof api>()),
  getUserProjectPm: vi.fn(),
}));
const data: api.UserProjectPmData = {
  items: [
    { userId: "person-a", name: "담당 가", scopeSn: 1 },
    { userId: "person-b", name: "담당 나", scopeSn: 3 },
  ],
  revision: "opaque",
};
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api.getUserProjectPm).mockResolvedValue(data);
});
function open(projectId = "selected-project") {
  return render(
    <MemoryRouter>
      <ProjectPmPanel projectId={projectId} />
    </MemoryRouter>,
  );
}
it("shows every legacy PM without assignment or removal controls", async () => {
  open();
  expect(await screen.findByText("담당 가")).toBeInTheDocument();
  expect(screen.getByText("담당 나")).toBeInTheDocument();
  expect(screen.getByText("person-a")).toBeInTheDocument();
  expect(screen.getByText("person-b")).toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: /지정|해제|교체/ }),
  ).not.toBeInTheDocument();
  expect(api.getUserProjectPm).toHaveBeenCalledWith(
    "selected-project",
    expect.any(AbortSignal),
  );
});
it("distinguishes loading from empty", async () => {
  let resolve!: (value: api.UserProjectPmData) => void;
  vi.mocked(api.getUserProjectPm).mockReturnValue(
    new Promise((yes) => {
      resolve = yes;
    }),
  );
  open();
  expect(screen.getByText("불러오는 중입니다.")).toBeInTheDocument();
  expect(screen.queryByText("지정된 PM이 없습니다.")).not.toBeInTheDocument();
  await act(async () => resolve({ items: [], revision: "empty" }));
  expect(await screen.findByText("지정된 PM이 없습니다.")).toBeInTheDocument();
});
it("failed query shows request ID and retries only on explicit action", async () => {
  vi.mocked(api.getUserProjectPm).mockRejectedValueOnce(
    new ApiError(500, {
      success: false,
      meta: {},
      code: "INTERNAL_ERROR",
      message: "PM 조회 실패",
      requestId: "pm-request",
    }),
  );
  open();
  expect(await screen.findByRole("alert")).toHaveTextContent("pm-request");
  expect(api.getUserProjectPm).toHaveBeenCalledTimes(1);
  expect(screen.queryByText("지정된 PM이 없습니다.")).not.toBeInTheDocument();
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  await screen.findByText("담당 가");
});
it("management link navigates to existing UI-23 without a new route", async () => {
  render(
    <MemoryRouter initialEntries={["/UI-01"]}>
      <Routes>
        <Route
          path="/UI-01"
          element={<ProjectPmPanel projectId="selected" />}
        />
        <Route path="/UI-23" element={<h1>사용자 업무</h1>} />
      </Routes>
    </MemoryRouter>,
  );
  await screen.findByText("담당 가");
  await userEvent.click(
    screen.getByRole("link", { name: "사용자에서 담당 프로젝트 관리" }),
  );
  expect(
    screen.getByRole("heading", { name: "사용자 업무" }),
  ).toBeInTheDocument();
});
it("committed UI-23 changes refresh the readonly PM region", async () => {
  open();
  await screen.findByText("담당 가");
  vi.mocked(api.getUserProjectPm).mockResolvedValue({
    items: [],
    revision: "after",
  });
  act(() =>
    window.dispatchEvent(new CustomEvent(api.userProjectsCommittedEvent)),
  );
  await screen.findByText("지정된 PM이 없습니다.");
  expect(api.getUserProjectPm).toHaveBeenCalledTimes(2);
});
it("project changes abort obsolete reads and do not show the prior project PM", async () => {
  let resolve!: (value: api.UserProjectPmData) => void;
  vi.mocked(api.getUserProjectPm).mockImplementation((id) =>
    id === "old"
      ? new Promise((yes) => {
          resolve = yes;
        })
      : Promise.resolve({ items: [], revision: "new" }),
  );
  const view = open("old");
  view.rerender(
    <MemoryRouter>
      <ProjectPmPanel projectId="new" />
    </MemoryRouter>,
  );
  await screen.findByText("지정된 PM이 없습니다.");
  await act(async () => resolve(data));
  expect(screen.queryByText("담당 가")).not.toBeInTheDocument();
});
it("parent refresh key reloads without writing", async () => {
  const view = open();
  await screen.findByText("담당 가");
  view.rerender(
    <MemoryRouter>
      <ProjectPmPanel projectId="selected-project" refreshKey={1} />
    </MemoryRouter>,
  );
  await waitFor(() => expect(api.getUserProjectPm).toHaveBeenCalledTimes(2));
});
