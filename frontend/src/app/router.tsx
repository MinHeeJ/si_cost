import { useCallback, useEffect, useState } from "react";
import { ErrorBanner } from "../shared/Feedback";
import {
  BrowserRouter,
  useLocation,
  useNavigate,
  useSearchParams,
  useRoutes,
  type RouteObject,
} from "react-router-dom";
import { BusinessPartnersScreen } from "../pages/shared/BusinessPartnersScreen";
import { UsersScreen } from "../pages/shared/UsersScreen";
import { BusinessUsersWorkspace } from "../pages/businessusers/BusinessUsersWorkspace";
import { ProjectsWorkspace } from "../pages/projects/ProjectsWorkspace";
import type { ProjectsRegistration } from "../pages/projects/ProjectsScreen";
import type { ProjectDetailContext } from "../pages/projects/ProjectDetailPanel";
import { PROJECTS_COMMITTED, getProject, type ProjectDetail } from "../pages/projects/projectsApi";
import { ProjectPartDialog } from "../pages/parts/ProjectPartDialog";
import { ProjectStatusPanel } from "../pages/projectsops/ProjectStatusPanel";
import { ProjectCostAnalysisScreen } from "../pages/shared/ProjectCostAnalysisScreen";
import { ProjectStatusBoardScreen } from "../pages/shared/ProjectStatusBoardScreen";
import { ProjectCostDetailPanel } from "../pages/costanalysis/ProjectCostDetailPanel";
import { ProjectCostSummaryPanel } from "../pages/costanalysis/ProjectCostSummaryPanel";
import { ProjectProgressPanel } from "../pages/progress/ProjectProgressPanel";
import { ProjectIssuesPanel } from "../pages/issues/ProjectIssuesPanel";
import { ProjectStatusIssuesPanel } from "../pages/issues/ProjectStatusIssuesPanel";
import { B6UploadDialog } from "../pages/b6/B6UploadDialog";
import { ClientsScreen } from "../pages/clients/ClientsScreen";
import { DeveloperPoolScreen } from "../pages/shared/DeveloperPoolScreen";
import { StaffingAssignmentsScreen } from "../pages/shared/StaffingAssignmentsScreen";
import { BillingReceiptsScreen } from "../pages/shared/BillingReceiptsScreen";
import { FinancialItemsScreen } from "../pages/financialitems/FinancialItemsScreen";
import { CandidatesScreen } from "../pages/candidates/CandidatesScreen";
import { SharedScreenFrame } from "../pages/shared/FollowUpScreen";
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

const peopleColumns: Record<"developers" | "candidates", PreferenceColumn[]> = {
  developers: [
    { id: "name", label: "이름", required: true },
    { id: "companyName", label: "소속" },
    { id: "employeeType", label: "직원구분" },
    { id: "grade", label: "등급" },
    { id: "job", label: "주 직무" },
    { id: "status", label: "상태" },
    { id: "currentProjects", label: "현재 프로젝트" },
    { id: "withdrawalDate", label: "철수 예정일" },
    { id: "monthlyRate", label: "월 단가" },
  ],
  candidates: [
    { id: "name", label: "이름", required: true },
    { id: "employeeType", label: "구분" },
    { id: "job", label: "직무" },
    { id: "grade", label: "등급" },
    { id: "skills", label: "기술" },
    { id: "homeAddress", label: "거주지" },
    { id: "desiredRate", label: "희망 단가" },
    { id: "availableDate", label: "투입 가능일" },
    { id: "contactStatus", label: "연락 상태" },
    { id: "lastContactDate", label: "최근 연락" },
    { id: "source", label: "출처" },
  ],
};

