import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { BusinessUsersWorkspace } from "./BusinessUsersWorkspace";
import { PreferencesProvider } from "../me/usePreferences";
import type { Preference, PreferenceApi } from "../me/preferencesApi";
import * as business from "./businessUsersApi";
vi.mock("./businessUsersApi");
let stored: Preference[];
let api: PreferenceApi;
beforeEach(() => {
  vi.resetAllMocks();
  stored = [
    {
      type: "COLS",
      key: "UI-23",
      value: {
        users: {
          order: ["userId", "name", "email", "roles", "status", "lastLoginAt"],
          hidden: [],
        },
      },
      revision: null,
      stored: false,
    },
    { type: "DENSE", key: "*", value: "넓게", revision: null, stored: false },
    { type: "PAGE_SIZE", key: "*", value: 50, revision: null, stored: false },
  ];
  api = {
    getPreferences: vi.fn(async () => ({ items: structuredClone(stored) })),
    putPreference: vi.fn(async (type, key, value) => {
      const preference = { type, key, value, stored: true, revision: "next" };
      stored = stored.map((item) =>
        item.type === type && item.key === key ? preference : item,
      );
      return { changed: true, preference };
    }),
    deletePreference: vi.fn(async () => {}),
  };
  vi.mocked(business.getBusinessUsers).mockResolvedValue({
    items: [],
    total: 0,
    page: 1,
    size: 50,
  });
});
it("사용자 열 설정은 공유 모달에서 필수열을 잠그고 서버 저장 뒤 적용한다", async () => {
  render(
    <PreferencesProvider api={api}>
      <BusinessUsersWorkspace />
    </PreferencesProvider>,
  );
  await screen.findByText("조회된 사용자가 없습니다.");
  await userEvent.click(screen.getByRole("button", { name: "열 설정" }));
  const dialog = screen.getByRole("dialog");
  expect(within(dialog).getByLabelText("아이디 숨김")).toBeDisabled();
  expect(within(dialog).getByLabelText("이름 숨김")).toBeDisabled();
  await userEvent.click(within(dialog).getByLabelText("이메일 숨김"));
  await userEvent.click(within(dialog).getByRole("button", { name: "적용" }));
  await waitFor(() =>
    expect(api.putPreference).toHaveBeenCalledWith(
      "COLS",
      "UI-23",
      {
        users: {
          order: ["userId", "name", "email", "roles", "status", "lastLoginAt"],
          hidden: ["email"],
        },
      },
      null,
    ),
  );
  await waitFor(() =>
    expect(
      screen.queryByRole("columnheader", { name: "이메일 정렬" }),
    ).not.toBeInTheDocument(),
  );
});
it("밀도 저장 실패는 기존 밀도를 보존하고 실패 안내를 표시한다", async () => {
  vi.mocked(api.putPreference).mockRejectedValue(new Error("설정 저장 실패"));
  render(
    <PreferencesProvider api={api}>
      <BusinessUsersWorkspace />
    </PreferencesProvider>,
  );
  await screen.findByText("조회된 사용자가 없습니다.");
  await userEvent.click(screen.getByRole("button", { name: "좁게 보기" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("설정 저장 실패");
  expect(screen.getByRole("table")).not.toHaveClass("sicost-dense");
  expect(api.putPreference).toHaveBeenCalledTimes(1);
});
