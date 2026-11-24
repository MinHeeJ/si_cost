export function noWeeklyMemo(days: number | string): string {
  return `주간 메모 없음(계약 시작 ${days}일)`;
}

export function progressReasonText(kind: string, value: string): string {
  return kind === "주간 메모 없음" ? noWeeklyMemo(value) : `${kind} ${value}`;
}
