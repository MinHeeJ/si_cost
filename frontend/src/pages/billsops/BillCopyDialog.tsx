import { useEffect, useState } from "react";
import { Dialog } from "../../shared/Dialog";
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
  const { pending, message, commit } = useCommitRefresh();
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
  return (
    <>
      <Dialog
        open
        title="고쳐서 청구 — 다음월 신규복사"
        pending={pending}
        onCancel={close}
        onConfirm={() => submit()}
        confirmLabel="등록"
        confirmDisabled={stale || Boolean(saved)}
      >
        <div
          className="bill-copy-form"
          data-screen-id="SCR-UI-13"
          data-testid="bill-copy-dialog"
        >
          <BillingError error={error} />
          <p>
            프로젝트 {projectName} · {draft.projectId}
          </p>
          <p>청구월 {draft.billingMonth} (고정)</p>
          <p>
            청구번호·차수는 등록 시 발급됩니다. 원청구 {draft.sourceBillId}는
            변경하지 않습니다.
          </p>
          {draft.warnings.map((warning, i) => (
            <p key={i} className="sicost-banner sicost-tone-warn">
              {warning}
            </p>
          ))}
          {(
            [
              ["supplyAmount", "공급가 (원) *", "text"],
              ["vatRate", "부가세율 (%) *", "text"],
              ["billingDate", "청구일 *", "date"],
              ["dueDate", "입금예정일 *", "date"],
              ["taxInvoiceNo", "세금계산서번호", "text"],
            ] as const
          ).map(([key, label, type]) => (
            <div className="sicost-field" key={key}>
              <label htmlFor={`bill-copy-${key}`}>{label}</label>
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
          {!form.taxInvoiceNo.trim() && (
            <p className="sicost-banner sicost-tone-warn">
              세금계산서 번호 미입력 — 빈칸으로 등록할 수 있습니다.
            </p>
          )}
          <p>계산 금액은 저장 후 상세에서 확인해 주세요.</p>
          {stale && (
            <p role="alert">
              원청구가 변경되었습니다. 창을 닫고 다시 조회해 주세요. 자동
              재전송하지 않습니다.
            </p>
          )}
          {message && <p role="status">{message}</p>}
          {saved && (
            <button
              type="button"
              data-testid="bill-copy-refresh"
              onClick={() => {
                void refresh(saved).catch(setError);
              }}
            >
              다시 조회
            </button>
          )}
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
            <p key={row.id}>
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
