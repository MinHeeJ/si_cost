import { useEffect, useRef, useState, type ReactNode } from "react";
import { ApiError } from "../../shared/api";
import { Dialog } from "../../shared/Dialog";
import { UploadIssuesPanel } from "../uploadsops/UploadIssuesPanel";
import * as api from "./b7Api";
import "./b7.css";
export type ImportNumberRow = {
  rowNumber: number;
  managementNo: string;
  id?: string;
};
export type UploadValidation = Omit<api.B7Validation, "status"> & {
  status?: "CHECKED" | "APPLIED";
  expectedNumbers?: ImportNumberRow[];
  revision?: string | null;
  prospectiveComparison?: unknown;
  uploadedTableTotal?: string | null;
};
export type UploadApplied = {
  summary: api.B7Summary & { tableWarningCount?: number };
  uploadId?: number;
  status?: "CHECKED" | "APPLIED";
  appliedCount?: number;
  appliedAt?: string;
  finalNumbers?: ImportNumberRow[];
  results?: { rowNumber: number; id: string; name: string }[];
  revision?: string | null;
};
export type UploadWorkflow = {
  validate(file: File): Promise<UploadValidation>;
  apply(
    file: File,
    uploadId: number,
    revision?: string | null,
  ): Promise<UploadApplied>;
  result?(uploadId: number): Promise<UploadApplied>;
};

