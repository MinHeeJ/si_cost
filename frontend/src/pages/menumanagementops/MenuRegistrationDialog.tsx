import { ScreenLoading } from "../../shared/ScreenLoading";
import { FieldHelp } from "../../shared/FieldHelp";
import { useEffect, useRef, useState } from "react";
import { ApiError } from "../../shared/api";
import { Dialog } from "../../shared/Dialog";
import { ErrorBanner } from "../../shared/Feedback";
import { implementedBusinessScreenIds } from "../shared/screenCapabilities";
import * as api from "../menumanagement/menuManagementApi";

// SCR-UI-25 /UI-25 M-NEW
export function MenuRegistrationDialog({
  tree,
  onClose,
  onSaved,
}: {
  tree: api.ManagedMenuTree;
  onClose(): void;
  onSaved(value: api.ManagedMenuTree): void;
}) {
  const [level, setLevel] = useState<1 | 2>(1),
    [name, setName] = useState("");
  const [parentId, setParent] = useState(""),
    [screenId, setScreen] = useState(""),
    [pcOnly, setPc] = useState(false);
  const [screens, setScreens] = useState<api.AvailableMenuScreens>(),
    [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(),
    [fields, setFields] = useState<Record<string, string>>({});
  const [pending, setPending] = useState(false);
  const submitted = useRef(false),
    request = useRef(0);
  async function loadScreens() {
    const version = ++request.current;
    setLoading(true);
    setError(undefined);
    try {
      const value = await api.getMenuManagementScreens();
      if (version !== request.current) return;
      const linked = new Set(tree.items.map((row) => row.screenId));
      const items = value.items.filter(
        (row) =>
          implementedBusinessScreenIds.has(row.screenId) &&
          !linked.has(row.screenId),
      );
      setScreens({ ...value, items });
      setScreen((current) =>
        items.some((row) => row.screenId === current) ? current : "",
      );
    } catch (e) {
      if (version === request.current) {
        setScreens(undefined);
        setError(e);
      }
    } finally {
      if (version === request.current) setLoading(false);
    }
  }
  useEffect(() => {
    void loadScreens();
    return () => {
      request.current++;
    };
  }, []);
  function changeLevel(value: 1 | 2) {
    setLevel(value);
    setFields({});
    if (value === 1) {
      setParent("");
      setScreen("");
      setPc(false);
    }
  }
  async function submit() {
    if (
      submitted.current ||
      (level === 2 && (loading || !screens?.items.length))
    )
      return;
    const invalid: Record<string, string> = {};
    if (!name.trim() || [...name.trim()].length > 50)
      invalid.name = "이름은 공백 제거 후 1~50자로 입력해 주세요.";
    if (
      level === 2 &&
      !tree.items.some((row) => row.level === 1 && row.menuId === parentId)
    )
      invalid.parentId = "상위대메뉴를 선택해 주세요.";
    if (level === 2 && !screens?.items.some((row) => row.screenId === screenId))
      invalid.screenId = "개발된 화면을 선택해 주세요.";
    setFields(invalid);
    if (Object.keys(invalid).length) return;
    submitted.current = true;
    setPending(true);
    setError(undefined);
    try {
      const result = await api.createManagedMenu(
        level === 1
          ? { level, name: name.trim(), expectedRevision: tree.revision }
          : {
              level,
              name: name.trim(),
              parentId,
              screenId,
              pcOnly,
              expectedRevision: tree.revision,
            },
      );
      onSaved(result.value);
    } catch (e) {
      setError(e);
      setFields(e instanceof ApiError ? e.fieldMessages : {});
    } finally {
      submitted.current = false;
      setPending(false);
    }
  }
  return (
    <Dialog
      open
      title="메뉴 등록"
      testId="menu-registration-dialog"
      pending={pending}
      confirmLabel="등록"
      confirmTestId="menu-registration-save"
      cancelTestId="menu-registration-cancel"
      onCancel={onClose}
      onConfirm={submit}
      confirmDisabled={level === 2 && (loading || !screens?.items.length)}
      noValidate
    >
      {error != null && (
        <ErrorBanner
          message={
            error instanceof Error ? error.message : "화면 조회에 실패했습니다."
          }
        />
      )}
      <label className="sicost-field">
        레벨
        <select
          data-testid="menu-registration-level"
          disabled={pending}
          value={level}
          onChange={(e) => changeLevel(Number(e.target.value) as 1 | 2)}
        >
          <option value={1}>대메뉴</option>
          <option value={2}>서브</option>
        </select>
      </label>
      <label className="sicost-field">
        이름
        <input
          aria-label="이름"
          data-testid="menu-registration-name"
          disabled={pending}
          value={name}
          aria-invalid={Boolean(fields.name)}
          onChange={(e) => setName(e.target.value)}
        />
        {fields.name && (
          <span role="alert" className="sicost-field-error">
            {fields.name}
          </span>
        )}
      </label>
      {level === 2 && (
        <>
          <label className="sicost-field">
            상위대메뉴
            <select
              data-testid="menu-registration-parent"
              disabled={pending}
              aria-invalid={Boolean(fields.parentId)}
              value={parentId}
              onChange={(e) => setParent(e.target.value)}
            >
              <option value="">선택</option>
              {tree.items
                .filter((row) => row.level === 1)
                .map((row) => (
                  <option key={row.menuId} value={row.menuId}>
                    {row.name}
                  </option>
                ))}
            </select>
            {fields.parentId && (
              <span role="alert" className="sicost-field-error">
                {fields.parentId}
              </span>
            )}
          </label>
          <label className="sicost-field">
            화면번호
            <FieldHelp label="화면번호" /><select
              data-testid="menu-registration-screen"
              disabled={pending || loading}
              aria-invalid={Boolean(fields.screenId)}
              value={screenId}
              onChange={(e) => setScreen(e.target.value)}
            >
              <option value="">선택</option>
              {screens?.items.map((row) => (
                <option key={row.screenId} value={row.screenId}>
                  {row.screenId} {row.name}
                </option>
              ))}
            </select>
            {fields.screenId && (
              <span role="alert" className="sicost-field-error">
                {fields.screenId}
              </span>
            )}
          </label>
          <label>
            <input
              data-testid="menu-registration-pc"
              disabled={pending}
              type="checkbox"
              checked={pcOnly}
              onChange={(e) => setPc(e.target.checked)}
            />
            PC전용
          </label>
          {loading ? (
            <ScreenLoading inline message="가능 화면을 불러오는 중…" />
          ) : (
            screens &&
            !screens.items.length && (
              <p className="sicost-empty">메뉴에 없는 개발된 화면이 없습니다</p>
            )
          )}
          <button
            data-testid="menu-registration-reload"
            type="button"
            disabled={pending || loading}
            onClick={() => void loadScreens()}
          >
            가능 화면 다시 조회
          </button>
        </>
      )}
      <p>
        ID·순서·아이콘·잠금은 서버에서 정합니다. 빈 대메뉴는 탐색에서
        제외됩니다.
      </p>
    </Dialog>
  );
}
