import { useEffect, useRef, useState } from "react";
import { DataTable } from "../../shared/DataTable";
import * as api from "./uploadIssuesApi";
import "./uploadIssues.css";

export type UploadIssuesPanelProps = {
  uploadId: number;
  initialPage: api.UploadIssuesPage;
  disabled?: boolean;
};
// SCR-UI-15: readonly saved diagnostics inside the existing B7 dialog, never a new route.
export function UploadIssuesPanel({
  uploadId,
  initialPage,
  disabled = false,
}: UploadIssuesPanelProps) {
  const [result, setResult] = useState(initialPage);
  const [severity, setSeverity] = useState<api.Severity>("");
  const [loading, setLoading] = useState(false),
    [downloading, setDownloading] = useState(false);
  const [error, setError] = useState(""),
    [downloadError, setDownloadError] = useState("");
  const [notice, setNotice] = useState("");
  const [target, setTarget] = useState({
    severity: "" as api.Severity,
    page: initialPage.page,
    size: initialPage.size,
  });
  const busy = useRef(false),
    mounted = useRef(true);
  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);
  useEffect(() => {
    setResult(initialPage);
    setSeverity("");
    setError("");
    setDownloadError("");
    setNotice("");
    setTarget({ severity: "", page: initialPage.page, size: initialPage.size });
  }, [uploadId, initialPage]);
  async function load(next: typeof target) {
    if (busy.current || disabled) return;
    busy.current = true;
    setLoading(true);
    setError("");
    setNotice("");
    setTarget(next);
    try {
      const page = await api.getUploadIssues(
        uploadId,
        next.severity,
        next.page,
        next.size,
      );
      if (mounted.current) {
        setResult(page);
        setSeverity(next.severity);
      }
    } catch (e) {
      if (mounted.current)
        setError(
          e instanceof Error ? e.message : "검사 문제를 불러오지 못했습니다.",
        );
    } finally {
      busy.current = false;
      if (mounted.current) setLoading(false);
    }
  }
  async function download() {
    if (busy.current || disabled) return;
    busy.current = true;
    setDownloading(true);
    setDownloadError("");
    setNotice("");
    try {
      const file = await api.getUploadIssuesXlsx(uploadId, severity);
      if (mounted.current) {
        api.saveDownload(file);
        setNotice("현재 유형의 전체 문제를 다운로드했습니다.");
      }
    } catch (e) {
      if (mounted.current)
        setDownloadError(
          e instanceof Error ? e.message : "다운로드하지 못했습니다.",
        );
    } finally {
      busy.current = false;
      if (mounted.current) setDownloading(false);
    }
  }
  const pending = disabled || loading || downloading;
  return (
    <section
      className="upload-issues"
      data-testid="upload-issues-panel"
      data-screen-id="UI-15"
      aria-label="검사 문제"
    >
      <div className="upload-issues-toolbar">
        <label>
          유형{" "}
          <select
            data-testid="upload-issues-severity"
            aria-label="문제 유형"
            value={severity}
            disabled={pending}
            onChange={(event) =>
              void load({
                severity: event.target.value as api.Severity,
                page: 1,
                size: result.size,
              })
            }
          >
            <option value="">전체</option>
            <option value="ERR">오류 (ERR)</option>
            <option value="WARN">경고 (WARN)</option>
          </select>
        </label>
        <button
          type="button"
          data-testid="upload-issues-download"
          disabled={pending || Boolean(error)}
          onClick={() => void download()}
        >
          {downloading ? "파일 생성 중…" : "현재 severity 전체 문제 다운로드"}
        </button>
      </div>
      <p className="upload-issues-info">
        현재 선택한 유형의 모든 저장 문제와 파일 경고를 내려받습니다. 현재
        페이지에 한정하지 않습니다. 계좌는 [비공개]로 표시합니다.
      </p>
      {downloadError && (
        <p role="alert">
          {downloadError} 자동 재시도하지 않습니다. 다운로드 버튼으로 다시
          시도해 주세요.
        </p>
      )}
      {notice && <p role="status">{notice}</p>}
      <DataTable
        rows={result.items}
        rowKey={(issue) => issue.sequence}
        loading={loading}
        error={error}
        onRetry={() => void load(target)}
        page={result.page}
        size={result.size}
        total={result.total}
        onPageChange={(page) => {
          if (!pending) void load({ severity, page, size: result.size });
        }}
        onSizeChange={(size) => {
          if (!pending) void load({ severity, page: 1, size });
        }}
        columns={[
          {
            id: "severity",
            label: "유형",
            render: (issue) => (
              <span
                className={
                  issue.severity === "ERR"
                    ? "upload-issue-error"
                    : "upload-issue-warning"
                }
              >
                {issue.severity === "ERR" ? "오류" : "경고"}
              </span>
            ),
          },
          { id: "rowNumber", label: "행", render: (issue) => issue.rowNumber },
          {
            id: "columnName",
            label: "열",
            render: (issue) => issue.columnName,
          },
          {
            id: "inputValue",
            label: "입력값",
            render: (issue) => issue.inputValue,
          },
          { id: "reason", label: "이유", render: (issue) => issue.reason },
        ]}
        empty="검사 문제가 없습니다."
      />
      {!error && !loading && result.total === 0 && severity && (
        <button
          type="button"
          data-testid="upload-issues-reset"
          disabled={pending}
          onClick={() =>
            void load({ severity: "", page: 1, size: result.size })
          }
        >
          조건 초기화
        </button>
      )}
    </section>
  );
}
