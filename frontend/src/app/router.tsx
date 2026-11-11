import { useCallback, useEffect, useState } from "react";
import { ErrorBanner } from "../shared/Feedback";
import { BrowserRouter, useRoutes, type RouteObject } from "react-router-dom";
import {
  SharedApplication,
  type SharedPreferenceAdapter,
} from "../pages/shared/SharedApplication";
import { screenRegistry } from "../pages/shared/screenRegistry";
import { useSharedScreen } from "../pages/shared/SharedScreenContext";
import { ChangeHistoryPanel } from "../pages/shared/ChangeHistoryPanel";
import { CommonCodesScreen } from "../pages/codegroups/CommonCodesScreen";
import {
  CODE_GROUPS_COMMITTED,
  getCodeGroups,
} from "../pages/codegroups/codeGroupsApi";
import { CodeMappingsScreen } from "../pages/codemappings/CodeMappingsScreen";
import { OperatingStandardsScreen } from "../pages/operatingstandards/OperatingStandardsScreen";
import {
  PreferencesProvider,
  usePreferences,
} from "../pages/me/usePreferences";
import { PreferenceSettings } from "../pages/me/PreferenceSettings";
import {
  ColumnPreferencesDialog,
  type PreferenceColumn,
} from "../pages/me/ColumnPreferencesDialog";
import type {
  ColumnPreferences,
  PreferenceType,
  PreferenceValue,
} from "../pages/me/preferencesApi";

type View = keyof ColumnPreferences;
const tableColumns: Record<View, PreferenceColumn[]> = {
  codes: [
    { id: "code", label: "코드", required: true },
    { id: "name", label: "이름", required: true },
    { id: "addVal1", label: "추가값 1" },
    { id: "addVal2", label: "추가값 2" },
    { id: "sortOrder", label: "순서" },
    { id: "useYn", label: "사용" },
  ],
  valueCodes: [
    { id: "code", label: "코드", required: true },
    { id: "name", label: "이름", required: true },
    { id: "codeVal", label: "값" },
    { id: "unitNm", label: "단위" },
  ],
  mappings: [
    { id: "sourceText", label: "엑셀 표기", required: true },
    { id: "groupId", label: "코드 그룹", required: true },
    { id: "code", label: "바꿀 코드 / 이름", required: true },
  ],
  standards: [
    { id: "key", label: "키", required: true },
    { id: "name", label: "이름", required: true },
    { id: "value", label: "값", required: true },
    { id: "unitNm", label: "단위" },
    { id: "effectiveMonth", label: "적용월" },
    { id: "description", label: "설명" },
  ],
  history: [
    { id: "occurredAt", label: "일시", required: true },
    { id: "target", label: "대상", required: true },
    { id: "groupId", label: "그룹" },
    { id: "entityKey", label: "코드 키" },
    { id: "type", label: "유형" },
    { id: "item", label: "항목" },
    { id: "before", label: "변경 전" },
    { id: "after", label: "변경 후" },
    { id: "actor", label: "처리자" },
  ],
};

