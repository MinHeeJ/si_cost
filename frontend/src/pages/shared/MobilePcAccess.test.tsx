import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { MemoryRouter, useLocation } from "react-router-dom";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { AuthenticationBoundary } from "../authentication/AuthenticationBoundary";
import { SharedApplication } from "./SharedApplication";
import * as auth from "../authentication/authenticationApi";
import * as api from "./sharedApi";

vi.mock("../authentication/authenticationApi", () => ({
  session: vi.fn(),
  capabilities: vi.fn(),
  authenticate: vi.fn(),
  clearAuthentication: vi.fn(),
  authenticationChannel: null,
}));
vi.mock("./sharedApi");
vi.mock("./HeaderActions", () => ({ HeaderActions: () => null }));
vi.mock("../meops/ProfileDialog", () => ({ ProfileDialog: () => null }));
const definitions = [
  ["UI-16", "대시보드"],
  ["UI-23", "사용자"],
  ["UI-27", "공통코드"],
  ["UI-33", "후보자"],
] as const;
let grants: auth.Capabilities;
let width = 390;
const mounts = vi.fn();
function Business({ id }: { id: string }) {
  mounts(id);
  return <p data-testid={`body-${id.toLowerCase()}`}>{id} 업무 본문</p>;
}
function Location() {
  return <output data-testid="route">{useLocation().pathname}</output>;
}
function open(id: string) {
  render(
    <MemoryRouter initialEntries={[`/${id}`]}>
      <AuthenticationBoundary>
        <SharedApplication
          screens={Object.fromEntries(
            definitions.map(([id]) => [id, <Business key={id} id={id} />]),
          )}
        />
        <Location />
      </AuthenticationBoundary>
    </MemoryRouter>,
  );
}
function resize(next: number) {
  width = next;
  Object.defineProperty(window, "innerWidth", {
    configurable: true,
    value: next,
  });
  act(() => window.dispatchEvent(new Event("resize")));
}
beforeEach(() => {
  vi.resetAllMocks();
  width = 390;
  Object.defineProperty(window, "innerWidth", {
    configurable: true,
    value: width,
  });
  vi.stubGlobal(
    "matchMedia",
    vi.fn().mockImplementation(() => ({
      matches: width <= 767,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })),
  );
  grants = {
    screens: Object.fromEntries(definitions.map(([id]) => [id, ["INQ"]])),
    pcOnlyScreens: ["UI-23", "UI-27"],
    scope: { all: true, partIds: [], projectIds: [] },
    sensitive: false,
  };
  vi.mocked(auth.session).mockResolvedValue({
    nextStep: "AUTHENTICATED",
    serverTime: "2026-11-20T00:00:00Z",
  });
  vi.mocked(auth.capabilities).mockImplementation(async () => grants);
  vi.mocked(api.getMenus).mockImplementation(async () => ({
    items: definitions
      .filter(([id]) => grants.screens[id])
      .map(([screenId, name]) => ({
        menuId: screenId,
        parentId: null,
        screenId,
        name,
        pcOnly: grants.pcOnlyScreens!.includes(screenId),
        sortOrder: 1,
        icon: null,
        children: [],
      })),
    diagnostics: [],
  }));
  vi.mocked(api.getCurrentUser).mockResolvedValue({
    userId: "test",
    name: "검증 사용자",
    roleIds: [],
    developmentMode: false,
  });
  vi.mocked(api.getSharedPreferences).mockResolvedValue({
    items: ["FAV", "RECENT"].map((type) => ({
      type,
      key: "*",
      value: ["UI-23", "UI-27"],
      revision: "r",
      stored: true,
    })),
  });
  vi.mocked(api.putSharedPreference).mockImplementation(
    async (type, key, value) => ({
      changed: true,
      preference: { type, key, value, revision: "r2", stored: true },
    }),
  );
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

it.each(
  definitions
    .filter(([id]) => ["UI-23", "UI-27"].includes(id))
    .flatMap(([id, name]) => [390, 767].map((width) => ({ id, name, width }))),
)(
  "$id at $width keeps the named notice until clicked, without mounting its body",
  async ({ id, name, width }) => {
    resize(width);
    open(id);
    const notice = await screen.findByTestId("pc-only-notice");
    expect(notice).toHaveTextContent(`「${name}」`);
    expect(screen.getByTestId("route")).toHaveTextContent(`/${id}`);
    expect(screen.queryByTestId(`body-${id.toLowerCase()}`)).toBeNull();
    expect(mounts).not.toHaveBeenCalledWith(id);
    for (const prefix of ["menu", "favorite", "recent"]) {
      expect(
        document.querySelector(`[data-testid="${prefix}-${id.toLowerCase()}"]`),
      ).toBeNull();
    }
    fireEvent.click(screen.getByRole("button", { name: "대시보드로" }));
    await waitFor(() =>
      expect(screen.getByTestId("route")).toHaveTextContent("/UI-16"),
    );
    expect(screen.queryByTestId("pc-only-notice")).toBeNull();
  },
);
it.each(["UI-23", "UI-27"])(
  "%s is usable at 768 and preserves its cached desktop body behind the 767 notice",
  async (id) => {
    resize(768);
    open(id);
    const body = await screen.findByTestId(`body-${id.toLowerCase()}`);
    expect(body).toBeVisible();
    resize(767);
    expect(await screen.findByTestId("pc-only-notice")).toBeVisible();
    expect(body).not.toBeVisible();
    resize(768);
    await waitFor(() => expect(body).toBeVisible());
    expect(screen.getByTestId(`body-${id.toLowerCase()}`)).toBe(body);
  },
);
it("uses the existing first permitted usable screen when dashboard access is absent", async () => {
  delete grants.screens["UI-16"];
  open("UI-27");
  await screen.findByTestId("pc-only-notice");
  expect(screen.getByTestId("route")).toHaveTextContent("/UI-27");
  fireEvent.click(screen.getByRole("button", { name: "대시보드로" }));
  await waitFor(() =>
    expect(screen.getByTestId("route")).toHaveTextContent("/UI-33"),
  );
});
it("keeps actual permission denial distinct from a device restriction", async () => {
  delete grants.screens["UI-27"];
  open("UI-27");
  await screen.findByText("이 화면을 볼 권한이 없습니다.");
  await waitFor(() =>
    expect(screen.getByTestId("route")).toHaveTextContent("/UI-16"),
  );
  expect(screen.queryByTestId("pc-only-notice")).toBeNull();
  expect(mounts).not.toHaveBeenCalledWith("UI-27");
});
it("leaves unauthenticated direct access in the login flow", async () => {
  vi.mocked(auth.session).mockResolvedValue({
    nextStep: "LOGIN",
    serverTime: "2026-11-20T00:00:00Z",
  });
  open("UI-27");
  expect(await screen.findByRole("heading", { name: "로그인" })).toBeVisible();
  expect(screen.queryByTestId("pc-only-notice")).toBeNull();
  expect(mounts).not.toHaveBeenCalled();
});
it("honors current menu PC settings instead of a fixed list of screen numbers", async () => {
  grants.pcOnlyScreens = [];
  open("UI-27");
  expect(await screen.findByTestId("body-ui-27")).toBeVisible();
  expect(screen.queryByTestId("pc-only-notice")).toBeNull();
});
