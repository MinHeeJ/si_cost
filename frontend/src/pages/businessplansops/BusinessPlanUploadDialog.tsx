import { useState } from "react";
import {
  ExcelUploadDialog,
  type UploadWorkflow,
} from "../b7/ExcelUploadDialog";
import { formatAmount } from "../../shared/format";
import * as api from "./businessPlanPerformanceApi";

export function BusinessPlanUploadDialog({
  year,
  onClose,
  onApplied,
}: {
  year: number;
  onClose(): void;
  onApplied(): Promise<void>;
}) {
  const [validation, setValidation] = useState<api.PlanValidation>();
  const [mode, setMode] = useState<"ALL" | "KEEP_CLOSED">();
  const workflow: UploadWorkflow<string> = {
    async validate(file) {
      setMode(undefined);
      const result = await api.validateBusinessPlan(file, year);
      setValidation(result);
      return { ...result, revision: result.expectedRevision };
    },
    async apply(file, uploadId, revision) {
      if (
        !validation ||
        !mode ||
        uploadId !== validation.uploadId ||
        revision !== validation.expectedRevision
      )
        throw new Error("다시 검사하고 반영 방식을 선택해 주세요.");
      return api.applyBusinessPlan(file, validation, mode);
    },
  };
  return (
    <ExcelUploadDialog<string>
      onClose={onClose}
      onApplied={onApplied}
      workflow={workflow}
      screenId="UI-18"
      title="목표 업로드"
      applyLabel={mode === "KEEP_CLOSED" ? "마감한 달은 두기" : "전체 변경"}
      description={`${year}년 회사 전체 목표 · 12개월 모두 필요 · 빈 금액은 0으로 검사 경고 · 목표와 실적은 별개입니다. 취소하면 반영하지 않습니다.`}
      onTemplate={() => api.downloadBusinessPlanTemplate(year)}
      requireRecheckOnConflict
      confirmAllowed={mode !== undefined}
      renderValidation={() =>
        validation && (
          <div data-testid="business-plan-upload-comparison">
            {validation.requiresClosedMonthConfirmation && (
              <p className="business-plan-warning">
                마감한 달 목표가 바뀝니다. 변경 전·후를 확인하고 반영 방식을
                선택해 주세요.
              </p>
            )}
            {validation.warnings.map((text, i) => (
              <p key={i}>{text}</p>
            ))}
            <div className="business-plan-scroll">
              <table>
                <thead>
                  <tr>
                    <th>월</th>
                    <th>상태</th>
                    <th>매출 목표 변경 전 → 후 (원)</th>
                    <th>영업이익 목표 변경 전 → 후 (원)</th>
                  </tr>
                </thead>
                <tbody>
                  {validation.changes
                    .filter((change) => change.changed)
                    .map((change) => (
                      <tr
                        key={change.month}
                        data-testid={`business-plan-target-change-${change.month}`}
                      >
                        <th>{change.month}</th>
                        <td>{change.closed ? "확정" : "작업중"}</td>
                        <td>
                          {formatAmount(change.before?.salesTargetAmt, "원")} →{" "}
                          {formatAmount(change.after.salesTargetAmt, "원")}
                        </td>
                        <td>
                          {formatAmount(
                            change.before?.operatingProfitTargetAmt,
                            "원",
                          )}{" "}
                          →{" "}
                          {formatAmount(
                            change.after.operatingProfitTargetAmt,
                            "원",
                          )}
                        </td>
                      </tr>
                    ))}
                </tbody>
              </table>
            </div>
            <fieldset>
              <legend>반영 방식 (자동 선택 없음)</legend>
              <label>
                <input
                  data-testid="business-plan-mode-keep"
                  type="radio"
                  name="business-plan-mode"
                  checked={mode === "KEEP_CLOSED"}
                  onChange={() => setMode("KEEP_CLOSED")}
                />
                마감한 달은 두기
              </label>
              <label>
                <input
                  data-testid="business-plan-mode-all"
                  type="radio"
                  name="business-plan-mode"
                  checked={mode === "ALL"}
                  onChange={() => setMode("ALL")}
                />
                전체 변경
              </label>
            </fieldset>
          </div>
        )
      }
      renderApplied={(result) => (
        <p data-testid="business-plan-upload-applied">
          {(result as api.PlanApplied).message} · 변경 월:{" "}
          {(result as api.PlanApplied).changedMonths.join(", ") || "없음"}
        </p>
      )}
    />
  );
}
