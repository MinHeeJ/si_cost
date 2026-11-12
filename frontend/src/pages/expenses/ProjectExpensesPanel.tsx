import { useCallback, useEffect, useRef, useState } from "react";
import { DataTable, type Column } from "../../shared/DataTable";
import { Dialog } from "../../shared/Dialog";
import { ErrorBanner } from "../../shared/Feedback";
import { ApiError } from "../../shared/api";
import { formatDecimal, validMonth } from "../../shared/format";
import { useCommitRefresh } from "../../shared/useCommitRefresh";
import {
  getMonthClose,
  getPeriodContext,
  getSharedCodes,
  type PeriodContext,
  type MonthClose,
  type SharedCodeLookup,
} from "../shared/sharedApi";
import * as api from "./projectExpensesApi";
import "./expenses.css";

export type ProjectExpensesPanelProps = {
  projectId: string;
  year: number;
  contractType: string;
  effectiveStatus: string;
  amountUnit?: "원" | "천원" | "백만원";
  onChanged?(result: api.ExpenseResult): Promise<void> | void;
  onDirtyChange?(dirty: boolean): void;
};
export function ProjectExpensesPanel(props: ProjectExpensesPanelProps) {
  if (props.contractType === "IN") return null;
  return <ExpensesContent key={props.projectId} {...props} />;
}
function ExpensesContent({
  projectId,
  year,
  effectiveStatus,
  amountUnit = "천원",
  onChanged,
  onDirtyChange,
}: ProjectExpensesPanelProps) {
  const [data, setData] = useState<api.ProjectExpensesData>();
  const [period, setPeriod] = useState<PeriodContext>();
  const [codes, setCodes] = useState<SharedCodeLookup[]>([]);
  const [closes, setCloses] = useState<MonthClose[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>();
  const [actionError, setActionError] = useState<unknown>();
  const [fields, setFields] = useState<Record<string, string>>({});
  const [adding, setAdding] = useState(false);
  const [deleting, setDeleting] = useState<api.ProjectExpense>();
  const [form, setForm] = useState({
    expenseMonth: "",
    itemCode: "",
    amount: "",
    memo: "",
  });
  const [monthBlocked, setMonthBlocked] = useState(false);
  const [checkingMonth, setCheckingMonth] = useState(false);
  const [monthError, setMonthError] = useState<unknown>();
  const [leaving, setLeaving] = useState(false);
  const [stale, setStale] = useState(false);
  const [refreshResult, setRefreshResult] = useState<api.ExpenseResult>();
  const generation = useRef(0);
  const { pending, message, commit } = useCommitRefresh();
  const locked =
    effectiveStatus === "LOSE" ||
    !period ||
    year < Number(period.currentMonth.slice(0, 4));
  const dirty =
    adding &&
    Boolean(
      form.itemCode ||
        form.amount ||
        form.memo ||
        (period && form.expenseMonth !== period.currentMonth),
    );
  const load = useCallback(async () => {
    const version = ++generation.current;
    setLoading(true);
    setError(undefined);
    try {
      const [value, context, choices] = await Promise.all([
        api.getProjectExpenses(projectId),
        getPeriodContext(),
        getSharedCodes("DRCT_EXPS_ITEM"),
      ]);
      const months = [
        context.currentMonth,
        ...value.items.map((row) => row.expenseMonth),
      ].sort();
      const closed = await getMonthClose(months[0], months[months.length - 1]);
      if (version !== generation.current) return;
      setData(value);
      setPeriod(context);
      setCodes(choices.items);
      setCloses(closed.items);
      setStale(false);
    } catch (e) {
      if (version === generation.current) setError(e);
      throw e;
    } finally {
      if (version === generation.current) setLoading(false);
    }
  }, [projectId]);
  useEffect(() => {
    void load().catch(() => {});
    return () => {
      generation.current++;
    };
  }, [load]);
  useEffect(() => {
    onDirtyChange?.(dirty);
    return () => onDirtyChange?.(false);
  }, [dirty, onDirtyChange]);
  useEffect(() => {
    const prevent = (event: BeforeUnloadEvent) => {
      if (dirty) {
        event.preventDefault();
        event.returnValue = "";
      }
    };
    window.addEventListener("beforeunload", prevent);
    return () => window.removeEventListener("beforeunload", prevent);
  }, [dirty]);
  useEffect(() => {
    if (!adding || !validMonth(form.expenseMonth)) return;
    let current = true;
    setCheckingMonth(true);
    setMonthError(undefined);
    void getMonthClose(form.expenseMonth, form.expenseMonth)
      .then((result) => {
        if (current)
          setMonthBlocked(
            result.items.length !== 1 || result.items[0].status !== "작업중",
          );
      })
      .catch((e) => {
        if (current) {
          setMonthError(e);
          setMonthBlocked(true);
        }
      })
      .finally(() => {
        if (current) setCheckingMonth(false);
      });
    return () => {
      current = false;
    };
  }, [adding, form.expenseMonth]);
  const protectedMonth = (month: string) =>
    !period ||
    month < period.cashBaseMonth ||
    !closes.some((row) => row.month === month && row.status === "작업중");
  function change(field: keyof typeof form, value: string) {
    setForm((previous) => ({ ...previous, [field]: value }));
    setFields((previous) => ({ ...previous, [field]: "" }));
  }
  function closeAdd() {
    if (pending) return;
    if (dirty) setLeaving(true);
    else setAdding(false);
  }
  async function refresh(result: api.ExpenseResult) {
    setRefreshResult(result);
    await load();
    await onChanged?.(result);
    setRefreshResult(undefined);
  }
  async function mutate(save: () => Promise<api.ExpenseResult>) {
    setActionError(undefined);
    setFields({});
    try {
      let saved: api.ExpenseResult | undefined;
      await commit(
        async () => {
          saved = await save();
          setData(saved.value);
          setAdding(false);
          setDeleting(undefined);
          setForm({ expenseMonth: "", itemCode: "", amount: "", memo: "" });
          onDirtyChange?.(false);
        },
        async () => {
          if (saved) await refresh(saved);
        },
      );
    } catch (e) {
      setActionError(e);
      if (e instanceof ApiError) {
        setFields(e.fieldMessages);
        if (e.body.code === "STALE_WRITE") setStale(true);
      }
    }
  }
  async function submit() {
    if (
      locked ||
      pending ||
      loading ||
      stale ||
      !data ||
      checkingMonth ||
      monthError
    )
      return;
    const validation: Record<string, string> = {};
    const amount = form.amount.replace(/[\s\p{Z},]/gu, "").replace(/원$/, "");
    if (
      !/^[0-9]+$/.test(amount) ||
      BigInt(amount) < 1n ||
      BigInt(amount) > 999999999999999n
    )
      validation.amount =
        "금액은 1~999,999,999,999,999원의 정수로 입력해 주세요.";
    if (!validMonth(form.expenseMonth))
      validation.expenseMonth = "실제 연월을 YYYY-MM 형식으로 입력해 주세요.";
    else if (
      monthBlocked ||
      (period && form.expenseMonth < period.cashBaseMonth)
    )
      validation.expenseMonth = "마감·이관 월은 등록할 수 없습니다.";
    if (!codes.some((code) => code.code === form.itemCode))
      validation.itemCode = "사용 중인 항목을 선택해 주세요.";
    if ([...form.memo.trim()].length > 500)
      validation.memo = "메모는 500자 이내로 입력해 주세요.";
    setFields(validation);
    if (Object.keys(validation).length) return;
    await mutate(() =>
      api.createProjectExpense(projectId, {
        ...form,
        memo: form.memo.trim() || null,
        amount: BigInt(amount).toString(),
        year,
        expectedRevision: data.revision,
      }),
    );
  }
  function displayAmount(amount: string) {
    const digits = amountUnit === "원" ? 0 : amountUnit === "천원" ? 3 : 6;
    const value = amount.padStart(digits + 1, "0");
    return formatDecimal(
      digits ? `${value.slice(0, -digits)}.${value.slice(-digits)}` : value,
    );
  }
  const columns: Column<api.ProjectExpense>[] = [
    { id: "month", label: "월", render: (row) => row.expenseMonth },
    { id: "item", label: "항목", render: (row) => row.itemName },
    {
      id: "amount",
      label: `금액 (${amountUnit})`,
      align: "right",
      render: (row) => displayAmount(row.amount),
    },
    {
      id: "memo",
      label: "메모",
      render: (row) => (
        <span title={row.memo ?? undefined}>{row.memo ?? "–"}</span>
      ),
    },
    {
      id: "delete",
      label: "삭제",
      align: "center",
      render: (row) =>
        !locked && (
          <button
            type="button"
            data-testid={`expense-delete-${row.expenseMonth}-${row.expenseSn}`}
            disabled={
              pending || stale || loading || protectedMonth(row.expenseMonth)
            }
            onClick={() => {
              setActionError(undefined);
              setDeleting(row);
            }}
          >
            삭제
          </button>
        ),
    },
  ];
  const feedback = (e: unknown, retry?: () => void) =>
    e ? (
      <ErrorBanner
        message={e instanceof Error ? e.message : "요청을 처리하지 못했습니다."}
        requestId={e instanceof ApiError ? e.body.requestId : undefined}
        onRetry={retry}
        pending={pending}
      />
    ) : null;
  return (
    <section
      className="project-expenses"
      data-screen-id="SCR-UI-01"
      data-testid="project-expenses-panel"
    >
      <header className="project-expenses-heading">
        <h2>추가 직접 경비</h2>
        <button
          type="button"
          data-testid="pj-exps-open"
          disabled={locked || loading || pending || stale || Boolean(error)}
          onClick={() => {
            setForm({
              expenseMonth: period?.currentMonth ?? "",
              itemCode: "",
              amount: "",
              memo: "",
            });
            setFields({});
            setActionError(undefined);
            setAdding(true);
          }}
        >
          경비 추가
        </button>
      </header>
      <p className="project-expenses-help">
        월 반복 팀비/경비와 별개인 일회성 직접 경비입니다. 미마감·비이관 월만
        등록·삭제할 수 있습니다.
      </p>
      {locked && period && (
        <p className="sicost-banner sicost-tone-lock">
          실주 또는 지난 연도는 조회만 할 수 있습니다.
        </p>
      )}
      {feedback(error, () => {
        void load().catch(() => {});
      })}
      {!adding && !deleting && feedback(actionError)}
      {stale && (
        <div className="sicost-banner sicost-tone-warn">
          다른 사용자가 변경했습니다. 다시 조회한 뒤 확인해 주세요. 자동으로
          재저장하지 않습니다.
          <button
            type="button"
            data-testid="expenses-stale-refresh"
            onClick={() => {
              void load().catch(() => {});
            }}
          >
            다시 조회
          </button>
        </div>
      )}
      {message && <p role="status">{message}</p>}
      {refreshResult && (
        <button
          type="button"
          data-testid="expenses-committed-refresh"
          disabled={pending}
          onClick={() => {
            void refresh(refreshResult).catch(() => {});
          }}
        >
          다시 조회
        </button>
      )}
      <DataTable
        rows={data?.items ?? []}
        columns={columns}
        rowKey={(row) => `${row.expenseMonth}-${row.expenseSn}`}
        loading={loading}
        empty="등록된 직접 경비가 없습니다."
      />
      {data && (
        <p className="project-expenses-total">
          합계 {displayAmount(data.totalAmount)} {amountUnit}
        </p>
      )}
      <Dialog
        open={adding}
        title="직접 경비 추가"
        pending={pending}
        onCancel={closeAdd}
        onConfirm={submit}
        confirmLabel="저장"
        confirmDisabled={
          locked || loading || stale || checkingMonth || Boolean(monthError)
        }
      >
        <div
          className="project-expenses-form"
          data-testid="expense-create-dialog"
        >
          {feedback(actionError)}
          {feedback(monthError)}
          <div className="sicost-field">
            <label htmlFor="expense-month">경비 월 *</label>
            <input
              id="expense-month"
              data-testid="expense-month"
              type="month"
              value={form.expenseMonth}
              disabled={pending}
              onChange={(e) => change("expenseMonth", e.target.value)}
              aria-invalid={Boolean(fields.expenseMonth)}
            />
            {fields.expenseMonth && (
              <span role="alert" className="sicost-field-error">
                {fields.expenseMonth}
              </span>
            )}
          </div>
          <div className="sicost-field">
            <label htmlFor="expense-item">항목 *</label>
            <select
              id="expense-item"
              data-testid="expense-item"
              value={form.itemCode}
              disabled={pending}
              onChange={(e) => change("itemCode", e.target.value)}
              aria-invalid={Boolean(fields.itemCode)}
            >
              <option value="">선택해 주세요</option>
              {codes.map((code) => (
                <option
                  data-testid={`expense-item-option-${code.code.toLowerCase().replace(/[^a-z0-9]+/g, "-")}`}
                  key={code.code}
                  value={code.code}
                >
                  {code.name}
                </option>
              ))}
            </select>
            {fields.itemCode && (
              <span role="alert" className="sicost-field-error">
                {fields.itemCode}
              </span>
            )}
          </div>
          <div className="sicost-field">
            <label htmlFor="expense-amount">금액 (원) *</label>
            <input
              id="expense-amount"
              data-testid="expense-amount"
              inputMode="numeric"
              value={form.amount}
              disabled={pending}
              onChange={(e) => change("amount", e.target.value)}
              aria-invalid={Boolean(fields.amount)}
            />
            {fields.amount && (
              <span role="alert" className="sicost-field-error">
                {fields.amount}
              </span>
            )}
          </div>
          <div className="sicost-field">
            <label htmlFor="expense-memo">메모</label>
            <textarea
              id="expense-memo"
              data-testid="expense-memo"
              value={form.memo}
              disabled={pending}
              onChange={(e) => change("memo", e.target.value)}
              aria-invalid={Boolean(fields.memo)}
            />
            {fields.memo && (
              <span role="alert" className="sicost-field-error">
                {fields.memo}
              </span>
            )}
          </div>
          {monthBlocked && !checkingMonth && (
            <p className="sicost-banner sicost-tone-lock">
              마감·이관 월은 등록할 수 없습니다.
            </p>
          )}
          <p className="project-expenses-help">
            계약 종료 뒤 발생한 일회성 경비도 등록할 수 있습니다. 사람·받는
            곳·부가세 입력이나 지급·청구는 없습니다.
          </p>
        </div>
      </Dialog>
      <Dialog
        open={Boolean(deleting)}
        title="직접 경비 삭제 확인"
        pending={pending}
        confirmLabel="삭제 확인"
        onCancel={() => {
          if (!pending) setDeleting(undefined);
        }}
        confirmDisabled={
          locked || stale || !deleting || protectedMonth(deleting.expenseMonth)
        }
        onConfirm={async () => {
          if (deleting && data && !locked && !stale)
            await mutate(() =>
              api.deleteProjectExpense(projectId, deleting, {
                year,
                expectedRevision: data.revision,
              }),
            );
        }}
      >
        <div data-testid="expense-delete-dialog">
          {feedback(actionError)}
          <p>
            {projectId} / {deleting?.expenseMonth} / {deleting?.expenseSn}
          </p>
          <p>
            {deleting?.itemName} · {formatDecimal(deleting?.amount)} 원 ·{" "}
            {deleting?.memo ?? "–"}
          </p>
          <p>
            이 직접 경비를 삭제합니다. 미마감 행만 삭제 가능하며 과거 감사는
            유지됩니다.
          </p>
        </div>
      </Dialog>
      <Dialog
        open={leaving}
        title="저장하지 않은 내용이 있습니다."
        confirmLabel="나가기"
        cancelLabel="계속 작성"
        onCancel={() => setLeaving(false)}
        onConfirm={() => {
          setLeaving(false);
          setAdding(false);
        }}
      >
        <p>입력한 직접 경비를 저장하지 않고 나갑니다.</p>
      </Dialog>
    </section>
  );
}
