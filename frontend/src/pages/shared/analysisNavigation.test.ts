import { it, expect } from "vitest";
import { analysisDestination } from "./analysisNavigation";
import type { AnalysisScope, AnalysisTarget } from "./analysisClient";
const target = (kind: string, id: string): AnalysisTarget => ({
  targetType: kind,
  targetId: id,
  displayName: id,
  metricKeys: [],
});
function scope(targets: AnalysisTarget[]): AnalysisScope {
  return {
    targets,
    filters: [],
    fromMonth: "2026-10",
    toMonth: "2026-10",
    baseDate: "2026-11-16",
    baseMonth: "2026-10",
  };
}
it("uses the existing project-cost URL query to bind both exact target and month", () => {
  const result = analysisDestination({
    screenId: "UI-04",
    scope: scope([target("PROJECT", "P-0001")]),
    guidance: "같은 프로젝트",
  });
  expect(result.to).toBe("/UI-04?month=2026-10&projectId=P-0001");
  expect(result.state.projectId).toBe("P-0001");
  expect(result.state.month).toBe("2026-10");
});
it("never replaces a multi-person/project result by the arbitrary first entity", () => {
  const result = analysisDestination({
    screenId: "UI-03",
    scope: scope([target("DEVELOPER", "D1"), target("DEVELOPER", "D2")]),
    guidance: "목록",
  });
  expect(result.state.developerId).toBeUndefined();
  expect(result.state.scope.targets).toHaveLength(2);
});
it("passes a unique settlement part and frozen scope without changing them", () => {
  const source = scope([target("PART", "PART-001")]);
  const before = JSON.stringify(source);
  const result = analysisDestination({
    screenId: "UI-19",
    scope: source,
    guidance: "파트 정산",
  });
  expect(result.state.partId).toBe("PART-001");
  expect(result.state.fromAnalysis).toBe(true);
  expect(JSON.stringify(source)).toBe(before);
});
