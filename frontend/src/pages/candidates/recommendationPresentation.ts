import type { RecommendationCandidate } from "./recommendationActionsApi";
import { formatAmount } from "../../shared/format";

export function recommendationItemLabel(code: string): string {
  return (
    (
      {
        JOB: "직무",
        SKILL: "스킬",
        CUST: "고객",
        GRADE: "등급",
        PERF: "성과",
        COST: "비용",
        AVAIL: "가용일",
        LOC: "근무지",
        GAP: "공백",
        PROB: "수주 확률",
      } as Record<string, string>
    )[code] ?? "평가 항목"
  );
}

/** Explain persisted scoring evidence without exposing its internal enum tokens. */
export function recommendationBasis(
  item: RecommendationCandidate["scoreItems"][number],
): string {
  const value = item.basis;
  if (item.itemCode === "JOB")
    return (
      (
        {
          P: "주 직무 일치",
          S: "보조 직무 일치",
          R: "관련 직무 일치",
          O: "그 밖의 직무",
        } as Record<string, string>
      )[value.replace("직무 분류 ", "")] ?? value
    );
  if (item.itemCode === "CUST")
    return (
      (
        {
          S: "같은 고객 경험 있음",
          I: "같은 업종 경험 있음",
          O: "같은 고객 경험 없음",
        } as Record<string, string>
      )[value.replace("고객 경험 ", "")] ?? value
    );
  if (item.itemCode === "AVAIL")
    return (
      (
        {
          IDLE: "대기 중",
          BEFORE: "예정 시작월 전에 철수",
          LOW: "현재 투입의 연장 가능성 낮음",
          OTHER: "현재 투입 일정 확인 필요",
        } as Record<string, string>
      )[value.replace("가용 상태 ", "")] ?? value
    );
  if (item.itemCode === "LOC")
    return (
      (
        {
          N: "근무지 정보 없음",
          U: "거주지 또는 근무지 확인 필요",
          S: "거주지와 근무지 지역 일치",
          D: "거주지와 근무지 지역 다름",
        } as Record<string, string>
      )[value.replace("근무지 ", "")] ?? value
    );
  if (item.itemCode === "PERF") {
    const counts = /^완료\/연장\/HR \[(\d+), (\d+), (\d+)\]$/.exec(value);
    if (counts)
      return counts[1] === "0"
        ? "끝난 투입 없음"
        : `끝난 투입 ${counts[1]}건 · 연장 ${counts[2]}건 · 인력 사정 조기 철수 ${counts[3]}건`;
  }
  if (item.itemCode === "COST") {
    const cost =
      /^원가월 (\d{4})-?(\d{2}) · 인건비 (\d+) · 단가 (\d+)( · 예정)?$/.exec(
        value,
      );
    if (cost)
      return `${cost[1]}년 ${Number(cost[2])}월 원가 기준 · 인건비 ${formatAmount(cost[3], "원")} 원 · 단가 ${formatAmount(cost[4], "원")} 원${cost[5] ?? ""}`;
  }
  return value;
}
export function recommendationReason(value: string): string {
  return value.replace(/가용 (IDLE|BEFORE|LOW|OTHER)/g, (_, code: string) =>
    recommendationBasis({
      itemCode: "AVAIL",
      basis: `가용 상태 ${code}`,
      baseScore: "",
      score: "",
      weight: "",
    }),
  );
}
