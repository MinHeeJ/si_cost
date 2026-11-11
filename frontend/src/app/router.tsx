import { useCallback, useEffect, useState } from "react";
import { ErrorBanner } from "../shared/Feedback";
import {
  BrowserRouter,
  useLocation,
  useNavigate,
  useRoutes,
  type RouteObject,
} from "react-router-dom";
import { BusinessPartnersScreen } from "../pages/shared/BusinessPartnersScreen";
import { UsersScreen } from "../pages/shared/UsersScreen";
import { BusinessUsersWorkspace } from "../pages/businessusers/BusinessUsersWorkspace";
import { ClientsScreen } from "../pages/clients/ClientsScreen";
import { SalesPartsScreen } from "../pages/salesparts/SalesPartsScreen";
import { SalesPartStandardDialog } from "../pages/salespartsops/SalesPartStandardDialog";
import { ExcelUploadDialog } from "../pages/b7/ExcelUploadDialog";
import { getB7Template, saveDownload } from "../pages/b7/b7Api";
import { notifyClientsCommitted } from "../pages/clientsops/clientActionsApi";
import {
  clientExportColumns,
  createMasterDataExport,
  saveMasterDataDownload,
} from "../pages/exportsops/masterDataExportApi";
import type { ClientFilters } from "../pages/clients/clientsApi";
import type { ScreenColumnPreferences } from "../pages/me/preferencesApi";
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

function BusinessPartnersWorkspace({
  onDirtyChange,
}: {
  onDirtyChange(dirty: boolean): void;
}) {
  const preferences = usePreferences(),
    location = useLocation();
  const [columnsOpen, setColumnsOpen] = useState(false),
    [upload, setUpload] = useState(false);
  const [standardPart, setStandardPart] = useState<string>();
  const [error, setError] = useState<unknown>(),
    [pending, setPending] = useState(false);
  const [dirty, setDirty] = useState({
    clients: false,
    parts: false,
    upload: false,
    standard: false,
  });
  const clientsDirty = useCallback(
    (value: boolean) => setDirty((old) => ({ ...old, clients: value })),
    [],
  );
  const partsDirty = useCallback(
    (value: boolean) => setDirty((old) => ({ ...old, parts: value })),
    [],
  );
  const uploadDirty = useCallback(
    (value: boolean) => setDirty((old) => ({ ...old, upload: value })),
    [],
  );
  const standardDirty = useCallback(
    (value: boolean) => setDirty((old) => ({ ...old, standard: value })),
    [],
  );
  useEffect(() => {
    onDirtyChange(Object.values(dirty).some(Boolean));
    return () => onDirtyChange(false);
  }, [dirty, onDirtyChange]);
  const view = (
    preferences.get("COLS", "UI-15")?.value as
      | ScreenColumnPreferences
      | undefined
  )?.clients;
  const size = preferences.get("PAGE_SIZE")?.value;
  const pageSize = size === 20 || size === 100 ? size : 50;
  const unit = preferences.get("AMT_UNIT")?.value;
  const amountUnit = unit === "원" || unit === "백만원" ? unit : "천원";
  const dense = preferences.get("DENSE")?.value === "좁게";
  async function download(filters?: ClientFilters) {
    if (pending) return;
    setPending(true);
    setError(undefined);
    try {
      if (!filters) saveDownload(await getB7Template());
      else
        saveMasterDataDownload(
          await createMasterDataExport({
            view: "CLIENTS",
            filters,
            sort: filters.sort,
            direction: filters.direction,
            amountUnit,
            columns: clientExportColumns
              .filter((id) => id === "name" || !view?.hidden.includes(id))
              .sort((a, b) =>
                view ? view.order.indexOf(a) - view.order.indexOf(b) : 0,
              ),
          }),
        );
    } catch (e) {
      setError(e);
    } finally {
      setPending(false);
    }
  }
  return (
    <>
      {error != null && (
        <ErrorBanner
          message={
            error instanceof Error ? error.message : "요청하지 못했습니다."
          }
        />
      )}
      {pending && <p role="status">파일을 준비하고 있습니다.</p>}
      <ClientsScreen
        active={location.pathname === "/UI-15"}
        dense={dense}
        pageSize={pageSize}
        columnView={view}
        onDirtyChange={clientsDirty}
        panelDirty={dirty.parts || dirty.upload || dirty.standard}
        onColumnsChange={() => setColumnsOpen(true)}
        onDensityChange={() => {
          void preferences
            .put("DENSE", "*", dense ? "넓게" : "좁게")
            .catch(setError);
        }}
        onTemplate={() => void download()}
        onUpload={() => setUpload(true)}
        onExport={(filters) => void download(filters)}
        renderParts={
          <SalesPartsScreen
            dense={dense}
            pageSize={pageSize}
            amountUnit={amountUnit}
            onDirtyChange={partsDirty}
            onStandardChange={(detail) => setStandardPart(detail.part.id)}
          />
        }
      />
      {columnsOpen && (
        <ColumnPreferencesDialog
          open
          screenId="UI-15"
          viewId="clients"
          columns={clientExportColumns.map((id) => ({
            id,
            label: {
              type: "구분",
              name: "상호",
              businessNo: "사업자번호",
              ceo: "대표자",
              industry: "업종",
              contactName: "담당자",
              phone: "연락처",
              taxEmail: "세금계산서 이메일",
              account: "지급 계좌",
              projectCount: "진행 프로젝트",
              personCount: "투입 인력",
              useYn: "사용 여부",
            }[id],
            required: id === "name",
          }))}
          onClose={() => setColumnsOpen(false)}
        />
      )}
      {upload && (
        <ExcelUploadDialog
          onClose={() => setUpload(false)}
          onApplied={notifyClientsCommitted}
          onDirtyChange={uploadDirty}
        />
      )}
      {standardPart && (
        <SalesPartStandardDialog
          partId={standardPart}
          onClose={() => setStandardPart(undefined)}
          onSaved={() => setStandardPart(undefined)}
          onDirtyChange={standardDirty}
        />
      )}
    </>
  );
}

