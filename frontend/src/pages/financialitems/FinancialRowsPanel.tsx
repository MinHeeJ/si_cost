import { DataTable, type Column } from "../../shared/DataTable";
import { StatusBadge } from "../../shared/Feedback";
import { WonInput } from "./WonInput";
import type { Collection, DraftRow } from "./financialApi";

type Field = {
  key: string;
  label: string;
  kind?: "amount" | "signed" | "positive" | "date" | "repeat";
  options?: [string, string][];
  max?: number;
};
const fields: Record<Collection, Field[]> = {
  items: [
    {
      key: "finClass",
      label: "구분",
      options: [
        ["SGA", "판관비"],
        ["NOPR_IN", "영업외수익"],
        ["NOPR_OUT", "영업외비용"],
      ],
    },
    { key: "itemName", label: "세부 항목 *", max: 50 },
    { key: "amount", label: "금액 *", kind: "amount" },
    { key: "vatAmount", label: "부가세", kind: "amount" },
    { key: "ioDate", label: "입출금일", kind: "date" },
    { key: "repeat", label: "매달 반복", kind: "repeat" },
    { key: "remark", label: "비고", max: 500 },
  ],
  adjustments: [
    { key: "targetMonth", label: "대상 달 *" },
    {
      key: "itemCode",
      label: "조정 항목 *",
      options: [
        ["SALES", "매출"],
        ["COST", "원가"],
        ["SGA", "판관비"],
        ["ETC", "기타"],
      ],
    },
    { key: "amount", label: "금액(이익 기준 ±) *", kind: "signed" },
    { key: "reason", label: "사유 *", max: 300 },
  ],
  transfers: [
    { key: "date", label: "날짜 *", kind: "date" },
    {
      key: "classCode",
      label: "구분 *",
      options: [
        ["EXP", "계좌이체(제경비)"],
        ["SPCL", "특별비(미지급 인건비)"],
      ],
    },
    { key: "itemName", label: "내용 *", max: 100 },
    { key: "receiver", label: "받는 곳", max: 100 },
    { key: "amount", label: "금액 *", kind: "positive" },
    { key: "remark", label: "메모", max: 200 },
  ],
  loans: [
    { key: "date", label: "날짜 *", kind: "date" },
    {
      key: "direction",
      label: "구분 *",
      options: [
        ["IN", "받음"],
        ["OUT", "갚음"],
      ],
    },
    { key: "amount", label: "금액 *", kind: "positive" },
    { key: "counterparty", label: "상대", max: 100 },
    { key: "remark", label: "메모", max: 200 },
  ],
};
export type FinancialRowsPanelProps = {
  collection: Collection;
  rows: DraftRow[];
  originals: DraftRow[];
  readOnly: boolean;
  pending: boolean;
  month: string;
  targets: string[];
  codes?: { code: string; name: string }[];
  violations?: {
    collection: string;
    rowId: string;
    field: string;
    message: string;
  }[];
  onChange(key: string, field: string, value: string | null): void;
  onDelete(key: string): void;
  onAdd(): void;
};
export function FinancialRowsPanel({
  collection,
  rows,
  originals,
  readOnly,
  pending,
  month,
  targets,
  codes = [],
  violations = [],
  onChange,
  onDelete,
  onAdd,
}: FinancialRowsPanelProps) {
  const title = {
    items: "판관비·영업외 항목",
    adjustments: "전월 조정",
    transfers: "계좌이체 지출",
    loans: "대여금 입출금",
  }[collection];
  const columns: Column<DraftRow>[] = fields[collection].map((field) => ({
    id: field.key,
    label: field.label,
    render: (row) => {
      const released =
        collection === "adjustments" &&
        Boolean(row.id) &&
        !targets.includes(
          originals.find((old) => old.id === row.id)?.values.targetMonth ?? "",
        );
      const disabled = readOnly || pending || released;
      const testId = `financial-${collection}-${row.key}-${field.key.replace(/[A-Z]/g, (letter) => `-${letter.toLowerCase()}`)}`;
      const input = {
        "aria-label": `${title} ${row.key} ${field.label}`,
        "data-testid": testId,
        disabled,
        value: row.values[field.key] ?? "",
      };
      const set = (value: string | null) => onChange(row.key, field.key, value);
      const errors = [
        ...(row.errors ?? []).filter((error) => error.field === field.key),
        ...violations.filter(
          (v) =>
            v.collection === collection &&
            (v.rowId === row.id || v.rowId === row.key) &&
            v.field === field.key,
        ),
      ];
      let control;
      if (field.kind === "repeat")
        control = (
          <input
            {...input}
            type="checkbox"
            value={undefined}
            checked={row.values.repeat === "Y"}
            onChange={(event) => set(event.target.checked ? "Y" : "N")}
          />
        );
      else if (["amount", "positive", "signed"].includes(field.kind ?? ""))
        control = (
          <WonInput
            {...input}
            className={
              collection === "loans" && row.values.direction === "OUT"
                ? "financial-negative"
                : undefined
            }
            signed={field.kind === "signed"}
            positive={field.kind === "positive"}
            onValueChange={set}
          />
        );
      else if (field.options || field.key === "targetMonth") {
        const options =
          field.options ??
          targets.map((value) => [value, value] as [string, string]);
        control = (
          <select {...input} onChange={(event) => set(event.target.value)}>
            {!row.values[field.key] && <option value="">선택</option>}
            {field.key === "targetMonth" &&
              row.values.targetMonth &&
              !targets.includes(row.values.targetMonth) && (
                <option value={row.values.targetMonth}>
                  {row.values.targetMonth} · 해제됨
                </option>
              )}
            {options.map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        );
      } else
        control = (
          <input
            {...input}
            type={field.kind === "date" ? "date" : "text"}
            maxLength={field.max}
            min={field.key === "date" ? `${month}-01` : undefined}
            list={
              collection === "items" && field.key === "itemName"
                ? "financial-sga-codes"
                : undefined
            }
            onChange={(event) => {
              set(event.target.value || null);
              if (collection === "items" && field.key === "itemName")
                onChange(
                  row.key,
                  "sgaItemCode",
                  codes.find((code) => code.name === event.target.value)
                    ?.code ?? null,
                );
            }}
          />
        );
      return (
        <>
          {control}
          {field.key === "itemName" && row.automatic && (
            <StatusBadge tone="info">자동</StatusBadge>
          )}
          {errors.map((error, index) => (
            <small key={index} role="alert" className="financial-negative">
              {error.message}
            </small>
          ))}
        </>
      );
    },
  }));
  columns.push({
    id: "delete",
    label: "삭제",
    render: (row) => (
      <button
        type="button"
        data-testid={`financial-${collection}-${row.key}-delete`}
        disabled={readOnly || pending}
        aria-label={`${title} ${row.key} 삭제`}
        onClick={() => onDelete(row.key)}
      >
        ×
      </button>
    ),
  });
  return (
    <section
      className="financial-section"
      data-testid={`financial-${collection}-panel`}
      id={`financial-${collection}-section`}
    >
      <div className="sicost-list-toolbar">
        <h2>{title}</h2>
        <span>
          {month} · {rows.length}건
        </span>
        <button
          type="button"
          data-testid={`financial-${collection}-add`}
          disabled={
            readOnly ||
            pending ||
            (collection === "adjustments" && !targets.length)
          }
          onClick={onAdd}
        >
          {collection === "items"
            ? "행 추가"
            : collection === "adjustments"
              ? "조정 추가"
              : collection === "transfers"
                ? "이체 추가"
                : "대여금 추가"}
        </button>
      </div>
      {collection === "adjustments" && !targets.length && (
        <p>
          최근 12개월 실제 마감월이 없습니다. 기존 해제 대상은 삭제만 할 수
          있습니다.
        </p>
      )}
      <DataTable
        dense
        rows={rows}
        columns={[
          {
            id: "draft",
            label: "",
            render: (row) =>
              !row.id ||
              JSON.stringify(
                originals.find((old) => old.id === row.id)?.values,
              ) !== JSON.stringify(row.values) ? (
                <span data-testid={`financial-${collection}-${row.key}-draft`}>
                  <StatusBadge tone="warn">저장 전</StatusBadge>
                </span>
              ) : null,
          },
          ...columns,
        ]}
        rowKey={(row) => row.key}
        empty="등록된 항목이 없습니다."
      />
      {collection === "items" && (
        <datalist id="financial-sga-codes">
          {codes.map((code) => (
            <option key={code.code} value={code.name} />
          ))}
        </datalist>
      )}
    </section>
  );
}
