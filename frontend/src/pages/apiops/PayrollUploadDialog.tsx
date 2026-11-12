import { useRef, useState } from "react";
import { ApiError } from "../../shared/api";
import { DataTable } from "../../shared/DataTable";
import {
  ExcelUploadDialog,
  type UploadApplied,
  type UploadValidation,
  type UploadWorkflow,
} from "../b7/ExcelUploadDialog";
import { getUploadIssues } from "../uploadsops/uploadIssuesApi";
import * as api from "./payrollApi";

export function PayrollReceipt({ result }: { result: api.PayrollResult }) {
  return (
    <section data-testid="payroll-receipt" aria-label="급여 최초 반영 결과">
      <p>
        {result.month} · {result.status} · 업로드 번호 {result.uploadId}
      </p>
      <p>최초 반영 결과 · {result.changed ? "월자료 변경" : "변경 없음"}</p>
      <DataTable
        rows={result.results}
        rowKey={(r) => r.rowNumber}
        columns={[
          { id: "rowNumber", label: "원래 행", render: (r) => r.rowNumber },
          {
            id: "developerId",
            label: "개발자 ID",
            render: (r) => (
              <span data-testid={`payroll-result-${r.rowNumber}`}>
                {r.developerId}
              </span>
            ),
          },
        ]}
        empty="반영된 급여가 없습니다."
      />
    </section>
  );
}

export function PayrollUploadDialog({
  month,
  readOnly,
  readOnlyMessage,
  onClose,
  onApplied,
  onDirtyChange,
}: {
  month: string;
  readOnly: boolean;
  readOnlyMessage: string;
  onClose(): void;
  onApplied(): void | Promise<void>;
  onDirtyChange?(dirty: boolean): void;
}) {
  const [checked, setChecked] = useState<api.PayrollValidation>();
  const [receipt, setReceipt] = useState<api.PayrollResult>();
  const [confirmed, setConfirmed] = useState(false);
  const previous = useRef<{ file: File; uploadId: number }>();
  const stale = useRef(false);
  const [mustRecheck, setMustRecheck] = useState(false);
  async function adapt(
    value: api.PayrollValidation,
  ): Promise<UploadValidation> {
    setChecked(value);
    setConfirmed(false);
    setMustRecheck(false);
    stale.current = false;
    const issues = await getUploadIssues(value.uploadId, "", 1, 50);
    return {
      uploadId: value.uploadId,
      status: value.status,
      summary: value.summary,
      revision: value.revision,
      canApply:
        value.status === "CHECKED" &&
        value.summary.errorRows === 0 &&
        value.summary.totalRows > 0,
      issues,
    };
  }
  function applied(value: api.PayrollResult): UploadApplied {
    setReceipt(value);
    return {
      uploadId: value.uploadId,
      status: value.status,
      summary: value.summary,
      revision: value.revision,
    };
  }
  const workflow: UploadWorkflow = {
    async validate(file) {
      setConfirmed(false);
      setChecked(undefined);
      const value = await api.createB1Validation(
        file,
        month,
        previous.current?.file === file ? previous.current.uploadId : undefined,
      );
      previous.current = { file, uploadId: value.uploadId };
      return adapt(value);
    },
    async apply(file, uploadId, revision) {
      if (!confirmed || stale.current || !revision)
        throw new Error("월 전체 교체를 확인하고 다시 검사해 주세요.");
      try {
        return applied(
          await api.createB1Apply(file, {
            month,
            uploadId,
            expectedRevision: revision,
            confirmFullReplacement: true,
          }),
        );
      } catch (e) {
        setConfirmed(false);
        if (e instanceof ApiError && e.status === 409) {
          stale.current = true;
          setMustRecheck(true);
        }
        if (e instanceof ApiError && e.status === 422) {
          const value = e.body.details as unknown as api.PayrollValidation;
          if (value?.uploadId && value.summary && value.replacement) {
            const adapted = await adapt(value);
            throw new ApiError(e.status, {
              ...e.body,
              details: adapted as unknown as ApiError["body"]["details"],
            });
          }
        }
        throw e;
      }
    },
    async result(uploadId) {
      return applied(await api.getPayrollResult(uploadId));
    },
  };
  return (
    <ExcelUploadDialog
      onClose={onClose}
      onApplied={onApplied}
      onDirtyChange={onDirtyChange}
      workflow={workflow}
      screenId="UI-02"
      title="급여 업로드 B1"
      description="관리번호* / 이름 / 급여 / 4대보험 회사 / 4대보험 본인 / 근로소득세 / 외주 공급가 · XLSX 10MiB 이하 · 첫 시트 1~5,000행. 급여 직접수정은 지원하지 않습니다."
      readOnly={readOnly}
      readOnlyMessage={readOnlyMessage}
      confirmAllowed={confirmed && !mustRecheck}
      validationFailureMessage="오류를 수정한 파일을 다시 검사해 주세요. 기존 월 급여는 변경되지 않았습니다."
      onTemplate={async () => api.saveDownload(await api.getPayrollTemplate())}
      renderContext={() => (
        <p data-testid="payroll-upload-month">
          선택월 {month} · 다른월은 유지됩니다. 응답 유실 시 결과 재조회로 먼저
          확인해 주세요.
        </p>
      )}
      renderValidation={() =>
        checked && (
          <section
            data-testid="payroll-replacement-preview"
            className="payroll-replacement"
          >
            <p>
              신규 {checked.replacement.newCount}건 / 변경{" "}
              {checked.replacement.changedCount}건 / 제거{" "}
              {checked.replacement.removedCount}건
            </p>
            <div className="payroll-removed" aria-label="제거인력 목록">
              {checked.replacement.removedDevelopers.map((r) => (
                <p
                  key={r.developerId}
                  data-testid={`payroll-removed-${r.developerId.toLowerCase()}`}
                >
                  {r.developerName} · {r.managementNo} · {r.developerId}
                </p>
              ))}
            </div>
            {mustRecheck && (
              <p role="alert" className="sicost-banner sicost-tone-warn">
                월자료가 변경되었습니다. 다시 검사해 주세요.
              </p>
            )}
            <label>
              <input
                type="checkbox"
                data-testid="payroll-full-replacement"
                checked={confirmed}
                disabled={
                  readOnly || mustRecheck || checked.summary.errorRows > 0
                }
                onChange={(e) => setConfirmed(e.target.checked)}
              />
              선택월 전체를 새 파일로 교체하며 누락 인력의 해당월 행을 제거함을
              확인
            </label>
          </section>
        )
      }
      renderApplied={() => receipt && <PayrollReceipt result={receipt} />}
    />
  );
}
