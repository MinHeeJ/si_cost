import { useState } from "react";
import { ApiError } from "../../shared/api";
import { formatAmount } from "../../shared/format";
import {
  ExcelUploadDialog,
  type UploadWorkflow,
} from "../b7/ExcelUploadDialog";
import * as api from "./historicalProfitLossImportsApi";
import "./migration.css";

type Props = {
  onClose(): void;
  onApplied(): void | Promise<void>;
  onDirtyChange?(dirty: boolean): void;
};
// SCR-UI-08: embedded in the existing operating profit screen; no separate router.
export function HistoricalProfitLossImportDialog({
  onClose,
  onApplied,
  onDirtyChange,
}: Props) {
  const [checked, setChecked] = useState<api.MigrationValidation>();
  const [receipt, setReceipt] = useState<
    api.MigrationApplied | api.MigrationUploadResult
  >();
  const [uncertain, setUncertain] = useState(false);
  const workflow: UploadWorkflow<string> = {
    validate: async (file) => {
      setReceipt(undefined);
      setUncertain(false);
      const result = await api.createHistoricalProfitLossImportValidation(file);
      setChecked(result);
      return { ...result, revision: result.expectedRevision };
    },
    apply: async (file, id, revision) => {
      if (!revision || uncertain) throw new Error("다시 검사해 주세요.");
      try {
        const result = await api.saveHistoricalProfitLossImport(
          file,
          id,
          revision,
        );
        setReceipt(result);
        return { ...result, appliedCount: result.changedMonths.length };
      } catch (error) {
        if (!(error instanceof ApiError)) setUncertain(true);
        throw error;
      }
    },
    result: async (id) => {
      const result = await api.getHistoricalProfitLossUploadResult(id);
      if (result.status === "APPLIED") setReceipt(result);
      return {
        ...result,
        appliedAt: result.appliedAt ?? undefined,
        appliedCount: result.changedMonths.length,
      };
    },
  };
  if (
    typeof matchMedia === "function" &&
    matchMedia("(max-width: 767px)").matches
  )
    return (
      <section data-testid="migration-pc-notice">
        <p>엑셀 업로드는 PC에서 이용해 주세요.</p>
        <button data-testid="migration-pc-close" onClick={onClose}>
          닫기
        </button>
      </section>
    );
  return (
    <ExcelUploadDialog<string>
      onClose={onClose}
      onDirtyChange={onDirtyChange}
      workflow={workflow}
      screenId="UI-08"
      title="이관 손익 업로드"
      applyLabel="반영"
      requireRecheckOnConflict
      confirmAllowed={!uncertain}
      description="BASE 전년 1월~직전월 중 일부 월만 올릴 수 있습니다. 미사용 연월 행은 삭제하세요. 필수 금액 빈칸은 오류이며 선택 빈칸·–·누락 열은 NULL로 교체합니다. XLSX 10MiB 이하 · 첫 시트 최대 5,000행."
      emptyRegistrationMessage="이관 손익은 변경되지 않았습니다."
      onTemplate={async () =>
        api.saveDownload(await api.getHistoricalProfitLossImportTemplate())
      }
      onApplied={async () => {
        window.dispatchEvent(
          new CustomEvent(api.HISTORICAL_PROFIT_LOSS_IMPORT_COMMITTED),
        );
        await onApplied();
      }}
      extractValidationError={(error) => {
        const latest = (
          error.body.details as
            | { financial?: { upload?: api.MigrationValidation } }
            | undefined
        )?.financial?.upload;
        if (!latest) return undefined;
        setChecked(latest);
        return { ...latest, revision: latest.expectedRevision };
      }}
      renderContext={() => (
        <p data-screen-id="SCR-UI-08" data-testid="migration-range">
          {checked
            ? `허용 범위 ${checked.allowedFromMonth} ~ ${checked.allowedThroughMonth}`
            : "검사 후 허용 범위와 변경월을 확인하세요."}
          {
            " · 검사만으로는 손익을 변경하지 않습니다. 운영월은 이관할 수 없습니다."
          }
        </p>
      )}
      renderValidation={() => (
        <section data-testid="migration-validation">
          {uncertain && (
            <p role="alert">
              통신 실패로 반영 여부가 불명확합니다. 결과 재조회로 확인하세요.
              자동 재반영하지 않습니다.
            </p>
          )}
          {checked?.warnings.map((warning) => (
            <p key={warning} className="b7-warn">
              {warning}
            </p>
          ))}
          {checked && (
            <div className="migration-changes">
              <table className="sicost-table">
                <thead>
                  <tr>
                    <th>연월</th>
                    <th>매출</th>
                    <th>매출원가</th>
                    <th>판관비 합계</th>
                    <th>영업이익</th>
                    <th>매출총이익</th>
                    <th>당기순이익</th>
                    <th>변경</th>
                  </tr>
                </thead>
                <tbody>
                  {checked.changes.map((change) => (
                    <tr
                      key={change.month}
                      data-testid={`migration-month-${change.month}`}
                    >
                      <td>{change.month}</td>
                      {[
                        "salesAmt",
                        "salesCostAmt",
                        "sgaTotAmt",
                        "oprtPrftAmt",
                        "grossPrftAmt",
                        "netPrftAmt",
                      ].map((key) => (
                        <td key={key}>
                          {formatAmount(change.after[key], "원")}
                        </td>
                      ))}
                      <td>{change.changed ? "변경" : "동일"}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
              {checked.changes.length === 0 && (
                <p className="b7-neutral">표시할 변경월이 없습니다.</p>
              )}
            </div>
          )}
          <p>
            계산식 불일치는 경고이며 금액을 보정하지 않습니다. 누락 상세는 –로
            유지합니다.
          </p>
        </section>
      )}
      renderApplied={() =>
        receipt && (
          <section data-testid="migration-result">
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
            <p>
              파일에 없는 월·월 마감·운영 원천·개인 원가·기초 현금은 변경하지
              않습니다.
            </p>
          </section>
        )
      }
    />
  );
}
export function HistoricalProfitLossImportActions({
  onApplied,
}: Pick<Props, "onApplied">) {
  const [open, setOpen] = useState(false),
    [pending, setPending] = useState(false),
    [error, setError] = useState("");
  async function template() {
    if (pending) return;
    setPending(true);
    setError("");
    try {
      api.saveDownload(await api.getHistoricalProfitLossImportTemplate());
    } catch (error) {
      setError(
        error instanceof Error ? error.message : "다운로드하지 못했습니다.",
      );
    } finally {
      setPending(false);
    }
  }
  return (
    <section
      data-screen-id="SCR-UI-08"
      data-testid="migration-actions"
      className="b7-actions"
    >
      <button
        type="button"
        data-testid="migration-template"
        disabled={pending}
        onClick={() => void template()}
      >
        이관 양식 내려받기
      </button>
      <button
        type="button"
        data-testid="migration-open"
        onClick={() => setOpen(true)}
      >
        이관 손익 업로드
      </button>
      {error && <p role="alert">{error}</p>}
      {open && (
        <HistoricalProfitLossImportDialog
          onApplied={onApplied}
          onClose={() => setOpen(false)}
        />
      )}
    </section>
  );
}
