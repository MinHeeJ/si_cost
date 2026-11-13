import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { expect, it, vi } from "vitest";
import { PreferencesProvider } from "../me/usePreferences";
import type { Preference, PreferenceApi } from "../me/preferencesApi";
import { ProjectIssueColumnSettings } from "./ProjectIssueColumnSettings";
import { issueColumnIds } from "./ProjectIssueTable";
function mount(fail = false) {
  let stored: Preference = {
    type: "COLS",
    key: "UI-31",
    revision: "before",
    stored: true,
    value: {
      statusBoard: { order: ["projectName", "light"], hidden: ["light"] },
      issues: { order: [...issueColumnIds], hidden: [] },
    },
  };
  const api: PreferenceApi = {
    getPreferences: vi.fn(async () => ({ items: [structuredClone(stored)] })),
    putPreference: vi.fn(async (type, key, value) => {
      if (fail) throw new Error("설정 저장 실패");
      stored = { type, key, value, revision: "after", stored: true };
      return { changed: true, preference: stored };
    }),
    deletePreference: vi.fn(async () => {}),
  };
  const close = vi.fn();
  render(
    <PreferencesProvider api={api}>
      <ProjectIssueColumnSettings onClose={close} />
    </PreferencesProvider>,
  );
  return { api, close };
}
it("제목/프로젝트 필수열과 전체 순서를 유지하고 현황판 설정을 보존해 저장한다", async () => {
  const { api } = mount();
  expect(
    await screen.findByRole("checkbox", { name: "제목 숨김" }),
  ).toBeDisabled();
  expect(
    screen.getByRole("checkbox", { name: "프로젝트 숨김" }),
  ).toBeDisabled();
  fireEvent.click(screen.getByRole("checkbox", { name: "조치 숨김" }));
  fireEvent.click(screen.getByRole("button", { name: "적용" }));
  await waitFor(() =>
    expect(api.putPreference).toHaveBeenCalledWith(
      "COLS",
      "UI-31",
      {
        statusBoard: { order: ["projectName", "light"], hidden: ["light"] },
        issues: { order: [...issueColumnIds], hidden: ["action"] },
      },
      "before",
    ),
  );
});
it("취소는 쓰지 않고 저장 오류는 초안을 유지한다", async () => {
  const { api, close } = mount(true);
  await screen.findByRole("checkbox", { name: "조치 숨김" });
  fireEvent.click(screen.getByRole("checkbox", { name: "조치 숨김" }));
  fireEvent.click(screen.getByRole("button", { name: "적용" }));
  await screen.findAllByText("설정 저장 실패");
  expect(screen.getByRole("checkbox", { name: "조치 숨김" })).toBeChecked();
  expect(close).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole("button", { name: "취소" }));
  expect(close).toHaveBeenCalledOnce();
  expect(api.putPreference).toHaveBeenCalledTimes(1);
});