function CommonCodeWorkspace({
  onCodesDirty,
  onStandardsDirty,
  standardsDirty,
}: {
  onCodesDirty(dirty: boolean): void;
  onStandardsDirty(dirty: boolean): void;
  standardsDirty: boolean;
}) {
  const p = usePreferences(),
    shared = useSharedScreen("UI-27");
  const [view, setView] = useState<View | null>(null),
    [codeView, setCodeView] = useState<"codes" | "valueCodes">("codes");
  const [groups, setGroups] = useState<{ groupId: string; name: string }[]>([]);
  const [groupError, setGroupError] = useState("");
  const [groupReload, setGroupReload] = useState(0);
  const cols = (p.get("COLS", "UI-27")?.value ?? {}) as ColumnPreferences;
  const dense = p.get("DENSE")?.value === "좁게";
  const pageSize = shared.pageSize ?? 50;
  const saveSize = async (size: 20 | 50 | 100) => {
    await p.put("PAGE_SIZE", "*", size);
  };
  const density = () => {
    void p.put("DENSE", "*", dense ? "넓게" : "좁게").catch(() => {});
  };
  useEffect(() => {
    let live = true;
    const load = () => {
      setGroupError("");
      void getCodeGroups()
        .then((result) => {
          if (live) setGroups(result.items);
        })
        .catch((error: unknown) => {
          if (live)
            setGroupError(
              error instanceof Error
                ? error.message
                : "그룹을 불러오지 못했습니다.",
            );
        });
    };
    load();
    window.addEventListener(CODE_GROUPS_COMMITTED, load);
    return () => {
      live = false;
      window.removeEventListener(CODE_GROUPS_COMMITTED, load);
    };
  }, [groupReload]);
  return (
    <>
      {groupError && (
        <ErrorBanner
          message={groupError}
          onRetry={() => setGroupReload((n) => n + 1)}
        />
      )}
      <CommonCodesScreen
        embedded
        panelDirty={standardsDirty}
        onDirtyChange={onCodesDirty}
        onModeChange={setCodeView}
        pageSize={pageSize}
        dense={dense}
        columnView={cols[codeView]}
        onColumnsChange={() => setView(codeView)}
        onDensityChange={density}
        onPageSizeChange={saveSize}
        panels={{
          mappings: (
            <CodeMappingsScreen
              pageSize={pageSize}
              dense={dense}
              columnOrder={cols.mappings?.order}
              onPageSizeChange={saveSize}
              onColumnsChange={() => setView("mappings")}
              onDenseChange={density}
            />
          ),
          standards: (
            <OperatingStandardsScreen
              dense={dense}
              columnView={cols.standards}
              onColumnsChange={() => setView("standards")}
              onDensityChange={density}
              onDirtyChange={onStandardsDirty}
            />
          ),
          history: (
            <ChangeHistoryPanel
              groupOptions={groups}
              pageSize={pageSize}
              dense={dense}
              columnOrder={cols.history?.order}
              hiddenColumns={cols.history?.hidden}
              onPageSizeChange={saveSize}
              onColumnsChange={() => setView("history")}
              onDensityChange={density}
            />
          ),
        }}
      />
      {view && (
        <ColumnPreferencesDialog
          key={view}
          open
          onClose={() => setView(null)}
          screenId="UI-27"
          viewId={view}
          columns={tableColumns[view]}
        />
      )}
      {p.message && <p role="status">{p.message}</p>}
    </>
  );
}

function RegisteredApplication() {
  const preferences = usePreferences();
  const [codesDirty, setCodesDirty] = useState(false),
    [standardsDirty, setStandardsDirty] = useState(false);
  const onCodesDirty = useCallback(
    (dirty: boolean) => setCodesDirty(dirty),
    [],
  );
  const onStandardsDirty = useCallback(
    (dirty: boolean) => setStandardsDirty(dirty),
    [],
  );
  const adapter: SharedPreferenceAdapter = {
    items: preferences.items,
    loading: preferences.loading,
    pending: preferences.pending,
    error: preferences.error,
    retry: () => {
      void preferences.refresh().catch(() => {});
    },
    save: (type, key, value) =>
      preferences.put(type as PreferenceType, key, value as PreferenceValue),
    reset: (type, key) => preferences.reset(type as PreferenceType, key),
  };
  return (
    <SharedApplication
      preferences={adapter}
      isDirty={(id) => id === "UI-27" && (codesDirty || standardsDirty)}
      renderSettings={(open, onClose) => (
        <PreferenceSettings open={open} onClose={onClose} />
      )}
      codes={
        <CommonCodeWorkspace
          standardsDirty={standardsDirty}
          onCodesDirty={onCodesDirty}
          onStandardsDirty={onStandardsDirty}
        />
      }
    />
  );
}

const application = <RegisteredApplication />;
export const applicationRoutes: RouteObject[] = [
  { path: "/", element: application },
  ...Object.keys(screenRegistry).map((id) => ({
    path: `/${id}`,
    element: application,
  })),
  { path: "*", element: application },
];
function RegisteredRoutes() {
  return useRoutes(applicationRoutes);
}
export function ApplicationRoutes() {
  return (
    <PreferencesProvider>
      <RegisteredRoutes />
    </PreferencesProvider>
  );
}
export function ApplicationRouter() {
  return (
    <BrowserRouter>
      <ApplicationRoutes />
    </BrowserRouter>
  );
}
