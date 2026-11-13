import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { MemoryRouter, useLocation } from "react-router-dom";
import { PreferencesProvider } from "../me/usePreferences";
import { useTablePreferences } from "../me/ColumnPreferencesDialog";
import type { Preference, PreferenceApi } from "../me/preferencesApi";
import { ProfitabilityColumnSettings } from "./ProfitabilityColumnSettings";
import {
  profitabilityColumns,
  profitabilityReferenceColumns,
} from "./profitabilityColumns";

// Follow the existing preference-provider API isolation pattern; no financial source fixtures.
function mount(failSave = false) {
  let stored: Preference = {
    type: "COLS",
    key: "UI-03",
    revision: null,
    stored: false,
    value: {
      individualProfitability: {
        order: profitabilityColumns.map((c) => c.id),
        hidden: [...profitabilityReferenceColumns],
      },
    },
  };
  const api: PreferenceApi = {
    getPreferences: vi.fn(async () => ({ items: [structuredClone(stored)] })),
    putPreference: vi.fn(async (type, key, value) => {
      if (failSave) throw new Error("설정 저장 실패");
      stored = { type, key, value, revision: "saved", stored: true };
      return { changed: true, preference: stored };
    }),
    deletePreference: vi.fn(async () => {}),
  };
  function ColumnsConsumer() {
    const columns = useTablePreferences(
      "UI-03",
      "individualProfitability",
      profitabilityColumns,
    ).columns;
    const location = useLocation();
    return (
      <div data-testid="selected-columns">
        <output>{location.pathname}</output>
        <table>
          <thead>
            <tr>
              {columns.map((c) => (
                <th key={c.id}>{c.label}</th>
              ))}
            </tr>
          </thead>
        </table>
      </div>
    );
  }
  render(
    <MemoryRouter initialEntries={["/UI-03"]}>
      <PreferencesProvider api={api}>
        <ProfitabilityColumnSettings />
        <ColumnsConsumer />
      </PreferencesProvider>
    </MemoryRouter>,
  );
  return api;
}
describe("임시 관리자 개인 원가 열 설정", () => {
  it("인증 이동 없이 기본 숨김 참고열 모두 표시·저장하고 열린 표 소비자를 갱신한다", async () => {
    const api = mount();
    await waitFor(() =>
      expect(
        screen.queryByRole("columnheader", { name: "본인보험" }),
      ).not.toBeInTheDocument(),
    );
    fireEvent.click(screen.getByRole("button", { name: "열 설정" }));
    for (const name of ["본인보험", "원천세", "근로소득세", "실지급액"]) {
      const toggle = await screen.findByRole("checkbox", {
        name: `${name} 숨김`,
      });
      expect(toggle).toBeEnabled();
      expect(toggle).toBeChecked();
      fireEvent.click(toggle);
    }
    for (const name of ["이름", "개인 단가", "원가합계", "영업이익"])
      expect(
        screen.getByRole("checkbox", { name: `${name} 숨김` }),
      ).toBeDisabled();
    fireEvent.click(screen.getByRole("button", { name: "적용" }));
    await screen.findByRole("columnheader", { name: "본인보험" });
    for (const name of ["원천세", "근로소득세", "실지급액"])
      expect(screen.getByRole("columnheader", { name })).toBeInTheDocument();
    expect(api.putPreference).toHaveBeenCalledWith(
      "COLS",
      "UI-03",
      {
        individualProfitability: {
          order: profitabilityColumns.map((c) => c.id),
          hidden: [],
        },
      },
      null,
    );
    expect(screen.getByText("/UI-03")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "열 설정" }));
    expect(
      await screen.findByRole("checkbox", { name: "본인보험 숨김" }),
    ).not.toBeChecked();
  });
  it("조회는 저장하지 않고 실패는 적용 열과 미저장 선택을 보존한다", async () => {
    const api = mount(true);
    fireEvent.click(screen.getByRole("button", { name: "열 설정" }));
    const toggle = await screen.findByRole("checkbox", {
      name: "본인보험 숨김",
    });
    expect(api.putPreference).not.toHaveBeenCalled();
    fireEvent.click(toggle);
    fireEvent.click(screen.getByRole("button", { name: "적용" }));
    await screen.findByText("설정 저장 실패");
    expect(toggle).not.toBeChecked();
    expect(
      screen.queryByRole("columnheader", { name: "본인보험" }),
    ).not.toBeInTheDocument();
    expect(api.putPreference).toHaveBeenCalledTimes(1);
    expect(screen.getByText("/UI-03")).toBeInTheDocument();
  });
});
