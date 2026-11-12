import { useCallback, useEffect, useRef, useState } from "react";
import { DataTable, type Column } from "../../shared/DataTable";
import { Dialog } from "../../shared/Dialog";
import { Text } from "../../shared/Text";
import { ErrorBanner, StatusBadge } from "../../shared/Feedback";
import { ApiError } from "../../shared/api";
import {
  useCommitRefresh,
  COMMITTED_REFRESH_FAILED,
} from "../../shared/useCommitRefresh";
import * as api from "./projectPlansApi";
import "./plans.css";

export type ProjectPlansPanelProps = {
  projectId: string;
  year: number;
  currentYear: number;
  effectiveStatus: string;
  readOnly?: boolean;
  onDirtyChange?(dirty: boolean): void;
  onSaved(result: api.SaveProjectPlansResult): Promise<void> | void;
};
export function ProjectPlansPanel(props: ProjectPlansPanelProps) {
  return <ProjectPlansEditor key={props.projectId} {...props} />;
}
function ProjectPlansEditor({
  projectId,
  year,
  currentYear,
  effectiveStatus,
  readOnly = false,
  onDirtyChange,
  onSaved,
}: ProjectPlansPanelProps) {
  const [data, setData] = useState<api.ProjectPlansData>();
  const [rows, setRows] = useState<api.ProjectPlan[]>([]);
  const [values, setValues] = useState<Record<string, string>>({});
  const [fields, setFields] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>();
  const [stale, setStale] = useState(false);
  const [discard, setDiscard] = useState(false);
  const [adding, setAdding] = useState(false);
  const [jobs, setJobs] =
    useState<Awaited<ReturnType<typeof api.getPlanJobOptions>>>();
  const [job, setJob] = useState("");
  const [jobLoading, setJobLoading] = useState(false);
  const [jobError, setJobError] = useState<unknown>();
  const [saved, setSaved] = useState<api.SaveProjectPlansResult>();
  const [refreshFailed, setRefreshFailed] = useState(false);
  const [retrying, setRetrying] = useState(false);
  const generation = useRef(0);
  const { pending, commit } = useCommitRefresh();
  const locked =
    readOnly ||
    year < currentYear ||
    effectiveStatus === "LOSE" ||
    effectiveStatus === "END";
  const dirty = rows.some((row) => {
    const original = data?.items.find((item) => item.jobCode === row.jobCode);
    return !original || values[row.jobCode] !== (original.planMm ?? "");
  });
  const blocked =
    loading || !data || pending || locked || stale || refreshFailed || retrying;
  const load = useCallback(async () => {
    const version = ++generation.current;
    setLoading(true);
    setError(undefined);
    try {
      const result = await api.getProjectPlans(projectId);
      if (version !== generation.current) return;
      setData(result);
      setRows(result.items);
      setValues(
        Object.fromEntries(
          result.items.map((row) => [row.jobCode, row.planMm ?? ""]),
        ),
      );
      setFields({});
      setStale(false);
      setSaved(undefined);
    } catch (e) {
      if (version === generation.current) setError(e);
    } finally {
      if (version === generation.current) setLoading(false);
    }
  }, [projectId]);
  useEffect(() => {
    void load();
    return () => {
      generation.current++;
    };
  }, [load]);
  useEffect(() => {
    onDirtyChange?.(dirty);
    return () => onDirtyChange?.(false);
  }, [dirty, onDirtyChange]);
  useEffect(() => {
    const guard = (event: BeforeUnloadEvent) => {
      if (dirty) {
        event.preventDefault();
        event.returnValue = "";
      }
    };
    window.addEventListener("beforeunload", guard);
    return () => window.removeEventListener("beforeunload", guard);
  }, [dirty]);
  useEffect(() => {
    if (locked) setAdding(false);
  }, [locked]);
  async function loadJobs() {
    setJobLoading(true);
    setJobError(undefined);
    try {
      setJobs(await api.getPlanJobOptions());
    } catch (e) {
      setJobError(e);
    } finally {
      setJobLoading(false);
    }
  }
  function openJobs() {
    if (blocked) return;
    setJob("");
    setJobs(undefined);
    setAdding(true);
    void loadJobs();
  }
  const available =
    jobs?.items.filter(
      (option) => !rows.some((row) => row.jobCode === option.code),
    ) ?? [];
  function addJob() {
    if (blocked || jobLoading) return;
    const option = available.find((item) => item.code === job);
    if (!option) return;
    const order = jobs?.order ?? [];
    setRows((previous) =>
      [
        ...previous,
        {
          jobCode: option.code,
          jobName: option.name,
          useYn: "Y" as const,
          planMm: null,
          actualMm: "0.00",
          differenceMm: "0.00",
          source: null,
        },
      ].sort((a, b) => {
        const ai = order.indexOf(a.jobCode),
          bi = order.indexOf(b.jobCode);
        return (
          (ai < 0 ? order.length : ai) - (bi < 0 ? order.length : bi) ||
          a.jobCode.localeCompare(b.jobCode)
        );
      }),
    );
    setValues((previous) => ({ ...previous, [option.code]: "" }));
    setAdding(false);
  }
  async function save() {
    if (blocked) return;
    const invalid: Record<string, string> = {};
    const items: { jobCode: string; planMm: string }[] = [];
    rows.forEach((row) => {
      const value = api.planMmInput(values[row.jobCode] ?? "");
      if (value === null)
        invalid[row.jobCode] =
          "계획 MM은 0~9,999.99, 소수 최대 2자리로 입력해 주세요.";
      else if (
        row.planMm === null ||
        api.planMmUnits(value) !== api.planMmUnits(row.planMm)
      )
        items.push({ jobCode: row.jobCode, planMm: value });
    });
    setFields(invalid);
    if (Object.keys(invalid).length) return;
    setError(undefined);
    try {
      let result: api.SaveProjectPlansResult | undefined;
      await commit(
        async () => {
          result = await api.saveProjectPlans(projectId, {
            year,
            expectedRevision: data!.revision,
            items,
          });
          setSaved(result);
          setData(result.value);
          setRows(result.value.items);
          setValues(
            Object.fromEntries(
              result.value.items.map((row) => [row.jobCode, row.planMm ?? ""]),
            ),
          );
          onDirtyChange?.(false);
        },
        async () => {
          if (result)
            try {
              await onSaved(result);
            } catch (e) {
              setRefreshFailed(true);
              throw e;
            }
        },
      );
    } catch (e) {
      setError(e);
      if (e instanceof ApiError) {
        setStale(e.body.code === "STALE_WRITE");
        const mapped: Record<string, string> = {};
        Object.entries(e.fieldMessages).forEach(([field, message]) => {
          const index = /^items\[(\d+)\]\.planMm$/.exec(field)?.[1];
          if (index !== undefined && items[Number(index)])
            mapped[items[Number(index)].jobCode] = message;
        });
        setFields(mapped);
      }
    }
  }
  async function retryRefresh() {
    if (!saved || retrying) return;
    setRetrying(true);
    try {
      await onSaved(saved);
      setRefreshFailed(false);
    } catch {
      setRefreshFailed(true);
    } finally {
      setRetrying(false);
    }
  }
  function showError(e: unknown, retry?: () => void) {
    return (
      <ErrorBanner
        message={e instanceof Error ? e.message : "계획을 처리하지 못했습니다."}
        requestId={e instanceof ApiError ? e.body.requestId : undefined}
        onRetry={retry}
      />
    );
  }
  function difference(value: bigint) {
    return (
      <span className="project-plans-difference">
        {api.planMmDisplay(value)}{" "}
        <StatusBadge tone={value < 0n ? "bad" : value > 0n ? "warn" : "ok"}>
          {value < 0n ? "부족" : value > 0n ? "초과" : "맞음"}
        </StatusBadge>
      </span>
    );
  }
  let plannedTotal = 0n;
  let actualTotal = 0n;
  rows.forEach((row) => {
    const parsed = api.planMmInput(values[row.jobCode] ?? "");
    plannedTotal +=
      parsed === null
        ? api.planMmUnits(row.planMm ?? "0")
        : api.planMmUnits(parsed);
    actualTotal += api.planMmUnits(row.actualMm);
  });
  const columns: Column<api.ProjectPlan>[] = [
    {
      id: "job",
      label: "직무",
      render: (row) => (
        <span
          data-testid={`project-plans-row-${row.jobCode.toLowerCase().replace(/[^a-z0-9-]/g, "-")}`}
        >
          {row.jobName} <small>{row.jobCode}</small>{" "}
          {row.useYn === "N" && <StatusBadge tone="lock">중지</StatusBadge>}
        </span>
      ),
    },
    {
      id: "planMm",
      label: "계획 MM",
      align: "right",
      render: (row) =>
        locked ? (
          (row.planMm ?? "–")
        ) : (
          <Text
            label={`${row.jobName} 계획 MM`}
            aria-label={`${row.jobName} 계획 MM`}
            className="sicost-decimal"
            data-testid={`project-plan-mm-${row.jobCode.toLowerCase().replace(/[^a-z0-9-]/g, "-")}`}
            value={values[row.jobCode] ?? ""}
            inputMode="decimal"
            error={fields[row.jobCode]}
            disabled={pending || stale || refreshFailed || retrying}
            onChange={(value) => {
              setValues((previous) => ({ ...previous, [row.jobCode]: value }));
              setFields((previous) => ({ ...previous, [row.jobCode]: "" }));
              setSaved(undefined);
            }}
          />
        ),
    },
    {
      id: "actualMm",
      label: "실제 MM",
      align: "right",
      render: (row) => api.planMmDisplay(api.planMmUnits(row.actualMm)),
    },
    {
      id: "differenceMm",
      label: "차이",
      align: "right",
      render: (row) => {
        const parsed = locked
          ? row.planMm
          : api.planMmInput(values[row.jobCode] ?? "");
        return difference(
          api.planMmUnits(row.actualMm) -
            api.planMmUnits(parsed ?? row.planMm ?? "0"),
        );
      },
    },
  ];
  return (
    <section
      className="project-plans-panel"
      data-testid="project-plans-panel"
      data-screen-id="SCR-UI-01"
      aria-label="인원 계획"
    >
      <header>
        <h2>인원 계획</h2>
        {!locked && (
          <div>
            <button
              type="button"
              data-testid="project-plans-add-job"
              disabled={blocked}
              onClick={openJobs}
            >
              직무 추가
            </button>
            <button
              type="button"
              className="sicost-primary"
              data-testid="project-plans-save"
              disabled={blocked}
              onClick={() => void save()}
            >
              {pending ? "저장 중…" : "저장"}
            </button>
          </div>
        )}
      </header>
      <p className="project-plans-description">
        계획은 프로젝트 전체 기간 총 MM입니다. 실제 MM은 미래 월을 포함한 ACT
        배정의 저장 월 MM 합계입니다.
      </p>
      {locked && (
        <p className="sicost-banner sicost-tone-lock">
          선택 연도 또는 프로젝트 상태에 따라 조회만 할 수 있습니다.
        </p>
      )}
      {error != null && showError(error, !data ? () => void load() : undefined)}
      {stale && (
        <p className="sicost-banner sicost-tone-warn">
          다시 조회한 뒤 저장해 주세요. 입력값을 유지하며 자동으로 재저장하지
          않습니다.{" "}
          <button
            type="button"
            data-testid="project-plans-reload"
            onClick={() => (dirty ? setDiscard(true) : void load())}
          >
            다시 조회
          </button>
        </p>
      )}
      <DataTable
        rows={rows}
        columns={columns}
        rowKey={(row) => row.jobCode}
        loading={loading}
        empty="등록된 인원 계획과 실제 투입 직무가 없습니다."
      />
      {data && !loading && (
        <div className="project-plans-total" aria-label="MM 합계">
          <strong>합계</strong>
          <span>계획 {api.planMmDisplay(plannedTotal)}</span>
          <span>실제 {api.planMmDisplay(actualTotal)}</span>
          {difference(actualTotal - plannedTotal)}
        </div>
      )}
      <p className="sicost-banner sicost-tone-info">
        계획보다 모자라는 MM(미충원)은 인력 수요 예측의 필요 인력에 잡힙니다. 더
        투입하지 않으면 계획 MM을 실제에 맞게 줄여 주세요.
      </p>
      {saved && (
        <p role="status" className="sicost-banner sicost-tone-ok">
          {saved.changed
            ? "인원 계획을 저장했습니다."
            : "바뀐 내용이 없습니다."}
        </p>
      )}
      {refreshFailed && (
        <ErrorBanner
          message={COMMITTED_REFRESH_FAILED}
          pending={retrying}
          onRetry={() => void retryRefresh()}
        />
      )}
      <Dialog
        open={adding}
        title="직무 추가"
        onCancel={() => setAdding(false)}
        onConfirm={addJob}
        confirmLabel="추가"
        confirmDisabled={
          blocked || jobLoading || !available.some((item) => item.code === job)
        }
      >
        <div data-testid="project-plans-job-dialog">
          {jobLoading ? (
            <p role="status">직무를 불러오는 중입니다.</p>
          ) : jobError != null ? (
            showError(jobError, () => void loadJobs())
          ) : available.length ? (
            <label className="sicost-field">
              직무 *
              <select
                data-testid="project-plans-job-select"
                value={job}
                onChange={(event) => setJob(event.target.value)}
              >
                <option value="">직무를 선택해 주세요.</option>
                {available.map((item) => (
                  <option key={item.code} value={item.code}>
                    {item.name} ({item.code})
                  </option>
                ))}
              </select>
            </label>
          ) : (
            <p>추가 가능한 직무가 없습니다.</p>
          )}
        </div>
      </Dialog>
      <Dialog
        open={discard}
        title="저장하지 않은 내용이 있습니다."
        cancelLabel="계속 작성"
        confirmLabel="나가기"
        onCancel={() => setDiscard(false)}
        onConfirm={() => {
          setDiscard(false);
          void load();
        }}
      >
        <p>입력한 계획을 폐기하고 최신 값을 조회합니다.</p>
      </Dialog>
    </section>
  );
}
