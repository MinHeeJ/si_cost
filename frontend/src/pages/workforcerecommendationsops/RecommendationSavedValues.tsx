import { ApiError } from "../../shared/api";
import { ErrorBanner, StatusBadge } from "../../shared/Feedback";
import type { RecommendationSnapshot } from "./recommendationBrowseApi";
import { useCodeNames } from "../../shared/codeNames";
import { BusinessName } from "../../shared/BusinessName";
import {
  recommendationBasis,
  recommendationReason,
  recommendationItemLabel,
} from "../candidates/recommendationPresentation";

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
  const name = useCodeNames(
    "RCMD_TYPE",
    "JOB",
    "GRADE",
    "RCMD_ACT",
    "RCMD_EXCL",
  );
  return (
    <section
      className="recommendation-saved"
      data-testid="recommendation-saved-values"
      data-screen-id="SCR-UI-19-B140-HISTORY-VALUE"
    >
      <h3>
        회차 {value.rcmdId ?? "–"} ·{" "}
        {name("RCMD_TYPE", value.recommendationType)}
      </h3>
      <p>
        {value.executedAt ?? "–"} / {value.executedBy ?? "–"}
      </p>
      {value.appliedCondition && (
        <p>
          조건{" "}
          <BusinessName
            kind={
              value.appliedCondition.targetType === "PROJECT"
                ? "project"
                : "opportunity"
            }
            id={value.appliedCondition.targetId}
          />{" "}
          · {name("JOB", value.appliedCondition.jobCode)} ·{" "}
          {value.appliedCondition.startMonth} ·{" "}
          {name("GRADE", value.appliedCondition.requestedGradeCode)}
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
            {name("JOB", candidate.jobCode)} · {candidate.startMonth} ·{" "}
            {name("GRADE", candidate.requestedGradeCode)} · 총점{" "}
            {candidate.totalScore}
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
                    <td>{recommendationItemLabel(item.itemCode)}</td>
                    <td className="sicost-decimal">{item.baseScore}</td>
                    <td className="sicost-decimal">{item.weight}</td>
                    <td className="sicost-decimal">{item.score}</td>
                    <td>{recommendationBasis(item)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <p>{recommendationReason(candidate.reason)}</p>
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
              {candidate.selection.actionCode
                ? name("RCMD_ACT", candidate.selection.actionCode)
                : "미선택"}
            </StatusBadge>{" "}
            / {name("RCMD_EXCL", candidate.selection.exclusionReasonCode)}
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
