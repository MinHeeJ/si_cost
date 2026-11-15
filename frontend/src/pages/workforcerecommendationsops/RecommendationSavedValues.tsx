import { ApiError } from "../../shared/api";
import { ErrorBanner, StatusBadge } from "../../shared/Feedback";
import type { RecommendationSnapshot } from "./recommendationBrowseApi";

export function RecommendationBrowseError({
  error,
  retry,
}: {
  error: unknown;
  retry?: () => void;
}) {
  if (!error) return null;
  return (
    <ErrorBanner
      message={error instanceof Error ? error.message : "조회하지 못했습니다."}
      requestId={error instanceof ApiError ? error.body.requestId : undefined}
      onRetry={retry}
    />
  );
}

export function RecommendationSavedValues({
  value,
}: {
  value: RecommendationSnapshot;
}) {
  return (
    <section
      className="recommendation-saved"
      data-testid="recommendation-saved-values"
      data-screen-id="SCR-UI-19-B140-HISTORY-VALUE"
    >
      <h3>
        회차 {value.rcmdId ?? "–"} · {value.recommendationType}
      </h3>
      <p>
        {value.executedAt ?? "–"} / {value.executedBy ?? "–"}
      </p>
      {value.appliedCondition && (
        <p>
          조건 {value.appliedCondition.targetId} /{" "}
          {value.appliedCondition.jobCode} / {value.appliedCondition.startMonth}{" "}
          / {value.appliedCondition.requestedGradeCode}
        </p>
      )}
      {value.sourceAssignmentId && <p>원천 배정 {value.sourceAssignmentId}</p>}
      {value.candidates.map((candidate) => (
        <article
          className="recommendation-card"
          key={candidate.rank}
          data-testid={`saved-candidate-${candidate.rank}`}
        >
          <h3>
            당시 순위 {candidate.rank} · {candidate.developer.name} /{" "}
            {candidate.developer.managementNumber}
          </h3>
          <p>
            {candidate.jobCode} / {candidate.startMonth} /{" "}
            {candidate.requestedGradeCode} · 총점 {candidate.totalScore}
          </p>
          <div className="sicost-table-viewport">
            <table className="sicost-table">
              <thead>
                <tr>
                  <th>항목</th>
                  <th>기준 점수</th>
                  <th>당시 배점</th>
                  <th>득점</th>
                  <th>근거</th>
                </tr>
              </thead>
              <tbody>
                {candidate.scoreItems.map((item) => (
                  <tr
                    key={item.itemCode}
                    data-testid={`saved-score-${candidate.rank}-${item.itemCode.toLowerCase()}`}
                  >
                    <td>{item.itemCode}</td>
                    <td className="sicost-decimal">{item.baseScore}</td>
                    <td className="sicost-decimal">{item.weight}</td>
                    <td className="sicost-decimal">{item.score}</td>
                    <td>{item.basis}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <p>{candidate.reason}</p>
          <p>
            최종 선택{" "}
            <StatusBadge
              tone={
                candidate.selection.actionCode === "EXCL"
                  ? "bad"
                  : candidate.selection.actionCode
                    ? "ok"
                    : "neutral"
              }
            >
              {candidate.selection.actionCode ?? "미선택"}
            </StatusBadge>{" "}
            / {candidate.selection.exclusionReasonCode ?? "–"}
          </p>
          <p>
            처리자 {candidate.selection.actedBy ?? "–"} /{" "}
            {candidate.selection.actedAt ?? "–"}
          </p>
          <p>
            연결 {candidate.selection.assignmentId ?? "–"} /{" "}
            {candidate.selection.tentativeAssignmentId ?? "–"} /{" "}
            {candidate.selection.finalOpportunityId ?? "–"} /{" "}
            {candidate.selection.finalNeedSn ?? "–"}
          </p>
        </article>
      ))}
      {value.candidates.length === 0 && (
        <p className="recommendation-empty">맞는 인력이 없습니다.</p>
      )}
      {value.message && <p role="status">{value.message}</p>}
    </section>
  );
}