function PeopleWorkspace({
  screenId,
  onDirtyChange,
}: {
  screenId: "UI-02" | "UI-33";
  onDirtyChange(dirty: boolean): void;
}) {
  const preferences = usePreferences(),
    location = useLocation();
  const [columnsOpen, setColumnsOpen] = useState(false);
  const [error, setError] = useState<unknown>();
  const viewId = screenId === "UI-02" ? "developers" : "candidates";
  const columns = preferences.get("COLS", screenId)?.value as
    | ScreenColumnPreferences
    | undefined;
  const size = preferences.get("PAGE_SIZE")?.value;
  const unit = preferences.get("AMT_UNIT")?.value;
  const dense = preferences.get("DENSE")?.value === "좁게";
  const pageSize: 20 | 50 | 100 = size === 20 || size === 100 ? size : 50;
  const props = {
    active: location.pathname === `/${screenId}`,
    pageSize,
    dense,
    columnView: columns?.[viewId],
    onDirtyChange,
    onColumnsChange: () => setColumnsOpen(true),
    onDensityChange: () => {
      void preferences
        .put("DENSE", "*", dense ? "넓게" : "좁게")
        .catch(setError);
    },
  };
  return (
    <SharedScreenFrame screenId={screenId}>
      {error != null && (
        <ErrorBanner
          message={
            error instanceof Error
              ? error.message
              : "설정을 저장하지 못했습니다."
          }
        />
      )}
      {screenId === "UI-02" ? (
        <DeveloperPoolScreen {...props} />
      ) : (
        <CandidatesScreen
          {...props}
          amountUnit={unit === "원" || unit === "백만원" ? unit : "천원"}
        />
      )}
      {columnsOpen && (
        <ColumnPreferencesDialog
          open
          screenId={screenId}
          viewId={viewId}
          columns={peopleColumns[viewId]}
          onClose={() => setColumnsOpen(false)}
        />
      )}
    </SharedScreenFrame>
  );
}

function RegisteredProjectPart({
  detail,
  year,
  currentYear,
  readOnly,
  onChanged,
  onDirtyChange,
}: ProjectDetailContext) {
  const [open, setOpen] = useState(false);
  const project = detail.project;
  const locked =
    readOnly ||
    project.contractType === "IN" ||
    detail.effectiveStatus === "END" ||
    detail.effectiveStatus === "LOSE";
  return (
    <>
      <button
        type="button"
        data-testid="project-part-change"
        disabled={locked}
        onClick={() => setOpen(true)}
      >
        변경
      </button>
      {open && (
        <ProjectPartDialog
          key={project.id}
          projectId={project.id}
          projectName={project.name}
          year={year}
          currentYear={currentYear}
          effectiveStatus={detail.effectiveStatus}
          contractType={project.contractType}
          readOnly={locked}
          onClose={() => setOpen(false)}
          onSaved={onChanged}
          onDirtyChange={onDirtyChange}
        />
      )}
    </>
  );
}

type DistributionUploadContext = Parameters<
  NonNullable<ProjectsRegistration["distributionUpload"]>
>[0];
function RegisteredDistributionUpload({
  projectId,
  year,
  step,
  onApplied,
  onClose,
  onDirtyChange,
}: DistributionUploadContext) {
  const [detail, setDetail] = useState<ProjectDetail>();
  const [error, setError] = useState<unknown>();
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setDetail(undefined);
    setError(undefined);
    void getProject(projectId, false, controller.signal)
      .then((value) => {
        if (!controller.signal.aborted) setDetail(value);
      })
      .catch((e) => {
        if (!controller.signal.aborted) setError(e);
      });
    return () => controller.abort();
  }, [projectId, retry]);
  if (!detail)
    return (
      <>
        {error != null ? (
          <ErrorBanner
            message={
              error instanceof Error
                ? error.message
                : "프로젝트를 불러오지 못했습니다."
            }
            onRetry={() => setRetry((n) => n + 1)}
          />
        ) : (
          <p role="status">업로드 대상 프로젝트를 불러오는 중입니다.</p>
        )}
        <button
          type="button"
          data-testid="distribution-upload-close"
          onClick={onClose}
        >
          닫기
        </button>
      </>
    );
  return (
    <B6UploadDialog
      key={`${projectId}:${year}:${step}`}
      projectId={projectId}
      year={year}
      initialStep={step}
      startDate={detail.project.startDate}
      endDate={detail.project.endDate}
      readOnly={
        detail.readOnly ||
        detail.effectiveStatus === "END" ||
        detail.effectiveStatus === "LOSE"
      }
      onApplied={onApplied}
      onClose={onClose}
      onDirtyChange={onDirtyChange}
    />
  );
}

