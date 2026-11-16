import { request, ApiError, type ApiErrorBody } from "../../shared/api";
export type AnalysisContext = {
  screenId?: string;
  baseMonth?: string;
  projectId?: string;
  developerId?: string;
};
export type AnalysisTarget = {
  targetType: string;
  targetId: string;
  displayName: string;
  metricKeys: string[];
};
export type AnalysisScope = {
  targets: AnalysisTarget[];
  filters: { key: string; operator: string; values: string[] }[];
  fromMonth: string | null;
  toMonth: string | null;
  baseDate: string;
  baseMonth: string;
};
export type AnalysisCell = {
  columnKey: string;
  value: string | null;
  reason: string | null;
};
export type AnalysisRow = {
  rowKey: string;
  target: AnalysisTarget | null;
  cells: AnalysisCell[];
  reason: string | null;
};
export type AnalysisTable = {
  tableId: string;
  title: string;
  periodLabel: string;
  metricName: string;
  unit: string;
  sourceState: string;
  scenario: "LOW" | "BASE" | "HIGH";
  columns: { key: string; label: string; valueType: string; unit: string }[];
  rows: AnalysisRow[];
  totalRows: number;
  totals: AnalysisCell[];
  sort: { columnKey: string; direction: "ASC" | "DESC" }[];
  displayPageSize: 50;
  emptyReason: string | null;
};
export type AnalysisResult = {
  schemaVersion: "1";
  conclusion: string;
  tables: AnalysisTable[];
  assumptions: string[];
  unknownReasons: string[];
  usedData: {
    queryName: string;
    scope: AnalysisScope;
    sourceState: string;
    calculationBasis: string;
    referenceIds: string[];
  }[];
  nextActions: string[];
  context: AnalysisContext;
  appliedScope: AnalysisScope;
  status: "OK" | "NO_ANS" | "ERR";
  reason: string | null;
  screenLinks: { screenId: string; scope: AnalysisScope; guidance: string }[];
  interpreter: { mode: string; modelName: string | null; notice: string };
  dataAsOf: string;
};
export type AnalysisSummary = {
  qnaId: string;
  question: string;
  questionAt: string;
  status: "OK" | "NO_ANS" | "ERR";
  provisional: boolean;
};
export type AnalysisDetail = AnalysisSummary & {
  result: AnalysisResult;
  baseMonth: string | null;
  snapshotNotice: string;
};
export type AnalysisFinal = {
  persisted: boolean;
  qnaId: string | null;
  status: AnalysisSummary["status"];
  result: AnalysisResult | null;
  serverErrorId?: string;
  message?: string;
};
export type AnalysisProgress = {
  phase: "FINDING_DATA" | "CALCULATING" | "SUMMARIZING";
  message: string;
  acceptedAt: string;
};
export type AnalysisPage = {
  items: AnalysisSummary[];
  nextCursor: string | null;
  hasMore: boolean;
};
export const listQuestions = (cursor?: string) =>
  request<AnalysisPage>(
    `/api/analysis/questions${cursor ? `?cursor=${encodeURIComponent(cursor)}` : ""}`,
  );
export const getQuestion = (id: string) =>
  request<AnalysisDetail>(`/api/analysis/questions/${encodeURIComponent(id)}`);
export async function sendQuestion(
  question: string,
  context: AnalysisContext,
  onProgress: (p: AnalysisProgress) => void,
): Promise<AnalysisFinal> {
  const response = await fetch("/api/analysis/questions", {
    method: "POST",
    redirect: "error",
    headers: {
      "Content-Type": "application/json",
      Accept: "text/event-stream",
    },
    body: JSON.stringify({ question, context }),
  });
  if (!response.ok)
    throw new ApiError(
      response.status,
      (await response.json()) as ApiErrorBody,
    );
  if (!response.body)
    throw new Error("응답 연결이 없습니다. 질문 기록을 확인해 주세요.");
  const reader = response.body.getReader(),
    decoder = new TextDecoder();
  let buffer = "",
    final: AnalysisFinal | undefined;
  const consume = (block: string) => {
    let event = "",
      data = "";
    for (const line of block.split("\n")) {
      if (line.startsWith("event:")) event = line.slice(6).trim();
      if (line.startsWith("data:")) data += line.slice(5).trim();
    }
    if (!data) return;
    const envelope = JSON.parse(data);
    if (envelope.success !== true)
      throw new Error("처리 응답을 확인해 주세요.");
    if (event === "progress") onProgress(envelope.data);
    else if (event === "final") final = envelope.data;
  };
  try {
    for (;;) {
      const { done, value } = await reader.read();
      buffer = (buffer + decoder.decode(value, { stream: !done })).replace(
        /\r\n/g,
        "\n",
      );
      let boundary;
      while ((boundary = buffer.indexOf("\n\n")) >= 0) {
        consume(buffer.slice(0, boundary));
        buffer = buffer.slice(boundary + 2);
      }
      if (done) break;
    }
    if (buffer.trim()) consume(buffer);
  } finally {
    reader.releaseLock();
  }
  if (!final)
    throw new Error(
      "연결이 끊겨 완료 여부를 확인할 수 없습니다. 자동 재전송하지 않습니다. 질문 기록을 확인해 주세요.",
    );
  return final;
}
export async function downloadQuestion(id: string) {
  const r = await fetch(
    `/api/analysis/questions/${encodeURIComponent(id)}/excel`,
    { redirect: "error" },
  );
  if (!r.ok) throw new ApiError(r.status, await r.json());
  const url = URL.createObjectURL(await r.blob());
  const a = document.createElement("a");
  a.href = url;
  a.download = `AI분석_${id}.xlsx`;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 0);
}
