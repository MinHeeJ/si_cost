import {
  createContext,
  createElement,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
  type ReactNode,
} from "react";
import {
  preferencesApi,
  type Preference,
  type PreferenceApi,
  type PreferenceType,
  type PreferenceValue,
} from "./preferencesApi";
import {
  COMMITTED_REFRESH_FAILED,
  useCommitRefresh,
} from "../../shared/useCommitRefresh";

function usePreferenceState(api: PreferenceApi) {
  const [items, setItems] = useState<Preference[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const { pending, message, commit } = useCommitRefresh();
  const current = useRef(items);
  current.current = items;
  const refresh = useCallback(async () => {
    try {
      const data = await api.getPreferences();
      current.current = data.items;
      setItems(data.items);
      setError(null);
    } catch (e) {
      setError(e);
      throw e;
    } finally {
      setLoading(false);
    }
  }, [api]);
  useEffect(() => {
    void refresh().catch(() => {});
  }, [refresh]);
  const get = (type: PreferenceType, key = "*") =>
    items.find((i) => i.type === type && i.key === key);
  const find = (type: PreferenceType, key: string) =>
    current.current.find((i) => i.type === type && i.key === key);
  const put = async (
    type: PreferenceType,
    key: string,
    value: PreferenceValue,
    options: { quiet?: boolean } = {},
  ) => {
    const previous = find(type, key);
    if (!previous) throw new Error("먼저 설정을 조회해 주세요.");
    await commit(
      async () => {
        const result = await api.putPreference(
          type,
          key,
          value,
          previous.revision,
        );
        current.current = current.current.map((i) =>
          i.type === type && i.key === key ? result.preference : i,
        );
        setItems(current.current);
        return result;
      },
      refresh,
      options,
    );
  };
  const reset = async (type: PreferenceType, key = "*") => {
    const preference = find(type, key);
    if (!preference?.revision) throw new Error("저장된 설정이 없습니다.");
    await commit(
      () => api.deletePreference(type, key, preference.revision!),
      refresh,
    );
  };
  const list = (type: "FAV" | "RECENT"): string[] => {
    const value = find(type, "*")?.value;
    return Array.isArray(value) ? value : [];
  };
  const toggleFavorite = async (screenId: string) => {
    const ids = list("FAV");
    if (!ids.includes(screenId) && ids.length >= 10)
      throw new Error("즐겨찾기는 최대 10개까지 등록할 수 있습니다.");
    await put(
      "FAV",
      "*",
      ids.includes(screenId)
        ? ids.filter((id) => id !== screenId)
        : [...ids, screenId],
    );
  };
  const visit = async (screenId: string) => {
    const ids = list("RECENT");
    if (ids[0] === screenId) return;
    await put(
      "RECENT",
      "*",
      [screenId, ...ids.filter((id) => id !== screenId)].slice(0, 5),
    );
  };
  const menuFold = (width: number) => {
    const value = get("MENU_FOLD")?.value;
    return typeof value === "boolean" ? value : width >= 768 && width < 1280;
  };
  const setMenuFold = async (value: boolean, width: number) => {
    if (width >= 768) await put("MENU_FOLD", "*", value);
  };
  return {
    items,
    loading,
    error,
    pending,
    message:
      message === COMMITTED_REFRESH_FAILED
        ? "저장됐지만 다시 불러오지 못했습니다."
        : message,
    get,
    put,
    reset,
    refresh,
    toggleFavorite,
    visit,
    menuFold,
    setMenuFold,
  };
}
const PreferencesContext = createContext<ReturnType<
  typeof usePreferenceState
> | null>(null);
export function PreferencesProvider({
  children,
  api = preferencesApi,
}: {
  children: ReactNode;
  api?: PreferenceApi;
}) {
  const state = usePreferenceState(api);
  return createElement(PreferencesContext.Provider, { value: state }, children);
}
export function usePreferences() {
  const state = useContext(PreferencesContext);
  if (!state) throw new Error("PreferencesProvider가 필요합니다.");
  return state;
}