function RegisteredUsersWorkspace({
  onDirtyChange,
}: {
  onDirtyChange(dirty: boolean): void;
}) {
  const location = useLocation(),
    navigate = useNavigate();
  const [consumed, setConsumed] = useState<string>();
  const state = location.state as {
    businessUserRegistration?: unknown;
    qualificationPurpose?: unknown;
    returnTo?: unknown;
  } | null;
  const request =
    location.pathname === "/UI-23" &&
    state?.businessUserRegistration === true &&
    consumed !== location.key;
  return (
    <BusinessUsersWorkspace
      onDirtyChange={onDirtyChange}
      registration={
        request
          ? {
              key: location.key,
              role:
                state?.qualificationPurpose === "PART_LEADER"
                  ? "SALES_LDR"
                  : "PM",
              onClose: () => {
                setConsumed(location.key);
                const target = state?.returnTo;
                if (
                  typeof target === "string" &&
                  target !== "/UI-23" &&
                  Object.keys(screenRegistry).some((id) => target === `/${id}`)
                )
                  navigate(target);
                else navigate("/UI-23", { replace: true, state: null });
              },
            }
          : undefined
      }
    />
  );
}

function RegisteredApplication() {
  const preferences = usePreferences();
  const [codesDirty, setCodesDirty] = useState(false),
    [standardsDirty, setStandardsDirty] = useState(false);
  const [partnersDirty, setPartnersDirty] = useState(false),
    [usersDirty, setUsersDirty] = useState(false);
  const onPartnersDirty = useCallback(
    (dirty: boolean) => setPartnersDirty(dirty),
    [],
  );
  const onUsersDirty = useCallback(
    (dirty: boolean) => setUsersDirty(dirty),
    [],
  );
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
      isDirty={(id) =>
        id === "UI-27"
          ? codesDirty || standardsDirty
          : id === "UI-15"
            ? partnersDirty
            : id === "UI-23" && usersDirty
      }
      screens={{
        "UI-15": (
          <BusinessPartnersScreen>
            <BusinessPartnersWorkspace onDirtyChange={onPartnersDirty} />
          </BusinessPartnersScreen>
        ),
        "UI-23": (
          <UsersScreen>
            <RegisteredUsersWorkspace onDirtyChange={onUsersDirty} />
          </UsersScreen>
        ),
      }}
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
