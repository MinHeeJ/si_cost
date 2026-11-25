import { ScreenLoading } from "../../shared/ScreenLoading";
import { HelpTooltip } from "../../shared/HelpTooltip";
import { FieldHelp } from "../../shared/FieldHelp";
import { useEffect, useRef, useState } from "react";
import { ApiError } from "../../shared/api";
import { Dialog } from "../../shared/Dialog";
import { EditorFooter } from "../../shared/EditorFooter";
import { ErrorBanner, StatusBadge } from "../../shared/Feedback";
import { Icon } from "../../shared/Icon";
import { Pagination } from "../../shared/Pagination";
import { ScreenTitleActions } from "../../shared/ScreenTitleActions";
import { COMMITTED_REFRESH_FAILED } from "../../shared/useCommitRefresh";
import * as api from "./menuManagementApi";
import { MenuRegistrationDialog } from "../menumanagementops/MenuRegistrationDialog";
import { usePermission } from "../../shared/permission/PermissionBoundary";
import "./menus.css";

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
  const editorRef = useRef<HTMLElement>(null);
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
      ref={editorRef}
      className="menu-management"
      data-screen-id="SCR-UI-25"
      data-testid="menus-screen"
    >
      <ScreenTitleActions>
        <button
          type="button"
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
        {canRegister && (
          <button
            type="button"
            className="sicost-primary"
            data-testid="menus-register-button"
            disabled={loading || pending || !tree || refreshOnly}
            onClick={openRegistration}
          >
            메뉴 등록
          </button>
        )}
      </ScreenTitleActions>
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
      {loading ? (
        <ScreenLoading message="메뉴를 불러오는 중…" />
      ) : (
        <div className="sicost-table-viewport menu-management-viewport">
          <table className="sicost-table" aria-label="메뉴 설정">
            <colgroup>
              <col className="menu-management-col-handle" />
              <col className="menu-management-col-name" />
              <col className="menu-management-col-screen" />
              <col className="menu-management-col-order" />
              <col />
              <col className="menu-management-col-toggle" span={2} />
              <col className="menu-management-col-lock" />
              <col className="menu-management-col-history" />
            </colgroup>
            <thead>
              <tr>
                <th scope="col">이동</th>
                <th scope="col">메뉴 이름<FieldHelp label="메뉴이름" kind="B" /></th>
                <th scope="col">화면 번호<FieldHelp label="화면번호" kind="B" /></th>
                <th scope="col">순서</th>
                <th scope="col">대메뉴</th>
                <th scope="col">사용</th>
                <th scope="col">PC 전용</th>
                <th scope="col">상태</th>
                <th scope="col">이력</th>
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
                          className="menu-management-handle"
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
                        <div className="menu-management-name">
                          {row.level === 1 ? (
                            <button
                              type="button"
                              className="menu-management-fold"
                              data-testid={`menu-collapse-${row.menuId.toLowerCase()}`}
                              aria-expanded={!collapsed.includes(row.menuId)}
                              aria-label={`${row.name} ${collapsed.includes(row.menuId) ? "펼치기" : "접기"}`}
                              onClick={() =>
                                setCollapsed((ids) =>
                                  ids.includes(row.menuId)
                                    ? ids.filter((id) => id !== row.menuId)
                                    : [...ids, row.menuId],
                                )
                              }
                            >
                              <Icon name="chevron" size={15} />
                            </button>
                          ) : (
                            <span className="menu-management-child-mark" aria-hidden="true" />
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
                      <td className="menu-management-screen-number">{row.screenId ?? "–"}</td>
                      <td>
                        <div className="menu-management-order">
                          <button
                            type="button"
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
                            <Icon name="caretUp" size={20} fill="currentColor" stroke="none" />
                          </button>
                          <span>{row.sortOrder}</span>
                          <button
                            type="button"
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
                            <Icon name="caretDown" size={20} fill="currentColor" stroke="none" />
                          </button>
                        </div>
                      </td>
                      <td className="menu-management-parent">
                        {row.level === 2 ? (
                          <select
                            data-testid={`menu-group-${row.menuId.toLowerCase()}`}
                            aria-label={`${row.menuId} 그룹`}
                            value={row.parentId!}
                            disabled={
                              !canUpdate || pending || refreshOnly || protectedRow
                            }
                            onChange={(event) => {
                              const parentId = event.target.value;
                              setDraft((items) =>
                                api.moveMenu(
                                  items,
                                  row.menuId,
                                  parentId,
                                  items.filter((item) => item.parentId === parentId)
                                    .length,
                                ),
                              );
                            }}
                          >
                            {roots.map((root) => (
                              <option key={root.menuId} value={root.menuId}>
                                {root.name}
                              </option>
                            ))}
                          </select>
                        ) : (
                          "–"
                        )}
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
                          type="button"
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
      {canUpdate && tree && (
        <EditorFooter editorRef={editorRef} label="메뉴 설정 저장">
          <div className="menu-management-footer-note">
            <small>메뉴 이름, 순서, 사용 여부, PC 전용 설정을 함께 저장합니다.</small>
            {dirty && <StatusBadge tone="warn">저장 전</StatusBadge>}
          </div>
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
        </EditorFooter>
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
    [size, setSize] = useState<20 | 50 | 100>(50),
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
    <Dialog
      open
      wide
      hideCancel
      title={`${menu.name} (${menu.menuId}) 변경 이력`}
      testId="menu-history-panel"
      confirmTestId="menu-history-close"
      confirmLabel="닫기"
      onCancel={onClose}
      onConfirm={onClose}
      footerStart={
        <button
          type="button"
          data-testid="menu-history-reload"
          onClick={() => setReload((n) => n + 1)}
        >
          새로고침
        </button>
      }
    >
      {error != null ? (
        <ErrorBanner
          message={
            error instanceof Error ? error.message : "이력 조회에 실패했습니다."
          }
          onRetry={() => setReload((n) => n + 1)}
        />
      ) : !data ? (
        <ScreenLoading message="이력을 불러오는 중…" />
      ) : (
        <>
          <div className="sicost-table-viewport menu-management-history">
            <table className="sicost-table" aria-label="메뉴 변경 이력">
              <thead>
                <tr>
                  <th>일시</th>
                  <th>항목</th>
                  <th>변경 전</th>
                  <th>변경 후</th>
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
          <Pagination
            page={page}
            size={size}
            total={data.total}
            onPageChange={setPage}
            onSizeChange={(nextSize) => {
              setSize(nextSize);
              setPage(1);
            }}
            label="메뉴 변경 이력 페이지"
            testId="menu-history"
          />
        </>
      )}
    </Dialog>
  );
}