// SCR-UI-15: B7 dialog lives in the retained clients tab, never in another route.
export type ExcelUploadDialogProps = {
  onClose(): void;
  onApplied(): void | Promise<void>;
  onDirtyChange?(dirty: boolean): void;
  workflow?: UploadWorkflow;
  title?: string;
  description?: string;
  screenId?: string;
  onTemplate?(): Promise<void>;
  readOnly?: boolean;
  readOnlyMessage?: string;
  emptyRegistrationMessage?: string;
  renderContext?(pending: boolean, applied: boolean): ReactNode;
  renderValidation?(result: UploadValidation): ReactNode;
  renderApplied?(result: UploadApplied): ReactNode;
  confirmAllowed?: boolean;
  validationFailureMessage?: string;
};
export function ExcelUploadDialog({
  onClose,
  onApplied,
  onDirtyChange,
  workflow,
  title = "엑셀 업로드 결과 · 거래처",
  description = "B7 거래처 등록 · XLSX 10MiB 이하 · 첫 시트 최대 5,000행. 검사 후 같은 파일로 전량 반영합니다.",
  screenId = "UI-15",
  onTemplate,
  readOnly = false,
  readOnlyMessage = "지난 연도는 조회만 할 수 있습니다.",
  emptyRegistrationMessage = "등록된 거래처는 없습니다.",
  renderContext,
  renderValidation,
  renderApplied,
  confirmAllowed = true,
  validationFailureMessage,
}: ExcelUploadDialogProps) {
  const [file, setFile] = useState<File>();
  const [result, setResult] = useState<UploadValidation>();
  const [applied, setApplied] = useState<UploadApplied>();
  const [error, setError] = useState(""),
    [dragging, setDragging] = useState(false);
  const [pending, setPending] = useState(false),
    [confirmClose, setConfirmClose] = useState(false);
  const [refreshFailed, setRefreshFailed] = useState(false);
  const busy = useRef(false);
  useEffect(() => {
    onDirtyChange?.(Boolean(file && !applied));
  }, [file, applied, onDirtyChange]);
  useEffect(() => () => onDirtyChange?.(false), [onDirtyChange]);
  async function run(action: () => Promise<void>) {
    if (busy.current) return;
    busy.current = true;
    setPending(true);
    setError("");
    try {
      await action();
    } catch (e) {
      setError(
        e instanceof ApiError
          ? `${e.message} (요청 ID: ${e.body.requestId})`
          : e instanceof Error
            ? e.message
            : "요청을 처리하지 못했습니다.",
      );
    } finally {
      busy.current = false;
      setPending(false);
    }
  }
  async function check() {
    if (!file || readOnly) return;
    await run(async () => {
      const checked = await (workflow?.validate ?? api.createB7Validation)(
        file,
      );
      setResult(checked);
      setApplied(undefined);
      if (checked.status === "APPLIED" && workflow?.result) {
        setApplied(await workflow.result(checked.uploadId));
        try {
          await onApplied();
        } catch {
          setRefreshFailed(true);
        }
      }
    });
  }
  async function apply() {
    if (
      !file ||
      !result?.canApply ||
      !confirmAllowed ||
      applied ||
      readOnly ||
      result.summary.errorRows > 0 ||
      result.summary.totalRows < 1
    )
      return;
    await run(async () => {
      let saved: UploadApplied;
      try {
        saved = workflow
          ? result.revision == null
            ? await workflow.apply(file, result.uploadId)
            : await workflow.apply(file, result.uploadId, result.revision)
          : await api.saveB7Apply(file, result.uploadId);
      } catch (e) {
        if (e instanceof ApiError && e.status === 422) {
          const details = e.body.details as unknown as Omit<
            api.B7Validation,
            "status"
          >;
          if (details?.uploadId && details.issues && details.summary)
            setResult({
              ...details,
              status: "CHECKED",
              canApply: false,
              ...(workflow ? { expectedNumbers: [] } : {}),
            });
        }
        throw e;
      }
      setApplied(saved);
      setRefreshFailed(false);
      try {
        await onApplied();
      } catch {
        setRefreshFailed(true);
      }
    });
  }

  const summary = applied?.summary ?? result?.summary;
  function choose(selected: File | undefined) {
    setFile(selected);
    setResult(undefined);
    setApplied(undefined);
    setError("");
    if (selected && (!/\.xlsx$/i.test(selected.name) || selected.size > 10485760)) {
      setFile(undefined);
      setError("10MiB 이하의 XLSX 파일을 선택해 주세요.");
    }
  }
  return (
    <>
      <Dialog
        open
        title={title}
        wide
        pending={pending}
        onConfirm={applied ? onClose : result ? apply : check}
        onCancel={() => {
          if (file && !applied) setConfirmClose(true);
          else onClose();
        }}
        confirmLabel={applied ? "닫기" : result ? "전량 반영" : "검사"}
        cancelLabel="취소"
        confirmDisabled={
          !applied &&
          (readOnly ||
            !file ||
            Boolean(
              result &&
                (!confirmAllowed ||
                  !result.canApply ||
                  result.summary.errorRows > 0 ||
                  result.summary.totalRows < 1),
            ))
        }
      >
        <section
          className="b7-upload"
          data-screen-id={screenId}
          data-testid="b7-upload-dialog"
        >
          <div className="b7-upload-head">
            <p className="b7-info">{description}</p>
            {onTemplate && (
              <button
                type="button"
                className="b7-upload-template"
                data-testid="import-template-button"
                disabled={pending}
                onClick={() => void run(onTemplate)}
              >
                양식 내려받기
              </button>
            )}
          </div>
          {renderContext?.(pending, Boolean(applied))}
          {readOnly && <p className="b7-neutral">{readOnlyMessage}</p>}
          <label
            className={`b7-dropzone${dragging ? " b7-dropzone-active" : ""}${
              readOnly || pending || applied ? " b7-dropzone-disabled" : ""
            }${error && !result ? " b7-dropzone-invalid" : ""}`}
            onDragOver={(event) => {
              if (readOnly || pending || applied) return;
              event.preventDefault();
              setDragging(true);
            }}
            onDragLeave={() => setDragging(false)}
            onDrop={(event) => {
              setDragging(false);
              if (readOnly || pending || applied) return;
              event.preventDefault();
              choose(event.dataTransfer.files?.[0]);
            }}
          >
            <span className="b7-dropzone-icon" aria-hidden="true">
              ⬆
            </span>
            <span className="b7-dropzone-text">
              <strong>XLSX 파일을 끌어다 놓거나 선택해 주세요</strong>
              <small>10MiB 이하 · 첫 시트 기준</small>
            </span>
            <span className="b7-dropzone-button">파일 선택</span>
            <input
              className="b7-file-input"
              data-testid="b7-file-input"
              type="file"
              accept=".xlsx"
              aria-label="파일 선택"
              aria-invalid={Boolean(error && !result)}
              disabled={readOnly || pending || Boolean(applied)}
              onChange={(event) => choose(event.target.files?.[0])}
            />
          </label>
          {file && (
            <div className="b7-file-chip" data-testid="b7-file-name">
              <span className="b7-file-chip-name">{file.name}</span>
              <span className="b7-file-chip-size">
                {file.size >= 1048576
                  ? `${(file.size / 1048576).toFixed(1)} MiB`
                  : `${Math.max(1, Math.round(file.size / 1024))} KiB`}
              </span>
              {!applied && !pending && (
                <button
                  type="button"
                  className="b7-file-chip-remove"
                  onClick={() => {
                    setFile(undefined);
                    setResult(undefined);
                    setError("");
                  }}
                >
                  제거
                </button>
              )}
            </div>
          )}
          {result && (
            <p>
              업로드 번호 {result.uploadId} ·{" "}
              {applied ? "반영 완료" : "검사 완료"}
            </p>
          )}
          {summary && (
            <div className="b7-summary" role="status">
              <span>전체 {summary.totalRows}건</span>
              <span className="b7-ok">정상 {summary.successRows}건</span>
              <span className="b7-warn">경고 {summary.warningRows}건</span>
              <span className="b7-bad">오류 {summary.errorRows}건</span>
              <span>파일 경고 {summary.fileWarningCount}건</span>
              {"tableWarningCount" in summary && (
                <span>표 경고 {String(summary.tableWarningCount)}건</span>
              )}
            </div>
          )}
          {error && (
            <p role="alert" className="b7-bad">
              {error} 파일과 업로드 번호는 유지됩니다. 자동 재시도하지 않습니다.
            </p>
          )}
          {result && !applied && (
            <>
              {renderValidation?.(result)}
              <p className={result.canApply ? "b7-info" : "b7-bad"}>
                {result.canApply
                  ? "오류가 없어 반영할 수 있습니다. 경고 내용을 확인해 주세요."
                  : (validationFailureMessage ??
                    (screenId === "UI-02"
                      ? "오류를 수정한 파일을 다시 검사해 주세요. 등록된 개발자는 없습니다."
                      : `오류를 수정한 파일을 다시 검사해 주세요. ${emptyRegistrationMessage}`))}
              </p>
              <div className="b7-actions">
                <button
                  data-testid="b7-recheck-button"
                  type="button"
                  disabled={pending || readOnly}
                  onClick={() => void check()}
                >
                  다시 검사
                </button>
              </div>
              <UploadIssuesPanel
                key={result.uploadId}
                uploadId={result.uploadId}
                initialPage={result.issues}
                disabled={pending}
                screenId={screenId}
              />
            </>
          )}
          {applied && (
            <p role="status" className="b7-ok">
              {applied.appliedCount ??
                applied.results?.length ??
                applied.summary.totalRows}
              건 반영 완료{applied.appliedAt ? ` · ${applied.appliedAt}` : ""}
            </p>
          )}
          {applied && renderApplied?.(applied)}
          {(applied?.finalNumbers ?? result?.expectedNumbers) && (
            <section
              data-testid="import-numbers-panel"
              aria-label={applied ? "반영된 최종번호" : "예상 관리번호"}
            >
              <p>
                {applied
                  ? "반영된 최종번호 · APPLIED"
                  : "새로 붙을 관리번호 · 예상·미예약"}
              </p>
              <table className="sicost-table">
                <thead>
                  <tr>
                    <th>원래 행</th>
                    <th>관리번호</th>
                    {applied && <th>개발자 ID</th>}
                  </tr>
                </thead>
                <tbody>
                  {(applied?.finalNumbers ?? result?.expectedNumbers)?.map(
                    (row) => (
                      <tr
                        key={row.rowNumber}
                        data-testid={`import-number-row-${row.rowNumber}`}
                      >
                        <td>{row.rowNumber}</td>
                        <td>{row.managementNo}</td>
                        {applied && <td>{row.id}</td>}
                      </tr>
                    ),
                  )}
                </tbody>
              </table>
            </section>
          )}
          {Boolean(applied?.results?.length) && (
            <section
              data-testid="import-project-results"
              aria-label="반영된 프로젝트"
            >
              <p>APPLIED 반영 완료 · 최초 반영 결과</p>
              <table className="sicost-table">
                <thead>
                  <tr>
                    <th>원래 행</th>
                    <th>최종 프로젝트ID</th>
                    <th>이름</th>
                  </tr>
                </thead>
                <tbody>
                  {applied?.results?.map((row) => (
                    <tr
                      key={row.rowNumber}
                      data-testid={`import-project-row-${row.rowNumber}`}
                    >
                      <td>{row.rowNumber}</td>
                      <td>{row.id}</td>
                      <td>{row.name}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </section>
          )}
          {result && workflow?.result && (
            <button
              type="button"
              data-testid="import-result-button"
              disabled={pending}
              onClick={() =>
                void run(async () => {
                  const saved = await workflow.result!(result.uploadId);
                  if (saved.status === "APPLIED") {
                    setApplied(saved);
                    try {
                      await onApplied();
                    } catch {
                      setRefreshFailed(true);
                    }
                  } else
                    setError(
                      "검사 완료(CHECKED) 상태입니다. 같은 파일과 업로드 번호로 반영 여부를 확인해 주세요.",
                    );
                })
              }
            >
              결과 재조회
            </button>
          )}
          {refreshFailed && (
            <div role="alert">
              저장됐지만 다시 불러오지 못했습니다.
              <button
                type="button"
                data-testid="b7-refresh-button"
                disabled={pending}
                onClick={() =>
                  void run(async () => {
                    await onApplied();
                    setRefreshFailed(false);
                  })
                }
              >
                다시 불러오기
              </button>
            </div>
          )}
        </section>
      </Dialog>
      <Dialog
        open={confirmClose}
        title="저장하지 않은 내용이 있습니다."
        confirmLabel="나가기"
        cancelLabel="계속 작성"
        onConfirm={onClose}
        onCancel={() => setConfirmClose(false)}
      >
        <p>
          검사 결과는 보존됩니다. 다시 반영하려면 같은 파일을 선택해 주세요.
        </p>
      </Dialog>
    </>
  );
}
