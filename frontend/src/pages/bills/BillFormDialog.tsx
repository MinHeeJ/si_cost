import { useEffect, useRef, useState } from "react";
import { DataTable } from "../../shared/DataTable";
import { Dialog } from "../../shared/Dialog";
import { ErrorBanner, FeedbackNotice, StatusBadge } from "../../shared/Feedback";
import { HelpTooltip } from "../../shared/HelpTooltip";
import { ApiError } from "../../shared/api";
import { formatDecimal, validMonth } from "../../shared/format";
import {
  getMonthClose,
  getSharedParameter,
  type PeriodContext,
} from "../shared/sharedApi";
import * as api from "./billsApi";

export type BillFormProps = {
  year: number;
  period: PeriodContext;
  bill?: api.Bill;
  initial?: api.CreateBill;
  onSaved(result: api.Mutation): Promise<void> | void;
  onClose(): void;
  onDirtyChange?(dirty: boolean): void;
};
export function BillFormDialog({
  year,
  period,
  bill,
  initial,
  onSaved,
  onClose,
  onDirtyChange,
}: BillFormProps) {
  const [form, setForm] = useState({
    projectId: bill?.projectId ?? initial?.projectId ?? "",
    billingMonth:
      bill?.billingMonth ?? initial?.billingMonth ?? period.currentMonth,
    supplyAmount: bill?.supplyAmount ?? initial?.supplyAmount ?? "",
    vatRate: bill?.vatRate ?? initial?.vatRate ?? "",
    billingDate:
      bill?.billingDate ?? initial?.billingDate ?? period.currentDate,
    dueDate: bill?.dueDate ?? initial?.dueDate ?? nextDue(period.currentMonth),
    taxInvoiceNo: bill?.taxInvoiceNo ?? initial?.taxInvoiceNo ?? "",
  });
  const [projects, setProjects] = useState<api.ProjectOption[]>([]),
    [estimate, setEstimate] = useState<api.Estimate>();
  const [loading, setLoading] = useState(false),
    [pending, setPending] = useState(false),
    [error, setError] = useState<unknown>(),
    [fields, setFields] = useState<Record<string, string>>({});
  const [dirty, setDirty] = useState(false),
    [leaving, setLeaving] = useState(false),
    [stale, setStale] = useState(false),
    [blocked, setBlocked] = useState(false),
    [defaultWarning, setDefaultWarning] = useState(""),
    [defaultError, setDefaultError] = useState<unknown>(),
    [dueDateReset, setDueDateReset] = useState(false);
  const [duplicate, setDuplicate] = useState<{
    existingBills: { id: string; order: number }[];
    duplicateToken: string;
  }>();
  const [replaceContext, setReplaceContext] = useState<{
    key: "projectId" | "billingMonth";
    value: string;
  }>();
  const [reload, setReload] = useState(0);
  const editedAmount = useRef(false),
    editedDue = useRef(false),
    inFlight = useRef(false);
  const closedMonthAdjustment = !bill && Boolean(initial?.closedMonthAdjustment);
  const fixed = Boolean(bill || initial?.copyContext || initial?.checkContext);
  useEffect(() => {
    onDirtyChange?.(dirty);
    return () => onDirtyChange?.(false);
  }, [dirty, onDirtyChange]);
  useEffect(() => {
    const prevent = (e: BeforeUnloadEvent) => {
      if (dirty) {
        e.preventDefault();
        e.returnValue = "";
      }
    };
    window.addEventListener("beforeunload", prevent);
    return () => window.removeEventListener("beforeunload", prevent);
  }, [dirty]);
  useEffect(() => {
    if (fixed) return;
    let live = true;
    void api
      .getProjectOptions()
      .then((v) => {
        if (live) setProjects(v.items);
      })
      .catch((e) => {
        if (live) setError(e);
      });
    return () => {
      live = false;
    };
  }, [fixed, reload]);
  useEffect(() => {
    let live = true;
    setBlocked(true);
    setLoading(true);
    void (async () => {
      try {
        if (!validMonth(form.billingMonth)) return;
        const months = new Set([form.billingMonth]);
        const type =
          bill?.contractType ??
          projects.find((p) => p.id === form.projectId)?.contractType;
        if (type === "TK") {
          if (bill) months.add(bill.billingDate.slice(0, 7));
          if (/^\d{4}-\d{2}-\d{2}$/.test(form.billingDate))
            months.add(form.billingDate.slice(0, 7));
        }
        const guards = await Promise.all(
          [...months].map(async (month) => {
            if (
              month < period.cashBaseMonth ||
              month.slice(0, 4) < period.currentMonth.slice(0, 4)
            )
              return false;
            const close = await getMonthClose(month, month);
            return close.items.length === 1 &&
              close.items[0].status === (closedMonthAdjustment ? "마감" : "작업중");
          }),
        );
        if (live) setBlocked(guards.some((v) => !v));
      } catch (e) {
        if (live) setError(e);
      } finally {
        if (live) setLoading(false);
      }
    })();
    return () => {
      live = false;
    };
  }, [
    form.billingMonth,
    form.billingDate,
    form.projectId,
    bill,
    period,
    projects,
    reload,
    closedMonthAdjustment,
  ]);
  useEffect(() => {
    if (bill || initial) return;
    let live = true;
    setEstimate(undefined);
    setDefaultWarning("");
    setDefaultError(undefined);
    void (async () => {
      try {
        if (!validMonth(form.billingMonth)) return;
        if (form.projectId) {
          const value = await api.getEstimate(
            form.projectId,
            form.billingMonth,
          );
          if (!live) return;
          setEstimate(value);
          setForm((old) => ({
            ...old,
            supplyAmount: value.supplyAmount ?? "",
            vatRate: value.vatRate ?? "",
            dueDate: value.defaultDueDate,
          }));
        } else {
          const value = await getSharedParameter("VAT_RT", form.billingMonth);
          if (!live) return;
          const normalized = value.value.includes(".")
            ? value.value.replace(/0+$/, "").replace(/\.$/, "")
            : value.value;
          const valid =
            /^\d+(?:\.\d{1,2})?$/.test(normalized) && validRate(normalized);
          setForm((old) => ({
            ...old,
            vatRate: valid ? normalized : "",
            dueDate: nextDue(form.billingMonth),
          }));
          if (!valid)
            setDefaultWarning(
              "부가세율 기본 설정을 확인해 주세요. 유효한 세율을 직접 입력할 수 있습니다.",
            );
        }
      } catch (e) {
        if (live) setDefaultError(e);
      }
    })();
    return () => {
      live = false;
    };
  }, [form.projectId, form.billingMonth, bill, initial, reload]);
  function change(key: keyof typeof form, value: string) {
    setDirty(true);
    setDuplicate(undefined);
    setFields((old) => ({ ...old, [key]: "" }));
    if (
      (key === "projectId" || key === "billingMonth") &&
      editedAmount.current
    ) {
      setReplaceContext({ key, value });
      return;
    }
    applyChange(key, value);
  }
  function applyChange(key: keyof typeof form, value: string) {
    if (key === "supplyAmount") editedAmount.current = true;
    if (key === "dueDate") {
      editedDue.current = true;
      setDueDateReset(false);
    }
    if (key === "billingMonth") {
      setDueDateReset(editedDue.current);
      editedDue.current = false;
      setForm((old) => ({
        ...old,
        billingMonth: value,
        dueDate: nextDue(value),
      }));
    } else setForm((old) => ({ ...old, [key]: value }));
  }
  async function save(confirmed = false) {
    if (pending || inFlight.current || blocked || stale) return;
    const validation: Record<string, string> = {};
    const supply = form.supplyAmount
      .replace(/[\s\p{Z},]/gu, "")
      .replace(/원$/, "");
    if (
      !/^\d+$/.test(supply) ||
      BigInt(supply || "0") < 1n ||
      BigInt(supply || "0") > 999999999999999n
    )
      validation.supplyAmount =
        "공급가는 1~999,999,999,999,999원의 정수로 입력해 주세요.";
    const vat = form.vatRate.replace(/[\s\p{Z},]/gu, "").replace(/%$/, "");
    if (!/^\d+(?:\.\d{1,2})?$/.test(vat) || !validRate(vat))
      validation.vatRate = "세율은 0~100, 소수 두 자리 이내로 입력해 주세요.";
    if (!form.projectId) validation.projectId = "프로젝트를 선택해 주세요.";
    if (!validMonth(form.billingMonth))
      validation.billingMonth = "실제 청구월을 입력해 주세요.";
    if (!validDate(form.billingDate))
      validation.billingDate = "실제 청구일을 입력해 주세요.";
    if (!validDate(form.dueDate) || form.dueDate < form.billingDate)
      validation.dueDate =
        "입금예정일은 청구일 이후로 입력해 주세요. 자동 보정하지 않습니다.";
    if ([...form.taxInvoiceNo.trim()].length > 30)
      validation.taxInvoiceNo = "세금계산서번호는 30자 이내입니다.";
    setFields(validation);
    if (Object.keys(validation).length) return;
    setPending(true);
    inFlight.current = true;
    setError(undefined);
    try {
      const editable: api.Editable = {
        supplyAmount: supply,
        vatRate: vat,
        billingDate: form.billingDate,
        dueDate: form.dueDate,
        taxInvoiceNo: form.taxInvoiceNo.trim() || null,
      };
      const saved = bill
        ? await api.putBill(bill.id, {
            ...editable,
            year,
            expectedRevision: bill.revision,
            ...(initial?.checkContext
              ? { checkContext: initial.checkContext }
              : {}),
          })
        : await api.createBill({
            ...editable,
            year,
            projectId: form.projectId,
            billingMonth: form.billingMonth,
            ...(initial?.checkContext
              ? { checkContext: initial.checkContext }
              : {}),
            ...(initial?.copyContext
              ? { copyContext: initial.copyContext }
              : {}),
            ...(closedMonthAdjustment ? { closedMonthAdjustment: true } : {}),
            ...(confirmed && duplicate
              ? {
                  confirmDuplicate: true,
                  duplicateToken: duplicate.duplicateToken,
                }
              : {}),
          });
      setDirty(false);
      onDirtyChange?.(false);
      window.dispatchEvent(
        new CustomEvent(api.BILLS_COMMITTED, { detail: saved }),
      );
      await onSaved(saved);
    } catch (e) {
      setError(e);
      if (e instanceof ApiError) {
        setFields(e.fieldMessages);
        if (e.body.code === "DUPLICATE_BILL_CONFIRMATION_REQUIRED") {
          const d = e.body.details as
            | {
                billing?: {
                  existingBills: { id: string; order: number }[];
                  duplicateToken: string;
                };
              }
            | undefined;
          setDuplicate(d?.billing);
        } else if (["STALE_WRITE", "BILL_CHECK_CHANGED"].includes(e.body.code))
          setStale(true);
      }
    } finally {
      inFlight.current = false;
      setPending(false);
    }
  }
  function close() {
    if (pending) return;
    if (dirty) setLeaving(true);
    else onClose();
  }
  const title = closedMonthAdjustment ? "추가 청구(차액)"
    : bill ? "청구 수정" : initial?.copyContext ? "고쳐서 청구" : "청구 등록";
  const warningMessages = [
    ...new Set(estimate?.warnings ?? (defaultWarning ? [defaultWarning] : [])),
  ];
  const visibleError = error ?? defaultError;
  const labels: Record<string, string> = {
    supplyAmount: "공급가 (원) *",
    vatRate: "부가세율 (%) *",
    billingDate: "청구일 *",
    dueDate: "입금 예정일 *",
    taxInvoiceNo: "세금계산서 번호",
  };
  return (
    <>
      <Dialog
        open
        title={title}
        className="bill-form-modal"
        titleHelp={
          <HelpTooltip label={title} iconOnly>
            <ul>
              <li>세금계산서 번호는 비워 두어도 저장할 수 있습니다.</li>
              <li>부가세와 합계는 저장 후 목록·상세에서 확인합니다.</li>
            </ul>
          </HelpTooltip>
        }
        pending={pending}
        onCancel={close}
        onConfirm={() => save()}
        confirmLabel={bill ? "저장" : "등록"}
        confirmDisabled={
          loading || blocked || stale || (!fixed && !projects.length)
        }
      >
        <section data-testid="bill-form-dialog" className="bill-form">
          {visibleError ? (
            <ErrorBanner
              title={error == null ? "청구 기본값 조회 실패" : undefined}
              message={
                visibleError instanceof Error
                  ? visibleError.message
                  : "요청을 처리하지 못했습니다."
              }
              requestId={
                visibleError instanceof ApiError ? visibleError.body.requestId : undefined
              }
            />
          ) : null}
          {stale && (
            <p role="alert">
              변경된 자료를 다시 조회한 뒤 창을 다시 열어 주세요. 자동
              재저장하지 않습니다.
            </p>
          )}
          {!loading && blocked && (
            <p className="sicost-banner sicost-tone-lock">
              {closedMonthAdjustment
                ? "마감 상태를 확인하지 못했습니다. 청구 누락 점검에서 다시 조회해 주세요."
                : "마감·이관·지난해 업무월은 변경할 수 없습니다."}
            </p>
          )}
          {fixed ? (
            <p>
              {bill?.id ?? "저장 시 채번"} ·{" "}
              {bill?.projectName ?? form.projectId} · {form.billingMonth} ·{" "}
              {bill ? `${bill.order}차` : "차수 자동 발급"}
            </p>
          ) : (
            <>
              <div className="sicost-field">
                <label htmlFor="bill-project">프로젝트 *</label>
                <select
                  id="bill-project"
                  data-testid="bill-project"
                  value={form.projectId}
                  disabled={pending}
                  onChange={(e) => change("projectId", e.target.value)}
                  aria-invalid={Boolean(fields.projectId)}
                >
                  <option value="">선택해 주세요</option>
                  {projects.map((p) => (
                    <option
                      key={p.id}
                      value={p.id}
                      data-testid={`bill-form-project-option-${p.id.toLowerCase().replace(/[^a-z0-9-]/g, "-")}`}
                    >
                      {p.name} · {p.customerName ?? "–"}
                    </option>
                  ))}
                </select>
                {fields.projectId && (
                  <span role="alert">{fields.projectId}</span>
                )}
              </div>
              {!projects.length && <p>신규 청구 가능한 프로젝트가 없습니다.</p>}
              <div className="sicost-field">
                <label htmlFor="bill-month">청구월 *</label>
                <input
                  id="bill-month"
                  data-testid="bill-month"
                  type="month"
                  value={form.billingMonth}
                  disabled={pending}
                  onChange={(e) => change("billingMonth", e.target.value)}
                  aria-invalid={Boolean(fields.billingMonth)}
                />
                {fields.billingMonth && (
                  <span role="alert">{fields.billingMonth}</span>
                )}
              </div>
            </>
          )}
          {dueDateReset && (
            <FeedbackNotice tone="info" title="입금 예정일 변경">
              청구월이 바뀌어 입금 예정일을 다음 달 25일로 설정했습니다.
            </FeedbackNotice>
          )}
          {warningMessages.length > 0 && (
            <FeedbackNotice tone="warn" title="청구 기본값 확인">
              {warningMessages.length === 1 ? warningMessages[0] : (
                <ul>
                  {warningMessages.map((warning) => (
                    <li key={warning}>{warning}</li>
                  ))}
                </ul>
              )}
            </FeedbackNotice>
          )}
          {(
            [
              "supplyAmount",
              "vatRate",
              "billingDate",
              "dueDate",
              "taxInvoiceNo",
            ] as const
          ).map((key) => (
            <div
              className="sicost-field"
              key={key}
              data-testid={`bill-form-field-${key.replace(/[A-Z]/g, (c) => `-${c.toLowerCase()}`)}`}
            >
              <label htmlFor={`bill-${key}`}>{labels[key]}</label>
              <input
                id={`bill-${key}`}
                data-testid={`bill-${key.replace(/[A-Z]/g, (c) => `-${c.toLowerCase()}`)}`}
                type={key.endsWith("Date") ? "date" : "text"}
                inputMode={
                  key === "supplyAmount"
                    ? "numeric"
                    : key === "vatRate"
                      ? "decimal"
                      : undefined
                }
                value={form[key]}
                readOnly={closedMonthAdjustment && key === "supplyAmount"}
                disabled={pending}
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
          {estimate && estimate.sources.length > 0 && (
            <details className="bill-estimate-sources" data-testid="bill-estimate-sources">
              <summary>공급가 산정 내역 ({estimate.sources.length}건)</summary>
              <DataTable
                rows={estimate.sources}
                rowKey={(source) => source.assignmentId}
                tableLabel="공급가 산정 내역"
                dense
                columns={[
                  {
                    id: "person",
                    label: "투입 인력",
                    render: (source) => (
                      <div data-testid={`bill-form-estimate-source-${source.assignmentId.toLowerCase().replace(/[^a-z0-9-]/g, "-")}`}>
                        <div>{source.developerName}</div>
                        <small>{source.managementNo}</small>
                        {source.protected && (
                          <div><StatusBadge tone="lock">보호월 자료</StatusBadge></div>
                        )}
                      </div>
                    ),
                  },
                  {
                    id: "unitPrice",
                    label: "단가 (원)",
                    align: "right",
                    render: (source) => formatDecimal(source.appliedUnitPrice),
                  },
                  {
                    id: "mm",
                    label: "MM",
                    align: "right",
                    render: (source) => formatDecimal(source.mm),
                  },
                  {
                    id: "amount",
                    label: "공급가 (원)",
                    align: "right",
                    render: (source) => formatDecimal(source.supplyAmount),
                  },
                ]}
              />
            </details>
          )}
        </section>
      </Dialog>
      <Dialog
        open={Boolean(duplicate)}
        title="같은 달 청구 확인"
        pending={pending}
        onCancel={() => setDuplicate(undefined)}
        onConfirm={() => save(true)}
        confirmLabel="등록"
      >
        <section data-testid="bill-duplicate-dialog">
          {duplicate?.existingBills.map((b) => (
            <p
              key={b.id}
              data-testid={`bill-form-duplicate-${b.id.toLowerCase().replace(/[^a-z0-9-]/g, "-")}`}
            >
              {b.id} · {b.order}차
            </p>
          ))}
          <p>같은 달 청구를 추가하려면 등록을 한 번 더 누르세요.</p>
        </section>
      </Dialog>
      <Dialog
        open={Boolean(replaceContext)}
        title="입력한 공급가를 변경하시겠습니까?"
        onCancel={() => setReplaceContext(undefined)}
        onConfirm={() => {
          if (replaceContext) {
            editedAmount.current = false;
            applyChange(replaceContext.key, replaceContext.value);
          }
          setReplaceContext(undefined);
        }}
        confirmLabel="기본값 다시 읽기"
      >
        <p>
          프로젝트·월을 바꾸면 편집한 공급가 대신 해당 월 원천의 기본값을 다시
          불러옵니다.
        </p>
      </Dialog>
      <Dialog
        open={leaving}
        title="작성 중인 내용을 나가시겠습니까?"
        confirmLabel="나가기"
        cancelLabel="계속 작성"
        onCancel={() => setLeaving(false)}
        onConfirm={onClose}
      >
        <p>자동 저장하지 않습니다.</p>
      </Dialog>
      {Boolean(error) && !pending && (
        <button
          data-testid="bill-defaults-retry"
          type="button"
          onClick={() => setReload((v) => v + 1)}
        >
          기본값 재조회
        </button>
      )}
    </>
  );
}
function validRate(value: string) {
  const [whole, fraction = ""] = value.split(".");
  return (
    BigInt(whole || "0") * 100n + BigInt(fraction.padEnd(2, "0") || "0") <=
    10000n
  );
}
function validDate(value: string) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value) || value.startsWith("0000"))
    return false;
  const date = new Date(`${value}T00:00:00Z`);
  return (
    !Number.isNaN(date.getTime()) && date.toISOString().slice(0, 10) === value
  );
}
export function nextDue(month: string) {
  if (!validMonth(month)) return "";
  const [year, m] = month.split("-").map(Number);
  return `${m === 12 ? year + 1 : year}-${String(m === 12 ? 1 : m + 1).padStart(2, "0")}-25`;
}
