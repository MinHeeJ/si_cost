import { useEffect, useState } from "react";
import { ApiError } from "../../shared/api";
import { ErrorBanner } from "../../shared/Feedback";
import {
  getCandidateSummary,
  type CandidateSummary,
} from "./candidateSummaryApi";
import "./candidateSummary.css";

const metrics: {
  key: keyof CandidateSummary;
  label: string;
  explanation: string;
}[] = [
  { key: "total", label: "전체 후보", explanation: "등록된 전체 후보" },
  { key: "inProgress", label: "진행 중", explanation: "보류·계약 완료 제외" },
  {
    key: "interviewScheduled",
    label: "면접 예정",
    explanation: "면접 예정 상태인 후보",
  },
  {
    key: "availableByNextMonthStart",
    label: "다음 달 시작까지 가능",
    explanation: "진행 중 후보의 가능일이 다음 달 1일 이하",
  },
  {
    key: "overdueContact",
    label: "30일 넘게 미연락",
    explanation: "진행 중·최근 연락이 없으면 등록일 기준·정확히 30일은 제외",
  },
];

export function CandidateSummaryPanel({
  refreshKey = 0,
  active = true,
}: {
  refreshKey?: number;
  active?: boolean;
}) {
  const [data, setData] = useState<CandidateSummary>();
  const [error, setError] = useState<unknown>();
  const [loading, setLoading] = useState(true);
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    if (!active) return;
    const controller = new AbortController();
    setLoading(true);
    setData(undefined);
    setError(undefined);
    void getCandidateSummary(controller.signal)
      .then((result) => {
        if (!controller.signal.aborted) setData(result);
      })
      .catch((reason: unknown) => {
        if (!controller.signal.aborted) setError(reason);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [refreshKey, active, retry]);

  if (!active) return null;
  return (
    <section
      className="candidate-summary"
      aria-label="후보 요약"
      aria-busy={loading}
      data-testid="candidate-summary-panel"
    >
      {error ? (
        <ErrorBanner
          message={
            error instanceof Error
              ? error.message
              : "후보 요약을 불러오지 못했습니다."
          }
          requestId={
            error instanceof ApiError ? error.body.requestId : undefined
          }
          onRetry={() => setRetry((value) => value + 1)}
        />
      ) : loading ? (
        <p role="status">후보 요약을 불러오는 중입니다.</p>
      ) : data ? (
        <>
          <dl className="candidate-summary-metrics">
            {metrics.map(({ key, label, explanation }) => (
              <div
                className="candidate-summary-metric"
                key={key}
                title={explanation}
                data-testid={`candidate-summary-${key.replace(/[A-Z]/g, (letter) => `-${letter.toLowerCase()}`)}`}
              >
                <dt>{label}</dt>
                <dd
                  className={
                    key === "overdueContact" && data[key] > 0
                      ? "candidate-summary-overdue"
                      : key === "interviewScheduled" && data[key] > 0
                        ? "candidate-summary-interview"
                        : undefined
                  }
                >
                  {data[key].toLocaleString("ko-KR")}
                  <small>명</small>
                </dd>
              </div>
            ))}
          </dl>
          {data.total === 0 && (
            <p className="candidate-summary-empty">등록된 후보가 없습니다.</p>
          )}
          <p className="candidate-summary-help">
            요약은 전체 후보 기준입니다. 다음 달 시작까지 가능은 다음 달 1일
            기준이며, 목록의 ‘다음 달까지’는 다음 달 말일 기준입니다.
          </p>
        </>
      ) : null}
    </section>
  );
}
