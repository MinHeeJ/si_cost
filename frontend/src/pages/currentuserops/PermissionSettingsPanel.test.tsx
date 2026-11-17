import {
  render,
  screen,
  within,
  fireEvent,
  waitFor,
} from "../../shared/testSupport/businessFixtures";
import { beforeEach, expect, it, vi } from "vitest";
import { ApiError } from "../../shared/api";
import { PermissionSettingsPanel } from "./PermissionSettingsPanel";
import * as api from "./permissionSettingsApi";
import type { CurrentPermissionSettings } from "./permissionSettingsApi";
vi.mock("./permissionSettingsApi");
const settings = (): CurrentPermissionSettings => ({
  userId: "db-administrator",
  roleIds: ["ADMIN", "PM"],
  menuPermissions: [
    {
      menuId: "stored-menu",
      inquire: false,
      register: true,
      update: false,
      delete: true,
      upload: false,
      download: true,
      confirm: false,
    },
  ],
  sensitiveView: true,
  rowScope: {
    all: false,
    partIds: ["stored-part"],
    projectIds: ["stored-project"],
  },
  assignedParts: [
    { partId: "stored-part", name: "실제 담당파트", headOffice: true },
  ],
  assignedProjects: [{ projectId: "stored-project", scopeSn: 1 }],
  developmentMode: true,
  accessEnforcement: false,
  settingsRevision: "stored-revision",
});
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api.getCurrentUserPermissionSettings).mockResolvedValue(settings());
});
it("renders both scope sets, stored flags and seven independent read-only permissions", async () => {
  render(<PermissionSettingsPanel />);
  expect(await screen.findByText("db-administrator")).toBeInTheDocument();
  expect(screen.getByText("담당파트 (PART): stored-part")).toBeInTheDocument();
  expect(
    screen.getByText("담당프로젝트 (PROJ): stored-project"),
  ).toBeInTheDocument();
  const row = screen.getByTestId("permission-menu-stored-menu");
  expect(
    within(row)
      .getAllByRole("cell")
      .map((cell) => cell.textContent),
  ).toEqual(["미허용", "허용", "미허용", "허용", "미허용", "허용", "미허용"]);
  expect(screen.getByText("허용 설정")).toHaveClass("sicost-tone-info");
  expect(screen.queryByRole("checkbox")).not.toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: "저장" }),
  ).not.toBeInTheDocument();
  expect(screen.getByRole("button", { name: "다시 조회" })).toBeEnabled();
  expect(screen.getByText(/실제 메뉴·API 차단/)).toBeInTheDocument();
});
it("does not collapse ALL to zero when no assignments exist", async () => {
  vi.mocked(api.getCurrentUserPermissionSettings).mockResolvedValue({
    ...settings(),
    rowScope: { all: true, partIds: [], projectIds: [] },
    assignedParts: [],
    assignedProjects: [],
  });
  render(<PermissionSettingsPanel />);
  expect(await screen.findByText("전체 (ALL)")).toHaveClass("sicost-tone-info");
  expect(screen.getByText("담당파트가 없습니다.")).toBeInTheDocument();
  expect(screen.getByText("담당프로젝트가 없습니다.")).toBeInTheDocument();
});
it("shows absent scope targets and empty menu source without synthesizing permissions", async () => {
  vi.mocked(api.getCurrentUserPermissionSettings).mockResolvedValue({
    ...settings(),
    sensitiveView: false,
    roleIds: [],
    menuPermissions: [],
    rowScope: { all: false, partIds: [], projectIds: [] },
  });
  render(<PermissionSettingsPanel />);
  expect(
    await screen.findByText("저장된 메뉴 권한이 없습니다."),
  ).toBeInTheDocument();
  expect(
    screen.getByText("담당파트 (PART): 대상 없음 (0건)"),
  ).toBeInTheDocument();
  expect(
    screen.getByText("담당프로젝트 (PROJ): 대상 없음 (0건)"),
  ).toBeInTheDocument();
  expect(screen.getByText("활성 역할이 없습니다.")).toBeInTheDocument();
  expect(screen.getByText("허용 안 함")).toHaveClass("sicost-tone-neutral");
});
it("shows loading and disables duplicate reads", async () => {
  let resolve!: (value: CurrentPermissionSettings) => void;
  vi.mocked(api.getCurrentUserPermissionSettings).mockReturnValue(
    new Promise((done) => {
      resolve = done;
    }),
  );
  render(<PermissionSettingsPanel />);
  expect(screen.getByRole("status")).toHaveTextContent(
    "저장 권한을 조회하고 있습니다.",
  );
  expect(screen.getByRole("button", { name: "다시 조회" })).toBeDisabled();
  resolve(settings());
  expect(await screen.findByText("db-administrator")).toBeInTheDocument();
});
it("shows root API field errors and retries only the GET", async () => {
  vi.mocked(api.getCurrentUserPermissionSettings).mockRejectedValueOnce(
    new ApiError(400, {
      success: false,
      meta: {},
      code: "UNKNOWN_FIELD",
      message: "허용되지 않은 조회 항목입니다.",
      field: "actorId",
      requestId: "request-trace",
    }),
  );
  render(<PermissionSettingsPanel />);
  expect(await screen.findByRole("alert")).toHaveTextContent("request-trace");
  expect(
    screen.getByText("actorId: 허용되지 않은 조회 항목입니다."),
  ).toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  expect(await screen.findByText("db-administrator")).toBeInTheDocument();
  expect(api.getCurrentUserPermissionSettings).toHaveBeenCalledTimes(2);
});
it("failed refresh never leaves outdated permissions presented as current", async () => {
  render(<PermissionSettingsPanel />);
  await screen.findByText("db-administrator");
  vi.mocked(api.getCurrentUserPermissionSettings).mockRejectedValueOnce(
    new Error("다시 조회하지 못했습니다."),
  );
  fireEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "다시 조회하지 못했습니다.",
  );
  expect(screen.queryByRole("table")).not.toBeInTheDocument();
});
it("refresh replaces stored revision and settings without writing", async () => {
  render(<PermissionSettingsPanel />);
  await screen.findByText("stored-revision");
  vi.mocked(api.getCurrentUserPermissionSettings).mockResolvedValue({
    ...settings(),
    settingsRevision: "latest-revision",
    sensitiveView: false,
  });
  fireEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  expect(await screen.findByText("latest-revision")).toBeInTheDocument();
  await waitFor(() =>
    expect(screen.getByText("허용 안 함")).toBeInTheDocument(),
  );
});
