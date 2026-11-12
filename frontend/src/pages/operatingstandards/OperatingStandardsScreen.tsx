import { useEffect, useRef, useState } from "react";
import { DataTable, type Column } from "../../shared/DataTable";
import { Text } from "../../shared/Text";
import { ErrorBanner } from "../../shared/Feedback";
import { ApiError } from "../../shared/api";
import { Dialog } from "../../shared/Dialog";
import { formatDecimal } from "../../shared/format";
import { StandardHistoryDialog } from "./StandardHistoryDialog";
import { StandardValueDialog } from "./StandardValueDialog";
import * as api from "./operatingStandardsApi";
import type {
  OperatingStandard,
  StandardsPeriodContext,
  StandardsSave,
} from "./operatingStandardsApi";
import "./operatingstandards.css";

// SCR-UI-27: standards panel in CommonCodesScreen, not another route. Readonly metadata, nonpaged SQL keys,
// grouped value modal, readonly history, separate CASH update without effectiveMonth, CLOSE protection,
// loading/empty/error/success and failed-input preservation. Host supplies column/density preferences and dirty guard.
export type OperatingStandardsScreenProps = {
  dense?: boolean;
  columnView?: { order: string[]; hidden: string[] };
  onColumnsChange?(): void;
  onDensityChange?(): void;
  onDirtyChange?(dirty: boolean): void;
  onCommitted?(cash: boolean): void | Promise<void>;
};
export function OperatingStandardsScreen({
  dense = false,
  columnView,
  onColumnsChange,
  onDensityChange,
  onDirtyChange,
  onCommitted,
}: OperatingStandardsScreenProps) {
  const [rows, setRows] = useState<OperatingStandard[]>([]),
    [context, setContext] = useState<StandardsPeriodContext>();
  const [minimum, setMinimum] = useState(""),
    [cashLocked, setCashLocked] = useState(true),
    [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(),
    [notice, setNotice] = useState(""),
    [pending, setPending] = useState(false);
  const [editing, setEditing] = useState<OperatingStandard[]>(),
    [history, setHistory] = useState<OperatingStandard>();
  const [cashValues, setCashValues] = useState<Record<string, string>>({}),
    [cashConfirm, setCashConfirm] = useState(false),
    [fields, setFields] = useState<Record<string, string>>({});
  const saving = useRef(false);
  const cashRows = rows.filter((row) => api.isCashStandard(row.key));
  const cashDirty = cashRows.some(
    (row) =>
      cashValues[row.key] !== undefined && cashValues[row.key] !== row.value,
  );
  useEffect(() => {
    onDirtyChange?.(cashDirty);
  }, [cashDirty, onDirtyChange]);
  useEffect(() => () => onDirtyChange?.(false), [onDirtyChange]);
  async function load(preserveCash = false) {
    setLoading(true);
    setError(undefined);
    try {
      const [standards, period] = await Promise.all([
        api.getOperatingStandards(),
        api.getStandardsPeriodContext(),
      ]);
      const closes = await api.getStandardsMonthClose(
        period.cashBaseMonth,
        period.currentMonth > period.cashBaseMonth
          ? period.currentMonth
          : period.cashBaseMonth,
      );
      const closed = closes.items
        .filter((row) => row.status === "마감")
        .map((row) => row.month)
        .sort();
      const next = closed.length
        ? api.nextStandardMonth(closed[closed.length - 1])
        : period.cashBaseMonth;
      setMinimum(next > period.cashBaseMonth ? next : period.cashBaseMonth);
      setContext(period);
      setCashLocked(
        closes.items.some(
          (row) => row.month === period.cashBaseMonth && row.status === "마감",
        ),
      );
      setRows(standards.items);
      if (!preserveCash)
        setCashValues(
          Object.fromEntries(
            standards.items
              .filter((row) => api.isCashStandard(row.key))
              .map((row) => [row.key, row.value ?? ""]),
          ),
        );
    } catch (e) {
      setError(e);
      throw e;
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => {
    void load().catch(() => {});
  }, []);
  useEffect(() => {
    const leave = (event: BeforeUnloadEvent) => {
      if (cashDirty || editing) {
        event.preventDefault();
        event.returnValue = "";
      }
    };
    window.addEventListener("beforeunload", leave);
    return () => window.removeEventListener("beforeunload", leave);
  }, [cashDirty, editing]);
  async function save(body: StandardsSave, cash = false) {
    if (saving.current) return;
    if (!body.changes.length) {
      setNotice("바뀐 내용이 없습니다.");
      return;
    }
    saving.current = true;
    setPending(true);
    setNotice("");
    setError(undefined);
    setFields({});
    try {
      const result = await api.saveOperatingStandards(body);
      setRows(result.items);
      setCashConfirm(false);
      onDirtyChange?.(false);
      setNotice(
        result.message ??
          (result.changed ? "저장했습니다." : "바뀐 내용이 없습니다."),
      );
      try {
        if (result.changed) {
          api.notifyOperatingStandardsCommitted(cash);
          await onCommitted?.(cash);
        }
        await load();
      } catch {
        setError(undefined);
        setNotice("저장됐지만 다시 불러오지 못했습니다.");
      }
    } catch (e) {
      setError(e);
      if (e instanceof ApiError) {
        const next = { ...e.fieldMessages };
        body.changes.forEach((change, i) => {
          next[change.key] =
            next[`changes[${i}].value`] ?? next[`changes.${i}.value`] ?? "";
        });
        setFields(next);
      }
      throw e;
    } finally {
      saving.current = false;
      setPending(false);
    }
  }
  const cashRequest = (): StandardsSave => ({
    changes: cashRows
      .filter((row) => cashValues[row.key] !== row.value)
      .map((row) => ({
        key: row.key,
        value: cashValues[row.key] ?? "",
        expectedRevision: row.keyRevision,
      })),
  });
  function edit(row: OperatingStandard) {
    const group = api.operatingStandardGroups.find((keys) =>
      keys.includes(row.key),
    ) ?? [row.key];
    setEditing(
      group
        .map((key) => rows.find((item) => item.key === key))
        .filter((item): item is OperatingStandard => Boolean(item)),
    );
  }
  const columns: Column<OperatingStandard>[] = [
    { id: "key", label: "키", render: (row) => row.key },
    { id: "name", label: "기준값", render: (row) => row.name },
    {
      id: "value",
      label: "값",
      align: "right",
      render: (row) =>
        row.value === null
          ? "기준값 없음"
          : row.key === "CASH_BASE_YM"
            ? `${row.value.slice(0, 4)}-${row.value.slice(4)}`
            : formatDecimal(row.value),
    },
    { id: "unitNm", label: "단위", render: (row) => row.unitNm },
    {
      id: "effectiveMonth",
      label: "적용 시작월",
      render: (row) => row.effectiveMonth ?? "기준값 없음",
    },
    { id: "description", label: "쓰는 곳", render: (row) => row.description },
    {
      id: "actions",
      label: "작업",
      render: (row) => (
        <div
          data-testid={`operating-standard-actions-${row.key.toLowerCase().replace(/_/g, "-")}`}
          className="operatingstandards-actions"
        >
          {!api.isCashStandard(row.key) && (
            <button
              data-testid={`operating-standard-edit-${row.key.toLowerCase().replace(/_/g, "-")}`}
              disabled={pending || loading || Boolean(error)}
              onClick={() => edit(row)}
            >
              값 변경<span className="operatingstandards-sr"> {row.key}</span>
            </button>
          )}
          <button onClick={() => setHistory(row)}>
            이력<span className="operatingstandards-sr"> {row.key}</span>
          </button>
        </div>
      ),
    },
  ];
  const visible = columnView
    ? columns
        .filter(
          (column) =>
            !columnView.hidden.includes(column.id) ||
            ["key", "name", "actions"].includes(column.id),
        )
        .sort((a, b) => {
          const index = (id: string) =>
            id === "actions"
              ? Number.MAX_SAFE_INTEGER
              : columnView.order.indexOf(id);
          return index(a.id) - index(b.id);
        })
    : columns;
  return (
    <section className="operatingstandards-screen" data-screen-id="SCR-UI-27">
      <div className="operatingstandards-mobile">
        이 화면은 PC에서 사용해 주세요.
      </div>
      <div className="operatingstandards-desktop">
        <div className="operatingstandards-toolbar">
          <h2>운영 기준값</h2>
          <div>
            {onColumnsChange && (
              <button onClick={onColumnsChange}>열 설정</button>
            )}
            {onDensityChange && (
              <button onClick={onDensityChange}>목록 밀도</button>
            )}
          </div>
        </div>
        <p className="sicost-banner sicost-tone-info">
          이름·키·단위·설명은 읽기 전용입니다. 값 변경은 적용 시작월부터
          반영되며 다른 적용월은 보존됩니다.
        </p>
        {error != null && (
          <ErrorBanner
            message={
              error instanceof Error
                ? error.message
                : "기준값을 불러오지 못했습니다."
            }
            requestId={
              error instanceof ApiError ? error.body.requestId : undefined
            }
            onRetry={() => void load(true).catch(() => {})}
            pending={pending}
          />
        )}
        {notice && (
          <p role="status" className="sicost-banner sicost-tone-info">
            {notice}
            {notice.includes("다시 불러오지") && (
              <button onClick={() => void load().catch(() => {})}>
                다시 조회
              </button>
            )}
          </p>
        )}
        {(!error || rows.length > 0) && (
          <DataTable
            rows={rows}
            columns={visible}
            rowKey={(row) => row.key}
            loading={loading}
            dense={dense}
            empty="등록된 운영 기준값이 없습니다."
          />
        )}
        {!!cashRows.length && (
          <section className="operatingstandards-cash">
            <h2>자금수지 시작 기준</h2>
            {cashLocked && (
              <p className="sicost-banner sicost-tone-lock">
                기존 시작월이 마감되어 자금 기준을 변경할 수 없습니다.
              </p>
            )}
            <div className="operatingstandards-cash-fields">
              {cashRows.map((row) => (
                <Text
                  key={row.key}
                  label={
                    row.key === "CASH_BASE_YM"
                      ? "자금수지 시작 월"
                      : "기초 잔액"
                  }
                  type={row.key === "CASH_BASE_YM" ? "month" : "text"}
                  readOnly={cashLocked || pending || loading}
                  value={
                    row.key === "CASH_BASE_YM"
                      ? `${(cashValues[row.key] ?? "").slice(0, 4)}-${(cashValues[row.key] ?? "").slice(4)}`
                      : (cashValues[row.key] ?? "")
                  }
                  error={fields[row.key]}
                  onChange={(value) =>
                    setCashValues((previous) => ({
                      ...previous,
                      [row.key]:
                        row.key === "CASH_BASE_YM"
                          ? value.replace("-", "")
                          : value,
                    }))
                  }
                />
              ))}
              <button
                disabled={cashLocked || pending || loading || !cashDirty}
                onClick={() => setCashConfirm(true)}
              >
                자금 기준 저장
              </button>
            </div>
            <p>
              적용 시작월은 바꾸지 않습니다. 자금수지 계산과 마감 자료는
              변경하지 않습니다.
            </p>
          </section>
        )}
      </div>
      {editing && context && (
        <StandardValueDialog
          rows={editing}
          minimumMonth={minimum}
          defaultMonth={
            context.currentMonth > minimum ? context.currentMonth : minimum
          }
          onDirtyChange={onDirtyChange}
          onClose={() => {
            setEditing(undefined);
            onDirtyChange?.(cashDirty);
          }}
          onSave={(body) => save(body)}
        />
      )}
      {history && (
        <StandardHistoryDialog
          standard={history}
          onClose={() => setHistory(undefined)}
        />
      )}
      <Dialog
        open={cashConfirm}
        title="자금 기준 변경 확인"
        pending={pending}
        confirmLabel="확인 후 저장"
        onCancel={() => setCashConfirm(false)}
        onConfirm={() => save(cashRequest(), true)}
      >
        <span className="operatingstandards-cash-confirm" />
        {cashRequest().changes.map((change) => (
          <p key={change.key}>
            {change.key}: {rows.find((row) => row.key === change.key)?.value} →{" "}
            {change.value}
          </p>
        ))}
        <p>기존 시작월의 마감 상태를 다시 확인한 뒤 같은 행을 변경합니다.</p>
      </Dialog>
    </section>
  );
}
