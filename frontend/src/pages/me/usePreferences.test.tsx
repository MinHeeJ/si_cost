import {
  act,
  render,
  renderHook,
  screen,
  fireEvent,
  waitFor,
} from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { PreferencesProvider, usePreferences } from "./usePreferences";
import { PreferenceSettings } from "./PreferenceSettings";
import {
  FavoriteButton,
  FavoritePreferences,
  PreferenceQuickLinks,
} from "./FavoritePreferences";
import { ColumnPreferencesDialog } from "./ColumnPreferencesDialog";
import { type Preference, type PreferenceApi } from "./preferencesApi";
import { type ReactNode } from "react";

let stored: Preference[];
let api: PreferenceApi;
const item = (
  type: Preference["type"],
  value: Preference["value"],
  revision: string | null = null,
): Preference => ({
  type,
  key: "*",
  value,
  revision,
  stored: revision !== null,
});
beforeEach(() => {
  stored = [
    item("FAV", []),
    item("RECENT", []),
    item("PAGE_SIZE", 50),
    item("DENSE", "넓게"),
    item("MENU_FOLD", null),
    item("AMT_UNIT", "천원"),
  ];
  api = {
    getPreferences: vi.fn(async () => ({ items: structuredClone(stored) })),
    putPreference: vi.fn(async (type, key, value) => {
      const p = { type, key, value, stored: true, revision: "next" };
      stored = stored.map((i) => (i.type === type && i.key === key ? p : i));
      return { changed: true, preference: p };
    }),
    deletePreference: vi.fn(async (type) => {
      stored = stored.map((i) =>
        i.type === type ? item(type, type === "PAGE_SIZE" ? 50 : null) : i,
      );
    }),
  };
});
function wrapper({ children }: { children: ReactNode }) {
  return <PreferencesProvider api={api}>{children}</PreferencesProvider>;
}

describe("서버 화면 설정", () => {
  it("조회만으로 저장하지 않고 null MENU_FOLD는 폭 기본값이다", async () => {
    const { result } = renderHook(() => usePreferences(), { wrapper });
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.menuFold(1440)).toBe(false);
    expect(result.current.menuFold(1024)).toBe(true);
    expect(api.putPreference).not.toHaveBeenCalled();
  });
  it("저장 후 모든 열린 소비자가 즉시 같은 PAGE_SIZE와 DENSE를 읽는다", async () => {
    function Consumer() {
      const p = usePreferences();
      return (
        <output>
          {String(p.get("PAGE_SIZE")?.value)} {String(p.get("DENSE")?.value)}
        </output>
      );
    }
    render(
      <PreferencesProvider api={api}>
        <PreferenceSettings open onClose={() => {}} />
        <Consumer />
        <Consumer />
      </PreferencesProvider>,
    );
    await screen.findByLabelText("한 화면 건수");
    fireEvent.change(screen.getByLabelText("한 화면 건수"), {
      target: { value: "20" },
    });
    await waitFor(() => expect(screen.getAllByText("20 넓게")).toHaveLength(2));
    fireEvent.change(screen.getByLabelText("목록 밀도"), {
      target: { value: "좁게" },
    });
    await waitFor(() => expect(screen.getAllByText("20 좁게")).toHaveLength(2));
    expect(api.putPreference).toHaveBeenCalledWith("PAGE_SIZE", "*", 20, null);
  });
  it("실패는 적용값과 미저장 선택을 보존하고 자동 재시도하지 않는다", async () => {
    vi.mocked(api.putPreference).mockRejectedValue(new Error("저장 실패"));
    render(
      <PreferencesProvider api={api}>
        <PreferenceSettings open onClose={() => {}} />
      </PreferencesProvider>,
    );
    await screen.findByLabelText("한 화면 건수");
    fireEvent.change(screen.getByLabelText("한 화면 건수"), {
      target: { value: "100" },
    });
    await screen.findByText("저장 실패");
    expect(screen.getByLabelText("한 화면 건수")).toHaveValue("100");
    expect(api.putPreference).toHaveBeenCalledTimes(1);
  });
  it("저장 완료 후 조회 실패는 별도 안내하고 재저장하지 않는다", async () => {
    const { result } = renderHook(() => usePreferences(), { wrapper });
    await waitFor(() => expect(result.current.loading).toBe(false));
    vi.mocked(api.getPreferences).mockRejectedValue(new Error("조회 실패"));
    await act(async () => {
      await result.current.put("DENSE", "*", "좁게");
    });
    expect(result.current.message).toBe("저장됐지만 다시 불러오지 못했습니다.");
    expect(result.current.get("DENSE")?.value).toBe("좁게");
    expect(api.putPreference).toHaveBeenCalledTimes(1);
  });
  it("모바일 슬라이드는 임시이며 MENU_FOLD 쓰기를 하지 않는다", async () => {
    const { result } = renderHook(() => usePreferences(), { wrapper });
    await waitFor(() => expect(result.current.loading).toBe(false));
    await act(async () => {
      await result.current.setMenuFold(true, 375);
    });
    expect(api.putPreference).not.toHaveBeenCalled();
  });
  it("손상행은 오류와 초기화만 제공하고 자동삭제하지 않는다", async () => {
    stored[3] = {
      ...item("DENSE", "넓게", "broken"),
      error: { code: "INVALID_PREFERENCE", message: "손상된 설정" },
    };
    render(
      <PreferencesProvider api={api}>
        <PreferenceSettings open onClose={() => {}} />
      </PreferencesProvider>,
    );
    await screen.findByText("손상된 설정");
    expect(api.deletePreference).not.toHaveBeenCalled();
  });
});

