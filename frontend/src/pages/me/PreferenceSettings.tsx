import { useState } from "react";
import { Dialog, ConfirmDialog } from "../../shared/Dialog";
import { ErrorBanner } from "../../shared/Feedback";
import { ApiError } from "../../shared/api";
import { usePreferences } from "./usePreferences";
import type { PreferenceType, PreferenceValue } from "./preferencesApi";
import "./preferences.css";

// SCR-UI-16-M1: common user-menu modal, not a route.
const settings = [
  { type: "MENU_FOLD", label: "메뉴 접힘", options: ["펼침", "접힘"] },
  { type: "PAGE_SIZE", label: "한 화면 건수", options: ["20", "50", "100"] },
  { type: "DENSE", label: "목록 밀도", options: ["넓게", "좁게"] },
  { type: "AMT_UNIT", label: "금액 단위", options: ["원", "천원", "백만원"] },
] as const;
export function PreferenceSettings({
  open,
  onClose,
  width = window.innerWidth,
}: {
  open: boolean;
  onClose(): void;
  width?: number;
}) {
  const preferences = usePreferences();
  const [draft, setDraft] = useState<Partial<Record<PreferenceType, string>>>(
    {},
  );
  const [failure, setFailure] = useState<unknown>(null);
  const [resetType, setResetType] = useState<PreferenceType | null>(null);
  const change = async (type: PreferenceType, selection: string) => {
    setDraft((d) => ({ ...d, [type]: selection }));
    setFailure(null);
    const value: PreferenceValue =
      type === "PAGE_SIZE"
        ? Number(selection)
        : type === "MENU_FOLD"
          ? selection === "접힘"
          : selection;
    try {
      await preferences.put(type, "*", value);
      setDraft((d) => {
        const next = { ...d };
        delete next[type];
        return next;
      });
    } catch (e) {
      setFailure(e);
    }
  };
  return (
    <>
      <Dialog
        open={open}
        title="화면 설정"
        onCancel={onClose}
        onConfirm={onClose}
        confirmLabel="닫기"
        cancelLabel="취소"
        pending={preferences.pending}
      >
        <div data-screen-id="SCR-UI-16-M1" className="me-settings">
          {preferences.loading ? (
            <p role="status">불러오는 중…</p>
          ) : (
            settings.map((setting) => {
              const pref = preferences.get(setting.type);
              if (!pref) return null;
              const applied =
                setting.type === "MENU_FOLD"
                  ? preferences.menuFold(width)
                    ? "접힘"
                    : "펼침"
                  : String(pref.value);
              return (
                <section
                  data-testid={`preference-setting-${setting.type.toLowerCase().replace(/_/g, "-")}`}
                  key={setting.type}
                  className="me-setting-row"
                >
                  <label className="sicost-field">
                    <span>{setting.label}</span>
                    <select
                      data-testid={`preference-select-${setting.type.toLowerCase().replace(/_/g, "-")}`}
                      aria-label={setting.label}
                      value={draft[setting.type] ?? applied}
                      disabled={
                        preferences.pending ||
                        (setting.type === "MENU_FOLD" && width < 768)
                      }
                      onChange={(e) =>
                        void change(setting.type, e.target.value)
                      }
                    >
                      {setting.options.map((option) => (
                        <option
                          data-testid={`preference-option-${setting.type.toLowerCase().replace(/_/g, "-")}-${(setting.options as readonly string[]).indexOf(option)}`}
                          key={option}
                        >
                          {option}
                        </option>
                      ))}
                    </select>
                  </label>
                  {pref.stored && (
                    <button
                      type="button"
                      disabled={preferences.pending}
                      onClick={() => setResetType(setting.type)}
                      aria-label={`${setting.label} 초기화`}
                    >
                      이 설정 초기화
                    </button>
                  )}
                  {pref.error && (
                    <p role="alert" className="sicost-field-error">
                      {pref.error.message}
                    </p>
                  )}
                  {draft[setting.type] !== undefined && failure != null && (
                    <button
                      type="button"
                      disabled={preferences.pending}
                      onClick={() =>
                        void change(setting.type, draft[setting.type]!)
                      }
                    >
                      다시 저장
                    </button>
                  )}
                </section>
              );
            })
          )}
          <p className="me-help">
            표 열 설정은 해당 표의 [열 설정]에서 변경합니다.
          </p>
          {failure != null && (
            <ErrorBanner
              message={
                failure instanceof Error
                  ? failure.message
                  : "설정을 저장하지 못했습니다."
              }
              requestId={
                failure instanceof ApiError ? failure.body.requestId : undefined
              }
            />
          )}
          {preferences.error != null && (
            <ErrorBanner
              message="설정을 다시 불러오지 못했습니다."
              onRetry={() => void preferences.refresh().catch(() => {})}
            />
          )}
          {preferences.message && !failure && (
            <p role="status">{preferences.message}</p>
          )}
        </div>
      </Dialog>
      <ConfirmDialog
        open={resetType !== null}
        title="이 설정 초기화"
        pending={preferences.pending}
        confirmLabel="초기화"
        onCancel={() => setResetType(null)}
        onConfirm={async () => {
          if (resetType) {
            await preferences.reset(resetType);
            setDraft((d) => {
              const next = { ...d };
              delete next[resetType];
              return next;
            });
            setResetType(null);
          }
        }}
      >
        <p>선택한 설정만 기본값으로 되돌립니다. 다른 설정은 유지됩니다.</p>
      </ConfirmDialog>
    </>
  );
}
