import type { AnalysisResult } from "./analysisClient";
export function analysisDestination(
  link: AnalysisResult["screenLinks"][number],
) {
  const one = (kind: string) => {
    const ids = [
      ...new Set(
        link.scope.targets
          .filter((t) => t.targetType === kind)
          .map((t) => t.targetId),
      ),
    ];
    return ids.length === 1 ? ids[0] : undefined;
  };
  const projectId = one("PROJECT"),
    developerId = one("DEVELOPER"),
    partId = one("PART"),
    month = link.scope.toMonth ?? undefined;
  const params = new URLSearchParams();
  if (link.screenId === "UI-04") {
    if (month) params.set("month", month);
    if (projectId) params.set("projectId", projectId);
  }
  return {
    to: `/${link.screenId}${params.size ? `?${params}` : ""}`,
    state: {
      fromAnalysis: true,
      month,
      projectId,
      developerId,
      partId,
      scope: link.scope,
      guidance: link.guidance,
    },
  };
}
