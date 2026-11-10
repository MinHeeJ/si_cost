import { useState } from "react";
import { Dialog } from "../../shared/Dialog";
import { usePreferences } from "./usePreferences";
import "./preferences.css";

export type PreferenceMenu = {
  screenId: string;
  name: string;
  pcOnly: boolean;
};
function screenIds(value: unknown): string[] {
  return Array.isArray(value)
    ? value.filter((v): v is string => typeof v === "string")
    : [];
}
export function FavoriteButton({ screenId }: { screenId: string }) {
  const p = usePreferences();
  const [error, setError] = useState<string | null>(null);
  const selected = screenIds(p.get("FAV")?.value).includes(screenId);
  return (
    <>
      <button
        type="button"
        className={`me-star${selected ? " me-star-selected" : ""}`}
        aria-label={selected ? "즐겨찾기 해제" : "즐겨찾기 추가"}
        aria-pressed={selected}
        disabled={p.loading || p.pending || !p.get("FAV")}
        onClick={() => {
          setError(null);
          void p
            .toggleFavorite(screenId)
            .catch((e) =>
              setError(e instanceof Error ? e.message : "저장하지 못했습니다."),
            );
        }}
      >
        <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
          <path d="m12 3 2.8 5.7 6.2.9-4.5 4.4 1.1 6.2-5.6-3-5.6 3 1.1-6.2L3 9.6l6.2-.9Z" />
        </svg>
      </button>
      {error && (
        <span role="alert" className="sicost-field-error">
          {error}
        </span>
      )}
    </>
  );
}
export function PreferenceQuickLinks({
  menus,
  collapsed,
  mobile,
  onNavigate,
}: {
  menus: PreferenceMenu[];
  collapsed: boolean;
  mobile: boolean;
  onNavigate(screenId: string): void;
}) {
  const p = usePreferences();
  const [editing, setEditing] = useState(false);
  const favorites = screenIds(p.get("FAV")?.value);
  const recent = screenIds(p.get("RECENT")?.value);
  const visible = (ids: string[]) =>
    ids.flatMap((id) => {
      const menu = menus.find((m) => m.screenId === id);
      return menu && (!mobile || !menu.pcOnly) ? [menu] : [];
    });
  if (collapsed) return null;
  return (
    <section className="me-quick-links">
      <div className="me-quick-caption">
        <span>☆ 즐겨찾기</span>
        <button
          type="button"
          disabled={p.loading || p.pending}
          onClick={() => setEditing(true)}
        >
          편집
        </button>
      </div>
      {p.loading ? (
        <p role="status">불러오는 중…</p>
      ) : (
        <>
          {visible(favorites).map((m) => (
            <button
              type="button"
              className="me-quick-link"
              key={m.screenId}
              onClick={() => onNavigate(m.screenId)}
            >
              {m.name}
            </button>
          ))}
          {visible(favorites).length === 0 && (
            <p className="me-quick-empty">화면 제목 옆 ☆를 누르면 추가됩니다</p>
          )}
          <p className="me-quick-caption">최근 본 화면</p>
          {visible(recent).map((m) => (
            <button
              type="button"
              className="me-quick-link"
              key={m.screenId}
              onClick={() => onNavigate(m.screenId)}
            >
              {m.name}
            </button>
          ))}
          {visible(recent).length === 0 && (
            <p className="me-quick-empty">아직 없습니다</p>
          )}
        </>
      )}
      {p.error != null && (
        <div role="alert">
          설정을 불러오지 못했습니다.
          <button
            type="button"
            onClick={() => void p.refresh().catch(() => {})}
          >
            다시 조회
          </button>
        </div>
      )}
      <FavoritePreferences
        open={editing}
        onClose={() => setEditing(false)}
        menus={menus}
        mobile={mobile}
      />
    </section>
  );
}
// SCR-UI-16-M2: order is applied only after successful server save.
export function FavoritePreferences({
  open,
  onClose,
  menus,
  mobile = false,
}: {
  open: boolean;
  onClose(): void;
  menus: PreferenceMenu[];
  mobile?: boolean;
}) {
  const p = usePreferences();
  const ids = screenIds(p.get("FAV")?.value);
  const [draft, setDraft] = useState<string[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const save = async (next: string[]) => {
    setDraft(next);
    setError(null);
    try {
      await p.put("FAV", "*", next);
      setDraft(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : "저장하지 못했습니다.");
    }
  };
  const reorder = (index: number, delta: number) => {
    const next = [...(draft ?? ids)];
    const target = index + delta;
    if (target < 0 || target >= next.length) return;
    [next[index], next[target]] = [next[target], next[index]];
    void save(next);
  };
  return (
    <Dialog
      open={open}
      title="즐겨찾기 목록 편집"
      pending={p.pending}
      onCancel={onClose}
      onConfirm={onClose}
      confirmLabel="닫기"
    >
      <div data-screen-id="SCR-UI-16-M2">
        <p className="me-help">
          최대 10개·중복 불가 · 순서 변경은 서버 저장 후 적용
        </p>
        {(draft ?? ids).map((id, index) => {
          const menu = menus.find((m) => m.screenId === id);
          if (!menu || (mobile && menu.pcOnly)) return null;
          return (
            <div key={id} className="me-favorite-row">
              <span>{menu.name}</span>
              <button
                type="button"
                disabled={p.pending || index === 0}
                aria-label={`${menu.name} 위로`}
                onClick={() => reorder(index, -1)}
              >
                ▲
              </button>
              <button
                type="button"
                disabled={p.pending || index === (draft ?? ids).length - 1}
                aria-label={`${menu.name} 아래로`}
                onClick={() => reorder(index, 1)}
              >
                ▼
              </button>
              <button
                type="button"
                disabled={p.pending}
                onClick={() =>
                  void save((draft ?? ids).filter((v) => v !== id))
                }
              >
                별 해제
              </button>
            </div>
          );
        })}
        {ids.length === 0 && (
          <p className="me-quick-empty">화면 제목 옆 ☆를 누르면 추가됩니다</p>
        )}
        {error && (
          <>
            <p role="alert" className="sicost-field-error">
              {error}
            </p>
            <button
              type="button"
              disabled={p.pending}
              onClick={() => draft && void save(draft)}
            >
              다시 저장
            </button>
          </>
        )}
        {p.message && !error && <p role="status">{p.message}</p>}
      </div>
    </Dialog>
  );
}
