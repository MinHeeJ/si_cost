import { useCallback, useEffect, useRef, useState } from "react";
import { createCorporateTaxDefaults } from "../corporatetaxesops/corporateTaxApi";
import { ApiError } from "../../shared/api";
import { normalizeFinancialWon } from "./WonInput";
import * as api from "./financialApi";

export type FinancialDraft = Record<api.Collection, api.DraftRow[]> & {
  cards: api.DraftRow[];
  estimatedTax: string | null;
  taxSchedules: {
    year: string;
    payType: string;
    dueDate: string;
    payAmount: string | null;
  }[];
  cashOverride?: { mode: "AUTO" | "MANUAL"; amount?: string };
};
function fromEditor(
  editor: api.FinancialEditor,
  automatic: boolean,
): FinancialDraft {
  return {
    items: [
      ...api.draftRows(editor.items, "items"),
      ...(automatic
        ? editor.automaticDrafts.map((row, index) =>
            api.copiedRow(row, `repeat-${index}`, "REPEAT"),
          )
        : []),
    ],
    adjustments: api.draftRows(editor.adjustments, "adjustments"),
    transfers: api.draftRows(editor.transfers, "transfers"),
    loans: api.draftRows(editor.loans, "loans"),
    cards: editor.cards.map((card) => ({
      key: card.cardId,
      id: card.cardId,
      values: {
        cardName: card.cardName,
        last4: card.last4,
        useYn: card.useYn,
        amount:
          editor.cardPayments.find((p) => p.cardId === card.cardId)?.amount ??
          "0",
      },
    })),
    estimatedTax: editor.corporateTax?.estimatedTax ?? null,
    taxSchedules: editor.taxPayments.map((p) => ({
      year: p.year,
      payType: p.payType,
      dueDate: p.dueDate,
      payAmount: p.payAmount,
    })),
  };
}
function normalized(row: api.DraftRow, collection: api.Collection): api.Values {
  const values = { ...row.values };
  values.amount = normalizeFinancialWon(
    values.amount ?? "",
    collection === "adjustments",
    collection === "loans" || collection === "transfers",
  );
  if (collection === "items")
    values.vatAmount = normalizeFinancialWon(values.vatAmount || "0");
  for (const field of api.financialFields[collection])
    if (field !== "amount" && field !== "vatAmount")
      values[field] = values[field]?.trim() || null;
  const required =
    collection === "items"
      ? ["finClass", "itemName"]
      : collection === "adjustments"
        ? ["targetMonth", "itemCode", "reason"]
        : collection === "transfers"
          ? ["date", "classCode", "itemName"]
          : ["date", "direction"];
  if (required.some((field) => !values[field]))
    throw new Error("필수 입력 항목을 확인해 주세요.");
  return values;
}
export function financialPayload(
  editor: api.FinancialEditor,
  draft: FinancialDraft,
): Record<string, unknown> {
  const body: Record<string, unknown> = {
      month: editor.month,
      expectedRevision: editor.revision,
    },
    base = fromEditor(editor, false);
  for (const collection of [
    "items",
    "adjustments",
    "transfers",
    "loans",
  ] as api.Collection[]) {
    const add: unknown[] = [],
      update: unknown[] = [];
    for (const row of draft[collection]) {
      const original = base[collection].find(
        (old) => old.id === row.id && row.id !== undefined,
      );
      if (original && api.sameValues(original.values, row.values)) continue;
      const values = normalized(row, collection);
      if (!row.id)
        add.push({
          ...values,
          ...(row.copySource ? { copySource: row.copySource } : {}),
        });
      else update.push({ [api.financialIds[collection]]: row.id, ...values });
    }
    const deleteIds = base[collection]
      .filter(
        (row) => !draft[collection].some((current) => current.id === row.id),
      )
      .map((row) => row.id);
    if (add.length || update.length || deleteIds.length)
      body[collection] = { add, update, deleteIds };
  }
  const cards: {
    add: unknown[];
    states: unknown[];
    payments: unknown[];
    cashOverride?: FinancialDraft["cashOverride"];
  } = { add: [], states: [], payments: [] };
  for (const row of draft.cards) {
    const original = base.cards.find(
      (old) => old.id === row.id && row.id !== undefined,
    );
    if (!original) {
      if (
        !row.values.cardName?.trim() ||
        !/^\d{4}$/.test(row.values.last4 ?? "")
      )
        throw new Error("카드 이름과 끝 4자리를 확인해 주세요.");
      cards.add.push({
        clientKey: row.key,
        cardName: row.values.cardName.trim(),
        last4: row.values.last4,
        amount: normalizeFinancialWon(row.values.amount || "0"),
      });
    } else {
      if (original.values.useYn !== row.values.useYn)
        cards.states.push({ cardId: row.id, useYn: row.values.useYn });
      if (original.values.amount !== row.values.amount)
        cards.payments.push({
          cardId: row.id,
          amount: normalizeFinancialWon(row.values.amount || "0"),
        });
    }
  }
  if (draft.cashOverride)
    cards.cashOverride =
      draft.cashOverride.mode === "AUTO"
        ? { mode: "AUTO" }
        : {
            mode: "MANUAL",
            amount: normalizeFinancialWon(draft.cashOverride.amount ?? ""),
          };
  if (
    cards.add.length ||
    cards.states.length ||
    cards.payments.length ||
    cards.cashOverride
  )
    body.cards = cards;
  if (draft.estimatedTax !== base.estimatedTax && draft.estimatedTax !== null)
    body.estimatedTax = normalizeFinancialWon(draft.estimatedTax);
  const schedules = draft.taxSchedules
    .filter(
      (row) =>
        JSON.stringify(row) !==
        JSON.stringify(
          base.taxSchedules.find(
            (old) => old.year === row.year && old.payType === row.payType,
          ),
        ),
    )
    .map((row) => ({
      ...row,
      payAmount:
        row.payAmount == null || row.payAmount === ""
          ? null
          : normalizeFinancialWon(row.payAmount),
    }));
  if (schedules.length) body.taxSchedules = schedules;
  return body;
}
export function useFinancialEditor(
  month?: string,
  onDirtyChange?: (dirty: boolean) => void,
) {
  const [editor, setEditor] = useState<api.FinancialEditor>();
  const [draft, setDraft] = useState<FinancialDraft>();
  const [error, setError] = useState<unknown>();
  const [message, setMessage] = useState("");
  const [loading, setLoading] = useState(true),
    [pending, setPending] = useState(false),
    [retry, setRetry] = useState(0);
  const [refreshFailed, setRefreshFailed] = useState(false);
  const sequence = useRef(0),
    busy = useRef(false),
    mounted = useRef(true);
  const adopt = useCallback((result: api.FinancialEditor, automatic = true) => {
    setEditor(result);
    setDraft(fromEditor(result, automatic));
  }, []);
  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);
  useEffect(() => {
    const controller = new AbortController(),
      generation = ++sequence.current;
    setLoading(true);
    setError(undefined);
    setEditor(undefined);
    setDraft(undefined);
    setMessage("");
    void (async () => {
      let result = await api.getFinancialItems(month, controller.signal);
      if (controller.signal.aborted || sequence.current !== generation) return;
      if (result.editable) {
        try {
          await createCorporateTaxDefaults(result.month);
          result = await api.getFinancialItems(result.month, controller.signal);
        } catch (failure) {
          if (!controller.signal.aborted) setError(failure);
        }
      }
      if (!controller.signal.aborted && sequence.current === generation)
        adopt(result);
    })()
      .catch((failure) => {
        if (!controller.signal.aborted) setError(failure);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [month, retry, adopt]);
  const dirty = Boolean(
    editor &&
      draft &&
      JSON.stringify(draft) !== JSON.stringify(fromEditor(editor, false)),
  );
  useEffect(() => {
    onDirtyChange?.(dirty);
    window.dispatchEvent(
      new CustomEvent("sicost:financial-dirty", { detail: { dirty } }),
    );
    return () => onDirtyChange?.(false);
  }, [dirty, onDirtyChange]);
  useEffect(() => {
    const before = (event: BeforeUnloadEvent) => {
      if (dirty) {
        event.preventDefault();
        event.returnValue = "";
      }
    };
    window.addEventListener("beforeunload", before);
    return () => window.removeEventListener("beforeunload", before);
  }, [dirty]);
  const refresh = async () => {
    if (!editor) throw new Error("조회 문맥이 없습니다.");
    const value = await api.getFinancialItems(editor.month);
    if (mounted.current) {
      adopt(value, false);
      setRefreshFailed(false);
      setError(undefined);
    }
    return value;
  };
  const discard = () => {
    if (editor) setDraft(fromEditor(editor, false));
    setError(undefined);
    setMessage("");
  };
  const save = async () => {
    if (!editor || !draft || !editor.editable || busy.current) return false;
    busy.current = true;
    setPending(true);
    setError(undefined);
    try {
      const result = await api.saveFinancialItems(
        financialPayload(editor, draft),
      );
      if (mounted.current) {
        adopt(result.editor, false);
        setMessage(result.message);
      }
      window.dispatchEvent(
        new CustomEvent("sicost:financial-committed", {
          detail: { month: editor.month },
        }),
      );
      try {
        const fresh = await api.getFinancialItems(editor.month);
        if (mounted.current) adopt(fresh, false);
      } catch {
        if (mounted.current) setRefreshFailed(true);
      }
      return true;
    } catch (failure) {
      if (mounted.current) setError(failure);
      return false;
    } finally {
      busy.current = false;
      if (mounted.current) setPending(false);
    }
  };
  const preview = async (action: "CALCULATE" | "LOAD_PREVIOUS") => {
    if (!editor || !draft || busy.current || !editor.editable) return;
    busy.current = true;
    setPending(true);
    setError(undefined);
    try {
      const result = await api.createFinancialPreview(
        financialPayload(editor, draft),
        action,
      );
      setEditor((old) =>
        old
          ? {
              ...old,
              allocation: result.allocation,
              contributions: result.contributions,
            }
          : old,
      );
      setMessage(result.message);
      if (result.addedDrafts.length)
        setDraft((old) =>
          old
            ? {
                ...old,
                items: [
                  ...old.items,
                  ...result.addedDrafts.map((row, index) =>
                    api.copiedRow(
                      row,
                      `copy-${sequence.current++}-${index}`,
                      "PREVIOUS",
                    ),
                  ),
                ],
              }
            : old,
        );
    } catch (failure) {
      setError(failure);
    } finally {
      busy.current = false;
      setPending(false);
    }
  };
  const violations =
    error instanceof ApiError
      ? ((
          error.body.details as
            | {
                financial?: {
                  violations?: {
                    tab: string;
                    collection: string;
                    rowId: string;
                    field: string;
                    message: string;
                  }[];
                };
              }
            | undefined
        )?.financial?.violations ?? [])
      : [];
  return {
    editor,
    draft,
    setDraft,
    error,
    setError,
    message,
    loading,
    pending,
    dirty,
    refreshFailed,
    save,
    discard,
    refresh,
    preview,
    retry: () => setRetry((n) => n + 1),
    violations,
  };
}
