import { useEffect, useRef, useState } from "react";
import { ApiError } from "../../shared/api";
import { Dialog } from "../../shared/Dialog";
import {
  ExcelUploadDialog,
  type UploadValidation,
  type UploadWorkflow,
} from "../b7/ExcelUploadDialog";
import * as api from "./financialImportApi";

function adapt(result: api.FinancialUploadResult) {
  return {
    ...result,
    revision: result.expectedRevision,
    appliedAt: result.appliedAt ?? undefined,
    appliedCount: result.items.length,
  };
}
export type FinancialUploadDialogProps = {
  month: string;
  readOnly?: boolean;
  readOnlyMessage?: string;
  onClose(): void;
  onApplied(): void | Promise<void>;
  onDirtyChange?(dirty: boolean): void;
};
export function FinancialUploadDialog(props: FinancialUploadDialogProps) {
  return <FinancialUploadSession key={props.month} {...props} />;
}
export function FinancialUploadActions({
  month,
  readOnly,
  onApplied,
  beforeOpen,
}: {
  month: string;
  readOnly?: boolean;
  onApplied(): void | Promise<void>;
  beforeOpen?(): Promise<boolean>;
}) {
  const [open, setOpen] = useState(false);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const busy = useRef(false);
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
  return (
    <section data-testid="financial-upload-actions" className="b7-actions">
      <button
        type="button"
        data-testid="financial-template-button"
        disabled={pending}
        onClick={() =>
          void run(async () => api.saveDownload(await api.getB2Template(month)))
        }
      >
        양식 내려받기
      </button>
      <button
        type="button"
        data-testid="financial-upload-button"
        disabled={pending || readOnly}
        onClick={() =>
          void run(async () => {
            if (!beforeOpen || (await beforeOpen())) setOpen(true);
          })
        }
      >
        엑셀 업로드
      </button>
      {error && <p role="alert">{error}</p>}
      {open && (
        <FinancialUploadDialog
          month={month}
          readOnly={readOnly}
          onClose={() => setOpen(false)}
          onApplied={onApplied}
        />
      )}
    </section>
  );
}
function FinancialUploadSession({
  month,
  readOnly = false,
  readOnlyMessage,
  onClose,
  onApplied,
  onDirtyChange,
}: FinancialUploadDialogProps) {
  const [checked, setChecked] = useState<api.FinancialUploadResult>();
  const [confirmation, setConfirmation] = useState(false);
  const resolve = useRef<(value: boolean) => void>();
  useEffect(() => () => resolve.current?.(false), []);
  const workflow: UploadWorkflow<string> = {
    validate: async (file) => {
      setConfirmation(false);
      const value = await api.createB2Validation(file, month);
      setChecked(value);
      return adapt(value);
    },
    apply: async (file, uploadId, revision) => {
      if (!revision) throw new Error("다시 검사해 주세요.");
      const value = await api.saveB2Apply(file, month, uploadId, revision);
      setChecked(value);
      return adapt(value);
    },
    result: async (uploadId) => {
      const value = await api.getFinancialUploadResult(uploadId);
      setChecked(value);
      return adapt(value);
    },
  };
  const finish = (value: boolean) => {
    setConfirmation(false);
    resolve.current?.(value);
    resolve.current = undefined;
  };
  const extract = (error: ApiError): UploadValidation<string> | undefined => {
    const details = error.body.details as
      | { financial?: { upload?: api.FinancialUploadResult } }
      | undefined;
    if (!details?.financial?.upload) return undefined;
    setChecked(details.financial.upload);
    return adapt(details.financial.upload);
  };
  if (
    typeof matchMedia === "function" &&
    matchMedia("(max-width: 767px)").matches
  )
    return (
      <section data-testid="financial-upload-pc">
        <p>엑셀 업로드는 PC에서 이용해 주세요.</p>
        <button
          type="button"
          data-testid="financial-upload-pc-close"
          onClick={onClose}
        >
          닫기
        </button>
      </section>
    );
  return (
    <>
      <ExcelUploadDialog<string>
        onClose={onClose}
        onApplied={onApplied}
        onDirtyChange={onDirtyChange}
        workflow={workflow}
        title="엑셀 업로드 · 재무 항목"
        screenId="UI-10"
        applyLabel="교체 내용 확인"
        description="B.2 재무 항목 · 첫 시트: 구분* / 판관비 항목 / 세부 항목* / 금액* / 부가세 / 입출금일 / 매달 반복 / 비고. 실제 OOXML XLSX 10MiB 이하 · 1~5,000행."
        readOnly={readOnly}
        readOnlyMessage={
          readOnlyMessage ??
          "마감·지난 연도·운영 시작 이전·미래 월은 업로드할 수 없습니다."
        }
        emptyRegistrationMessage="재무 항목은 변경되지 않았습니다."
        requireRecheckOnConflict
        extractValidationError={extract}
        onTemplate={async () =>
          api.saveDownload(await api.getB2Template(month))
        }
        renderContext={() => (
          <p data-testid="financial-upload-month">
            대상월 {month} · 검사만으로는 업무에 반영되지 않습니다.
          </p>
        )}
        beforeApply={() =>
          new Promise<boolean>((done) => {
            resolve.current = done;
            setConfirmation(true);
          })
        }
        renderValidation={() =>
          checked && (
            <section data-testid="financial-upload-replacement">
              <p>
                교체 예정 {checked.summary.replaceCount}줄 · 직접입력 보존{" "}
                {checked.summary.preserveCount}줄
              </p>
              <p>
                충돌이 발생하면 새 검사와 확인이 필요합니다. 응답이 유실되면
                같은 ID 결과를 먼저 조회해 주세요.
              </p>
            </section>
          )
        }
        renderApplied={() =>
          checked && (
            <section data-testid="financial-upload-receipt">
              <p>APPLIED · {checked.appliedAt?.replace("T", " ")}</p>
              <table className="sicost-table">
                <thead>
                  <tr>
                    <th>엑셀 행번호</th>
                    <th>최종 행번호</th>
                  </tr>
                </thead>
                <tbody>
                  {checked.items.map((item) => (
                    <tr
                      key={item.rowNumber}
                      data-testid={`financial-upload-result-${item.rowNumber}`}
                    >
                      <td>{item.rowNumber}</td>
                      <td>{item.itemSn}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </section>
          )
        }
      />
      <Dialog
        open={confirmation}
        title="재무 항목 엑셀 교체 확인"
        confirmLabel="교체 반영"
        onConfirm={() => finish(true)}
        onCancel={() => finish(false)}
      >
        <section data-testid="financial-upload-confirmation">
          <p>
            엑셀로 올린 {month}월 항목 {checked?.summary.replaceCount}줄을
            지우고 새 엑셀 내용으로 바꿉니다.
          </p>
          <p>직접 입력한 {checked?.summary.preserveCount}줄은 그대로 둡니다.</p>
          <p>
            자동 채움·지난달 복사 포함. 다른 월·카드·이체·대여금·세금·조정은
            교체하지 않습니다.
          </p>
        </section>
      </Dialog>
    </>
  );
}