describe("즐겨찾기와 최근 화면", () => {
  const menus = [
    { screenId: "UI-27", name: "공통코드", pcOnly: true },
    { screenId: "UI-16", name: "대시보드", pcOnly: false },
  ];
  it("서버 순서를 복원하고 순서 편집 성공 뒤 적용한다", async () => {
    stored[0] = item("FAV", ["UI-27", "UI-16"], "r");
    render(
      <PreferencesProvider api={api}>
        <FavoritePreferences open menus={menus} onClose={() => {}} />
      </PreferencesProvider>,
    );
    fireEvent.click(
      await screen.findByRole("button", { name: "대시보드 위로" }),
    );
    await waitFor(() =>
      expect(api.putPreference).toHaveBeenCalledWith(
        "FAV",
        "*",
        ["UI-16", "UI-27"],
        "r",
      ),
    );
  });
  it("11번째 즐겨찾기는 요청하지 않고 기존10개를 보존한다", async () => {
    stored[0] = item(
      "FAV",
      Array.from(
        { length: 10 },
        (_, i) => `UI-${String(i + 1).padStart(2, "0")}`,
      ),
      "r",
    );
    render(
      <PreferencesProvider api={api}>
        <FavoriteButton screenId="UI-16" />
      </PreferencesProvider>,
    );
    await waitFor(() =>
      expect(
        screen.getByRole("button", { name: "즐겨찾기 추가" }),
      ).not.toBeDisabled(),
    );
    fireEvent.click(screen.getByRole("button", { name: "즐겨찾기 추가" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("최대 10개");
    expect(api.putPreference).not.toHaveBeenCalled();
    expect(stored[0].value).toHaveLength(10);
  });
  it("서버 미등록 화면 거부를 표시하고 배열을 유지한다", async () => {
    vi.mocked(api.putPreference).mockRejectedValue(
      new Error("등록된 화면이 아닙니다."),
    );
    render(
      <PreferencesProvider api={api}>
        <FavoriteButton screenId="UI-99" />
      </PreferencesProvider>,
    );
    await waitFor(() =>
      expect(
        screen.getByRole("button", { name: "즐겨찾기 추가" }),
      ).not.toBeDisabled(),
    );
    fireEvent.click(screen.getByRole("button", { name: "즐겨찾기 추가" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("등록된 화면");
    expect(stored[0].value).toEqual([]);
  });
  it("PC 전용은 모바일 표시만 숨기고 PC에서 복원한다", async () => {
    stored[0] = item("FAV", ["UI-27"], "r");
    const { rerender } = render(
      <PreferencesProvider api={api}>
        <PreferenceQuickLinks
          menus={menus}
          collapsed={false}
          mobile
          onNavigate={() => {}}
        />
      </PreferencesProvider>,
    );
    await waitFor(() => expect(api.getPreferences).toHaveBeenCalled());
    expect(screen.queryByText("공통코드")).not.toBeInTheDocument();
    rerender(
      <PreferencesProvider api={api}>
        <PreferenceQuickLinks
          menus={menus}
          collapsed={false}
          mobile={false}
          onNavigate={() => {}}
        />
      </PreferencesProvider>,
    );
    expect(await screen.findByText("공통코드")).toBeInTheDocument();
    expect(api.putPreference).not.toHaveBeenCalled();
  });
  it("접힘은 두 목록을 숨긴다", async () => {
    render(
      <PreferencesProvider api={api}>
        <PreferenceQuickLinks
          menus={menus}
          collapsed
          mobile={false}
          onNavigate={() => {}}
        />
      </PreferencesProvider>,
    );
    await waitFor(() => expect(api.getPreferences).toHaveBeenCalled());
    expect(screen.queryByText("즐겨찾기")).not.toBeInTheDocument();
    expect(screen.queryByText("최근 본 화면")).not.toBeInTheDocument();
  });
  it("빈 안내를 표시하고 별을 명시 저장한다", async () => {
    render(
      <PreferencesProvider api={api}>
        <FavoriteButton screenId="UI-16" />
        <PreferenceQuickLinks
          menus={menus}
          collapsed={false}
          mobile={false}
          onNavigate={() => {}}
        />
      </PreferencesProvider>,
    );
    expect(
      await screen.findByText("화면 제목 옆 ☆를 누르면 추가됩니다"),
    ).toBeInTheDocument();
    expect(screen.getByText("아직 없습니다")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "즐겨찾기 추가" }));
    await waitFor(() =>
      expect(api.putPreference).toHaveBeenCalledWith(
        "FAV",
        "*",
        ["UI-16"],
        null,
      ),
    );
  });
  it("최근은 재방문 선두·중복 제거·최대5이고 재마운트시 복원한다", async () => {
    stored[1] = item(
      "RECENT",
      ["UI-01", "UI-02", "UI-03", "UI-04", "UI-05"],
      "r",
    );
    const hook = renderHook(() => usePreferences(), { wrapper });
    await waitFor(() => expect(hook.result.current.loading).toBe(false));
    await act(async () => {
      await hook.result.current.visit("UI-03");
    });
    expect(api.putPreference).toHaveBeenLastCalledWith(
      "RECENT",
      "*",
      ["UI-03", "UI-01", "UI-02", "UI-04", "UI-05"],
      "r",
    );
    await act(async () => {
      await hook.result.current.visit("UI-16");
    });
    expect(hook.result.current.get("RECENT")?.value).toEqual([
      "UI-16",
      "UI-03",
      "UI-01",
      "UI-02",
      "UI-04",
    ]);
    hook.unmount();
    const next = renderHook(() => usePreferences(), { wrapper });
    await waitFor(() => expect(next.result.current.loading).toBe(false));
    expect(next.result.current.get("RECENT")?.value).toEqual([
      "UI-16",
      "UI-03",
      "UI-01",
      "UI-02",
      "UI-04",
    ]);
  });
});

describe("표 열 설정", () => {
  const columns = [
    { id: "code", label: "코드", required: true },
    { id: "name", label: "이름", required: true },
    { id: "unitNm", label: "단위" },
  ];
  it("전체 순서를 유지하고 필수열 숨김을 비활성화한다", async () => {
    stored.push({
      type: "COLS",
      key: "UI-27",
      value: { valueCodes: { order: ["code", "name", "unitNm"], hidden: [] } },
      revision: null,
      stored: false,
    });
    render(
      <PreferencesProvider api={api}>
        <ColumnPreferencesDialog
          open
          onClose={() => {}}
          screenId="UI-27"
          viewId="valueCodes"
          columns={columns}
        />
      </PreferencesProvider>,
    );
    expect(await screen.findByLabelText("코드 숨김")).toBeDisabled();
    expect(screen.getByLabelText("이름 숨김")).toBeDisabled();
    fireEvent.click(screen.getByLabelText("단위 숨김"));
    fireEvent.click(screen.getByRole("button", { name: "단위 위로" }));
    fireEvent.click(screen.getByRole("button", { name: "적용" }));
    await waitFor(() =>
      expect(api.putPreference).toHaveBeenCalledWith(
        "COLS",
        "UI-27",
        {
          valueCodes: { order: ["code", "unitNm", "name"], hidden: ["unitNm"] },
        },
        null,
      ),
    );
  });
  it("열 저장 실패 후 미저장 편집과 모달을 유지한다", async () => {
    stored.push({
      type: "COLS",
      key: "UI-27",
      value: { valueCodes: { order: ["code", "name", "unitNm"], hidden: [] } },
      revision: "r",
      stored: true,
    });
    vi.mocked(api.putPreference).mockRejectedValue(new Error("충돌"));
    const close = vi.fn();
    render(
      <PreferencesProvider api={api}>
        <ColumnPreferencesDialog
          open
          onClose={close}
          screenId="UI-27"
          viewId="valueCodes"
          columns={columns}
        />
      </PreferencesProvider>,
    );
    fireEvent.click(await screen.findByLabelText("단위 숨김"));
    fireEvent.click(screen.getByRole("button", { name: "적용" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("충돌");
    expect(screen.getByLabelText("단위 숨김")).toBeChecked();
    expect(close).not.toHaveBeenCalled();
  });
});
