import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { PreferencesProvider } from "../me/usePreferences";
import type { PreferenceApi } from "../me/preferencesApi";
import { ProjectCostExportButton } from "./ProjectCostExportButton";
import * as api from "../projectstatusboardops/projectStatusApi";

vi.mock("../projectstatusboardops/projectStatusApi", async (original) => ({
  ...(await original<
    typeof import("../projectstatusboardops/projectStatusApi")
  >()),
  getProjectAnalysisDownload: vi.fn(),
  saveProjectAnalysisDownload: vi.fn(),
}));
const preferences: PreferenceApi = {
  getPreferences: async () => ({
    items: [
      {
        type: "AMT_UNIT",
        key: "*",
        value: "백만원",
        revision: null,
        stored: false,
      },
    ],
  }),
  putPreference: vi.fn(),
  deletePreference: vi.fn(),
};
const file = {
  blob: new Blob(["xlsx"]),
  filename: "프로젝트 원가_202606_20260701_000000.xlsx",
};
beforeEach(() => vi.resetAllMocks());
it("uses full applied filters and displayed settings, disables duplicates and reports success without preference writes", async () => {
  let complete!: (value: typeof file) => void;
  vi.mocked(api.getProjectAnalysisDownload).mockImplementation(
    () =>
      new Promise((resolve) => {
        complete = resolve;
      }),
  );
  const query = {
    month: "2026-06",
    contractType: "TK",
    alertOnly: true,
    page: 3,
    size: 20 as const,
    sort: "cumulativeCost",
    direction: "DESC",
  };
  const columns = {
    order: ["projectName", "cumulativeCost"],
    hidden: ["alerts"],
  };
  render(
    <PreferencesProvider api={preferences}>
      <ProjectCostExportButton query={query} columns={columns} unit="원" />
    </PreferencesProvider>,
  );
  const button = screen.getByRole("button", { name: "엑셀 다운로드" });
  await waitFor(() => expect(button).toBeEnabled());
  await userEvent.click(button);
  expect(screen.getByRole("button", { name: "엑셀 생성 중…" })).toBeDisabled();
  expect(api.getProjectAnalysisDownload).toHaveBeenCalledTimes(1);
  expect(api.getProjectAnalysisDownload).toHaveBeenCalledWith(
    "projectCost",
    query,
    { ...columns, unit: "원" },
    expect.any(AbortSignal),
  );
  await act(async () => complete(file));
  expect(api.saveProjectAnalysisDownload).toHaveBeenCalledWith(file);
  expect(screen.getByRole("status")).toHaveTextContent("전체 결과");
  expect(preferences.putPreference).not.toHaveBeenCalled();
});
it("failure preserves filters and retries without saving error bytes", async () => {
  vi.mocked(api.getProjectAnalysisDownload).mockRejectedValue(
    new Error("필수 접근기록 실패"),
  );
  render(
    <PreferencesProvider api={preferences}>
      <ProjectCostExportButton query={{ status: "END" }} />
    </PreferencesProvider>,
  );
  const button = screen.getByRole("button", { name: "엑셀 다운로드" });
  await waitFor(() => expect(button).toBeEnabled());
  await userEvent.click(button);
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "필수 접근기록 실패",
  );
  expect(api.saveProjectAnalysisDownload).not.toHaveBeenCalled();
  expect(button).toBeEnabled();
  vi.mocked(api.getProjectAnalysisDownload).mockResolvedValue(file);
  await userEvent.click(button);
  expect(await screen.findByRole("status")).toHaveTextContent("내려받았습니다");
  expect(vi.mocked(api.getProjectAnalysisDownload).mock.calls[1][1]).toEqual({
    status: "END",
  });
});
it("cancel aborts and ignores late response", async () => {
  let complete!: (value: typeof file) => void;
  vi.mocked(api.getProjectAnalysisDownload).mockImplementation(
    () =>
      new Promise((resolve) => {
        complete = resolve;
      }),
  );
  render(
    <PreferencesProvider api={preferences}>
      <ProjectCostExportButton query={{}} />
    </PreferencesProvider>,
  );
  const button = screen.getByRole("button", { name: "엑셀 다운로드" });
  await waitFor(() => expect(button).toBeEnabled());
  await userEvent.click(button);
  const signal = vi.mocked(api.getProjectAnalysisDownload).mock.calls[0][3];
  await userEvent.click(screen.getByRole("button", { name: "취소" }));
  expect(signal?.aborted).toBe(true);
  await act(async () => complete(file));
  expect(api.saveProjectAnalysisDownload).not.toHaveBeenCalled();
  expect(button).toBeEnabled();
});
it("unmount aborts the active request", async () => {
  vi.mocked(api.getProjectAnalysisDownload).mockImplementation(
    () => new Promise(() => {}),
  );
  const mounted = render(
    <PreferencesProvider api={preferences}>
      <ProjectCostExportButton query={{}} />
    </PreferencesProvider>,
  );
  const button = screen.getByRole("button", { name: "엑셀 다운로드" });
  await waitFor(() => expect(button).toBeEnabled());
  await userEvent.click(button);
  const signal = vi.mocked(api.getProjectAnalysisDownload).mock.calls[0][3];
  mounted.unmount();
  expect(signal?.aborted).toBe(true);
});
it("preference read failure disables export and never creates a file", async () => {
  const failed = {
    ...preferences,
    getPreferences: async () => {
      throw new Error("설정 조회 실패");
    },
  };
  render(
    <PreferencesProvider api={failed}>
      <ProjectCostExportButton query={{}} />
    </PreferencesProvider>,
  );
  expect(await screen.findByRole("alert")).toHaveTextContent("설정 조회 실패");
  expect(screen.getByRole("button", { name: "엑셀 다운로드" })).toBeDisabled();
  expect(api.getProjectAnalysisDownload).not.toHaveBeenCalled();
});
