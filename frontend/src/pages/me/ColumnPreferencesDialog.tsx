import { useState } from "react";
import { Dialog, ConfirmDialog } from "../../shared/Dialog";
import { usePreferences } from "./usePreferences";
import type { ColumnPreferences, ColumnView } from "./preferencesApi";
import "./preferences.css";

export type PreferenceColumn = {
  id: string;
  label: string;
  required?: boolean;
};
// SCR-UI-27-M9: callers supply the complete view definition, excluding check/action columns.
export function ColumnPreferencesDialog({
  open,
  onClose,
  screenId,
  viewId,
  columns,
}: {
  open: boolean;
  onClose(): void;
  screenId: string;
  viewId: keyof ColumnPreferences;
  columns: PreferenceColumn[];
}) {
  const p = usePreferences();
  const pref = p.get("COLS", screenId);
  const source = (pref?.value ?? {}) as ColumnPreferences;
  const [draft, setDraft] = useState<ColumnView | null>(null);
  const [reset, setReset] = useState(false);
  const applied = source[viewId];
  const value = draft ?? applied;
  const move = (index: number, delta: number) => {
    if (!value) return;
    const order = [...value.order];
    const target = index + delta;
    if (target < 0 || target >= order.length) return;
    [order[index], order[target]] = [order[target], order[index]];
    setDraft({ ...value, order });
  };
  return (
    <>
      <Dialog
        open={open}
        title="현재 표 열 설정"
        pending={p.pending}
        onCancel={onClose}
        confirmLabel="적용"
        onConfirm={async () => {
          if (!value) throw new Error("먼저 열 설정을 조회해 주세요.");
          await p.put("COLS", screenId, { ...source, [viewId]: value });
          setDraft(null);
          onClose();
        }}
      >
        <div data-screen-id="SCR-UI-27-M9">
          {p.loading ? (
            <p role="status">불러오는 중…</p>
          ) : !value ? (
            <p role="alert">열 설정을 불러오지 못했습니다.</p>
          ) : (
            value.order.map((id, index) => {
              const column = columns.find((c) => c.id === id);
              if (!column) return null;
              return (
                <div className="me-column-row" key={id}>
                  <span>{column.label}</span>
                  <button
                    type="button"
                    aria-label={`${column.label} 위로`}
                    disabled={p.pending || index === 0}
                    onClick={() => move(index, -1)}
                  >
                    ▲
                  </button>
                  <button
                    type="button"
                    aria-label={`${column.label} 아래로`}
                    disabled={p.pending || index === value.order.length - 1}
                    onClick={() => move(index, 1)}
                  >
                    ▼
                  </button>
                  <label>
                    <input
                      type="checkbox"
                      aria-label={`${column.label} 숨김`}
                      checked={value.hidden.includes(id)}
                      disabled={column.required || p.pending}
                      onChange={(e) =>
                        setDraft({
                          ...value,
                          hidden: e.target.checked
                            ? [...value.hidden, id]
                            : value.hidden.filter((v) => v !== id),
                        })
                      }
                    />
                    숨김
                  </label>
                </div>
              );
            })
          )}
          <p className="me-help">필수열 숨김 불가·전체순열·체크/작업열 제외</p>
          <button
            type="button"
            disabled={!pref?.stored || p.pending}
            onClick={() => setReset(true)}
          >
            이 설정 초기화
          </button>
          {pref?.error && <p role="alert">{pref.error.message}</p>}
        </div>
      </Dialog>
      <ConfirmDialog
        open={reset}
        title="열 설정 초기화"
        onCancel={() => setReset(false)}
        pending={p.pending}
        onConfirm={async () => {
          await p.reset("COLS", screenId);
          setDraft(null);
          setReset(false);
        }}
      >
        <p>이 화면의 열 설정을 기본값으로 되돌립니다.</p>
      </ConfirmDialog>
    </>
  );
}
export function useTablePreferences(
  screenId: string,
  viewId: keyof ColumnPreferences,
  columns: PreferenceColumn[],
) {
  const p = usePreferences();
  const cols = p.get("COLS", screenId)?.value as ColumnPreferences | undefined;
  const view = cols?.[viewId];
  return {
    columns: view
      ? view.order.flatMap((id) => {
          const c = columns.find((col) => col.id === id);
          return c && (!view.hidden.includes(id) || c.required) ? [c] : [];
        })
      : columns,
    pageSize: p.get("PAGE_SIZE")?.value as number | undefined,
    dense: p.get("DENSE")?.value === "좁게",
    amountUnit: p.get("AMT_UNIT")?.value as string | undefined,
  };
}