function RegisteredProjectIssues({context}:{context:ProjectDetailContext}) {
  const preferences=usePreferences();const size=preferences.get("PAGE_SIZE")?.value;
  return <ProjectIssuesPanel projectId={context.detail.project.id} projectName={context.detail.project.name} year={context.year}
    readOnly={context.readOnly||!["ING","PRE"].includes(context.detail.effectiveStatus)}
    onChanged={context.onChanged} onDirtyChange={context.onDirtyChange}
    pageSize={size===20||size===100?size:50} onPageSizeChange={(value)=>preferences.put("PAGE_SIZE","*",value)} dense={preferences.get("DENSE")?.value === "좁게"} />;
}

function ProjectIssueTabTitle({ projectId }: { projectId: string }) {
  const [count, setCount] = useState<number>();
  useEffect(() => {
    let live = true;
    const load = () => { void import("../pages/issues/projectIssuesApi").then(api => api.getProjectIssues(projectId, { status: "OPEN_ACTIVE", page: 1, size: 20 })).then(data => { if (live) setCount(data.openCount); }).catch(() => { if (live) setCount(undefined); }); };
    load();
    const committed = (event: Event) => { if ((event as CustomEvent<{id: string}>).detail?.id === projectId) load(); };
    window.addEventListener(PROJECTS_COMMITTED, committed);
    return () => { live = false; window.removeEventListener(PROJECTS_COMMITTED, committed); };
  }, [projectId]);
  return <>이슈 ({count ?? "–"})</>;
}

const projectRegistration: ProjectsRegistration = {
  issuesTitle: (context) => <ProjectIssueTabTitle projectId={context.detail.project.id} />,
  costSummary: (context) => (
    <ProjectCostSummaryPanel
      projectId={context.detail.project.id}
      month={context.currentDate.slice(0, 7)}
      amountUnit={context.amountUnit}
    />
  ),
  progress: (context) => (
    <ProjectProgressPanel
      projectId={context.detail.project.id}
      year={context.year}
      onChanged={context.onChanged}
      onDirtyChange={context.onDirtyChange}
    />
  ),
  issues: (context) => <RegisteredProjectIssues context={context} />,
  partAction: (context) => <RegisteredProjectPart {...context} />,
  statusActions: (context) => (
    <ProjectStatusPanel
      projectId={context.detail.project.id}
      name={context.detail.project.name}
      effectiveStatus={context.detail.effectiveStatus}
      revision={context.detail.revision}
      year={context.year}
      onChanged={context.onChanged}
      onDirtyChange={context.onDirtyChange}
    />
  ),
  distributionUpload: (context) => (
    <RegisteredDistributionUpload {...context} />
  ),
};

function RegisteredProjectsWorkspace({
  onDirtyChange,
}: {
  onDirtyChange(dirty: boolean): void;
}) {
  const [params, setParams] = useSearchParams();
  const location = useLocation();
  return (
    <ProjectsWorkspace
      active={location.pathname === "/UI-01"}
      onDirtyChange={onDirtyChange}
      registration={{
        ...projectRegistration,
        selectedProjectId: params.get("projectId") ?? undefined,
        activeTab: params.get("tab") ?? "basic",
        onSelectProject: (id) => {
          const next = new URLSearchParams(params);
          if (id) {
            next.set("projectId", id);
            next.set("tab", "basic");
          } else {
            next.delete("projectId");
            next.delete("tab");
          }
          setParams(next, { replace: true, preventScrollReset: true });
        },
        onTabChange: (tab) => {
          const next = new URLSearchParams(params);
          next.set("tab", tab);
          setParams(next, { replace: true, preventScrollReset: true });
        },
      }}
    />
  );
}

const analysisColumns: Record<
  "projectCost" | "statusBoard",
  PreferenceColumn[]
