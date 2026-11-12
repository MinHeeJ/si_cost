import { useEffect, useRef, useState } from "react";
import { DataTable } from "../../shared/DataTable";
import { ErrorBanner, StatusBadge, type Tone } from "../../shared/Feedback";
import { ApiError } from "../../shared/api";
import { formatDateTime } from "../../shared/format";
import {
  MonthlyMatrixTable,
  formatDistributionAmount,
} from "./MonthlyMatrixTable";
import { DistributionUploadResultDialog } from "./DistributionUploadResultDialog";
import * as api from "./distributionApi";
import "./distribution.css";

export type ProjectDistributionPanelProps = {
  projectId: string;
  customerName: string | null;
  initialStep?: api.DistributionStep;
  amountUnit?: api.AmountUnit;
  readOnly?: boolean;
  refreshKey?: number;
  onBack(): void;
  onUpload(context: {
    projectId: string;
    step: api.DistributionStep;
    expectedRevision: string;
    onApplied(): void;
  }): void;
  onAmountUnitChange?(unit: api.AmountUnit): void;
};
const steps: { code: api.DistributionStep; label: string }[] = [
  { code: "EST", label: "EST 견적" },
  { code: "CTRT", label: "CTRT 최종 계약" },
  { code: "PLAN", label: "PLAN 실제 투입계획" },
];
const statuses: Record<
  api.DistributionProject["status"],
  { label: string; tone: Tone }
