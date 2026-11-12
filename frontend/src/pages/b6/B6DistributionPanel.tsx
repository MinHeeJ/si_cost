import { useEffect, useState } from "react";
import { DataTable } from "../../shared/DataTable";
import { ErrorBanner, StatusBadge } from "../../shared/Feedback";
import { ApiError } from "../../shared/api";
import { B6UploadDialog } from "./B6UploadDialog";
import {
  B6ComparisonPanel,
  b6Amount,
  type B6AmountUnit,
} from "./B6ComparisonPanel";
import * as api from "./b6Api";
import "./b6.css";

export type B6DistributionPanelProps = {
  projectId: string;
  year: number;
  readOnly?: boolean;
  customerName?: string | null;
  amountUnit?: B6AmountUnit;
  onAmountUnitChange?(unit: B6AmountUnit): void;
  onBack(): void;
  onApplied?(): void | Promise<void>;
  onDirtyChange?(dirty: boolean): void;
};
// SCR-UI-01: the retained project list owns this content region and restores its filters/page/scroll onBack.
export function B6DistributionPanel({
  projectId,
  year,
  readOnly = false,
  customerName,
  amountUnit = "천원",
  onAmountUnitChange,
  onBack,
  onApplied,
  onDirtyChange,
}: B6DistributionPanelProps) {
  const [step, setStep] = useState<api.ProjectStep>("PLAN"),
    [unit, setUnit] = useState(amountUnit);
  const [data, setData] = useState<api.Distribution>(),
    [comparison, setComparison] = useState<api.B6Comparison>(),
    [uploads, setUploads] = useState<api.DistributionUpload[]>([]);
  const [loading, setLoading] = useState(true),
    [error, setError] = useState<unknown>(),
    [reload, setReload] = useState(0);
  const [upload, setUpload] = useState(false),
    [pending, setPending] = useState(false),
    [actionError, setActionError] = useState<unknown>(),
    [result, setResult] =
      useState<Awaited<ReturnType<typeof api.getUploadResult>>>();
  const mobile = window.matchMedia?.("(max-width: 767px)").matches ?? false;
  useEffect(() => {
    let active = true;
    if (mobile) return;
    setLoading(true);
    setError(undefined);
    Promise.all([
      api.getDistribution(projectId, step),
      api.getDistributionComparison(projectId),
      api.getDistributionUploads(projectId),
    ])
      .then(([table, check, history]) => {
        if (active) {
          setData(table);
          setComparison(check);
          setUploads(history.items);
        }
      })
      .catch((e) => {
        if (active) setError(e);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [projectId, step, reload, mobile]);
  useEffect(() => setUnit(amountUnit), [amountUnit]);
  async function action(run: () => Promise<void>) {
    if (pending) return;
    setPending(true);
    setActionError(undefined);
    try {
      await run();
    } catch (e) {
      setActionError(e);
    } finally {
      setPending(false);
    }
  }
  const feedback = (value: unknown) => (
    <ErrorBanner
      message={value instanceof Error ? value.message : "조회하지 못했습니다."}
      requestId={value instanceof ApiError ? value.body.requestId : undefined}
      onRetry={() => setReload((v) => v + 1)}
    />
  );
  if (mobile)
    return (
      <section data-testid="b6-desktop-notice" data-screen-id="SCR-UI-01">
        <p>이 기능은 PC에서 사용해 주세요.</p>
        <button type="button" data-testid="b6-mobile-back" onClick={onBack}>
          목록
        </button>
      </section>
    );
  const locked =
    readOnly ||
    data?.project.status === "LOSE" ||
    Boolean(data && data.project.contractType !== "TK");
  return (
    <section
      data-testid="b6-distribution-panel"
      data-screen-id="SCR-UI-01"
      className="b6-distribution"
    >
      <header className="b6-toolbar">
        <button type="button" data-testid="b6-back-button" onClick={onBack}>
          목록
        </button>
        <h2>턴키 배분표{data ? ` · ${data.project.name}` : ""}</h2>
        <button
          type="button"
          data-testid="b6-template-button"
          disabled={pending}
          onClick={() =>
            void action(async () =>
              api.saveDownload(await api.getB6Template(projectId)),
            )
          }
        >
          양식 받기
        </button>
        <button
          type="button"
          data-testid="b6-upload-button"
          disabled={locked || loading || Boolean(error)}
          onClick={() => setUpload(true)}
        >
          엑셀 업로드
        </button>
        <button
          type="button"
          data-testid="b6-export-button"
          disabled={pending || !data}
          onClick={() =>
            void action(async () =>
              api.saveDownload(
                await api.exportDistribution(projectId, step, unit),
              ),
            )
          }
        >
          배분표 다운로드
        </button>
      </header>
      {loading && <p role="status">불러오는 중입니다.</p>}
      {error ? feedback(error) : null}
      {actionError ? feedback(actionError) : null}
      {data && data.project.contractType !== "TK" ? (
        <p role="alert">턴키 프로젝트만 배분표를 사용할 수 있습니다.</p>
      ) : (
        data && (
          <>
            <section className="b6-project-info" data-testid="b6-project-info">
              <p>
                {projectId} · 고객사 {customerName ?? "–"} · 계약금액{" "}
                {b6Amount(data.project.contractAmount, unit)} {unit} ·{" "}
                {data.project.startDate} ~ {data.project.endDate}
              </p>
              <StatusBadge
                tone={
                  data.project.status === "PRE"
                    ? "warn"
                    : data.project.status === "ING"
                      ? "info"
                      : "lock"
                }
              >
                {data.project.status}
              </StatusBadge>
            </section>
            {locked && (
              <p className="sicost-banner sicost-tone-lock">
                지난 연도 또는 실주 프로젝트는 조회만 할 수 있습니다.
              </p>
            )}
            <div
              className="b6-toolbar"
              role="tablist"
              aria-label="배분표 조회 단계"
            >
              {(
                [
                  ["EST", "견적"],
                  ["CTRT", "최종 계약"],
                  ["PLAN", "실제 투입계획"],
                ] as const
              ).map(([value, label]) => (
                <button
                  type="button"
                  role="tab"
                  aria-selected={step === value}
                  data-testid={`b6-stage-${value.toLowerCase()}`}
                  key={value}
                  onClick={() => setStep(value)}
                >
                  {value} {label}
                </button>
              ))}
            </div>
            <p>
              계산에 쓰는 단계{" "}
              <StatusBadge tone="info">
                {data.selectedStep ?? "미등록"}
              </StatusBadge>{" "}
              · 조회 탭은 열람 전용이며 계산 단계를 수동으로 변경하지 않습니다.
            </p>
            <label>
              금액 단위
              <select
                data-testid="b6-amount-unit"
                value={unit}
                onChange={(event) => {
                  const value = event.target.value as B6AmountUnit;
                  setUnit(value);
                  onAmountUnitChange?.(value);
                }}
              >
                <option>원</option>
                <option>천원</option>
                <option>백만원</option>
              </select>
            </label>
            <section
              role="tabpanel"
              aria-label={`${step} 배분표`}
              data-testid="b6-matrix-panel"
              className="b6-matrix"
            >
              <DataTable
                rows={data.rows}
                rowKey={(row) => row.rowSn}
                empty="등록된 배분표가 없습니다. 양식을 받아 엑셀로 업로드해 주세요."
                columns={[
                  {
                    id: "type",
                    label: "사람/항목",
                    render: (row) => (row.rowType === "DEV" ? "사람" : "항목"),
                  },
                  {
                    id: "number",
                    label: "관리번호",
                    render: (row) => row.managementNo ?? "–",
                  },
                  {
                    id: "name",
                    label: "이름/항목명",
                    render: (row) => (
                      <>
                        {row.name ?? row.itemName}
                        {row.useYn === "N" && (
                          <StatusBadge tone="lock">중지</StatusBadge>
                        )}
                      </>
                    ),
                  },
                  {
                    id: "jobs",
                    label: "직무",
                    render: (row) => row.jobNames.join("·") || "–",
                  },
                  ...data.months.map((month) => ({
                    id: month,
                    label: month,
                    align: "right" as const,
                    render: (row: api.DistributionRow) =>
                      b6Amount(
                        row.cells.find((cell) => cell.month === month)
                          ?.amount ?? "0",
                        unit,
                      ),
                  })),
                  {
                    id: "sum",
                    label: "행합계",
                    align: "right",
                    render: (row) => b6Amount(row.totalAmount, unit),
                  },
                ]}
              />
              <div className="b6-month-totals" aria-label="월합계">
                {data.monthlyTotals.map((cell) => (
                  <span
                    key={cell.month}
                    data-testid={`b6-month-total-${cell.month}`}
                  >
                    {cell.month}: {b6Amount(cell.amount, unit)}
                  </span>
                ))}
                <strong>
                  전체합계 {b6Amount(data.totalAmount, unit)} {unit}
                </strong>
              </div>
            </section>
            {comparison && (
              <B6ComparisonPanel comparison={comparison} unit={unit} />
            )}
            <h3>업로드기록</h3>
            <DataTable
              rows={uploads}
              rowKey={(row) => row.uploadId}
              empty="업로드기록이 없습니다."
              columns={[
                { id: "at", label: "일시", render: (row) => row.registeredAt },
                { id: "step", label: "당시 단계", render: (row) => row.step },
                { id: "file", label: "파일", render: (row) => row.fileName },
                { id: "actor", label: "처리자", render: (row) => row.actor },
                {
                  id: "status",
                  label: "상태",
                  render: (row) => (
                    <StatusBadge
                      tone={row.status === "APPLIED" ? "ok" : "info"}
                    >
                      {row.status}
                    </StatusBadge>
                  ),
                },
                {
                  id: "result",
                  label: "결과",
                  render: (row) => (
                    <button
                      type="button"
                      data-testid={`b6-result-${row.uploadId}`}
                      disabled={pending}
                      onClick={() =>
                        void action(async () =>
                          setResult(await api.getUploadResult(row.uploadId)),
                        )
                      }
                    >
                      결과 보기
                    </button>
                  ),
                },
              ]}
            />
            {result && (
              <section data-testid="b6-history-result" role="status">
                업로드 번호 {result.uploadId} · {result.status} · 대상{" "}
                {result.projectId} · 당시 단계 {result.step} · 전체{" "}
                {result.summary.totalRows}건 / 정상 {result.summary.successRows}{" "}
                / 경고 {result.summary.warningRows} / 오류{" "}
                {result.summary.errorRows}
              </section>
            )}
            {upload && (
              <B6UploadDialog
                projectId={projectId}
                year={year}
                startDate={data.project.startDate}
                endDate={data.project.endDate}
                readOnly={locked}
                onClose={() => setUpload(false)}
                onDirtyChange={onDirtyChange}
                onApplied={async () => {
                  setReload((v) => v + 1);
                  await onApplied?.();
                }}
              />
            )}
          </>
        )
      )}
    </section>
  );
}
