import { FieldHelp } from "../../shared/FieldHelp";
import { useEffect, useState } from "react";
import { Dialog } from "../../shared/Dialog";
import { FeedbackNotice } from "../../shared/Feedback";
import { HelpTooltip } from "../../shared/HelpTooltip";
import { ApiError } from "../../shared/api";
import { useCommitRefresh } from "../../shared/useCommitRefresh";
import { BillingError } from "./BillSummaryCards";
import * as api from "./billOperationsApi";

export function BillCopyDialog({
  draft,
  projectName,
  onCancel,
  onChanged,
  onDirtyChange,
}: {
  draft: api.BillCopyDraft;
  projectName: string;
  onCancel(): void;
  onChanged(result: api.BillMutationResult): Promise<void> | void;
  onDirtyChange?(dirty: boolean): void;
}) {
  const initial = {
    supplyAmount: draft.supplyAmount ?? "",
    vatRate: draft.vatRate,
    billingDate: draft.billingDate,
    dueDate: draft.dueDate,
    taxInvoiceNo: "",
  };
  const [form, setForm] = useState(initial);
  const [error, setError] = useState<unknown>();
  const [fields, setFields] = useState<Record<string, string>>({});
  const [leaving, setLeaving] = useState(false);
  const [stale, setStale] = useState(false);
  const [duplicate, setDuplicate] = useState<{
    existingBills: { id: string; order: number }[];
    duplicateToken: string;
  }>();
  const [saved, setSaved] = useState<api.BillMutationResult>();
  const { pending, outcome, commit } = useCommitRefresh(
    "등록은 완료됐지만 청구 정보를 다시 불러오지 못했습니다.",
  );
  const dirty = JSON.stringify(form) !== JSON.stringify(initial);
  useEffect(() => {
    onDirtyChange?.(dirty);
    return () => onDirtyChange?.(false);
  }, [dirty, onDirtyChange]);
  const close = () => {
    if (!pending) {
      if (dirty) setLeaving(true);
      else onCancel();
    }
  };
  const change = (key: keyof typeof form, value: string) => {
    setForm((old) => ({ ...old, [key]: value }));
    setFields((old) => ({ ...old, [key]: "" }));
    setDuplicate(undefined);
  };
  async function refresh(value: api.BillMutationResult) {
    await onChanged(value);
    onCancel();
  }
  async function submit(confirm = false) {
    if (pending || stale || saved) return;
    const validation: Record<string, string> = {};
    const supply = form.supplyAmount
      .replace(/[\s\p{Z},]/gu, "")
      .replace(/원$/, "");
    const vat = form.vatRate.replace(/[\s\p{Z},]/gu, "").replace(/%$/, "");
    if (
      !/^\d+$/.test(supply) ||
      BigInt(supply) < 1n ||
      BigInt(supply) > 999999999999999n
    )
      validation.supplyAmount =
        "공급가는 1~999,999,999,999,999원의 정수입니다.";
    if (
      !/^\d+(\.\d{1,2})?$/.test(vat) ||
      BigInt(
        vat
          .replace(".", "")
          .padEnd(
            vat.includes(".") ? vat.indexOf(".") + 2 : vat.length + 2,
            "0",
          ),
      ) > 10000n
    )
      validation.vatRate = "세율은 0~100, 소수 둘째 자리까지 입력해 주세요.";
    if (!form.billingDate) validation.billingDate = "청구일을 입력해 주세요.";
    if (!form.dueDate || form.dueDate < form.billingDate)
      validation.dueDate = "입금예정일은 청구일 이상이어야 합니다.";
    if ([...form.taxInvoiceNo.trim()].length > 30)
      validation.taxInvoiceNo = "세금계산서번호는 30자 이내입니다.";
    setFields(validation);
    if (Object.keys(validation).length) return;
    setError(undefined);
    try {
      let result: api.BillMutationResult | undefined;
      await commit(
        async () => {
          result = await api.createCopiedBill({
            year: draft.year,
            projectId: draft.projectId,
            billingMonth: draft.billingMonth,
            ...form,
            supplyAmount: BigInt(supply).toString(),
            vatRate: vat,
            taxInvoiceNo: form.taxInvoiceNo.trim() || null,
            copyContext: {
              sourceBillId: draft.sourceBillId,
              expectedRevision: draft.expectedRevision,
            },
            ...(confirm && duplicate
              ? {
                  confirmDuplicate: true,
                  duplicateToken: duplicate.duplicateToken,
                }
              : {}),
          });
          setSaved(result);
          setDuplicate(undefined);
          onDirtyChange?.(false);
        },
        async () => {
          if (result) await refresh(result);
        },
      );
    } catch (e) {
      setError(e);
      if (e instanceof ApiError) {
        setFields(e.fieldMessages);
        if (e.body.code === "STALE_WRITE") setStale(true);
        if (e.body.code === "DUPLICATE_BILL_CONFIRMATION_REQUIRED") {
          const details = e.body.details as
            | {
                billing?: {
                  existingBills: { id: string; order: number }[];
                  duplicateToken: string;
                };
              }
            | undefined;
          if (details?.billing) setDuplicate(details.billing);
        }
      }
    }
  }
  const title = "고쳐서 청구 — 다음월 신규복사";
  const warnings = [...new Set(draft.warnings)];
  return (
    <>
      <Dialog
        open
        title={title}
        className="bill-form-modal"
        titleHelp={
          <HelpTooltip label="고쳐서 청구" iconOnly>
            <ul>
              <li>다음 달 청구를 새로 등록하며, 청구번호와 차수는 등록할 때 발급됩니다.</li>
              <li>세금계산서 번호는 비워 두어도 등록할 수 있습니다.</li>
              <li>부가세와 합계는 등록 후 청구 상세에서 확인할 수 있습니다.</li>
            </ul>
          </HelpTooltip>
        }
        pending={pending}
        onCancel={close}
        onConfirm={() => submit()}
        confirmLabel="등록"
        confirmDisabled={stale || Boolean(saved)}
      >
        <div
          className="bill-form"
          data-screen-id="SCR-UI-13"
          data-testid="bill-copy-dialog"
        >
          <BillingError error={error} />
          {stale && (
            <FeedbackNotice tone="warn" title="원청구 변경">
              창을 닫고 원청구를 다시 조회해 주세요.
            </FeedbackNotice>
          )}
          {outcome?.kind === "refresh-failed" && saved && (
            <FeedbackNotice
              tone="warn"
              title="등록 완료 · 조회 실패"
              action={
                <button
                  type="button"
                  data-testid="bill-copy-refresh"
                  onClick={() => { void refresh(saved).catch(setError); }}
                >
                  다시 조회
                </button>
              }
            >
              {outcome.message}
            </FeedbackNotice>
          )}
          <dl className="bill-copy-context">
            <div>
              <dt>프로젝트</dt>
              <dd>{projectName} <small>{draft.projectId}</small></dd>
            </div>
            <div>
              <dt>청구월</dt>
              <dd>{draft.billingMonth}</dd>
            </div>
            <div>
              <dt>원청구</dt>
              <dd>{draft.sourceBillId}</dd>
            </div>
          </dl>
          {warnings.length > 0 && (
            <FeedbackNotice tone="warn" title="청구 기본값 확인">
              {warnings.length === 1 ? warnings[0] : (
                <ul>
                  {warnings.map((warning) => <li key={warning}>{warning}</li>)}
                </ul>
              )}
            </FeedbackNotice>
          )}
          {(
            [
              ["supplyAmount", "공급가 (원) *", "text"],
              ["vatRate", "부가세율 (%) *", "text"],
              ["billingDate", "청구일 *", "date"],
              ["dueDate", "입금예정일 *", "date"],
              ["taxInvoiceNo", "세금계산서번호", "text"],
            ] as const
          ).map(([key, label, type]) => (
            <div
              className="sicost-field"
              key={key}
              data-testid={`bill-copy-field-${key.replace(/[A-Z]/g, (c) => `-${c.toLowerCase()}`)}`}
            >
              <label htmlFor={`bill-copy-${key}`}>{label}<FieldHelp label={label} /></label>
              <input
                id={`bill-copy-${key}`}
                data-testid={`bill-copy-${key.replace(/[A-Z]/g, (x) => `-${x.toLowerCase()}`)}`}
                type={type}
                inputMode={
                  key === "supplyAmount" || key === "vatRate"
                    ? "decimal"
                    : undefined
                }
                value={form[key]}
                disabled={pending || Boolean(saved)}
                aria-invalid={Boolean(fields[key])}
                onChange={(e) => change(key, e.target.value)}
              />
              {fields[key] && (
                <span role="alert" className="sicost-field-error">
                  {fields[key]}
                </span>
              )}
            </div>
          ))}
        </div>
      </Dialog>
      <Dialog
        open={Boolean(duplicate)}
        title="같은 달 청구 확인"
        confirmLabel="등록"
        pending={pending}
        onCancel={() => setDuplicate(undefined)}
        onConfirm={() => submit(true)}
      >
        <div data-testid="bill-duplicate-dialog">
          <p>같은 달 청구를 추가하려면 등록을 한 번 더 누르세요.</p>
          {duplicate?.existingBills.map((row) => (
            <p
              key={row.id}
              data-testid={`bill-copy-duplicate-${row.id.toLowerCase().replace(/[^a-z0-9-]/g, "-")}`}
            >
              {row.id} · {row.order}차
            </p>
          ))}
        </div>
      </Dialog>
      <Dialog
        open={leaving}
        title="작성 중인 내용을 나가시겠습니까?"
        confirmLabel="나가기"
        cancelLabel="계속 작성"
        onCancel={() => setLeaving(false)}
        onConfirm={onCancel}
      >
        <p>자동 저장하지 않습니다.</p>
      </Dialog>
    </>
  );
}
