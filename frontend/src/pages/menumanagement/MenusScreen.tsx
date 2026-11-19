import { HelpTooltip } from "../../shared/HelpTooltip";
import { FieldHelp } from "../../shared/FieldHelp";
import { useEffect, useRef, useState } from "react";
import { ApiError } from "../../shared/api";
import { Dialog } from "../../shared/Dialog";
import { ErrorBanner } from "../../shared/Feedback";
import { COMMITTED_REFRESH_FAILED } from "../../shared/useCommitRefresh";
import * as api from "./menuManagementApi";
import { MenuRegistrationDialog } from "../menumanagementops/MenuRegistrationDialog";
import { usePermission } from "../../shared/permission/PermissionBoundary";
import "./menus.css";

const modeMessage = "저장된 역할·메뉴 권한과 담당 범위를 적용합니다.";
export function MenusScreen({
  onDirtyChange,
}: {
  onDirtyChange?(dirty: boolean): void;
}) {
  const canRegister = usePermission("UI-25", "REG"),
    canUpdate = usePermission("UI-25", "UPD");
  const [tree, setTree] = useState<api.ManagedMenuTree>();
  const [draft, setDraft] = useState<api.ManagedMenu[]>([]);
  const [error, setError] = useState<unknown>(),
    [fields, setFields] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(true),
    [pending, setPending] = useState(false);
  const [message, setMessage] = useState(""),
    [refreshOnly, setRefreshOnly] = useState(false);
  const [register, setRegister] = useState(false),
    [history, setHistory] = useState<api.ManagedMenu>();
  const [leave, setLeave] = useState<{
    label: string;
    proceed(): void;
    registration?: boolean;
  }>();
  const [collapsed, setCollapsed] = useState<string[]>([]);
  const busy = useRef(false),
    drag = useRef<string>();
  const dirty = Boolean(
    tree &&
      JSON.stringify(api.menuPayload(draft)) !==
        JSON.stringify(api.menuPayload(tree.items)),
  );
  const install = (value: api.ManagedMenuTree) => {
    setTree(value);
    setDraft(api.orderedMenus(value.items));
    setFields({});
    setRefreshOnly(false);
  };
  async function load() {
    setLoading(true);
    setError(undefined);
    try {
      install(await api.getMenuManagement());
    } catch (e) {
      setError(e);
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => {
    void load();
  }, []);
  useEffect(() => {
    onDirtyChange?.(dirty);
    return () => onDirtyChange?.(false);
  }, [dirty, onDirtyChange]);
  useEffect(() => {
    const before = (event: BeforeUnloadEvent) => {
      if (dirty) {
        event.preventDefault();
        event.returnValue = "";
      }
    };
    const navigation = (event: Event) => {
      const detail = (
        event as CustomEvent<{
          screenId: string;
          label: string;
          proceed(): void;
        }>
      ).detail;
      if (dirty && detail?.screenId === "UI-25") {
        event.preventDefault();
        setLeave(detail);
      }
    };
    window.addEventListener("beforeunload", before);
    window.addEventListener("sicost:screen-navigation-request", navigation);
    return () => {
      window.removeEventListener("beforeunload", before);
      window.removeEventListener(
        "sicost:screen-navigation-request",
        navigation,
      );
    };
  }, [dirty]);
  function fail(e: unknown) {
    setError(e);
    setFields(e instanceof ApiError ? e.fieldMessages : {});
  }
  function edit(id: string, values: Partial<api.ManagedMenu>) {
    if (!canUpdate) return;
    setDraft((items) =>
      items.map((item) => (item.menuId === id ? { ...item, ...values } : item)),
    );
    setMessage("");
  }
  async function save(): Promise<boolean> {
    if (!canUpdate || !tree || busy.current || refreshOnly) return false;
    const invalid: Record<string, string> = {};
    draft.forEach((row, index) => {
      if (!row.name.trim() || [...row.name.trim()].length > 50)
        invalid[`items.${index}.name`] =
          "메뉴 이름은 공백 제거 후 1~50자로 입력해 주세요.";
    });
    setFields(invalid);
    if (Object.keys(invalid).length) {
      setError(new Error("메뉴 이름을 확인해 주세요."));
      return false;
    }
    busy.current = true;
    setPending(true);
    setError(undefined);
    setMessage("");
    try {
      const result = await api.putMenuManagement(
        api.menuPayload(draft),
        tree.revision,
      );
      install(result.value);
      window.dispatchEvent(new CustomEvent("sicost:configuration-committed"));
      try {
        install(await api.getMenuManagement());
        setMessage(result.changed ? "저장했습니다." : "바뀐 내용이 없습니다.");
        return true;
      } catch (e) {
        setMessage(COMMITTED_REFRESH_FAILED);
        setRefreshOnly(true);
        setError(e);
        return false;
      }
    } catch (e) {
      fail(e);
      return false;
    } finally {
      busy.current = false;
      setPending(false);
    }
  }
  function openRegistration() {
    if (!canRegister) return;
    if (dirty)
      setLeave({
        label: "메뉴 등록",
        registration: true,
        proceed: () => setRegister(true),
      });
    else setRegister(true);
  }
  const roots = draft.filter((row) => row.level === 1);
  return (
    <section
      className="menu-management"
      data-screen-id="SCR-UI-25"
      data-testid="menus-screen"
    >
      <header className="menu-management-heading">
        <div>
          <p>시스템</p>
          <h1>
            메뉴 <small>UI-25</small>
          </h1>
        </div>
        {canRegister && (
          <button
            data-testid="menus-register-button"
            disabled={loading || pending || !tree || refreshOnly}
            onClick={openRegistration}
          >
            메뉴 등록
          </button>
        )}
      </header>
      <p className="sicost-banner sicost-tone-info">{modeMessage}</p>
      {!canUpdate && (
        <p role="note">
          수정 권한이 없어 보기만 할 수 있습니다(권한 › 메뉴×동작 표의 수정).
        </p>
      )}
      {message && (
        <p role="status" className="sicost-banner sicost-tone-info">
          {message}
        </p>
      )}
      {error != null && (
        <ErrorBanner
          message={
            error instanceof Error
              ? error.message
              : "메뉴를 불러오지 못했습니다."
          }
          onRetry={() => {
            if (!dirty) void load();
            else
              setLeave({
                label: "최신값 다시 조회",
                proceed: () => void load(),
              });
          }}
        />
      )}
      <div className="menu-management-actions">
        <p>
          드래그 또는 위·아래 버튼과 그룹 선택으로 순서를 편집합니다. 저장
          전에는 반영되지 않습니다.
        </p>
        <button
          data-testid="menus-reload-button"
          disabled={pending || loading}
          onClick={() =>
            dirty
              ? setLeave({
                  label: "최신값 다시 조회",
                  proceed: () => void load(),
                })
              : void load()
          }
        >
          {refreshOnly ? "저장 후 재조회" : "최신값 다시 조회"}
        </button>
      </div>
      {loading ? (
        <p role="status">메뉴를 불러오는 중…</p>
      ) : (
        <div className="sicost-table-viewport">
          <table className="sicost-table">
            <thead>
              <tr>
                <th>손잡이</th>
                <th>메뉴이름<FieldHelp label="메뉴이름" kind="B" /></th>
                <th>화면번호<FieldHelp label="화면번호" kind="B" /></th>
                <th>형제 내 순서</th>
                <th>사용</th>
                <th>PC전용</th>
                <th>잠금</th>
                <th>기록</th>
              </tr>
            </thead>
            <tbody>
              {draft
                .filter(
                  (row) =>
                    row.level === 1 || !collapsed.includes(row.parentId!),
                )
                .map((row) => {
                  const index = draft.indexOf(row),
                    key = `items.${index}.name`,
                    protectedRow = api.protectedMenu(row);
                  const siblings = draft.filter(
                    (item) => item.parentId === row.parentId,
                  );
                  return (
                    <tr
                      key={row.menuId}
                      className={row.level === 1 ? "menu-management-root" : ""}
                      data-testid={`menu-row-${row.menuId.toLowerCase()}`}
                      onDragOver={(event) => event.preventDefault()}
                      onDrop={(event) => {
                        event.preventDefault();
                        if (!canUpdate) return;
                        const moving = draft.find(
                          (item) => item.menuId === drag.current,
                        );
                        if (moving && moving.level === row.level)
                          setDraft((items) =>
                            api.moveMenu(
                              items,
                              moving.menuId,
                              row.parentId,
                              row.sortOrder - 1,
                            ),
                          );
                        drag.current = undefined;
                      }}
                    >
                      <td>
                        <button
                          type="button"
                          data-testid={`menu-drag-${row.menuId.toLowerCase()}`}
                          draggable={canUpdate && !pending}
                          disabled={!canUpdate || pending || refreshOnly}
                          aria-label={`${row.name} 이동 손잡이`}
                          onDragStart={() => {
                            drag.current = row.menuId;
                          }}
                          onKeyDown={(event) => {
                            if (
                              event.key === "ArrowUp" ||
                              event.key === "ArrowDown"
                            ) {
                              event.preventDefault();
                              setDraft((items) =>
                                api.moveMenu(
                                  items,
                                  row.menuId,
                                  row.parentId,
                                  row.sortOrder -
                                    1 +
                                    (event.key === "ArrowUp" ? -1 : 1),
                                ),
                              );
                            }
                          }}
                        >
                          ↕
                        </button>
                      </td>
                      <td>
                        <div className="sicost-field menu-management-name">
                          {row.level === 1 && (
                            <button
                              data-testid={`menu-collapse-${row.menuId.toLowerCase()}`}
                              aria-expanded={!collapsed.includes(row.menuId)}
                              onClick={() =>
                                setCollapsed((ids) =>
                                  ids.includes(row.menuId)
                                    ? ids.filter((id) => id !== row.menuId)
                                    : [...ids, row.menuId],
                                )
                              }
                            >
                              {collapsed.includes(row.menuId)
                                ? "펼치기"
                                : "접기"}
                            </button>
                          )}
                          <input
                            data-testid={`menu-name-${row.menuId.toLowerCase()}`}
                            aria-label={`${row.menuId} 메뉴이름`}
                            aria-invalid={Boolean(fields[key])}
                            value={row.name}
                            disabled={!canUpdate || pending || refreshOnly}
                            onChange={(event) =>
                              edit(row.menuId, { name: event.target.value })
                            }
                          />
                          {fields[key] && (
                            <span role="alert" className="sicost-field-error">
                              {fields[key]}
                            </span>
                          )}
                        </div>
                      </td>
                      <td>{row.screenId ?? "–"}</td>
                      <td>
                        <div className="menu-management-order">
                          <span>{row.sortOrder}</span>
                          <button
                            data-testid={`menu-up-${row.menuId.toLowerCase()}`}
                            aria-label={`${row.menuId} 위`}
                            disabled={
                              !canUpdate ||
                              pending ||
                              refreshOnly ||
                              row.sortOrder === 1
                            }
                            onClick={() =>
                              setDraft((items) =>
                                api.moveMenu(
                                  items,
                                  row.menuId,
                                  row.parentId,
                                  row.sortOrder - 2,
                                ),
                              )
                            }
                          >
                            위
                          </button>
                          <button
                            data-testid={`menu-down-${row.menuId.toLowerCase()}`}
                            aria-label={`${row.menuId} 아래`}
                            disabled={
                              !canUpdate ||
                              pending ||
                              refreshOnly ||
                              row.sortOrder === siblings.length
                            }
                            onClick={() =>
                              setDraft((items) =>
                                api.moveMenu(
                                  items,
                                  row.menuId,
                                  row.parentId,
                                  row.sortOrder,
                                ),
                              )
                            }
                          >
                            아래
                          </button>
                          {row.level === 2 && (
                            <select
                              data-testid={`menu-group-${row.menuId.toLowerCase()}`}
                              aria-label={`${row.menuId} 그룹`}
                              value={row.parentId!}
                              disabled={
                                !canUpdate ||
                                pending ||
                                refreshOnly ||
                                protectedRow
                              }
                              onChange={(event) =>
                                setDraft((items) =>
                                  api.moveMenu(
                                    items,
                                    row.menuId,
                                    event.target.value,
                                    items.filter(
                                      (item) =>
                                        item.parentId === event.target.value,
                                    ).length,
                                  ),
                                )
                              }
                            >
                              {roots.map((root) => (
                                <option key={root.menuId} value={root.menuId}>
                                  {root.name}
                                </option>
                              ))}
                            </select>
                          )}
                        </div>
                      </td>
                      <td>
                        {row.level === 2 && (
                          <input
                            data-testid={`menu-use-${row.menuId.toLowerCase()}`}
                            type="checkbox"
                            aria-label={`${row.menuId} 사용`}
                            checked={row.use}
                            disabled={
                              !canUpdate ||
                              pending ||
                              refreshOnly ||
                              protectedRow
                            }
                            onChange={(event) =>
                              edit(row.menuId, { use: event.target.checked })
                            }
                          />
                        )}
                      </td>
                      <td>
                        {row.level === 2 && (
                          <input
                            data-testid={`menu-pc-${row.menuId.toLowerCase()}`}
                            type="checkbox"
                            aria-label={`${row.menuId} PC전용`}
                            checked={row.pcOnly}
                            disabled={!canUpdate || pending || refreshOnly}
                            onChange={(event) =>
                              edit(row.menuId, { pcOnly: event.target.checked })
                            }
                          />
                        )}
                      </td>
                      <td>
                        {protectedRow && (
                          <HelpTooltip label="시스템 묶음 잠금" trigger={<span className="sicost-badge sicost-tone-lock">잠금</span>}>시스템 묶음: 끌 수 없고, 안의 메뉴를 밖으로 옮길 수 없습니다.</HelpTooltip>
                        )}
                        {row.level === 2 && !row.use && (
                          <span className="sicost-badge sicost-tone-warn">
                            중지
                          </span>
                        )}
                      </td>
                      <td>
                        <button
                          data-testid={`menu-history-${row.menuId.toLowerCase()}`}
                          onClick={() => setHistory(row)}
                        >
                          변경 이력
                        </button>
                      </td>
                    </tr>
                  );
                })}
            </tbody>
          </table>
          {!draft.length && (
            <p className="sicost-empty">등록된 메뉴가 없습니다.</p>
          )}
        </div>
      )}
      {canUpdate && (
        <footer className="menu-management-actions">
          <span>
            {dirty ? "저장하지 않은 변경이 있습니다." : "저장된 메뉴 설정"}
          </span>
          <button
            data-testid="menus-cancel-button"
            disabled={!dirty || pending}
            onClick={() => {
              if (tree) {
                setDraft(api.orderedMenus(tree.items));
                setFields({});
                setError(undefined);
              }
            }}
          >
            취소
          </button>
          <button
            data-testid="menus-save-button"
            className="sicost-primary"
            disabled={!dirty || pending || refreshOnly}
            onClick={() => void save()}
          >
            {pending ? "저장 중…" : "저장"}
          </button>
        </footer>
      )}
      {leave && (
        <Dialog
          open
          title="저장하지 않은 내용이 있습니다."
          testId="menus-dirty-dialog"
          cancelTestId="menus-dirty-stay"
          confirmTestId="menus-dirty-discard"
          cancelLabel="계속 작성"
          confirmLabel="버리기/나가기"
          pending={pending}
          onCancel={() => setLeave(undefined)}
          onConfirm={() => {
            const action = leave;
            if (tree) setDraft(api.orderedMenus(tree.items));
            setLeave(undefined);
            action.proceed();
          }}
          footerStart={
            leave.registration ? (
              <button
                data-testid="menus-dirty-save"
                type="button"
                disabled={pending}
                onClick={() => {
                  void save().then((ok) => {
                    if (ok) {
                      setLeave(undefined);
                      setRegister(true);
                    }
                  });
                }}
              >
                저장 후 등록
              </button>
            ) : undefined
          }
        >
          <p>
            {leave.label} 전에 미저장 변경을 처리해 주세요. 자동으로 저장하지
            않습니다.
          </p>
        </Dialog>
      )}
      {canRegister && register && tree && (
        <MenuRegistrationDialog
          tree={tree}
          onClose={() => setRegister(false)}
          onSaved={(value) => {
            install(value);
            setRegister(false);
            setError(undefined);
            setMessage(
              "등록했습니다. 빈 대메뉴는 자식을 추가하기 전에는 탐색에 나타나지 않습니다.",
            );
            window.dispatchEvent(
              new CustomEvent("sicost:configuration-committed"),
            );
            void api
              .getMenuManagement()
              .then(install)
              .catch((e) => {
                setError(e);
                setMessage(COMMITTED_REFRESH_FAILED);
                setRefreshOnly(true);
              });
          }}
        />
      )}
      {history && (
        <MenuHistory menu={history} onClose={() => setHistory(undefined)} />
      )}
    </section>
  );
}

function MenuHistory({
  menu,
  onClose,
}: {
  menu: api.ManagedMenu;
  onClose(): void;
}) {
  const [data, setData] = useState<api.MenuHistoryPage>(),
    [error, setError] = useState<unknown>(),
    [page, setPage] = useState(1),
    [size, setSize] = useState(50),
    [reload, setReload] = useState(0);
  useEffect(() => {
    let live = true;
    setData(undefined);
    setError(undefined);
    void api
      .getMenuHistory(menu.menuId, page, size)
      .then((value) => {
        if (live) setData(value);
      })
      .catch((e) => {
        if (live) setError(e);
      });
    return () => {
      live = false;
    };
  }, [menu.menuId, page, size, reload]);
  return (
    <section
      className="menu-management-history"
      aria-label="메뉴 변경 이력"
      data-testid="menu-history-panel"
    >
      <header className="menu-management-actions">
        <h2>
          {menu.name} ({menu.menuId}) 변경 이력
        </h2>
        <button data-testid="menu-history-close" onClick={onClose}>
          닫기
        </button>
      </header>
      {error != null ? (
        <ErrorBanner
          message={
            error instanceof Error ? error.message : "이력 조회에 실패했습니다."
          }
          onRetry={() => setReload((n) => n + 1)}
        />
      ) : !data ? (
        <p role="status">이력을 불러오는 중…</p>
      ) : (
        <>
          <div className="sicost-table-viewport">
            <table className="sicost-table">
              <thead>
                <tr>
                  <th>일시</th>
                  <th>항목</th>
                  <th>전</th>
                  <th>후</th>
                  <th>처리자</th>
                  <th>사유</th>
                </tr>
              </thead>
              <tbody>
                {data.items.map((row) => (
                  <tr
                    key={row.historyId}
                    data-testid={`menu-history-row-${row.historyId}`}
                  >
                    <td>{row.createdAt}</td>
                    <td>{row.itemName}</td>
                    <td>
                      <details>
                        <summary>
                          {row.beforeValue?.slice(0, 40) ?? "–"}
                        </summary>
                        {row.beforeValue}
                      </details>
                    </td>
                    <td>
                      <details>
                        <summary>{row.afterValue?.slice(0, 40) ?? "–"}</summary>
                        {row.afterValue}
                      </details>
                    </td>
                    <td>
                      {row.actorName} ({row.actorId})
                    </td>
                    <td>{row.reason ?? "–"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            {!data.items.length && (
              <p className="sicost-empty">변경 이력이 없습니다.</p>
            )}
          </div>
          <div className="menu-management-actions">
            <button
              data-testid="menu-history-prev"
              disabled={page === 1}
              onClick={() => setPage((p) => p - 1)}
            >
              이전
            </button>
            <span>
              {page} / {Math.max(1, Math.ceil(data.total / size))}
            </span>
            <button
              data-testid="menu-history-next"
              disabled={page * size >= data.total}
              onClick={() => setPage((p) => p + 1)}
            >
              다음
            </button>
            <select
              data-testid="menu-history-size"
              aria-label="이력 한 화면 건수"
              value={size}
              onChange={(e) => {
                setSize(Number(e.target.value));
                setPage(1);
              }}
            >
              {[20, 50, 100].map((n) => (
                <option key={n} value={n}>
                  {n}
                </option>
              ))}
            </select>
            <button
              data-testid="menu-history-reload"
              onClick={() => setReload((n) => n + 1)}
            >
              재조회
            </button>
          </div>
        </>
      )}
    </section>
  );
}
