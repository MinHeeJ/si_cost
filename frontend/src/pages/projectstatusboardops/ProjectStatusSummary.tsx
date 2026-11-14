import {
  AnalysisFailure,
  AnalysisSummary,
  useAnalysisLoad,
} from "../apiops/ProjectAnalysisParts";
import type { AnalysisQuery } from "../apiops/projectAnalysisApi";
import { getProjectStatusBoardSummary } from "./projectStatusApi";

export function ProjectStatusSummary({ query }: { query: AnalysisQuery }) {
  const { light, contractType, partId } = query;
  const filters = { light, contractType, partId };
  const result = useAnalysisLoad(
    (signal) => getProjectStatusBoardSummary(filters, signal),
    JSON.stringify(filters),
  );
  return (
    <section data-testid="project-status-summary" aria-busy={result.loading}>
      <AnalysisFailure error={result.error} retry={result.retry} />
      {result.loading && <p role="status">현황 요약을 조회하고 있습니다.</p>}
      {!result.error && result.data && (
        <>
          <p>기준일 {result.data.baseDate} · Asia/Seoul</p>
          <AnalysisSummary
            fields={[
              ["projectCount", "진행 프로젝트"],
              ["dangerCount", "위험"],
              ["openIssueCount", "열린 이슈"],
              ["overdueIssueCount", "기한 지남"],
              ["staleProjectCount", "14일 초과 업데이트 없음"],
            ]}
            summary={result.data}
          />
          <p>
            정상 {result.data.normalCount} · 주의 {result.data.warningCount} ·
            위험 {result.data.dangerCount} · 열린 HIGH{" "}
            {result.data.openHighIssueCount} · 원가 미확인{" "}
            {result.data.unknownCostProjectCount}
          </p>
        </>
      )}
    </section>
  );
}