> = {
  projectCost: [
    ["projectName", "프로젝트"],
    ["contractType", "유형"],
    ["status", "상태"],
    ["originalContractAmount", "원 계약금액"],
    ["contractAmount", "현재 계약금액"],
    ["planMm", "계획 MM"],
    ["actualMm", "실제 MM"],
    ["cumulativeRevenue", "누적 매출"],
    ["cumulativeCost", "누적 원가"],
    ["cumulativeProfit", "이익"],
    ["cumulativeMargin", "누적 마진율"],
    ["forecastMargin", "예상 최종 마진율"],
    ["monthlyMargin", "월간 마진율"],
    ["targetMargin", "목표 마진율"],
    ["targetGap", "목표 대비"],
    ["budgetAmount", "예산"],
    ["budgetUsage", "예산 소진율"],
    ["pricePerMm", "MM당 단가"],
    ["costPerMm", "MM당 원가"],
    ["profitPerMm", "MM당 이익"],
    ["freeMm", "무상지원 MM"],
    ["freeLaborCost", "무상 원가"],
    ["alerts", "경보"],
  ].map(([id, label]) => ({ id, label, required: id === "projectName" })),
  statusBoard: [
    ["light", "신호등"],
    ["projectName", "프로젝트"],
    ["pm", "PM"],
    ["currentStep", "현재 단계"],
    ["planProgress", "계획 진척률"],
    ["actualProgress", "실적 진척률"],
    ["costUsage", "원가 사용률"],
    ["costProgressGap", "진척 대비 원가"],
    ["openIssueCount", "열린 이슈"],
    ["overdueIssueCount", "기한 지난 이슈"],
    ["lastUpdatedDate", "마지막 업데이트"],
    ["elapsedDays", "경과일"],
  ].map(([id, label]) => ({ id, label, required: id === "projectName" })),
};

function RegisteredProjectAnalysis({
  screenId,
  onDirtyChange,
}: {
  screenId: "UI-04" | "UI-31";
  onDirtyChange(dirty: boolean): void;
}) {
  const preferences = usePreferences();
  const [columns, setColumns] = useState(false);
  const viewId = screenId === "UI-04" ? "projectCost" : "statusBoard";
  const size=preferences.get("PAGE_SIZE")?.value;
  const pageSize:20|50|100=size===20||size===100?size:50;
  const storedUnit=preferences.get("AMT_UNIT")?.value;
  const amountUnit=storedUnit==="원"||storedUnit==="백만원"?storedUnit:"천원";
  const period=preferences.get("PERIOD","UI-04")?.value as {type?:string;value?:string}|undefined;
  const views = preferences.get("COLS", screenId)?.value as
    | ScreenColumnPreferences
    | undefined;
  const renderColumns = () => (
    <button
      type="button"
      data-testid={`project-${viewId === "projectCost" ? "cost" : "status"}-columns`}
      onClick={() => setColumns(true)}
    >
      열 설정
    </button>
  );
  if (preferences.loading) return <p role="status">화면 설정을 불러오는 중입니다.</p>;
  return (
    <>
      {screenId === "UI-04" ? (
        <ProjectCostAnalysisScreen
          pageSize={pageSize}
          onPageSizeChange={(value)=>preferences.put("PAGE_SIZE","*",value)}
          amountUnit={amountUnit}
          onAmountUnitChange={(value)=>preferences.put("AMT_UNIT","*",value)}
          initialMonth={period?.type === "YEAR_MONTH" ? period.value : undefined}
          onMonthChange={(value)=>preferences.put("PERIOD","UI-04",{type:"YEAR_MONTH",value})}
          columnView={views?.projectCost}
          dense={preferences.get("DENSE")?.value === "좁게"}
          renderColumns={renderColumns}
          renderDetail={(projectId, month, onClose, amountUnit) => (
            <ProjectCostDetailPanel
              key={projectId}
              projectId={projectId}
              month={month || undefined}
              amountUnit={amountUnit}
              onClose={onClose}
              onDirtyChange={onDirtyChange}
            />
          )}
        />
      ) : (
        <ProjectStatusBoardScreen
          pageSize={pageSize}
          onPageSizeChange={(value)=>preferences.put("PAGE_SIZE","*",value)}
          columnView={views?.statusBoard}
          dense={preferences.get("DENSE")?.value === "좁게"}
          renderColumns={renderColumns}
          renderIssues={(_query, onChanged) => (
            <ProjectStatusIssuesPanel
              onChanged={onChanged}
              onDirtyChange={onDirtyChange}
            />
          )}
        />
      )}
      {columns && (
        <ColumnPreferencesDialog
          open
          screenId={screenId}
          viewId={viewId}
          columns={analysisColumns[viewId]}
          onClose={() => setColumns(false)}
        />
      )}
    </>
  );
}

