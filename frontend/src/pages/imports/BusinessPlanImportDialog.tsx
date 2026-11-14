import { useEffect, useRef, useState } from "react";
import { ApiError } from "../../shared/api";
import { Dialog } from "../../shared/Dialog";
import { formatAmount } from "../../shared/format";
import {
  ExcelUploadDialog,
  type UploadValidation,
  type UploadWorkflow,
} from "../b7/ExcelUploadDialog";
import * as api from "./businessPlanImportsApi";
import "./imports.css";

export type BusinessPlanImportDialogProps = {
  year: number;
  currentYear: number;
  onClose(): void;
  onApplied(): void | Promise<void>;
  onDirtyChange?(dirty: boolean): void;
};
function adapt(value: api.BusinessPlanValidation) {
  return { ...value, revision: value.expectedRevision };
}
export function BusinessPlanImportDialog(props: BusinessPlanImportDialogProps) {
  return (
    <BusinessPlanImportSession
      key={`${props.year}:${props.currentYear}`}
      {...props}
    />
  );
}
function BusinessPlanImportSession({
  year,
  currentYear,
  onClose,
  onApplied,
  onDirtyChange,
}: BusinessPlanImportDialogProps) {
  const [checked, setChecked] = useState<api.BusinessPlanValidation>();
  const [receipt, setReceipt] = useState<
    api.BusinessPlanApplied | api.BusinessPlanUploadResult
  >();
  const [confirm, setConfirm] = useState(false),
    [uncertain, setUncertain] = useState(false);
  const selectedMode = useRef<api.ImportMode>();
  const resolve = useRef<(value: boolean) => void>();
  useEffect(() => () => resolve.current?.(false), []);
  const finish = (mode?: api.ImportMode) => {
    selectedMode.current = mode;
    setConfirm(false);
    resolve.current?.(Boolean(mode));
    resolve.current = undefined;
  };
  const workflow: UploadWorkflow<string> = {
    validate: async (file) => {
      selectedMode.current = undefined;
      setUncertain(false);
      setReceipt(undefined);
      const result = await api.createBusinessPlanImportValidation(file, year);
      setChecked(result);
      return adapt(result);
    },
    apply: async (file, uploadId, revision) => {
      if (!revision || !selectedMode.current || uncertain)
        throw new Error("다시 검사해 주세요.");
      try {
        const result = await api.saveBusinessPlanImport(
          file,
          year,
          uploadId,
          revision,
          selectedMode.current,
        );
        setReceipt(result);
        return { ...result, appliedCount: result.changedMonths.length };
      } catch (error) {
        if (!(error instanceof ApiError)) setUncertain(true);
        throw error;
      }
    },
    result: async (uploadId) => {
      const result = await api.getBusinessPlanUploadResult(uploadId);
      if (result.status === "APPLIED") setReceipt(result);
      return {
        ...result,
        appliedAt: result.appliedAt ?? undefined,
        appliedCount: result.changedMonths.length,
      };
    },
  };
  const extract = (error: ApiError): UploadValidation<string> | undefined => {
    const latest = (
      error.body.details as
        | { financial?: { upload?: api.BusinessPlanValidation } }
        | undefined
    )?.financial?.upload;
    if (!latest) return undefined;
    setChecked(latest);
    return adapt(latest);
  };
  if (
    typeof matchMedia === "function" &&
    matchMedia("(max-width: 767px)").matches
  )
    return (
      <section data-testid="business-plan-import-pc">
        <p>엑셀 업로드는 PC에서 이용해 주세요.</p>
        <button data-testid="business-plan-import-pc-close" onClick={onClose}>
          닫기
        </button>
      </section>
    );
  return (
    <>
      <ExcelUploadDialog<string>
        onClose={onClose}
        onApplied={async () => {
          window.dispatchEvent(
            new CustomEvent(api.BUSINESS_PLAN_IMPORT_COMMITTED, {
              detail: { year },
            }),
          );
          await onApplied();
        }}
        onDirtyChange={onDirtyChange}
        workflow={workflow}
        screenId="UI-18"
        title="목표 업로드"
        applyLabel="반영"
        readOnly={year !== currentYear}
        readOnlyMessage="지난 해는 읽기 전용입니다. 올해 연간 목표만 업로드할 수 있습니다."
        description="연월 / 매출 목표 / 영업이익 목표 · 올해 1~12월 각 1행 · XLSX 10MiB 이하 · 빈 목표는 0/경고 · 올해 미래월도 허용합니다."
        emptyRegistrationMessage="사업계획 목표는 변경되지 않았습니다."
        requireRecheckOnConflict
        confirmAllowed={!uncertain}
        extractValidationError={extract}
        onTemplate={async () =>
          api.saveDownload(await api.getBusinessPlanImportTemplate(year))
        }
        beforeApply={() => {
          if (!checked?.requiresClosedMonthConfirmation) {
            selectedMode.current = "ALL";
            return Promise.resolve(true);
          }
          return new Promise<boolean>((done) => {
            resolve.current = done;
            setConfirm(true);
          });
        }}
        renderContext={() => (
          <p data-testid="business-plan-import-year">
            대상연도 {year} · 검사만으로는 목표를 변경하지 않습니다.
          </p>
        )}
        renderValidation={() => (
          <section
            data-screen-id="SCR-UI-18"
            data-testid="business-plan-import-validation"
          >
            {checked?.warnings.map((warning) => (
              <p key={warning} className="b7-warn">
                {warning}
              </p>
            ))}
            {uncertain && (
              <p role="alert">
                통신 실패로 반영 여부를 확인할 수 없습니다. 결과 재조회로 먼저
                확인하세요. 자동 재반영하지 않습니다.
              </p>
            )}
            {checked && <TargetChanges changes={checked.changes} />}
          </section>
        )}
        renderApplied={() => receipt && <ImportResult receipt={receipt} />}
      />
      <Dialog
        open={confirm}
        title="목표 업로드 · 마감한 달 목표가 바뀝니다"
        wide
        confirmLabel="전체 변경"
        onConfirm={() => finish("ALL")}
        onCancel={() => finish()}
      >
        <section data-testid="business-plan-import-confirmation">
          <p>
            변경월의 기존/신규 목표를 확인하고 반영 방법을 선택해 주세요.
            취소하면 검사기록만 유지됩니다.
          </p>
          <TargetChanges
            changes={
              checked?.changes.filter(
                (change) => change.closed && change.changed,
              ) ?? []
            }
          />
          <button
            type="button"
            data-testid="business-plan-import-keep-closed"
            onClick={() => finish("KEEP_CLOSED")}
          >
            마감한 달은 두기
          </button>
        </section>
      </Dialog>
    </>
  );
}
function TargetChanges({ changes }: { changes: api.ImportChange[] }) {
  return (
    <div className="business-plan-import-scroll">
      <table className="sicost-table">
        <thead>
          <tr>
            <th>연월</th>
            <th>마감</th>
            <th>기존 매출 목표</th>
            <th>신규 매출 목표</th>
            <th>기존 영업이익 목표</th>
            <th>신규 영업이익 목표</th>
            <th>변경</th>
          </tr>
        </thead>
        <tbody>
          {changes.map((change) => (
            <tr
              key={change.month}
              data-testid={`business-plan-import-month-${change.month}`}
            >
              <td>{change.month}</td>
              <td>{change.closed ? "마감" : "미마감"}</td>
              <td>{formatAmount(change.before?.salesTargetAmt, "원")}</td>
              <td>{formatAmount(change.after.salesTargetAmt, "원")}</td>
              <td>
                {formatAmount(change.before?.operatingProfitTargetAmt, "원")}
              </td>
              <td>
                {formatAmount(change.after.operatingProfitTargetAmt, "원")}
              </td>
              <td>{change.changed ? "변경" : "동일"}</td>
            </tr>
          ))}
        </tbody>
      </table>
      {changes.length === 0 && (
        <p className="b7-neutral">표시할 변경월이 없습니다.</p>
      )}
    </div>
  );
}
function ImportResult({
  receipt,
}: {
  receipt: api.BusinessPlanApplied | api.BusinessPlanUploadResult;
}) {
  return (
    <section data-testid="business-plan-import-result">
      {"message" in receipt && <p>{receipt.message}</p>}
      <p>반영 월: {receipt.changedMonths.join(", ") || "없음"}</p>
      {"unchangedMonths" in receipt && receipt.unchangedMonths && (
        <p>동일 월: {receipt.unchangedMonths.join(", ") || "없음"}</p>
      )}
      {"keptMonths" in receipt && receipt.keptMonths && (
        <p>유지 월: {receipt.keptMonths.join(", ") || "없음"}</p>
      )}
      {"warnings" in receipt &&
        receipt.warnings.map((warning) => (
          <p className="b7-warn" key={warning}>
            {warning}
          </p>
        ))}
      <p>회사 손익·급여·청구·마감·세금은 변경하지 않습니다.</p>
    </section>
  );
}
export function BusinessPlanImportActions({
  year,
  currentYear,
  onApplied,
}: Omit<BusinessPlanImportDialogProps, "onClose">) {
  const [open, setOpen] = useState(false),
    [pending, setPending] = useState(false),
    [error, setError] = useState("");
  const busy = useRef(false);
  async function template() {
    if (busy.current) return;
    busy.current = true;
    setPending(true);
    setError("");
    try {
      api.saveDownload(await api.getBusinessPlanImportTemplate(year));
    } catch (error) {
      setError(
        error instanceof Error ? error.message : "다운로드하지 못했습니다.",
      );
    } finally {
      busy.current = false;
      setPending(false);
    }
  }
  return (
    <section
      data-screen-id="SCR-UI-18"
      data-testid="business-plan-import-actions"
      className="b7-actions"
    >
      <button
        type="button"
        data-testid="business-plan-import-template"
        disabled={pending}
        onClick={() => void template()}
      >
        양식 내려받기
      </button>
      <button
        type="button"
        data-testid="business-plan-import-open"
        disabled={year !== currentYear}
        onClick={() => setOpen(true)}
      >
        목표 업로드
      </button>
      {year !== currentYear && (
        <span className="b7-neutral">지난 해는 읽기 전용입니다.</span>
      )}
      {error && <p role="alert">{error}</p>}
      {open && (
        <BusinessPlanImportDialog
          year={year}
          currentYear={currentYear}
          onApplied={onApplied}
          onClose={() => setOpen(false)}
        />
      )}
    </section>
  );
}
