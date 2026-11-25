import { useRef, useState, type Dispatch, type SetStateAction } from "react";
import { DataTable } from "../../shared/DataTable";
import { StatusBadge } from "../../shared/Feedback";
import { HelpTooltip } from "../../shared/HelpTooltip";
import type { DraftRow, FinancialEditor } from "./financialApi";
import type { FinancialDraft } from "./useFinancialEditor";
import { FinancialAmount, type FinancialUnit } from "./FinancialAmount";
import { WonInput } from "./WonInput";

type Props = {
  month: string;
  draft: FinancialDraft;
  cardMonth: FinancialEditor["cardMonth"];
  disabled: boolean;
  readOnly: boolean;
  unit: FinancialUnit;
  setDraft: Dispatch<SetStateAction<FinancialDraft | undefined>>;
  violations: { collection: string; rowId: string; field: string }[];
};
export function FinancialCardsPanel({
  month,
  draft,
  cardMonth,
  disabled,
  readOnly,
  unit,
  setDraft,
  violations,
}: Props) {
  const invalid = (rowId: string, field: string) =>
    violations.some(
      (v) => v.collection === "cards" && v.rowId === rowId && v.field === field,
    );
  const [showStopped, setShowStopped] = useState(false);
  const sequence = useRef(0);
  const focusNewCard = useRef<string | null>(null);
  const update = (key: string, field: string, value: string) =>
    setDraft((old) =>
      old
        ? {
            ...old,
            cards: old.cards.map((row) =>
              row.key === key
                ? { ...row, values: { ...row.values, [field]: value } }
                : row,
            ),
          }
        : old,
    );
  const add = () => {
    const key = `new-card-${++sequence.current}`;
    focusNewCard.current = key;
    setDraft((old) =>
      old
        ? {
            ...old,
            cards: [
              ...old.cards,
              {
                key,
                values: {
                  cardName: "",
                  last4: "",
                  useYn: "Y",
                  amount: "0",
                },
              },
            ],
          }
        : old,
    );
  };
  const amount = (value: string | null | undefined, plus = false) => (
    <FinancialAmount value={value} unit={unit} plus={plus} />
  );
  const manual =
    draft.cashOverride?.mode === "MANUAL" ||
    (!draft.cashOverride && cardMonth.editYn === "Y");
  const auto = (
    <button
      type="button"
      data-testid="financial-card-auto"
      disabled={disabled}
      onClick={() =>
        setDraft((old) =>
          old ? { ...old, cashOverride: { mode: "AUTO" } } : old,
        )
      }
    >
      자동 합계로
    </button>
  );
  const rows: DraftRow[] = draft.cards.filter(
    (row) =>
      !row.id ||
      showStopped ||
      row.values.useYn === "Y" ||
      (/^[0-9]+$/.test(row.values.amount ?? "") &&
        BigInt(row.values.amount!) > 0n),
  );
  rows.push(
    { key: "card-total", values: {} },
    { key: "cash-total", values: {} },
  );
  const special: Record<string, Record<string, React.ReactNode>> = {
    "card-total": {
      name: <strong>카드별 합계</strong>,
      last4: `${draft.cards.length}장`,
      amount: <strong>{amount(cardMonth.cardSumAmount)}</strong>,
    },
    "cash-total": {
      name: <strong>자금수지 반영 합계</strong>,
      last4: manual ? "직접 고침" : "자동",
      amount: (
        <WonInput
          data-testid="financial-card-cash-override"
          aria-label="자금수지 반영 합계(원)"
          disabled={disabled}
          aria-invalid={invalid("cash", "override") || undefined}
          value={
            draft.cashOverride?.mode === "MANUAL"
              ? (draft.cashOverride.amount ?? "")
              : cardMonth.cashAmount
          }
          onValueChange={(value) =>
            setDraft((old) =>
              old
                ? { ...old, cashOverride: { mode: "MANUAL", amount: value } }
                : old,
            )
          }
        />
      ),
      action: (
        <>
          {manual && cardMonth.difference !== "0" && (
            <small>
              카드별 합계와 {amount(cardMonth.difference, true)}원 차이
            </small>
          )}
          {auto}
        </>
      ),
    },
  };
  return (
    <section
      className="financial-section financial-cards-section"
      aria-labelledby="financial-cards-heading"
      data-testid="financial-cards-panel"
    >
      <header className="sicost-section-heading financial-section-heading">
        <div>
          <h2 id="financial-cards-heading">법인카드 대금</h2>
          <HelpTooltip label="법인카드 대금" iconOnly>
            법인카드 대금은 자금수지표의 제경비에 반영됩니다.
          </HelpTooltip>
          <small>사용 중인 카드 {draft.cards.filter((row) => row.values.useYn === "Y").length}장</small>
        </div>
        <div>
          <label className="financial-card-filter">
            <input
              type="checkbox"
              data-testid="financial-show-stopped"
              checked={showStopped}
              onChange={(event) => setShowStopped(event.target.checked)}
            />
            <span>삭제된 카드 보기</span>
          </label>
          {!readOnly && (
            <button
              type="button"
              data-testid="financial-card-add"
              disabled={disabled}
              onClick={add}
            >
              카드 추가
            </button>
          )}
        </div>
      </header>
      <DataTable
        testId="financial-cards-table"
        tableLabel="법인카드 대금"
        rows={rows}
        rowKey={(row) => row.key}
        columns={[
          {
            id: "name",
            label: "카드 이름 *",
            align: "center" as const,
            render: (row: DraftRow) => row.id ? row.values.cardName : (
              <>
                <input
                  ref={(input) => {
                    if (input && row.key === focusNewCard.current) {
                      focusNewCard.current = null;
                      input.focus();
                    }
                  }}
                  data-testid={`financial-card-${row.key}-card-name`}
                  aria-label="새 카드 이름"
                  aria-invalid={invalid(row.key, "cardName") || undefined}
                  placeholder="예: 영업 4팀 카드"
                  value={row.values.cardName ?? ""}
                  maxLength={100}
                  disabled={disabled}
                  onChange={(event) => update(row.key, "cardName", event.target.value)}
                />
                <span className="financial-row-draft">
                  <StatusBadge tone="info">새 카드</StatusBadge>{" "}
                  <StatusBadge tone="warn">저장 전</StatusBadge>
                </span>
              </>
            ),
          },
          {
            id: "last4",
            label: "끝 4자리 *",
            align: "center" as const,
            render: (row: DraftRow) => row.id ? row.values.last4 : (
              <input
                data-testid={`financial-card-${row.key}-last4`}
                aria-label="새 카드 끝 4자리"
                aria-invalid={invalid(row.key, "last4") || undefined}
                placeholder="숫자 4개"
                value={row.values.last4 ?? ""}
                maxLength={4}
                inputMode="numeric"
                disabled={disabled}
                onChange={(event) => update(row.key, "last4", event.target.value)}
              />
            ),
          },
          {
            id: "amount",
            label: `${Number(month.slice(5))}월 결제액`,
            align: "right" as const,
            render: (row: DraftRow) => (
              <WonInput
                data-testid={`financial-card-${row.key}-amount`}
                aria-label={`${row.key} 결제액`}
                value={row.values.amount ?? ""}
                aria-invalid={invalid(row.key, "amount") || undefined}
                disabled={disabled}
                onValueChange={(value) => update(row.key, "amount", value)}
              />
            ),
          },
          {
            id: "state",
            label: "상태",
            align: "center" as const,
            render: (row: DraftRow) => (
              <StatusBadge tone={row.values.useYn === "Y" ? "ok" : "lock"}>
                {row.values.useYn === "Y" ? "사용" : "삭제됨"}
              </StatusBadge>
            ),
          },
          {
            id: "action",
            label: "관리",
            align: "center" as const,
            render: (row: DraftRow) =>
              !readOnly && (
                <button
                  type="button"
                  className={
                    row.values.useYn === "Y"
                      ? "financial-card-action financial-card-delete"
                      : "financial-card-action"
                  }
                  data-testid={`financial-card-${row.key}-state`}
                  disabled={disabled}
                  onClick={() => {
                    if (!row.id)
                      setDraft((old) =>
                        old
                          ? {
                              ...old,
                              cards: old.cards.filter(
                                (card) => card.key !== row.key,
                              ),
                            }
                          : old,
                      );
                    else
                      update(
                        row.key,
                        "useYn",
                        row.values.useYn === "Y" ? "N" : "Y",
                      );
                  }}
                >
                  {row.values.useYn === "Y" ? "카드 삭제" : "되살리기"}
                </button>
              ),
          },
        ].map((column) => ({
          ...column,
          render: (row: DraftRow) =>
            special[row.key] ? special[row.key][column.id] : column.render(row),
        }))}
      />
    </section>
  );
}