function RegisteredApplication() {
  const preferences = usePreferences();
  const [codesDirty, setCodesDirty] = useState(false),
    [standardsDirty, setStandardsDirty] = useState(false);
  const [partnersDirty, setPartnersDirty] = useState(false),
    [usersDirty, setUsersDirty] = useState(false);
  const [developersDirty, setDevelopersDirty] = useState(false),
    [candidatesDirty, setCandidatesDirty] = useState(false);
  const [projectsDirty, setProjectsDirty] = useState(false);
  const [costDirty, setCostDirty] = useState(false),
    [boardDirty, setBoardDirty] = useState(false);
  const [assignmentsDirty, setAssignmentsDirty] = useState(false);
  const [billingDirty, setBillingDirty] = useState(false);
  const [financialDirty, setFinancialDirty] = useState(false);
  const onBillingDirty = useCallback(
    (dirty: boolean) => setBillingDirty(dirty),
    [],
  );
  const onAssignmentsDirty = useCallback(
    (dirty: boolean) => setAssignmentsDirty(dirty),
    [],
  );
  const onProjectsDirty = useCallback(
    (dirty: boolean) => setProjectsDirty(dirty),
    [],
  );
  const onDevelopersDirty = useCallback(
    (dirty: boolean) => setDevelopersDirty(dirty),
    [],
  );
  const onCandidatesDirty = useCallback(
    (dirty: boolean) => setCandidatesDirty(dirty),
    [],
  );
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
    // 화면을 열 때 자동으로 남기는 "최근 본 화면"은 사용자가 저장한 것이 아니므로 성공 메시지를 띄우지 않는다.
    save: (type, key, value) =>
      preferences.put(type as PreferenceType, key, value as PreferenceValue, {
        quiet: type === "RECENT",
      }),
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
            : id === "UI-02"
              ? developersDirty
              : id === "UI-33"
                ? candidatesDirty
                : id === "UI-01"
                  ? projectsDirty
                  : id === "UI-04"
                    ? costDirty
                    : id === "UI-31"
                      ? boardDirty
                      : id === "UI-12"
                        ? assignmentsDirty
                        : id === "UI-13"
                          ? billingDirty
                          : id === "UI-10"
                            ? financialDirty
                            : id === "UI-23" && usersDirty
      }
      screens={{
        "UI-01": (
          <SharedScreenFrame screenId="UI-01">
            <RegisteredProjectsWorkspace onDirtyChange={onProjectsDirty} />
          </SharedScreenFrame>
        ),
        "UI-04": (
          <RegisteredProjectAnalysis
            screenId="UI-04"
            onDirtyChange={setCostDirty}
          />
        ),
        "UI-31": (
          <RegisteredProjectAnalysis
            screenId="UI-31"
            onDirtyChange={setBoardDirty}
          />
        ),
        "UI-02": (
          <PeopleWorkspace screenId="UI-02" onDirtyChange={onDevelopersDirty} />
        ),
        "UI-12": (
          <SharedScreenFrame screenId="UI-12">
            <StaffingAssignmentsScreen onDirtyChange={onAssignmentsDirty} />
          </SharedScreenFrame>
        ),
        "UI-13": <BillingReceiptsScreen onDirtyChange={onBillingDirty} />,
        "UI-10": <FinancialItemsScreen onDirtyChange={setFinancialDirty} />,
        "UI-33": (
          <PeopleWorkspace screenId="UI-33" onDirtyChange={onCandidatesDirty} />
        ),
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
