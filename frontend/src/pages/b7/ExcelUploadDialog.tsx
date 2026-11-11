import { useEffect, useRef, useState } from "react";
import { ApiError } from "../../shared/api";
import { Dialog } from "../../shared/Dialog";
import { UploadIssuesPanel } from "../uploadsops/UploadIssuesPanel";
import * as api from "./b7Api";
import "./b7.css";

// SCR-UI-15: B7 dialog lives in the retained clients tab, never in another route.
export type ExcelUploadDialogProps = {
  onClose(): void;
  onApplied(): void | Promise<void>;
  onDirtyChange?(dirty: boolean): void;
};
export function ExcelUploadDialog({
  onClose,
  onApplied,
  onDirtyChange,
}: ExcelUploadDialogProps) {
  const [file, setFile] = useState<File>();
  const [result, setResult] = useState<api.B7Validation>();
  const [applied, setApplied] = useState<api.B7Applied>();
  const [error, setError] = useState("");
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
      setError(e instanceof Error ? e.message : "요청을 처리하지 못했습니다.");
    } finally {
      busy.current = false;
      setPending(false);
    }
  }
  async function check() {
    if (!file) return;
    await run(async () => {
      setResult(await api.createB7Validation(file));
      setApplied(undefined);
    });
  }
  async function apply() {
    if (!file || !result?.canApply || applied) return;
    await run(async () => {
      let saved: api.B7Applied;
      try {
        saved = await api.saveB7Apply(file, result.uploadId);
      } catch (e) {
        if (e instanceof ApiError && e.status === 422) {
          const details = e.body.details as unknown as Omit<
            api.B7Validation,
            "status"
          >;
          if (details?.uploadId && details.issues && details.summary)
            setResult({ ...details, status: "CHECKED", canApply: false });
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
  return (
    <>
      <Dialog
        open
        title="엑셀 업로드 결과 · 거래처"
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
          !applied && (!file || Boolean(result && !result.canApply))
        }
      >
        <section className="b7-upload" data-testid="b7-upload-dialog">
          <p className="b7-info">
            B7 거래처 등록 · XLSX 10MiB 이하 · 첫 시트 최대 5,000행. 검사 후
            같은 파일로 전량 반영합니다.
          </p>
          <label className="sicost-field">
            파일 선택
            <input
              data-testid="b7-file-input"
              type="file"
              accept=".xlsx"
              disabled={pending || Boolean(applied)}
              onChange={(event) => {
                const selected = event.target.files?.[0];
                setFile(selected);
                setResult(undefined);
                setApplied(undefined);
                setError("");
                if (
                  selected &&
                  (!/\.xlsx$/i.test(selected.name) || selected.size > 10485760)
                ) {
                  setFile(undefined);
                  setError("10MiB 이하의 XLSX 파일을 선택해 주세요.");
                }
              }}
            />
          </label>
          {file && <p>{file.name}</p>}
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
            </div>
          )}
          {error && (
            <p role="alert" className="b7-bad">
              {error} 파일과 업로드 번호는 유지됩니다. 자동 재시도하지 않습니다.
            </p>
          )}
          {result && !applied && (
            <>
              <p className={result.canApply ? "b7-info" : "b7-bad"}>
                {result.canApply
                  ? "오류가 없어 반영할 수 있습니다. 경고 내용을 확인해 주세요."
                  : "오류를 수정한 파일을 다시 검사해 주세요. 등록된 거래처는 없습니다."}
              </p>
              <div className="b7-actions">
                <button
                  data-testid="b7-recheck-button"
                  type="button"
                  disabled={pending}
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
              />
            </>
          )}
          {applied && (
            <p role="status" className="b7-ok">
              {applied.appliedCount}건 반영 완료 · {applied.appliedAt}
            </p>
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
