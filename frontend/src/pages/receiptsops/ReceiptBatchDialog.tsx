import { useRef, useState } from "react";
import { ApiError } from "../../shared/api";
import { DataTable, type Column } from "../../shared/DataTable";
import { ConfirmDialog, Dialog } from "../../shared/Dialog";
import { ErrorBanner } from "../../shared/Feedback";
import { Text } from "../../shared/Text";
import { formatDecimal } from "../../shared/format";
import {
  createBatchReceipts,
  receiptBatchCommittedEvent,
  type ReceiptBatchSelection,
  type ReceiptBatchResult,
} from "./receiptBatchApi";
import "./receiptBatch.css";

type Target = ReceiptBatchSelection["items"][number];
export type ReceiptBatchDialogProps = {
  year: number;
  today: string;
  minimumDate: string;
  selection: ReceiptBatchSelection;
  projectNames?: Record<string, string>;
  onClose(): void;
  onReload(): void | Promise<void>;
  onRefresh(result: ReceiptBatchResult): Promise<void>;
};
export function receiptBatchWon(value: string): string | null {
  const normalized = value.replace(/[\s\p{Z},]/gu, "").replace(/원$/, "");
  if (!/^\d+$/.test(normalized)) return null;
  const amount = BigInt(normalized);
  return amount >= 1n && amount <= 999999999999999n ? amount.toString() : null;
}
export function ReceiptBatchDialog({
  year,
  today,
  minimumDate,
  selection,
  projectNames = {},
  onClose,
  onReload,
  onRefresh,
}: ReceiptBatchDialogProps) {
  const [targets] = useState(() => selection.items.map((row) => ({ ...row })));
  const [date, setDate] = useState(today);
  const [selected, setSelected] = useState(() =>
    targets.map((row) => row.billId),
  );
  const [amounts, setAmounts] = useState<Record<string, string>>(() =>
    Object.fromEntries(targets.map((row) => [row.billId, row.unpaidAmount])),
  );
  const [fields, setFields] = useState<Record<string, string>>({});
  const [pending, setPending] = useState(false),
    [error, setError] = useState<unknown>();
  const [saved, setSaved] = useState<ReceiptBatchResult>(),
    [refreshFailed, setRefreshFailed] = useState(false);
  const [cancelConfirm, setCancelConfirm] = useState(false),
    [dirty, setDirty] = useState(false),
    [mustReload, setMustReload] = useState(false);
  const active = useRef(false);
  const rows = targets.filter((row) => selected.includes(row.billId));
  const readOnly = year < Number(today.slice(0, 4));
  const normalized = rows.map((row) =>
    receiptBatchWon(amounts[row.billId] ?? ""),
  );
  const invalidRows = rows.some(
    (row, i) =>
      normalized[i] === null ||
      BigInt(normalized[i]!) > BigInt(row.unpaidAmount),
  );
  const total = normalized
    .reduce<bigint>((sum, value) => sum + BigInt(value ?? "0"), 0n)
    .toString();
  const dateInvalid =
    !/^\d{4}-\d{2}-\d{2}$/.test(date) ||
    date < minimumDate ||
    date > today ||
    date.slice(0, 4) < today.slice(0, 4);
  async function refresh(result: ReceiptBatchResult) {
    try {
      await onRefresh(result);
      setRefreshFailed(false);
    } catch {
      setRefreshFailed(true);
    }
  }
  async function submit() {
    if (
      active.current ||
      saved ||
      readOnly ||
      mustReload ||
      !selection.canSave ||
      !rows.length ||
      invalidRows ||
      dateInvalid
    )
      return;
    active.current = true;
    setPending(true);
    setError(undefined);
    setFields({});
    const payload = rows.map((row, i) => ({
      billId: row.billId,
      amount: normalized[i]!,
      expectedRevision: row.expectedRevision,
    }));
    try {
      const result = await createBatchReceipts({ year, date, items: payload });
      setSaved(result);
      setDirty(false);
      window.dispatchEvent(new Event(receiptBatchCommittedEvent));
      await refresh(result);
    } catch (reason) {
      setError(reason);
      if (reason instanceof ApiError) {
        const messages = reason.fieldMessages,
          mapped: Record<string, string> = { date: messages.date ?? "" };
        payload.forEach((row, i) => {
          mapped[row.billId] =
            messages[`items[${i}].amount`] ??
            messages[`items.${i}.amount`] ??
            "";
        });
        setFields(mapped);
        if (reason.status === 409 || reason.status >= 500) setMustReload(true);
      } else setMustReload(true);
    } finally {
      active.current = false;
      setPending(false);
    }
  }
  function cancel() {
    if (!pending) {
      if (dirty && !saved) setCancelConfirm(true);
      else onClose();
    }
  }
  const columns: Column<Target>[] = [
    {
      id: "billId",
      label: "청구번호",
      render: (row) => (
        <span data-testid={`receipt-batch-row-${targets.indexOf(row)}`}>
          {row.billId}
        </span>
      ),
    },
    {
      id: "projectName",
      label: "프로젝트",
      render: (row) => projectNames[row.billId] ?? "–",
    },
    {
      id: "unpaidAmount",
      label: "미수금 (원)",
      align: "right",
      render: (row) => formatDecimal(row.unpaidAmount),
    },
    {
      id: "amount",
      label: "입금액(원)",
      align: "right",
      render: (row) => {
        const value = receiptBatchWon(amounts[row.billId] ?? "");
        const invalid =
          selected.includes(row.billId) &&
          (value === null || BigInt(value) > BigInt(row.unpaidAmount));
        return (
          <Text
            label={`${row.billId} 입금액`}
            data-testid={`receipt-batch-amount-${targets.indexOf(row)}`}
            value={amounts[row.billId] ?? ""}
            inputMode="numeric"
            required
            disabled={
              pending ||
              !!saved ||
              readOnly ||
              mustReload ||
              !selected.includes(row.billId)
            }
            error={
              fields[row.billId] ||
              (invalid
                ? "1원 이상 현재 미수금 이하로 입력해 주세요."
                : undefined)
            }
            onChange={(value) => {
              setAmounts((previous) => ({ ...previous, [row.billId]: value }));
              setDirty(true);
            }}
          />
        );
      },
    },
  ];
  return (
    <>
      <Dialog
        open
        wide
        title="여러 청구에 한 번에 입금"
        pending={pending}
        onCancel={cancel}
        onConfirm={submit}
        confirmLabel="등록"
        confirmDisabled={
          readOnly ||
          !!saved ||
          mustReload ||
          !selection.canSave ||
          !rows.length ||
          invalidRows ||
          dateInvalid
        }
      >
        <section
          className="receipt-batch-dialog"
          data-testid="receipt-batch-dialog"
        >
          <Text
            label="공통 입금일"
            data-testid="receipt-batch-date"
            type="date"
            required
            min={minimumDate}
            max={today}
            value={date}
            disabled={pending || !!saved || readOnly || mustReload}
            error={
              fields.date ||
              (dateInvalid
                ? "운영 시작월 이후, 올해의 오늘 이하 날짜를 입력해 주세요."
                : undefined)
            }
            onChange={(value) => {
              setDate(value);
              setDirty(true);
            }}
          />
          <p>
            대상 {rows.length}건 · 제외{" "}
            {selection.excludedCount + targets.length - rows.length}건
          </p>
          {selection.excluded.map((row, index) => (
            <p
              key={row.billId}
              data-testid={`receipt-batch-excluded-${index}`}
              className="sicost-banner sicost-tone-warn"
            >
              {row.billId} · {row.reason}
            </p>
          ))}
          {readOnly && (
            <p className="sicost-banner sicost-tone-lock">
              지난 연도는 조회만 할 수 있습니다.
            </p>
          )}
          <DataTable
            rows={targets}
            columns={columns}
            rowKey={(row) => row.billId}
            empty="입금할 수 있는 청구가 없습니다. 선택 조건을 확인해 주세요."
            selectedKeys={selected}
            onSelectionChange={
              pending || saved || readOnly || mustReload
                ? undefined
                : (keys) => {
                    setSelected(keys.map(String));
                    setDirty(true);
                  }
            }
          />
          <div className="receipt-batch-toolbar">
            <button
              type="button"
              data-testid="receipt-batch-select-all"
              disabled={pending || !!saved || readOnly || mustReload}
              onClick={() => {
                setSelected(targets.map((row) => row.billId));
                setDirty(true);
              }}
            >
              전체 선택
            </button>
            <span>선택을 해제하면 개별 제외됩니다.</span>
            <strong>전체 입금합 {formatDecimal(total)}원</strong>
          </div>
          <p className="sicost-banner sicost-tone-info">
            한 건이라도 오류가 있으면 전체 입금·잔액·감사가 저장되지 않습니다.
            자동 배분·이월·초과금 보관은 하지 않습니다.
          </p>
          {error != null && (
            <ErrorBanner
              message={
                error instanceof Error
                  ? error.message
                  : "입금을 저장하지 못했습니다."
              }
              requestId={
                error instanceof ApiError ? error.body.requestId : undefined
              }
            />
          )}
          {mustReload && (
            <p className="sicost-banner sicost-tone-warn">
              자동 재전송하지 않습니다. 다시 조회하여 원행과 잔액을 확인해
              주세요.
              <button
                type="button"
                data-testid="receipt-batch-reload"
                onClick={() => void onReload()}
              >
                재조회
              </button>
            </p>
          )}
          {saved && (
            <p role="status" className="sicost-banner sicost-tone-ok">
              입금 {saved.completedCount}건 등록 완료 · 전체{" "}
              {formatDecimal(saved.totalAmount)}원
            </p>
          )}
          {refreshFailed && (
            <p role="alert" className="sicost-banner sicost-tone-warn">
              저장됐지만 다시 불러오지 못했습니다.
              <button
                type="button"
                data-testid="receipt-batch-refresh"
                onClick={() => {
                  if (saved) void refresh(saved);
                }}
              >
                재조회
              </button>
            </p>
          )}
        </section>
      </Dialog>
      {cancelConfirm && (
        <ConfirmDialog
          open
          title="입금 입력 취소"
          onCancel={() => setCancelConfirm(false)}
          onConfirm={onClose}
          confirmLabel="입력 취소"
        >
          입력한 내용을 버리고 돌아가시겠습니까?
        </ConfirmDialog>
      )}
    </>
  );
}
