import { useEffect, useState } from "react";
import { DataTable, type Column } from "../../shared/DataTable";
import { ErrorBanner } from "../../shared/Feedback";
import { ApiError } from "../../shared/api";
import { formatAmount, formatRate, type AmountUnit } from "../../shared/format";
import {
  getCandidatePositions,
  type CandidatePosition,
  type CandidatePositions,
  type CandidateRead,
} from "./candidateMatchingApi";
import "./candidateMatching.css";

// SCR-UI-33: child of candidate-basic-panel, never the top-level KPI summary.
export function CandidatePositionsPanel({
  candidate,
  active = true,
  refreshKey = 0,
  amountUnit = "원",
  label = (_group, code) => code ?? "–",
}: {
  candidate: {
    id: string;
    jobCode: string;
    desiredRate: string;
    availableDate: string;
    contactStatus: string;
    gradeCode?: string;
    employeeType?: string;
  };
  active?: boolean;
  refreshKey?: number;
  amountUnit?: AmountUnit;
  label?(group: string, code: string | null): string;
}) {
  const [result, setResult] = useState<CandidateRead<CandidatePositions>>();
  const [error, setError] = useState<unknown>(),
    [loading, setLoading] = useState(true),
    [retry, setRetry] = useState(0);
  useEffect(() => {
    if (!active) return;
    const controller = new AbortController();
    setResult(undefined);
    setError(undefined);
    setLoading(true);
    void getCandidatePositions(candidate.id, controller.signal)
      .then((data) => {
        if (!controller.signal.aborted) setResult(data);
      })
      .catch((reason) => {
        if (!controller.signal.aborted) setError(reason);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [
    active,
    candidate.id,
    candidate.jobCode,
    candidate.desiredRate,
    candidate.availableDate,
    candidate.contactStatus,
    candidate.gradeCode,
    candidate.employeeType,
    refreshKey,
    retry,
  ]);
  if (!active) return null;
  const columns: Column<CandidatePosition>[] = [
    {
      id: "opportunity",
      label: "기회 / 확률",
      render: (row) => (
        <>
          <a
            data-testid={`candidate-position-link-${row.opportunityId.toLowerCase()}-${row.needSn}`}
            href={`/UI-29?opportunityId=${encodeURIComponent(row.opportunityId)}`}
          >
            {row.name}
          </a>
          <small className="candidate-sub">{formatRate(row.probability)}</small>
        </>
      ),
    },
    {
      id: "job",
      label: "직무 / 등급",
      render: (row) =>
        `${label("JOB", row.jobCode)} / ${label("GRADE", row.gradeCode)}`,
    },
    {
      id: "startMonth",
      label: "시작월",
      render: (row) => (
        <span title={`종료 ${row.endMonth} · 가능일 ${row.availableDate}`}>
          {row.startMonth}
        </span>
      ),
    },
    {
      id: "effectiveRate",
      label: `유효 자리단가 (${amountUnit})`,
      align: "right",
      render: (row) => formatAmount(row.effectiveRate, amountUnit),
    },
    {
      id: "profitRate",
      label: "예상 이익률",
      align: "right",
      render: (row) => (
        <span
          className={
            row.belowApprovalThreshold ? "candidate-position-below" : undefined
          }
          title={`후보 원가 ${formatAmount(row.candidateCost, amountUnit)} ${amountUnit} · 등급 거리 ${row.gradeDistance ?? "미지정"}`}
        >
          {formatRate(row.profitRate)}
        </span>
      ),
    },
  ];
  return (
    <section
      className="candidate-matching-panel"
      aria-label="맞는 자리"
      aria-busy={loading}
      data-testid="candidate-positions-panel"
    >
      <header>
        <h3>맞는 자리</h3>
        <span>최대 4곳 · 영업 기회 기준</span>
      </header>
      <p className="candidate-matching-help">
        자동 개발자 등록·실제 배정·가배정이 아닌 조회 결과입니다.
      </p>
      {error ? (
        <ErrorBanner
          message={
            error instanceof Error
              ? error.message
              : "맞는 자리를 불러오지 못했습니다."
          }
          requestId={
            error instanceof ApiError ? error.body.requestId : undefined
          }
          onRetry={() => setRetry((v) => v + 1)}
        />
      ) : (
        <>
          {result?.issues.map((issue, index) => (
            <p
              key={index}
              role="status"
              className="sicost-banner sicost-tone-warn"
            >
              원천 미확인: {issue.reason} ({issue.field})
            </p>
          ))}
          {loading ? (
            <p role="status">맞는 자리를 불러오는 중입니다.</p>
          ) : result &&
            (result.data.items.length > 0 || !result.issues.length) ? (
            <DataTable
              testId="candidate-positions-table"
              rows={result.data.items}
              columns={columns}
              rowKey={(row) => `${row.opportunityId}-${row.needSn}`}
              empty="조건에 맞는 자리가 없습니다."
            />
          ) : null}
        </>
      )}
    </section>
  );
}
