import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { PreferencesProvider } from "../me/usePreferences";
import type { PreferenceApi } from "../me/preferencesApi";
import { defaultProfitabilityQuery } from "../profitability/individualProfitabilityApi";
import * as individual from "../profitability/profitabilityExportApi";
import {
  IndividualCostDownload,
  ProjectAnalysisDownload,
} from "./ProjectAnalysisDownload";
import * as api from "./projectStatusApi";

vi.mock("./projectStatusApi", async (original) => ({
  ...(await original<typeof import("./projectStatusApi")>()),
  getProjectAnalysisDownload: vi.fn(),
  saveProjectAnalysisDownload: vi.fn(),
}));
vi.mock("../profitability/profitabilityExportApi", () => ({
  getProfitabilityExport: vi.fn(),
  saveProfitabilityDownload: vi.fn(),
}));
const preferences: PreferenceApi = {
  getPreferences: async () => ({
    items: [
      {
        type: "COLS",
        key: "UI-31",
        value: { statusBoard: { order: ["projectName"], hidden: [] } } as never,
        revision: null,
        stored: false,
      },
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
  filename: "프로젝트현황_20260701_000000.xlsx",
};
beforeEach(() => vi.resetAllMocks());
describe.each(["statusBoard", "projectCost"] as const)(
  "%s export region",
  (view) => {
    it("uses current complete filters and settings, blocks duplicate clicks, and shows success", async () => {
      let complete!: (value: typeof file) => void;
      vi.mocked(api.getProjectAnalysisDownload).mockImplementation(
        () =>
          new Promise((resolve) => {
            complete = resolve;
          }),
      );
      render(
        <PreferencesProvider api={preferences}>
          <ProjectAnalysisDownload
            view={view}
            query={{ page: 3, size: 20, contractType: "TK" }}
          />
        </PreferencesProvider>,
      );
      const button = screen.getByRole("button", { name: "엑셀 다운로드" });
      await waitFor(() => expect(button).toBeEnabled());
      await userEvent.click(button);
      expect(
        screen.getByRole("button", { name: "엑셀 생성 중…" }),
      ).toBeDisabled();
      expect(api.getProjectAnalysisDownload).toHaveBeenCalledTimes(1);
      expect(api.getProjectAnalysisDownload).toHaveBeenCalledWith(
        view,
        { page: 3, size: 20, contractType: "TK" },
        expect.objectContaining({ unit: "백만원" }),
        expect.any(AbortSignal),
      );
      await act(async () => complete(file));
      expect(api.saveProjectAnalysisDownload).toHaveBeenCalledWith(file);
      expect(screen.getByRole("status")).toHaveTextContent("전체 결과");
      expect(preferences.putPreference).not.toHaveBeenCalled();
      expect(
        screen.getByTestId(`project-download-${view.toLowerCase()}`),
      ).toHaveAttribute(
        "data-screen-id",
        view === "statusBoard"
          ? "SCR-UI-31-B138-EXPORT"
          : "SCR-UI-04-B138-EXPORT",
      );
    });
    it("failure keeps filters and permits retry without saving error bytes", async () => {
      vi.mocked(api.getProjectAnalysisDownload).mockRejectedValue(
        new Error("필수 접근기록 실패"),
      );
      render(
        <PreferencesProvider api={preferences}>
          <ProjectAnalysisDownload view={view} query={{ light: "위험" }} />
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
      expect(await screen.findByRole("status")).toHaveTextContent(
        "내려받았습니다",
      );
    });
    it("cancel aborts request and ignores late response", async () => {
      let complete!: (value: typeof file) => void;
      vi.mocked(api.getProjectAnalysisDownload).mockImplementation(
        () =>
          new Promise((resolve) => {
            complete = resolve;
          }),
      );
      render(
        <PreferencesProvider api={preferences}>
          <ProjectAnalysisDownload view={view} query={{}} />
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
    it("unmount aborts and explicit displayed unit overrides stored unit", async () => {
      vi.mocked(api.getProjectAnalysisDownload).mockImplementation(
        () => new Promise(() => {}),
      );
      const mounted = render(
        <PreferencesProvider api={preferences}>
          <ProjectAnalysisDownload view={view} query={{}} unit="원" />
        </PreferencesProvider>,
      );
      const button = screen.getByRole("button", { name: "엑셀 다운로드" });
      await waitFor(() => expect(button).toBeEnabled());
      await userEvent.click(button);
      const call = vi.mocked(api.getProjectAnalysisDownload).mock.calls[0];
      expect(call[2]?.unit).toBe("원");
      mounted.unmount();
      expect(call[3]?.aborted).toBe(true);
    });
  },
);
it("SCR-UI-03-EXPORT delegates to the existing year-qualified annual export", async () => {
  vi.mocked(individual.getProfitabilityExport).mockResolvedValue({
    ...file,
    filename: "개인원가수익_2026_20260701_000000.xlsx",
  });
  render(
    <PreferencesProvider api={preferences}>
      <IndividualCostDownload
        query={{ ...defaultProfitabilityQuery(), year: 2026 }}
      />
    </PreferencesProvider>,
  );
  const button = screen.getByRole("button", { name: "엑셀 다운로드" });
  await waitFor(() => expect(button).toBeEnabled());
  await userEvent.click(button);
  expect(await screen.findByRole("status")).toHaveTextContent("내려받았습니다");
  expect(individual.getProfitabilityExport).toHaveBeenCalledWith(
    expect.objectContaining({ year: 2026 }),
    expect.any(Object),
    expect.any(AbortSignal),
  );
  expect(screen.getByTestId("individual-cost-download")).toHaveAttribute(
    "data-screen-id",
    "SCR-UI-03-EXPORT",
  );
});
