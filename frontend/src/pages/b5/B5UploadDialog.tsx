import { useEffect, useRef, useState } from "react";
import { ApiError } from "../../shared/api";
import { DataTable } from "../../shared/DataTable";
import {
  ExcelUploadDialog,
  type UploadApplied,
  type UploadValidation,
  type UploadWorkflow,
} from "../b7/ExcelUploadDialog";
import { getUploadIssues } from "../uploadsops/uploadIssuesApi";
import { getSharedCodes, type SharedCodeLookup } from "../shared/sharedApi";
import type { Withdrawal } from "../assignmentsops/assignmentActionsApi";
import * as api from "./b5Api";

// SCR-UI-12 B5-FILE / B5-RESULT: retained UI-12 dialog, not another route.
export function B5UploadDialog({
  year,
  readOnly,
  onClose,
  onApplied,
  onDirtyChange,
}: {
  year: number;
  readOnly: boolean;
  onClose(): void;
  onApplied(): void | Promise<void>;
  onDirtyChange?(dirty: boolean): void;
}) {
  const [checked, setChecked] = useState<api.B5Validation>(),
    [receipt, setReceipt] = useState<api.B5Result>();
  const [selections, setSelections] = useState<api.WithdrawalSelection[]>([]),
    [mustRecheck, setMustRecheck] = useState(false);
  const [reasons, setReasons] = useState<SharedCodeLookup[]>([]),
    [reasonError, setReasonError] = useState("");
  const previous = useRef<{ file: File; uploadId: number }>();
  const token = useRef<string>();
  useEffect(() => {
    let live = true;
    void getSharedCodes("ASGN_CHG_RSN")
      .then((r) => {
        if (live) setReasons(r.items);
      })
      .catch(() => {
        if (live)
          setReasonError(
            "철수 사유를 불러오지 못했습니다. 창을 다시 열어 주세요.",
          );
      });
    return () => {
      live = false;
    };
  }, []);
  function change(
    rowNumber: number,
    withdrawal: Withdrawal,
    selected: boolean,
  ) {
    token.current = undefined;
    setMustRecheck(true);
    setSelections((old) => {
      const values =
        old
          .find((r) => r.rowNumber === rowNumber)
          ?.withdrawals.filter(
            (w) => w.assignmentId !== withdrawal.assignmentId,
          ) ?? [];
      if (selected) values.push(withdrawal);
      return [
        ...old.filter((r) => r.rowNumber !== rowNumber),
        ...(values.length ? [{ rowNumber, withdrawals: values }] : []),
      ];
    });
  }
  async function adapt(value: api.B5Validation): Promise<UploadValidation> {
    setChecked(value);
    setMustRecheck(false);
    token.current = value.previewToken;
    const issues = await getUploadIssues(value.uploadId, "", 1, 50);
    return {
      uploadId: value.uploadId,
      status: value.status,
      summary: value.summary,
      canApply:
        value.status === "CHECKED" &&
        value.summary.errorRows === 0 &&
        value.summary.totalRows > 0,
      issues,
    };
  }
  function applied(value: api.B5Result): UploadApplied {
    setReceipt(value);
    return {
      uploadId: value.uploadId,
      status: value.status,
      summary: value.summary,
    };
  }
  const invalidSelections = selections.some((s) =>
    s.withdrawals.some((w) => !w.reasonCode || (w.memo?.length ?? 0) > 200),
  );
  const workflow: UploadWorkflow = {
    async validate(file) {
      const same = previous.current?.file === file;
      const current = same ? selections : [];
      if (!same) setSelections([]);
      token.current = undefined;
      const value = await api.createB5Validation(
        file,
        year,
        current,
        same ? previous.current?.uploadId : undefined,
      );
      previous.current = { file, uploadId: value.uploadId };
      return adapt(value);
    },
    async apply(file, uploadId) {
      if (!token.current || mustRecheck || invalidSelections)
        throw new Error(
          "철수 선택과 사유를 확인하고 같은 파일로 다시 검사해 주세요.",
        );
      try {
        return applied(
          await api.createB5Apply(
            file,
            year,
            selections,
            uploadId,
            token.current,
          ),
        );
      } catch (e) {
        if (e instanceof ApiError && e.status === 409) {
          token.current = undefined;
          setMustRecheck(true);
        }
        if (e instanceof ApiError && e.status === 422) {
          const value = e.body.details as unknown as api.B5Validation;
          if (value?.uploadId && value.summary && value.previewToken) {
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
      return applied(await api.getB5Result(uploadId));
    },
  };
  return (
    <ExcelUploadDialog
      title="투입 신규 업로드 B5"
      screenId="UI-12"
      workflow={workflow}
      onClose={onClose}
      onApplied={onApplied}
      onDirtyChange={onDirtyChange}
      readOnly={readOnly}
      confirmAllowed={!mustRecheck && !invalidSelections}
      onTemplate={async () => api.saveDownload(await api.getB5Template())}
      description="프로젝트ID* / 관리번호* / 직무* / 투입일* / 철수일* / 월 MM / 청구 단가 / 투입 구분* / 계약상 인력 관리번호 / 연장 가능성 · XLSX 10MiB 이하 · 첫 시트 1~5,000행. 신규 전용이며 수정은 변경 기록을 사용하세요."
      emptyRegistrationMessage="등록된 투입 배정은 없습니다."
      renderContext={() => (
        <p>
          선택 연도 {year} · 월 MM 빈칸 1.00 · 연장 가능성 빈칸 미정 ·
          대체투입은 계약상 인력 필수
        </p>
      )}
      renderValidation={() =>
        checked && (
          <section data-testid="b5-withdrawal-panel">
            {reasonError && (
              <p role="alert" className="b7-bad">
                {reasonError}
              </p>
            )}
            {mustRecheck && (
              <p role="alert" className="sicost-banner sicost-tone-warn">
                선택 또는 원천이 변경되었습니다. 다시 검사하고 확인해 주세요.
              </p>
            )}
            {checked.substitutionPreviews.map(({ rowNumber, preview }) => (
              <section
                key={rowNumber}
                data-testid={`b5-substitution-${rowNumber}`}
              >
                <h3>SUB 원래 행 {rowNumber} · 기본 대체만</h3>
                <p>
                  계약상 인력 동시 철수 검토 · 예정철수일{" "}
                  {preview.withdrawalDate}
                </p>
                {preview.turnkeyNotice && (
                  <p className="b7-warn">{preview.turnkeyNotice}</p>
                )}
                {preview.protectedMonths.length > 0 && (
                  <p className="b7-warn">
                    마감한 달 값은 유지합니다.{" "}
                    {preview.protectedMonths.join(", ")}
                  </p>
                )}
                {preview.candidates.length === 0 && (
                  <p>동시 철수할 기존 배정이 없습니다.</p>
                )}
                {preview.candidates.map((candidate) => {
                  const selected = selections
                    .find((s) => s.rowNumber === rowNumber)
                    ?.withdrawals.find((w) => w.assignmentId === candidate.id);
                  const value: Withdrawal = selected ?? {
                    assignmentId: candidate.id,
                    expectedRevision: candidate.expectedRevision,
                    withdrawalDate: preview.withdrawalDate,
                    reasonCode: "",
                    memo: "",
                  };
                  return (
                    <div
                      key={candidate.id}
                      data-testid={`b5-withdrawal-${rowNumber}-${candidate.id.toLowerCase()}`}
                      className="b7-actions"
                    >
                      <label>
                        <input
                          data-testid={`b5-select-${rowNumber}-${candidate.id.toLowerCase()}`}
                          type="checkbox"
                          checked={Boolean(selected)}
                          disabled={readOnly || Boolean(reasonError)}
                          onChange={(e) =>
                            change(rowNumber, value, e.target.checked)
                          }
                        />
                        {candidate.id} · {preview.withdrawalDate}
                      </label>
                      <label>
                        철수 사유
                        <select
                          data-testid={`b5-reason-${rowNumber}-${candidate.id.toLowerCase()}`}
                          disabled={!selected || readOnly}
                          value={value.reasonCode}
                          onChange={(e) =>
                            change(
                              rowNumber,
                              { ...value, reasonCode: e.target.value },
                              true,
                            )
                          }
                        >
                          <option value="">사유 선택</option>
                          {reasons.map((r) => (
                            <option
                              data-testid={`b5-reason-option-${rowNumber}-${candidate.id.toLowerCase()}-${r.code.toLowerCase().replace(/_/g, "-")}`}
                              key={r.code}
                              value={r.code}
                            >
                              {r.name}
                            </option>
                          ))}
                        </select>
                      </label>
                      <label>
                        철수 메모
                        <input
                          data-testid={`b5-memo-${rowNumber}-${candidate.id.toLowerCase()}`}
                          disabled={!selected || readOnly}
                          value={value.memo ?? ""}
                          maxLength={200}
                          onChange={(e) =>
                            change(
                              rowNumber,
                              { ...value, memo: e.target.value },
                              true,
                            )
                          }
                        />
                      </label>
                    </div>
                  );
                })}
              </section>
            ))}
          </section>
        )
      }
      renderApplied={() =>
        receipt && (
          <section data-testid="b5-receipt">
            <p>
              최초 반영 결과 · {receipt.status} · 업로드 번호 {receipt.uploadId}
            </p>
            <DataTable
              rows={receipt.results}
              rowKey={(r) => r.rowNumber}
              columns={[
                {
                  id: "rowNumber",
                  label: "원래 행",
                  render: (r) => r.rowNumber,
                },
                {
                  id: "assignmentId",
                  label: "최종 ASGN_ID",
                  render: (r) => r.assignmentId,
                },
              ]}
              empty="반영된 투입 배정이 없습니다."
            />
            <p>
              같이 철수한 배정{" "}
              {receipt.withdrawnIds.length
                ? receipt.withdrawnIds.join(", ")
                : "없음"}
            </p>
            <p className="b7-warn">
              턴키 배분표의 인력과 금액은 자동 이동하지 않습니다. 해당 배분표를
              다시 올리고 불일치 안내를 확인해 주세요.
            </p>
          </section>
        )
      }
    />
  );
}

export function B5Actions({
  year,
  readOnly,
  onApplied,
  onDirtyChange,
}: {
  year: number;
  readOnly: boolean;
  onApplied(): void | Promise<void>;
  onDirtyChange?(dirty: boolean): void;
}) {
  const [open, setOpen] = useState(false),
    [pending, setPending] = useState(false),
    [error, setError] = useState("");
  return (
    <span className="b7-actions assignment-desktop" data-testid="b5-actions">
      <button
        type="button"
        data-testid="b5-template"
        disabled={pending}
        onClick={async () => {
          setPending(true);
          setError("");
          try {
            api.saveDownload(await api.getB5Template());
          } catch (e) {
            setError(
              e instanceof Error ? e.message : "양식을 받지 못했습니다.",
            );
          } finally {
            setPending(false);
          }
        }}
      >
        양식 내려받기
      </button>
      <button
        type="button"
        data-testid="b5-upload"
        disabled={readOnly}
        onClick={() => setOpen(true)}
      >
        엑셀 업로드
      </button>
      {error && (
        <span role="alert" className="b7-bad">
          {error}
        </span>
      )}
      {open && (
        <B5UploadDialog
          year={year}
          readOnly={readOnly}
          onClose={() => setOpen(false)}
          onApplied={onApplied}
          onDirtyChange={onDirtyChange}
        />
      )}
    </span>
  );
}
