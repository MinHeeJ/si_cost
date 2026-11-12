import { useEffect, useRef, useState } from "react";
import { DataTable } from "../../shared/DataTable";
import { ErrorBanner, StatusBadge } from "../../shared/Feedback";
import { ApiError } from "../../shared/api";
import { getUploadResult, type ProjectUploadResult } from "../b9/b9Api";
import "../b7/b7.css";

export type UploadResultPanelProps = {
  uploadId: number;
  initialResult?: ProjectUploadResult;
  disabled?: boolean;
};

export function UploadResultPanel(props: UploadResultPanelProps) {
  return <SavedUploadResult key={props.uploadId} {...props} />;
}

function SavedUploadResult({
  uploadId,
  initialResult,
  disabled = false,
}: UploadResultPanelProps) {
  const [result, setResult] = useState(
    initialResult?.uploadId === uploadId ? initialResult : undefined,
  );
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<unknown>();
  const busy = useRef(false),
    mounted = useRef(true);
  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);
  async function load() {
    if (busy.current || disabled) return;
    busy.current = true;
    setPending(true);
    setError(undefined);
    try {
      const saved = await getUploadResult(uploadId);
      if (mounted.current) setResult(saved);
    } catch (e) {
      if (mounted.current) setError(e);
    } finally {
      busy.current = false;
      if (mounted.current) setPending(false);
    }
  }
  const summary = result?.summary;
  return (
    <section
      className="b7-upload"
      data-testid="upload-result-panel"
      aria-label="업로드 최초 반영 결과"
      aria-busy={pending}
    >
      <div className="b7-actions">
        <h3>
          {result?.formType ?? "업로드"} 결과 {uploadId}
        </h3>
        <button
          type="button"
          data-testid="upload-result-refresh-button"
          disabled={pending || disabled}
          onClick={() => void load()}
        >
          {pending ? "불러오는 중…" : "결과 재조회"}
        </button>
      </div>
      <p className="b7-info">
        원본 파일 없이 저장된 최초 반영 결과를 확인합니다. 자동 재시도하거나
        다시 반영하지 않습니다.
      </p>
      {pending && <p role="status">업로드 결과를 불러오는 중입니다.</p>}
      {error !== undefined && (
        <ErrorBanner
          message={
            error instanceof Error
              ? error.message
              : "업로드 결과를 불러오지 못했습니다."
          }
          requestId={
            error instanceof ApiError ? error.body.requestId : undefined
          }
        />
      )}
      {!result && !pending && !error && (
        <p className="sicost-empty">
          결과 재조회를 눌러 반영 여부를 확인해 주세요.
        </p>
      )}
      {result && (
        <>
          <p role="status">
            <StatusBadge tone={result.status === "APPLIED" ? "ok" : "info"}>
              {result.status === "APPLIED"
                ? "APPLIED 반영 완료"
                : "CHECKED 검사 완료"}
            </StatusBadge>
          </p>
          {summary && (
            <div className="b7-summary" aria-label="업로드 요약">
              <span>전체 {summary.totalRows}건</span>
              <span className="b7-ok">성공 {summary.successRows}건</span>
              <span className="b7-warn">경고 {summary.warningRows}건</span>
              <span className="b7-bad">오류 {summary.errorRows}건</span>
              <span>파일 경고 {summary.fileWarningCount}건</span>
              <span>표 경고 {summary.tableWarningCount}건</span>
            </div>
          )}
          {result.status === "CHECKED" && (
            <p className="b7-info">
              검사 완료 상태입니다. 아직 반영되지 않았습니다. 같은 파일과 업로드
              번호로 기존 반영 화면에서 진행해 주세요.
            </p>
          )}
          {result.formType === "B6" && (
            <dl>
              <dt>대상 프로젝트</dt>
              <dd>{result.projectId ?? "–"}</dd>
              <dt>당시 단계</dt>
              <dd>
                {result.step === "EST"
                  ? "견적"
                  : result.step === "CTRT"
                    ? "계약"
                    : result.step === "PLAN"
                      ? "계획"
                      : "–"}
              </dd>
            </dl>
          )}
          {result.formType === "B9" && (
            <DataTable
              rows={result.results}
              rowKey={(row) => row.rowNumber}
              columns={[
                {
                  id: "rowNumber",
                  label: "원래 행",
                  render: (row) => row.rowNumber,
                },
                { id: "id", label: "최종 프로젝트ID", render: (row) => row.id },
                { id: "name", label: "이름", render: (row) => row.name },
              ]}
              empty="반영된 프로젝트가 없습니다."
            />
          )}
        </>
      )}
    </section>
  );
}