> = {
  ING: { label: "진행", tone: "info" },
  PRE: { label: "수주예정", tone: "warn" },
  END: { label: "종료", tone: "lock" },
  LOSE: { label: "실주", tone: "bad" },
};
// SCR-UI-01: replaces the list region in the retained /UI-01 tab, never another route.
export function ProjectDistributionPanel({
  projectId,
  customerName,
  initialStep = "EST",
  amountUnit = "천원",
  readOnly = false,
  refreshKey = 0,
  onBack,
  onUpload,
  onAmountUnitChange,
}: ProjectDistributionPanelProps) {
  const [step, setStep] = useState(initialStep),
    [unit, setUnit] = useState(amountUnit);
  const [data, setData] = useState<api.ProjectDistributionData>(),
    [comparison, setComparison] = useState<api.ProjectDistributionComparison>();
  const [uploads, setUploads] = useState<api.ProjectUploadRecord[]>([]);
  const [loading, setLoading] = useState(true),
    [error, setError] = useState<unknown>(),
    [reload, setReload] = useState(0);
  const [actionError, setActionError] = useState<unknown>(),
    [pending, setPending] = useState(false),
    [resultId, setResultId] = useState<number>();
  const busy = useRef(false);
  useEffect(() => {
    setUnit(amountUnit);
  }, [amountUnit]);
  useEffect(() => {
    setStep(initialStep);
  }, [projectId, initialStep]);
  useEffect(() => {
    let current = true;
    setLoading(true);
    setError(undefined);
    setData(undefined);
    setComparison(undefined);
    setUploads([]);
    Promise.all([
      api.getProjectDistribution(projectId, step),
      api.getProjectDistributionComparison(projectId),
      api.getProjectDistributionUploads(projectId),
    ])
      .then(([table, compared, history]) => {
        if (current) {
          setData(table);
          setComparison(compared);
          setUploads(history.items);
        }
      })
      .catch((e) => {
        if (current) setError(e);
      })
      .finally(() => {
        if (current) setLoading(false);
      });
    return () => {
      current = false;
    };
  }, [projectId, step, reload, refreshKey]);
  async function download(action: () => Promise<void>) {
    if (busy.current) return;
    busy.current = true;
    setPending(true);
    setActionError(undefined);
    try {
      await action();
    } catch (e) {
      setActionError(e);
    } finally {
      busy.current = false;
      setPending(false);
    }
  }
  const money = (value: string | null) => formatDistributionAmount(value, unit);
  const banner = (e: unknown, retry?: () => void) => (
    <ErrorBanner
      message={e instanceof Error ? e.message : "조회하지 못했습니다."}
      requestId={e instanceof ApiError ? e.body.requestId : undefined}
      onRetry={retry}
    />
  );
  const locked =
    readOnly ||
    data?.project.status === "END" ||
    data?.project.status === "LOSE";
  return (
    <section
      className="distribution-panel"
      data-testid="project-distribution-panel"
      data-screen-id="SCR-UI-01"
      aria-busy={loading}
    >
      <header className="distribution-toolbar">
        <div className="distribution-actions">
          <button
            type="button"
            data-testid="distribution-back"
            onClick={onBack}
          >
            목록
          </button>
          <h1>턴키 배분표</h1>
        </div>
        <div className="distribution-actions distribution-desktop-actions">
          <button
            type="button"
            data-testid="distribution-template"
            disabled={pending || loading || !data}
            onClick={() =>
              void download(() => api.downloadDistributionTemplate(projectId))
            }
          >
            양식 받기
          </button>
          <button
            type="button"
            data-testid="distribution-upload"
            className="sicost-primary"
            disabled={pending || loading || !data || locked}
            onClick={() =>
              data &&
              onUpload({
                projectId,
                step,
                expectedRevision: data.revision,
                onApplied: () => setReload((value) => value + 1),
              })
            }
          >
            엑셀 업로드
          </button>
          <button
            type="button"
            data-testid="distribution-export"
            disabled={pending || loading || !data}
            onClick={() =>
              void download(() =>
                api.downloadDistribution(projectId, step, unit),
              )
            }
          >
            배분표 다운로드
          </button>
        </div>
      </header>
      {loading && <p role="status">불러오는 중입니다.</p>}
      {Boolean(error) && banner(error, () => setReload((value) => value + 1))}
      {Boolean(actionError) && banner(actionError)}
      {data && (
        <>
          <div
            className="distribution-project"
            data-testid="distribution-project-header"
          >
            <strong>
              {data.project.id} · {data.project.name}
            </strong>
            <span>고객사 {customerName ?? "–"}</span>
            <span>
              계약금액 {money(data.project.contractAmount)} {unit}
            </span>
            <span>
              {data.project.startDate} ~ {data.project.endDate}
            </span>
            <StatusBadge tone={statuses[data.project.status].tone}>
              {statuses[data.project.status].label}
            </StatusBadge>
          </div>
          {locked && (
            <p className="sicost-banner sicost-tone-lock">
              조회 전용입니다. 배분표는 열람·다운로드할 수 있습니다.
            </p>
          )}
          <nav
            className="distribution-tabs"
            aria-label="배분표 조회 단계"
            role="tablist"
          >
            {steps.map((item) => (
              <button
                type="button"
                role="tab"
                data-testid={`distribution-tab-${item.code.toLowerCase()}`}
                key={item.code}
                aria-selected={step === item.code}
                aria-controls="distribution-stage-panel"
                onClick={() => setStep(item.code)}
              >
                {item.label}
              </button>
            ))}
          </nav>
          <div className="distribution-toolbar">
            <p>
              계산에 쓰는 단계{" "}
              <StatusBadge tone={data.selectedStep ? "info" : "neutral"}>
                {data.selectedStep ?? "미등록"}
              </StatusBadge>{" "}
              · 저장된 표 전체 기준 PLAN &gt; CTRT &gt; EST
            </p>
            <label>
              단위{" "}
              <select
                aria-label="배분표 금액 단위"
                data-testid="distribution-unit"
                value={unit}
                onChange={(event) => {
                  const next = event.target.value as api.AmountUnit;
                  setUnit(next);
                  onAmountUnitChange?.(next);
                }}
              >
                <option>원</option>
                <option>천원</option>
                <option>백만원</option>
              </select>
            </label>
          </div>
          {comparison && (
            <div
              className={`sicost-banner ${comparison.warnings.length ? "sicost-tone-warn" : "sicost-tone-ok"}`}
              data-testid="distribution-comparison-banner"
            >
              <span>
                계산표 총액 {money(comparison.totalAmount)} {unit} · 계약{" "}
                {money(comparison.contractAmount)} {unit} · 차이{" "}
                {money(comparison.differenceAmount)} {unit} · 불일치{" "}
                {comparison.mismatchCount}건
              </span>
              {comparison.warnings.map((warning, index) => (
                <span data-testid={`distribution-warning-${index}`} key={index}>
                  {warning}
                </span>
              ))}
            </div>
          )}
          <div
            id="distribution-stage-panel"
            role="tabpanel"
            data-testid="distribution-stage-panel"
            aria-label={`${step} 배분표`}
          >
            <MonthlyMatrixTable data={data} amountUnit={unit} />
          </div>
          <p className="distribution-help">
            조회 단계는 계산 단계를 변경하지 않습니다. 직접 셀 수정·행 추가·삭제
            없이 재업로드로만 변경합니다.
          </p>
          <h2>배정·배분표 불일치 {comparison?.mismatchCount ?? 0}건</h2>
          <DataTable
            rows={comparison?.items ?? []}
            rowKey={(row) => `${row.developerId}-${row.month}-${row.type}`}
            empty="배정·배분표 불일치가 없습니다."
            columns={[
              {
                id: "person",
                label: "사람",
                render: (row) => (
                  <span
                    data-testid={`distribution-mismatch-${row.developerId}-${row.month}-${row.type.toLowerCase()}`}
                  >
                    {row.managementNo} · {row.name}
                  </span>
                ),
              },
              {
                id: "type",
                label: "유형",
                render: (row) => (
                  <StatusBadge tone="warn">
                    {row.type === "NO_ASSIGNMENT" ? "배정 없음" : "배분표 없음"}
                  </StatusBadge>
                ),
              },
              { id: "month", label: "해당 월", render: (row) => row.month },
              {
                id: "amount",
                label: `금액 (${unit})`,
                align: "right",
                render: (row) => money(row.amount),
              },
              {
                id: "mm",
                label: "실제MM",
                align: "right",
                render: (row) => row.actualMm,
              },
            ]}
          />
          <h2>업로드 기록</h2>
          <DataTable
            rows={uploads}
            rowKey={(row) => row.uploadId}
            empty="업로드 기록이 없습니다."
            columns={[
              {
                id: "date",
                label: "일시",
                render: (row) => (
                  <span data-testid={`distribution-upload-row-${row.uploadId}`}>
                    {formatDateTime(row.registeredAt)}
                  </span>
                ),
              },
              {
                id: "step",
                label: "당시 단계",
                render: (row) =>
                  steps.find((item) => item.code === row.step)?.label ??
                  row.step,
              },
              { id: "file", label: "파일", render: (row) => row.fileName },
              { id: "actor", label: "처리자", render: (row) => row.actor },
              {
                id: "status",
                label: "검사/반영 상태",
                render: (row) => (
                  <StatusBadge tone={row.status === "APPLIED" ? "ok" : "info"}>
                    {row.status === "APPLIED" ? "반영 완료" : "검사 완료"}
                  </StatusBadge>
                ),
              },
              {
                id: "result",
                label: "결과",
                render: (row) => (
                  <button
                    type="button"
                    data-testid={`distribution-result-${row.uploadId}`}
                    onClick={() => setResultId(row.uploadId)}
                  >
                    결과 보기
                  </button>
                ),
              },
            ]}
          />
        </>
      )}
      {resultId !== undefined && (
        <DistributionUploadResultDialog
          uploadId={resultId}
          onClose={() => setResultId(undefined)}
        />
      )}
    </section>
  );
}
