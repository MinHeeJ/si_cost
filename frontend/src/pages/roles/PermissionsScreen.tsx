import { FieldHelp } from "../../shared/FieldHelp";
import { SegmentedTabList } from "../../shared/SegmentedTabs";
import { Fragment, useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { ApiError } from "../../shared/api";
import { Dialog } from "../../shared/Dialog";
import { EditorFooter } from "../../shared/EditorFooter";
import { ErrorBanner, StatusBadge } from "../../shared/Feedback";
import { HelpTooltip } from "../../shared/HelpTooltip";
import { Icon } from "../../shared/Icon";
import { Pagination } from "../../shared/Pagination";
import { useCommitRefresh } from "../../shared/useCommitRefresh";
import type { MasterHistoryPage } from "../apiops/masterHistoryApi";
import * as api from "./rolesApi";
import {
  usePermission,
  useFeaturePermission,
} from "../../shared/permission/PermissionBoundary";
import "./roles.css";

const actions = [
  ["inquire", "조회"],
  ["register", "등록"],
  ["update", "수정"],
  ["delete", "삭제"],
  ["upload", "업로드"],
  ["download", "다운로드"],
  ["confirm", "확정"],
] as const;
const scopeNames = { ALL: "전체", PART: "담당파트", PROJ: "담당프로젝트" };
const menuPageSize = 20;
function ScopeSelect({
  value,
  onChange,
  testId,
  disabled,
}: {
  value: api.DataScope;
  onChange(value: api.DataScope): void;
  testId: string;
  disabled?: boolean;
}) {
  return (
    <select
      id={testId}
      aria-label="데이터범위"
      data-testid={testId}
      disabled={disabled}
      value={value}
      onChange={(e) => onChange(e.target.value as api.DataScope)}
    >
      {Object.entries(scopeNames).map(([id, label]) => (
        <option key={id} value={id}>
          {label} ({id})
        </option>
      ))}
    </select>
  );
}
export function PermissionsScreen({
  onDirtyChange,
  historyActionTarget,
}: {
  onDirtyChange?(dirty: boolean): void;
  historyActionTarget?: HTMLElement | null;
}) {
  const canRegister = usePermission("UI-24", "REG"),
    canUpdate = usePermission("UI-24", "UPD"),
    canStop = useFeaturePermission("roleStop");
  const [roles, setRoles] = useState<api.ManagedRole[]>([]),
    [menus, setMenus] = useState<api.RoleMenu[]>([]);
  const [saved, setSaved] = useState<api.ManagedRoleDetail>(),
    [draft, setDraft] = useState<api.ManagedRoleDetail>();
  const [loading, setLoading] = useState(true),
    [error, setError] = useState<unknown>();
  const [page, setPage] = useState(1),
    [historyOpen, setHistoryOpen] = useState(false);
  const [menuSearch, setMenuSearch] = useState("");
  const [history, setHistory] = useState<MasterHistoryPage>(),
    [historyError, setHistoryError] = useState<unknown>();
  const [historyPage, setHistoryPage] = useState(1),
    [historySize, setHistorySize] = useState<20 | 50 | 100>(50);
  const [historyRefresh, setHistoryRefresh] = useState(0);
  const [modal, setModal] = useState<"NEW" | "COPY" | "STOP">(),
    [name, setName] = useState("");
  const [newScope, setNewScope] = useState<api.DataScope>("PROJ"),
    [sourceId, setSourceId] = useState("");
  const [source, setSource] = useState<api.ManagedRoleDetail>(),
    [sourceError, setSourceError] = useState<unknown>();
  const [modalError, setModalError] = useState<unknown>(),
    [localFields, setLocalFields] = useState<Record<string, string>>({});
  const [leave, setLeave] = useState<() => void>();
  const editorRef = useRef<HTMLElement>(null);
  const sequence = useRef(0),
    { pending, message, commit } = useCommitRefresh();
  const dirty =
    !!saved &&
    !!draft &&
    JSON.stringify(api.roleInput(saved)) !==
      JSON.stringify(api.roleInput(draft));
  const dirtyRef = useRef(dirty);
  dirtyRef.current = dirty;
  useEffect(() => {
    onDirtyChange?.(dirty);
    return () => onDirtyChange?.(false);
  }, [dirty, onDirtyChange]);
  const requestPending = useRef(false), lastReceived = useRef(0);
  async function load(id?: string) {
    const seq = ++sequence.current;
    requestPending.current = true;
    setLoading(true);
    setError(undefined);
    try {
      const [list, tree] = await Promise.all([
        api.getRoles(),
        api.getRoleMenus(),
      ]);
      const selected =
        id && list.items.some((r) => r.roleId === id)
          ? id
          : list.items[0]?.roleId;
      const detail = selected ? await api.getRole(selected) : undefined;
      if (seq !== sequence.current) return;
      setRoles(list.items);
      setMenus(tree.items);
      setSaved(detail);
      setDraft(detail);
      lastReceived.current = Date.now();
      setPage(1);
    } catch (e) {
      if (seq === sequence.current) {
        setError(e);
        throw e;
      }
    } finally {
      if (seq === sequence.current) { requestPending.current = false; setLoading(false); }
    }
  }
  useEffect(() => {
    void load().catch(() => {});
    return () => {
      sequence.current++;
    };
  }, []);
  useEffect(() => {
    const focus = () => {
      if (!dirtyRef.current && document.visibilityState === "visible" && !requestPending.current && Date.now()-lastReceived.current >= 30_000) void load(saved?.roleId).catch(() => {});
    };
    window.addEventListener("focus", focus);
    return () => window.removeEventListener("focus", focus);
  }, [saved?.roleId]);
  useEffect(() => {
    if (!dirty) return;
    const prevent = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = "";
    };
    window.addEventListener("beforeunload", prevent);
    return () => window.removeEventListener("beforeunload", prevent);
  }, [dirty]);
  useEffect(() => {
    if (modal !== "COPY" || !sourceId) return;
    let active = true;
    setSource(undefined);
    setSourceError(undefined);
    api.getRole(sourceId).then(
      (value) => {
        if (active) setSource(value);
      },
      (e) => {
        if (active) setSourceError(e);
      },
    );
    return () => {
      active = false;
    };
  }, [sourceId, modal]);
  useEffect(() => {
    if (!historyOpen || !saved) return;
    let active = true;
    setHistory(undefined);
    setHistoryError(undefined);
    api.getRoleHistory(saved.roleId, historyPage, historySize).then(
      (value) => {
        if (active) setHistory(value);
      },
      (e) => {
        if (active) setHistoryError(e);
      },
    );
    return () => {
      active = false;
    };
  }, [
    historyOpen,
    saved?.roleId,
    saved?.revision,
    historyPage,
    historySize,
    historyRefresh,
  ]);
  function guard(action: () => void) {
    if (dirty) setLeave(() => action);
    else action();
  }
  function open(mode: "NEW" | "COPY" | "STOP") {
    if (mode === "STOP" ? !canStop : !canRegister) return;
    setModalError(undefined);
    setLocalFields({});
    setName("");
    setNewScope("PROJ");
    setSourceId(saved?.roleId ?? roles[0]?.roleId ?? "");
    setModal(mode);
  }
  function committed() {
    for (const resource of [
      "roles",
      "business-users",
      "menus",
      "permission-settings",
      "projects",
      "sales-parts",
    ])
      window.dispatchEvent(new Event(`sicost:${resource}-committed`));
  }
  async function save() {
    if (!canUpdate || !draft || pending) return;
    setError(undefined);
    try {
      await commit(
        async () => {
          const result = await api.putRole(draft.roleId, api.roleInput(draft));
          setSaved(result.value);
          setDraft(result.value);
          committed();
        },
        () => load(draft.roleId),
      );
    } catch (e) {
      setError(e);
    }
  }
  async function create() {
    if (!canRegister) return;
    setModalError(undefined);
    setLocalFields({});
    const normalized = name.trim();
    if (!normalized || [...normalized].length > 50) {
      setLocalFields({ name: "이름은 1~50자로 입력해 주세요." });
      return;
    }
    if (modal === "COPY" && !source) return;
    let newId: string | undefined;
    try {
      await commit(
        async () => {
          const result = await api.createRole(
            modal === "COPY" && source
              ? {
                  mode: "COPY",
                  name: normalized,
                  sourceRoleId: source.roleId,
                  sourceRevision: source.revision,
                }
              : { mode: "NEW", name: normalized, dataScope: newScope },
          );
          newId = result.value.roleId;
          setSaved(result.value);
          setDraft(result.value);
          setModal(undefined);
          committed();
        },
        () => load(newId),
      );
    } catch (e) {
      setModalError(e);
    }
  }
  async function stop() {
    if (!canStop || !saved) return;
    try {
      await commit(
        async () => {
          await api.stopRole(saved.roleId, saved.revision);
          setModal(undefined);
          setSaved(undefined);
          setDraft(undefined);
          committed();
        },
        () => load(),
      );
    } catch (e) {
      setModalError(e);
    }
  }
  const fields = error instanceof ApiError ? error.fieldMessages : {};
  const modalFields = {
    ...(modalError instanceof ApiError ? modalError.fieldMessages : {}),
    ...localFields,
  };
  const search = menuSearch.trim().toLocaleLowerCase();
  const menuNames = new Map(menus.map((menu) => [menu.menuId, menu.name]));
  const filteredMenus = (draft?.menuPermissions ?? []).filter(
    (menu) =>
      !search ||
      [menu.name, menu.screenId, menuNames.get(menu.parentId)].some((value) =>
        value?.toLocaleLowerCase().includes(search),
      ),
  );
  const currentPage = Math.min(
    page,
    Math.max(1, Math.ceil(filteredMenus.length / menuPageSize)),
  );
  const rows = filteredMenus.slice(
    (currentPage - 1) * menuPageSize,
    currentPage * menuPageSize,
  );
  const groups = [...new Set(rows.map((row) => row.parentId))];
  const historyButton = (
    <button
      type="button"
      data-testid="role-history-open"
      aria-haspopup="dialog"
      disabled={!saved || loading}
      onClick={() => {
        setHistoryPage(1);
        setHistoryOpen(true);
      }}
    >
      역할 변경 이력
    </button>
  );
  return (
    <section
      ref={editorRef}
      className="roles-editor"
      data-screen-id="SCR-UI-24"
      data-testid="roles-screen"
      aria-busy={loading || pending}
    >
      {historyActionTarget
        ? createPortal(historyButton, historyActionTarget)
        : historyActionTarget === undefined && historyButton}
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
              : "역할 설정을 조회하지 못했습니다."
          }
          requestId={
            error instanceof ApiError ? error.body.requestId : undefined
          }
        />
      )}
      {Object.entries(fields).map(([field, text]) => (
        <p className="sicost-field-error" key={field}>
          {field}: {text}
        </p>
      ))}
      <div className="roles-toolbar">
        {roles.length > 0 && (
          <SegmentedTabList aria-label="역할">
            {roles.map((role) => (
              <button
                type="button"
                role="tab"
                data-testid={`role-tab-${role.roleId}`}
                data-action-id="R-TAB"
                key={role.roleId}
                aria-selected={saved?.roleId === role.roleId}
                disabled={pending || loading}
                onClick={() => {
                  if (saved?.roleId === role.roleId) return;
                  guard(() => {
                    setHistoryOpen(false);
                    void load(role.roleId).catch(() => {});
                  });
                }}
              >
                {role.name}
              </button>
            ))}
          </SegmentedTabList>
        )}
        <div className="roles-role-actions">
          <button
            data-testid="role-refresh"
            disabled={pending || loading}
            onClick={() => guard(() => void load(saved?.roleId).catch(() => {}))}
          >
            새로고침
          </button>
          {canRegister && (
            <>
              <button
                data-testid="role-copy-open"
                data-action-id="R-COPY-OPEN"
                disabled={pending || loading || !saved}
                onClick={() => open("COPY")}
              >
                역할 복사
              </button>
              <button
                className="sicost-primary"
                data-testid="role-new-open"
                data-action-id="R-NEW-OPEN"
                disabled={pending || loading}
                onClick={() => open("NEW")}
              >
                역할 추가
              </button>
            </>
          )}
        </div>
      </div>
      {loading && <p role="status" className={draft ? "sicost-refresh-status" : undefined}>{draft ? "갱신 중…" : "역할 설정을 불러오고 있습니다."}</p>}
      {!loading && !roles.length && !error && (
        <p className="sicost-empty" role="status">
          사용 중인 역할이 없습니다. 역할을 추가해 주세요.
        </p>
      )}
      {draft && (
        <div
          role="tabpanel"
          data-testid="role-editor-panel"
          aria-label={`${draft.name} 권한`}
        >
          <div className="roles-table">
            <div className="roles-settings" role="group" aria-label="역할 편집 설정">
              <div className="roles-field-line">
                <div className="roles-field-title">
                  <label htmlFor="role-scope">데이터 범위</label>
                  <FieldHelp label="데이터 범위" screenId="UI-24" />
                </div>
                <ScopeSelect
                  testId="role-scope"
                  disabled={!canUpdate || pending || loading}
                  value={draft.dataScope}
                  onChange={(value) => setDraft({ ...draft, dataScope: value })}
                />
              </div>
              <div className="roles-sensitive">
                <label>
                  <input
                    data-testid="role-sensitive"
                    disabled={!canUpdate || pending || loading}
                    type="checkbox"
                    checked={draft.sensitiveView}
                    onChange={(e) =>
                      setDraft({ ...draft, sensitiveView: e.target.checked })
                    }
                  />
                  민감항목 보기
                </label>
                <HelpTooltip label="민감항목 보기" iconOnly>
                  급여·지급단가·개인별 원가·거래처 지급계좌는 허용된 업무와
                  담당 범위에서만 표시됩니다.
                </HelpTooltip>
              </div>
              {canStop && !draft.systemRole && (
                <button
                  className="roles-stop"
                  data-testid="role-stop-open"
                  disabled={pending || loading}
                  onClick={() => guard(() => open("STOP"))}
                >
                  역할 사용 중지
                </button>
              )}
            </div>
            <div className="roles-toolbar roles-table-toolbar">
              <div className="roles-field-line">
                <div className="roles-field-title">
                  <h2>메뉴별 권한</h2>
                  <HelpTooltip label="메뉴별 권한" iconOnly>
                    각 동작은 독립적으로 저장되며 업무 접근에는 조회 권한도
                    필요합니다. 확정은 월마감, 영업파트 정산확정·취소,
                    견적승인·반려에 적용됩니다. 마감해제는 별도 관리자 자격을
                    검사합니다.
                  </HelpTooltip>
                </div>
                <label className="roles-search">
                  <Icon name="search" size={16} />
                  <input
                    type="search"
                    aria-label="메뉴 검색"
                    value={menuSearch}
                    placeholder="메뉴명 또는 화면 번호"
                    onChange={(event) => {
                      setMenuSearch(event.target.value);
                      setPage(1);
                    }}
                  />
                </label>
              </div>
              <strong className="roles-total">총 {filteredMenus.length}개</strong>
            </div>
            <div className="sicost-table-viewport roles-matrix">
              <table className="sicost-table" aria-label="메뉴별 7동작 권한">
                <colgroup>
                  <col className="roles-menu-column" />
                  <col span={actions.length} />
                </colgroup>
                <thead>
                  <tr>
                    <th scope="col">메뉴</th>
                    {actions.map(([key, label]) => (
                      <th scope="col" key={key}>
                        <span className="roles-action-heading">
                          {label}
                          <FieldHelp label={label} kind="B" />
                        </span>
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {groups.map((parentId) => (
                    <Fragment key={parentId}>
                      <tr
                        data-testid={`role-group-${parentId}`}
                        className="roles-group"
                      >
                        <th colSpan={8}>
                          {menus.find((menu) => menu.menuId === parentId)?.name ??
                            parentId}
                        </th>
                      </tr>
                      {rows
                        .filter((row) => row.parentId === parentId)
                        .map((row) => (
                          <tr
                            key={row.menuId}
                            data-testid={`role-menu-${row.menuId}`}
                          >
                            <th scope="row">
                              {row.name}{" "}
                              {!row.use && (
                                <StatusBadge tone="lock">사용 중지</StatusBadge>
                              )}
                            </th>
                            {actions.map(([key, label]) => (
                              <td key={key}>
                                <label className="roles-permission-cell">
                                  <input
                                    type="checkbox"
                                    aria-label={`${row.name} ${label}`}
                                    data-testid={`role-${row.menuId}-${key}`}
                                    checked={row[key]}
                                    disabled={!canUpdate || pending || loading}
                                    onChange={(event) => {
                                      const checked = event.target.checked;
                                      setDraft({
                                        ...draft,
                                        menuPermissions: draft.menuPermissions.map(
                                          (permission) =>
                                            permission.menuId === row.menuId
                                              ? { ...permission, [key]: checked }
                                              : permission,
                                        ),
                                      });
                                    }}
                                  />
                                </label>
                              </td>
                            ))}
                          </tr>
                        ))}
                    </Fragment>
                  ))}
                </tbody>
              </table>
              {!filteredMenus.length && (
                <p className="sicost-empty">
                  {search && draft.menuPermissions.length
                    ? "검색 조건에 맞는 메뉴가 없습니다."
                    : "등록된 서브메뉴가 없습니다."}
                </p>
              )}
            </div>
          </div>
          <Pagination
            page={currentPage}
            size={menuPageSize}
            total={filteredMenus.length}
            onPageChange={setPage}
            disabled={pending || loading}
            label="메뉴별 권한 페이지"
            unit="개"
            testId="role-menu"
            hideSingle
          />
          {canUpdate && (
            <EditorFooter editorRef={editorRef} label="선택한 역할 저장">
              <div className="roles-footer-note">
                <small>데이터 범위, 민감항목 보기, 메뉴별 권한을 함께 저장합니다.</small>
                {dirty && (
                  <StatusBadge tone="warn">저장 전</StatusBadge>
                )}
              </div>
              <div className="roles-actions">
                <button
                  type="button"
                  data-testid="role-cancel"
                  disabled={pending || loading || !dirty}
                  onClick={() => setDraft(saved)}
                >
                  취소
                </button>
                <button
                  type="button"
                  data-testid="role-save"
                  className="sicost-primary"
                  disabled={pending || loading || !dirty}
                  onClick={() => void save()}
                >
                  저장
                </button>
              </div>
            </EditorFooter>
          )}
        </div>
      )}
      {historyOpen && saved && (
        <Dialog
          open
          wide
          hideCancel
          title={`${saved.name} 변경 이력`}
          testId="role-history-panel"
          confirmTestId="role-history-close"
          confirmLabel="닫기"
          onConfirm={() => setHistoryOpen(false)}
          onCancel={() => setHistoryOpen(false)}
          footerStart={
            <button
              type="button"
              data-testid="role-history-refresh"
              onClick={() => setHistoryRefresh((value) => value + 1)}
            >
              이력 다시 조회
            </button>
          }
        >
          {historyError != null && (
            <ErrorBanner
              message={
                historyError instanceof Error
                  ? historyError.message
                  : "변경 이력을 조회하지 못했습니다."
              }
            />
          )}
          {!history && !historyError && (
            <p role="status">변경 이력을 불러오고 있습니다.</p>
          )}
          {history && (
            <>
              <div className="sicost-table-viewport roles-history-table">
                <table className="sicost-table" aria-label="역할 변경 이력">
                  <thead>
                    <tr>
                      <th>일시</th>
                      <th>대상</th>
                      <th>항목</th>
                      <th>변경 전</th>
                      <th>변경 후</th>
                      <th>처리자</th>
                      <th>사유</th>
                    </tr>
                  </thead>
                  <tbody>
                    {history.items.map((item) => (
                      <tr
                        data-testid={`role-history-${item.historyId}`}
                        key={item.historyId}
                      >
                        <td>{item.createdAt}</td>
                        <td>{item.targetKey}</td>
                        <td>{item.itemName}</td>
                        <td>
                          <details>
                            <summary>변경 전 값 보기</summary>
                            {item.beforeValue ?? "–"}
                          </details>
                        </td>
                        <td>{item.afterValue ?? "–"}</td>
                        <td>
                          {item.actorName} ({item.actorId})
                        </td>
                        <td>{item.reason ?? "–"}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              {!history.items.length && (
                <p className="sicost-empty">변경 이력이 없습니다.</p>
              )}
              <Pagination
                page={historyPage}
                size={historySize}
                total={history.total}
                onPageChange={setHistoryPage}
                onSizeChange={(size) => {
                  setHistoryPage(1);
                  setHistorySize(size);
                }}
                label="역할 변경 이력 페이지"
                testId="role-history"
              />
            </>
          )}
        </Dialog>
      )}
      {(modal === "NEW" || modal === "COPY") && (
        <Dialog
          open
          title={modal === "NEW" ? "역할 추가" : "역할 복사"}
          testId="role-create-dialog"
          confirmTestId="role-create-confirm"
          cancelTestId="role-create-cancel"
          pending={pending}
          confirmLabel={modal === "NEW" ? "추가" : "복사"}
          confirmDisabled={modal === "COPY" && !source}
          onCancel={() => setModal(undefined)}
          onConfirm={create}
          noValidate
        >
          {modalError != null && (
            <ErrorBanner
              message={
                modalError instanceof Error
                  ? modalError.message
                  : "역할을 생성하지 못했습니다."
              }
            />
          )}
          <label className="sicost-field">
            이름
            <input
              data-testid="role-create-name"
              value={name}
              aria-invalid={!!modalFields.name}
              onChange={(e) => setName(e.target.value)}
            />
          </label>
          {Object.entries(modalFields).map(([field, text]) => (
            <p role="alert" key={field} className="sicost-field-error">
              {text}
            </p>
          ))}
          {modal === "NEW" ? (
            <>
              <label className="sicost-field">
                데이터범위
                <FieldHelp label="데이터범위" />
                <ScopeSelect
                  testId="role-create-scope"
                  value={newScope}
                  onChange={setNewScope}
                />
              </label>
              <p>새 역할은 민감 항목 보기와 모든 메뉴 권한이 해제된 상태로 시작합니다. 역할 ID와 표시 순서는 자동으로 지정됩니다.</p>
            </>
          ) : (
            <>
              <label className="sicost-field">
                원본 역할
                <select
                  data-testid="role-copy-source"
                  value={sourceId}
                  onChange={(e) => setSourceId(e.target.value)}
                >
                  {roles.map((role) => (
                    <option key={role.roleId} value={role.roleId}>
                      {role.name}
                    </option>
                  ))}
                </select>
              </label>
              {sourceError != null && (
                <ErrorBanner
                  message={
                    sourceError instanceof Error
                      ? sourceError.message
                      : "원본을 조회하지 못했습니다."
                  }
                />
              )}
              {!source && !sourceError && (
                <p role="status">저장된 원본을 조회하고 있습니다.</p>
              )}
              {source && (
                <p>
                  저장 원본 · {scopeNames[source.dataScope]} · 민감항목 보기{" "}
                  {source.sensitiveView ? "허용" : "해제"} · 메뉴{" "}
                  {source.menuPermissions.length}개
                </p>
              )}
              <p>
                미저장 초안은 복사하지 않습니다. 복사본은 기본
                ADMIN/PM/SALES_LDR 업무 자격을 상속하지 않습니다.
              </p>
            </>
          )}
        </Dialog>
      )}
      {modal === "STOP" && saved && (
        <Dialog
          open
          title="추가 역할 사용 중지"
          testId="role-stop-dialog"
          confirmTestId="role-stop-confirm"
          cancelTestId="role-stop-cancel"
          pending={pending}
          confirmLabel="중지"
          onCancel={() => setModal(undefined)}
          onConfirm={stop}
        >
          <p>
            대상: {saved.name} ({saved.roleId})
          </p>
          <p>
            시스템 역할은 중지할 수 없고 연결 사용자가 있으면 시스템 ›
            사용자에서 먼저 역할을 빼야 합니다.
          </p>
          {modalError != null && (
            <ErrorBanner
              message={
                modalError instanceof Error
                  ? modalError.message
                  : "중지하지 못했습니다."
              }
            />
          )}
        </Dialog>
      )}
      {leave && (
        <Dialog
          open
          title="저장하지 않은 내용이 있습니다."
          testId="role-dirty-dialog"
          confirmTestId="role-dirty-discard"
          cancelTestId="role-dirty-stay"
          confirmLabel="버리기/나가기"
          cancelLabel="계속 작성"
          onCancel={() => setLeave(undefined)}
          onConfirm={() => {
            const action = leave;
            setLeave(undefined);
            setDraft(saved);
            action();
          }}
        >
          <p>작성 중인 내용을 버리고 이동하시겠습니까?</p>
        </Dialog>
      )}
    </section>
  );
}
