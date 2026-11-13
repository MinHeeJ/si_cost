import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { PreferencesProvider } from "../me/usePreferences";
import type { PreferenceApi } from "../me/preferencesApi";
import { defaultProfitabilityQuery } from "./individualProfitabilityApi";
import { ProfitabilityExportButton } from "./ProfitabilityExportButton";
import * as api from "./profitabilityExportApi";
import {
  profitabilityColumns,
  profitabilityReferenceColumns,
} from "./profitabilityColumns";

vi.mock("./profitabilityExportApi", () => ({
  getProfitabilityExport: vi.fn(),
  saveProfitabilityDownload: vi.fn(),
}));
const order = profitabilityColumns.map((c) => c.id).reverse();
const preferences: PreferenceApi = {
  getPreferences: async () => ({
    items: [
      {
        type: "COLS",
        key: "UI-03",
        value: {
          individualProfitability: {
            order,
            hidden: profitabilityReferenceColumns,
          },
        },
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
  blob: new Blob(["test"], {
    type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
  }),
  filename: "개인원가수익_2026_20260131_090000.xlsx",
};
beforeEach(() => vi.resetAllMocks());
describe("임시 관리자 개인 원가 다운로드 동작", () => {
  it("현재 보기와 저장 열설정을 사용하고 처리중 중복클릭을 차단한 뒤 성공을 표시한다", async () => {
    let complete!: (value: typeof file) => void;
    vi.mocked(api.getProfitabilityExport).mockImplementation(
      () =>
        new Promise((resolve) => {
          complete = resolve;
        }),
    );
    const query = {
      ...defaultProfitabilityQuery(),
      page: 3,
      view: "MONTHLY_AVERAGE" as const,
      show: "LOSS" as const,
    };
    render(
      <PreferencesProvider api={preferences}>
        <ProfitabilityExportButton query={query} />
      </PreferencesProvider>,
    );
    const button = screen.getByRole("button", { name: "엑셀 다운로드" });
    await waitFor(() => expect(button).toBeEnabled());
    await userEvent.click(button);
    expect(
      screen.getByRole("button", { name: "엑셀 생성 중…" }),
    ).toBeDisabled();
    expect(api.getProfitabilityExport).toHaveBeenCalledWith(
      query,
      { order, hidden: profitabilityReferenceColumns, unit: "백만원" },
      expect.any(AbortSignal),
    );
    expect(api.getProfitabilityExport).toHaveBeenCalledTimes(1);
    await act(async () => complete(file));
    expect(api.saveProfitabilityDownload).toHaveBeenCalledWith(file);
    expect(screen.getByRole("status")).toHaveTextContent(
      "전체 결과 엑셀을 내려받았습니다.",
    );
    expect(preferences.putPreference).not.toHaveBeenCalled();
  });
  it("다운로드 실패를 표시하고 다시 누를 수 있으며 실패 응답은 저장하지 않는다", async () => {
    vi.mocked(api.getProfitabilityExport).mockRejectedValue(
      new Error("필수 접근기록 실패"),
    );
    render(
      <PreferencesProvider api={preferences}>
        <ProfitabilityExportButton query={defaultProfitabilityQuery()} />
      </PreferencesProvider>,
    );
    const button = screen.getByRole("button", { name: "엑셀 다운로드" });
    await waitFor(() => expect(button).toBeEnabled());
    await userEvent.click(button);
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "필수 접근기록 실패",
    );
    expect(button).toBeEnabled();
    expect(api.saveProfitabilityDownload).not.toHaveBeenCalled();
    vi.mocked(api.getProfitabilityExport).mockResolvedValue(file);
    await userEvent.click(button);
    expect(await screen.findByRole("status")).toHaveTextContent(
      "내려받았습니다",
    );
  });
  it("컴포넌트를 닫으면 요청을 취소하고 늦게 도착한 파일을 저장하지 않는다", async () => {
    let complete!: (value: typeof file) => void;
    vi.mocked(api.getProfitabilityExport).mockImplementation(
      () =>
        new Promise((resolve) => {
          complete = resolve;
        }),
    );
    const mounted = render(
      <PreferencesProvider api={preferences}>
        <ProfitabilityExportButton query={defaultProfitabilityQuery()} />
      </PreferencesProvider>,
    );
    const button = screen.getByRole("button", { name: "엑셀 다운로드" });
    await waitFor(() => expect(button).toBeEnabled());
    await userEvent.click(button);
    const signal = vi.mocked(api.getProfitabilityExport).mock.calls[0][2];
    mounted.unmount();
    expect(signal?.aborted).toBe(true);
    await act(async () => complete(file));
    expect(api.saveProfitabilityDownload).not.toHaveBeenCalled();
  });
});
