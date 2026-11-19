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
export class FinancialFieldError extends Error {
  constructor(
    public collection: string,
    public rowId: string,
    public field: string,
    message: string,
  ) {
    super(message);
  }
}
function requiredMoney(
  collection: string,
  rowId: string,
  field: string,
  value: string | null | undefined,
) {
  if (!value?.trim())
    throw new FinancialFieldError(
      collection,
      rowId,
      field,
      "금액을 입력해 주세요.",
    );
  try {
    return normalizeFinancialWon(value);
  } catch (error) {
    throw new FinancialFieldError(
      collection,
      rowId,
      field,
      (error as Error).message,
    );
  }
}
function normalized(row: api.DraftRow, collection: api.Collection): api.Values {
  const values = { ...row.values };
  const required =
    collection === "items"
      ? [
          ["itemName", "세부 항목을"],
          ["amount", "금액을"],
        ]
      : collection === "adjustments"
        ? [
            ["targetMonth", "대상 달을"],
            ["amount", "금액을"],
            ["reason", "사유를"],
          ]
        : collection === "transfers"
          ? [
              ["date", "날짜를"],
              ["itemName", "내용을"],
              ["amount", "금액을"],
            ]
          : [
              ["date", "날짜를"],
              ["amount", "금액을"],
            ];
  for (const [field, label] of required)
    if (!values[field]?.trim())
      throw new FinancialFieldError(
        collection,
        row.key,
        field,
        `${label} 입력해 주세요.`,
      );
  for (const field of [
    "amount",
    ...(collection === "items" ? ["vatAmount"] : []),
  ]) {
    try {
      values[field] = normalizeFinancialWon(
        field === "vatAmount" ? values[field] || "0" : (values[field] ?? ""),
        field === "amount" && collection === "adjustments",
        field === "amount" &&
          (collection === "loans" || collection === "transfers"),
      );
    } catch (error) {
      throw new FinancialFieldError(
        collection,
        row.key,
        field,
        (error as Error).message,
      );
    }
  }
  for (const field of api.financialFields[collection])
    if (field !== "amount" && field !== "vatAmount")
      values[field] = values[field]?.trim() || null;
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
        amount: requiredMoney("cards", row.key, "amount", row.values.amount),
      });
    } else {
      if (original.values.useYn !== row.values.useYn)
        cards.states.push({ cardId: row.id, useYn: row.values.useYn });
      if (original.values.amount !== row.values.amount)
        cards.payments.push({
          cardId: row.id,
          amount: requiredMoney("cards", row.key, "amount", row.values.amount),
        });
    }
  }
  if (draft.cashOverride)
    cards.cashOverride =
      draft.cashOverride.mode === "AUTO"
        ? { mode: "AUTO" }
        : {
            mode: "MANUAL",
            amount: requiredMoney(
              "cards",
              "cash",
              "override",
              draft.cashOverride.amount,
            ),
          };
  if (
    cards.add.length ||
    cards.states.length ||
    cards.payments.length ||
    cards.cashOverride
  )
    body.cards = cards;
  if (draft.estimatedTax !== base.estimatedTax && draft.estimatedTax !== null)
    body.estimatedTax = requiredMoney(
      "corporateTax",
      "annual",
      "amount",
      draft.estimatedTax,
    );
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
    .map((row) => {
      if (!row.dueDate)
        throw new FinancialFieldError(
          "tax",
          `${row.year}-${row.payType.toLowerCase()}`,
          "date",
          "날짜를 입력해 주세요.",
        );
      return {
        ...row,
        payAmount:
          row.payAmount == null || row.payAmount === ""
            ? null
            : requiredMoney(
                "tax",
                `${row.year}-${row.payType.toLowerCase()}`,
                "amount",
                row.payAmount,
              ),
      };
    });
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
  const [calculated, setCalculated] = useState<api.Preview>();
  const [previewError, setPreviewError] = useState<unknown>();
  const [previewing, setPreviewing] = useState(false);
  const calculationSequence = useRef(0);
  const sequence = useRef(0),
    busy = useRef(false),
    mounted = useRef(true);
  const adopt = useCallback((result: api.FinancialEditor, automatic = true) => {
    setEditor(result);
    setDraft(fromEditor(result, automatic));
    setCalculated(undefined);
    setPreviewError(undefined);
    calculationSequence.current++;
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
    const generation = ++calculationSequence.current;
    const controller = new AbortController();
    setPreviewError(undefined);
    if (!editor || !draft || !editor.editable || pending) return;
    if (!dirty) {
      setCalculated(undefined);
      setPreviewing(false);
      return;
    }
    let body: Record<string, unknown>;
    try {
      body = financialPayload(editor, draft);
    } catch {
      setPreviewing(false);
      return;
    }
    setPreviewing(true);
    const timer = window.setTimeout(() => {
      void api
        .createFinancialPreview(body, "CALCULATE", controller.signal)
        .then((result) => {
          if (
            !controller.signal.aborted &&
            generation === calculationSequence.current
          )
            setCalculated(result);
        })
        .catch((failure) => {
          if (
            !controller.signal.aborted &&
            generation === calculationSequence.current
          )
            setPreviewError(failure);
        })
        .finally(() => {
          if (
            !controller.signal.aborted &&
            generation === calculationSequence.current
          )
            setPreviewing(false);
        });
    }, 350);
    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
  }, [editor, draft, dirty, pending]);
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
    setMessage("변경을 취소했습니다.");
    setCalculated(undefined);
    setPreviewError(undefined);
  };
  const save = async () => {
    if (!editor || !draft || !editor.editable || busy.current) return false;
    if (!dirty) {
      setError(undefined);
      setMessage("바뀐 내용이 없습니다.");
      return true;
    }
    busy.current = true;
    setPending(true);
    setError(undefined);
    try {
      const body = financialPayload(editor, draft);
      const reflected = [
        [
          "법인세",
          body.estimatedTax !== undefined || body.taxSchedules !== undefined,
        ],
        ["전월 조정", body.adjustments !== undefined],
        ["법인카드", body.cards !== undefined],
        ["계좌이체", body.transfers !== undefined],
        ["대여금", body.loans !== undefined],
      ]
        .filter(([, included]) => included)
        .map(([label]) => label);
      const result = await api.saveFinancialItems(body);
      if (mounted.current) {
        adopt(result.editor, false);
        setMessage(
          !result.changed
            ? "바뀐 내용이 없습니다."
            : reflected.length
              ? `저장했습니다. ${reflected.join("·")}도 함께 반영했습니다.`
              : "저장했습니다.",
        );
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
      if (!result.addedDrafts.length) setMessage("불러올 항목이 없습니다.");
      return result;
    } catch (failure) {
      setError(failure);
    } finally {
      busy.current = false;
      setPending(false);
    }
  };
  const applyPrevious = (result: api.Preview) => {
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
    setMessage(`${result.addedDrafts.length}개 항목을 불러왔습니다.`);
  };
  const violations =
    error instanceof FinancialFieldError
      ? [
          {
            collection: error.collection,
            rowId: error.rowId,
            field: error.field,
            message: error.message,
            tab: ["cards", "transfers", "loans"].includes(error.collection)
              ? "CARD_TRANSFER_LOAN"
              : "FINANCIAL_TAX_ADJUSTMENT",
          },
        ]
      : error instanceof ApiError
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
    setMessage,
    calculated,
    previewError,
    previewing,
    loading,
    pending,
    dirty,
    refreshFailed,
    save,
    discard,
    refresh,
    preview,
    applyPrevious,
    retry: () => setRetry((n) => n + 1),
    violations,
  };
}
