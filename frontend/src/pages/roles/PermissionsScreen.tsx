import { SegmentedTabList } from "../../shared/SegmentedTabs";
import { Fragment, useEffect, useRef, useState } from "react";
import { ApiError } from "../../shared/api";
import { Dialog } from "../../shared/Dialog";
import { ErrorBanner, StatusBadge } from "../../shared/Feedback";
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
}: {
  onDirtyChange?(dirty: boolean): void;
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
  async function load(id?: string) {
    const seq = ++sequence.current;
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
      setPage(1);
    } catch (e) {
      if (seq === sequence.current) {
        setError(e);
        throw e;
      }
    } finally {
      if (seq === sequence.current) setLoading(false);
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
      if (!dirtyRef.current) void load(saved?.roleId).catch(() => {});
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
  const rows = draft?.menuPermissions.slice((page - 1) * 20, page * 20) ?? [];
  const groups = [...new Set(rows.map((row) => row.parentId))];
  return (
    <section
      className="roles-editor"
      data-screen-id="SCR-UI-24"
      data-testid="roles-screen"
      aria-busy={loading || pending}
    >
      <div className="roles-toolbar">
        <h2>역할별 권한 설정</h2>
        <div>
          {canRegister && (
            <button
              data-testid="role-new-open"
              data-action-id="R-NEW-OPEN"
              disabled={pending || loading}
              onClick={() => open("NEW")}
            >
              역할 추가
            </button>
          )}
          {canRegister && (
            <button
              data-testid="role-copy-open"
              data-action-id="R-COPY-OPEN"
              disabled={pending || loading || !saved}
              onClick={() => open("COPY")}
            >
              역할 복사
            </button>
          )}
        </div>
      </div>
      <p role="note" className="sicost-banner sicost-tone-info">
        저장된 역할·메뉴 권한과 담당 범위를 적용합니다.
      </p>
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
        <SegmentedTabList
          as="div"
          role="tablist"
          aria-label="역할"
          className="roles-tabs"
        >
          {roles.map((role) => (
            <button
              role="tab"
              data-testid={`role-tab-${role.roleId}`}
              data-action-id="R-TAB"
              key={role.roleId}
              aria-selected={saved?.roleId === role.roleId}
              disabled={pending || loading}
              onClick={() =>
                guard(() => {
                  setHistoryOpen(false);
                  void load(role.roleId).catch(() => {});
                })
              }
            >
              {role.name}
            </button>
          ))}
        </SegmentedTabList>
        <button
          data-testid="role-refresh"
          disabled={pending || loading}
          onClick={() => guard(() => void load(saved?.roleId).catch(() => {}))}
        >
          최신값 다시 조회
        </button>
      </div>
      {loading && <p role="status">역할 설정을 불러오고 있습니다.</p>}
      {!loading && !roles.length && !error && (
        <p className="sicost-empty" role="status">
          사용 중인 역할이 없습니다. 역할을 추가해 주세요.
        </p>
      )}
      {draft && !loading && (
        <div
          role="tabpanel"
          data-testid="role-editor-panel"
          aria-label={`${draft.name} 권한`}
        >
          <div className="roles-toolbar">
            <h3>
              {draft.name} <small>{draft.roleId}</small>
            </h3>
            <StatusBadge tone={draft.systemRole ? "info" : "neutral"}>
              {draft.systemRole ? "시스템 역할" : "추가 역할"}
            </StatusBadge>
          </div>
          <div className="roles-settings">
            {!canUpdate && (
              <p role="note">
                수정 권한이 없어 보기만 할 수 있습니다(권한 › 메뉴×동작 표의
                수정).
              </p>
            )}
            <label className="sicost-field">
              데이터범위
              <ScopeSelect
                testId="role-scope"
                disabled={!canUpdate || pending}
                value={draft.dataScope}
                onChange={(value) => setDraft({ ...draft, dataScope: value })}
              />
            </label>
            <label>
              <input
                data-testid="role-sensitive"
                disabled={!canUpdate || pending}
                type="checkbox"
                checked={draft.sensitiveView}
                onChange={(e) =>
                  setDraft({ ...draft, sensitiveView: e.target.checked })
                }
              />{" "}
              민감항목 보기
            </label>
            <span>
              급여·지급단가·개인별원가·거래처 지급계좌는 허용된 업무와 담당
              범위에서만 표시됩니다.
            </span>
          </div>
          <div className="sicost-table-viewport">
            <table className="sicost-table" aria-label="메뉴별 7동작 권한">
              <thead>
                <tr>
                  <th>서브메뉴</th>
                  {actions.map(([key, label]) => (
                    <th key={key}>{label}</th>
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
                              <input
                                type="checkbox"
                                aria-label={`${row.name} ${label}`}
                                data-testid={`role-${row.menuId}-${key}`}
                                checked={row[key]}
                                disabled={!canUpdate || pending}
                                onChange={(e) =>
                                  setDraft({
                                    ...draft,
                                    menuPermissions: draft.menuPermissions.map(
                                      (p) =>
                                        p.menuId === row.menuId
                                          ? { ...p, [key]: e.target.checked }
                                          : p,
                                    ),
                                  })
                                }
                              />
                            </td>
                          ))}
                        </tr>
                      ))}
                  </Fragment>
                ))}
              </tbody>
            </table>
            {!draft.menuPermissions.length && (
              <p className="sicost-empty">등록된 서브메뉴가 없습니다.</p>
            )}
          </div>
          <p className="roles-hint">
            각 동작은 독립적으로 저장되며 업무 접근에는 조회 권한도 필요합니다.
            확정은 월마감·영업파트 정산확정/취소·견적승인/반려에 적용됩니다.
            마감해제는 별도 관리자 자격을 검사합니다.
          </p>
          <div className="roles-toolbar roles-savebar">
            <div>
              <button
                data-testid="role-page-previous"
                disabled={page === 1}
                onClick={() => setPage(page - 1)}
              >
                이전
              </button>
              <span>
                {" "}
                {page} /{" "}
                {Math.max(1, Math.ceil(draft.menuPermissions.length / 20))}{" "}
              </span>
              <button
                data-testid="role-page-next"
                disabled={page * 20 >= draft.menuPermissions.length}
                onClick={() => setPage(page + 1)}
              >
                다음
              </button>
            </div>
            <div>
              <button
                data-testid="role-history-open"
                onClick={() => {
                  setHistoryPage(1);
                  setHistoryOpen((value) => !value);
                }}
              >
                변경 이력
              </button>
              {canStop && !draft.systemRole && (
                <button
                  data-testid="role-stop-open"
                  disabled={pending}
                  onClick={() => guard(() => open("STOP"))}
                >
                  사용 중지
                </button>
              )}
              {canUpdate && (
                <button
                  data-testid="role-cancel"
                  disabled={pending || !dirty}
                  onClick={() => setDraft(saved)}
                >
                  취소
                </button>
              )}
              {canUpdate && (
                <button
                  data-testid="role-save"
                  className="sicost-primary"
                  disabled={pending || !dirty}
                  onClick={() => void save()}
                >
                  저장
                </button>
              )}
            </div>
          </div>
          <details>
            <summary>저장 설정 식별값</summary>
            <p className="roles-revision">역할 revision: {saved?.revision}</p>
            <p className="roles-revision">
              설정 revision: {saved?.settingsRevision}
            </p>
          </details>
        </div>
      )}
      {historyOpen && saved && (
        <section data-testid="role-history-panel" aria-label="역할 변경 이력">
          <div className="roles-toolbar">
            <h3>{saved.name} 변경 이력</h3>
            <button
              data-testid="role-history-close"
              onClick={() => setHistoryOpen(false)}
            >
              닫기
            </button>
          </div>
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
              <div className="sicost-table-viewport">
                <table className="sicost-table">
                  <thead>
                    <tr>
                      <th>일시</th>
                      <th>대상</th>
                      <th>항목</th>
                      <th>전</th>
                      <th>후</th>
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
                            <summary>전후값 펼침/접기</summary>
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
              <button
                data-testid="role-history-prev"
                disabled={historyPage === 1}
                onClick={() => setHistoryPage(historyPage - 1)}
              >
                이력 이전
              </button>
              <button
                data-testid="role-history-next"
                disabled={historyPage * historySize >= history.total}
                onClick={() => setHistoryPage(historyPage + 1)}
              >
                이력 다음
              </button>
              <select
                aria-label="이력 표시 수"
                data-testid="role-history-size"
                value={historySize}
                onChange={(e) => {
                  setHistoryPage(1);
                  setHistorySize(Number(e.target.value) as 20 | 50 | 100);
                }}
              >
                {[20, 50, 100].map((size) => (
                  <option key={size}>{size}</option>
                ))}
              </select>
            </>
          )}
          <button
            data-testid="role-history-refresh"
            onClick={() => setHistoryRefresh((value) => value + 1)}
          >
            이력 다시 조회
          </button>
        </section>
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
                <ScopeSelect
                  testId="role-create-scope"
                  value={newScope}
                  onChange={setNewScope}
                />
              </label>
              <p>민감 N · 모든 서브메뉴 7동작 N · ID·순서는 서버 발급</p>
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
                  저장 원본: {scopeNames[source.dataScope]} · 민감{" "}
                  {source.sensitiveView ? "Y" : "N"} · 메뉴{" "}
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
