import { useState } from "react";
import {
  ExcelUploadDialog,
  type ExcelUploadDialogProps,
  type UploadWorkflow,
} from "../b7/ExcelUploadDialog";
import { B6ComparisonPanel } from "./B6ComparisonPanel";
import * as api from "./b6Api";
import "./b6.css";

export type B6UploadDialogProps = Pick<
  ExcelUploadDialogProps,
  "onClose" | "onApplied" | "onDirtyChange"
> & {
  projectId: string;
  year: number;
  startDate: string;
  endDate: string;
  readOnly?: boolean;
  initialStep?: api.ProjectStep;
};
// SCR-UI-01: embedded B6 upload region, never a separate URL.
export function B6UploadDialog({
  projectId,
  year,
  startDate,
  endDate,
  readOnly = false,
  initialStep,
  ...props
}: B6UploadDialogProps) {
  const [step, setStep] = useState<api.ProjectStep | "">(initialStep ?? "");
  const workflow: UploadWorkflow = {
    validate: (file) => {
      if (!step) throw new Error("단계를 선택해 주세요.");
      return api.createB6Validation(file, { year, projectId, step });
    },
    apply: (file, uploadId, revision) => {
      if (!step || !revision)
        throw new Error(
          "현재 단계와 최신 검사 revision을 확인해 주세요. 다시 검사해 주세요.",
        );
      return api
        .saveB6Apply(file, uploadId, { year, projectId, step }, revision)
        .then((result) => ({
          ...result,
          appliedCount: result.summary.totalRows,
        }));
    },
    result: (id) =>
      api.getUploadResult(id).then((result) => ({
        ...result,
        appliedCount: result.summary.totalRows,
      })),
  };
  if (window.matchMedia?.("(max-width: 767px)").matches)
    return (
      <section data-testid="b6-desktop-notice" data-screen-id="SCR-UI-01">
        <p>이 기능은 PC에서 사용해 주세요.</p>
        <button
          type="button"
          data-testid="b6-mobile-close"
          onClick={props.onClose}
        >
          닫기
        </button>
      </section>
    );
  return (
    <ExcelUploadDialog
      {...props}
      key={`${projectId}:${year}:${step}`}
      workflow={workflow}
      readOnly={readOnly || !step}
      screenId="UI-01"
      readOnlyMessage={
        readOnly
          ? "지난 연도 또는 프로젝트 상태에 따라 조회만 할 수 있습니다."
          : "단계를 먼저 선택해 주세요."
      }
      title="턴키 배분표 업로드 B6"
      onTemplate={async () =>
        api.saveDownload(await api.getB6Template(projectId))
      }
      emptyRegistrationMessage="배분표는 변경되지 않았습니다."
      description="같은 단계 전체 교체 · 다른 단계 유지. XLSX 10,485,760바이트 이하 · 1~5,000행. 모든 계약월을 원 단위로 입력하세요. File은 브라우저 메모리에만 유지되며 원본은 서버에 보관하지 않습니다. 보호월의 계산 선택표 금액 변경은 반영되지 않습니다."
      renderContext={(pending, applied) => (
        <section className="b6-upload-context" data-testid="b6-upload-context">
          <p>
            대상 {projectId} · 계약기간 {startDate} ~ {endDate} · 선택연도{" "}
            {year}
          </p>
          {readOnly && (
            <p className="sicost-banner sicost-tone-lock">
              지난 연도 또는 프로젝트 상태에 따라 조회만 할 수 있습니다.
            </p>
          )}
          <label className="sicost-field">
            단계 *
            <select
              data-testid="b6-step-select"
              value={step}
              disabled={pending || applied || readOnly}
              onChange={(event) =>
                setStep(event.target.value as api.ProjectStep | "")
              }
            >
              <option value="">단계 선택</option>
              <option value="EST">EST 견적</option>
              <option value="CTRT">CTRT 최종 계약</option>
              <option value="PLAN">PLAN 실제 투입계획</option>
            </select>
          </label>
          <p>
            대상·단계 변경은 새 검사입니다. 계산에 쓰는 단계는 PLAN &gt; CTRT
            &gt; EST 순으로 서버가 선택합니다.
          </p>
        </section>
      )}
      renderValidation={(result) => (
        <section data-testid="b6-prospective-panel">
          <p>업로드 표 자체 합계 {result.uploadedTableTotal ?? "–"}원</p>
          {isComparison(result.prospectiveComparison) && (
            <B6ComparisonPanel
              comparison={result.prospectiveComparison}
              title="계산 선택 예정표"
            />
          )}
        </section>
      )}
      renderApplied={() => (
        <p
          className="sicost-banner sicost-tone-ok"
          data-testid="b6-applied-target"
        >
          APPLIED · 대상 {projectId} · 당시 단계 {step} · 최초 반영 결과
        </p>
      )}
    />
  );
}
function isComparison(value: unknown): value is api.B6Comparison {
  return (
    typeof value === "object" &&
    value !== null &&
    "mismatchCount" in value &&
    "items" in value &&
    "differenceAmount" in value
  );
}
