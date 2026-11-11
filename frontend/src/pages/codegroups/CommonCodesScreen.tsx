import { useEffect, useRef, useState, type ReactNode } from "react";
import { DataTable, type Column } from "../../shared/DataTable";
import { Dialog } from "../../shared/Dialog";
import { Text } from "../../shared/Text";
import { ErrorBanner } from "../../shared/Feedback";
import { ApiError } from "../../shared/api";
import { Icon } from "../../shared/Icon";
import { normalizeDecimal } from "../../shared/format";
import * as api from "./codeGroupsApi";
import type {
  Code,
  CodeChange,
  CodeGroup,
  GroupCodesPage,
} from "./codeGroupsApi";
import "./codegroups.css";

export type CommonCodeTab = "codes" | "mappings" | "standards" | "history";
export type CommonCodesScreenProps = {
  embedded?: boolean;
  onModeChange?(mode: "codes" | "valueCodes"): void;
  panelDirty?: boolean;
  panels?: Partial<Record<Exclude<CommonCodeTab, "codes">, ReactNode>>;
  onDirtyChange?(dirty: boolean): void;
  pageSize?: 20 | 50 | 100;
  dense?: boolean;
  columnView?: { order: string[]; hidden: string[] };
  onColumnsChange?(): void;
  onDensityChange?(): void;
  onPageSizeChange?(size: 20 | 50 | 100): void | Promise<void>;
};
const tabs: { id: CommonCodeTab; label: string }[] = [
  { id: "codes", label: "코드" },
  { id: "mappings", label: "엑셀 표기 대응" },
  { id: "standards", label: "운영 기준값" },
  { id: "history", label: "변경 이력" },
];
const extraLabels = (group: string): string[] =>
  ({
    JOB: ["관련 직무", "견적 기준 단가(원)"],
    SKILL: ["관련 직무"],
    PROJ_STEP: ["진척률", "계약유형"],
    BANK: ["은행 번호"],
    RSM_LABEL: ["이름표 유형"],
    RSM_JOB_KW: ["관련 직무"],
  })[group] ?? [];
const cleanCode = (value: string) => value.trim().toUpperCase();
function CodeGroupLockMark() {
  return (
    <svg
      width="14"
      height="14"
      viewBox="0 0 16 16"
      fill="none"
      stroke="currentColor"
      aria-label="시스템 잠금"
    >
      <path d="M5 7V4a3 3 0 0 1 6 0v3M4 7h8v7H4z" />
    </svg>
  );
}

export function CommonCodesScreen({
  embedded = false,
  onModeChange,
  panelDirty = false,
  panels,
  onDirtyChange,
  pageSize = 50,
  dense = false,
  columnView,
  onColumnsChange,
  onDensityChange,
  onPageSizeChange,
}: CommonCodesScreenProps) {
  const [groups, setGroups] = useState<CodeGroup[]>([]),
    [group, setGroup] = useState<CodeGroup>();
  const [tab, setTab] = useState<CommonCodeTab>("codes");
  const [data, setData] = useState<GroupCodesPage>(),
    [page, setPage] = useState(1),
    [size, setSize] = useState<20 | 50 | 100>(50);
  const [drafts, setDrafts] = useState<Record<string, CodeChange>>({});
  const originals = useRef<Record<string, Code>>({});
  const [revision, setRevision] = useState(""),
    [loading, setLoading] = useState(true),
    [pending, setPending] = useState(false);
  const [error, setError] = useState<unknown>(),
    [notice, setNotice] = useState(""),
    [fields, setFields] = useState<Record<string, string>>({});
  const [modal, setModal] = useState<
    "group" | "code" | "blocked" | "move" | null
  >(null);
  const [destination, setDestination] = useState<(() => void) | null>(null);
  const [deleting, setDeleting] = useState<Code>();
  const [form, setForm] = useState<Record<string, string>>({});
  const requestNumber = useRef(0),
    submitting = useRef(false);
  const dirty = Object.keys(drafts).length > 0;
  const system = group?.sysCodeYn === "Y",
    valueMode = !system && group?.valCodeYn === "Y";
  useEffect(() => {
    onModeChange?.(valueMode ? "valueCodes" : "codes");
  }, [valueMode, onModeChange]);
  const extras = extraLabels(group?.groupId ?? "");
  useEffect(() => {
    setSize(pageSize);
    setPage(1);
  }, [pageSize]);
  useEffect(() => {
    onDirtyChange?.(dirty);
  }, [dirty, onDirtyChange]);
  useEffect(() => () => onDirtyChange?.(false), [onDirtyChange]);
  useEffect(() => {
    const leave = (event: BeforeUnloadEvent) => {
      if (dirty) {
        event.preventDefault();
        event.returnValue = "";
      }
    };
    window.addEventListener("beforeunload", leave);
    return () => window.removeEventListener("beforeunload", leave);
  }, [dirty]);
  async function loadGroups() {
    const result = await api.getCodeGroups();
    setGroups(result.items);
    return result.items;
  }
  useEffect(() => {
    let active = true;
    api
      .getCodeGroups()
      .then((result) => {
        if (active) {
          setGroups(result.items);
          setGroup(result.items[0]);
          if (!result.items.length) setLoading(false);
        }
      })
      .catch((e) => {
        if (active) {
          setError(e);
          setLoading(false);
        }
      });
    return () => {
      active = false;
    };
  }, []);
  async function loadCodes(
    selected: CodeGroup,
    targetPage: number,
    targetSize: 20 | 50 | 100,
    resetRevision = false,
  ) {
    const id = ++requestNumber.current;
    setLoading(true);
    setError(undefined);
    try {
      const result = await api.getGroupCodes(
        selected.groupId,
        targetPage,
        targetSize,
      );
      if (id !== requestNumber.current) return;
      setData(result);
      result.items.forEach((row) => {
        originals.current[row.code] = row;
      });
      if (resetRevision || !dirty) setRevision(result.groupRevision);
    } catch (e) {
      if (id === requestNumber.current) setError(e);
      throw e;
    } finally {
      if (id === requestNumber.current) setLoading(false);
    }
  }
  useEffect(() => {
    if (group) void loadCodes(group, page, size).catch(() => {});
  }, [group?.groupId, page, size]);
  function reset() {
    setDrafts({});
    setFields({});
    setError(undefined);
    setNotice("");
    if (data) setRevision(data.groupRevision);
  }
  function select(next: CodeGroup) {
    reset();
    originals.current = {};
    setData(undefined);
    setPage(1);
    setGroup(next);
  }
  function guard(action: () => void) {
    if (!dirty && !panelDirty) {
      action();
      return;
    }
    setDestination(() => action);
    setModal("move");
  }
  function protectedAction(action: () => void) {
    if (dirty || panelDirty) setModal("blocked");
    else action();
  }
  function fail(e: unknown) {
    setError(e);
    if (e instanceof ApiError) setFields(e.fieldMessages);
  }
  useEffect(() => {
    if (Object.keys(fields).length)
      document
        .querySelector<HTMLInputElement>(
          '.codegroups-screen [aria-invalid="true"]',
        )
        ?.focus();
  }, [fields]);
  function patch(row: Code, key: keyof CodeChange, value: string) {
    setFields({});
    setError(undefined);
    setDrafts((previous) => {
      const next = { ...previous };
      const change = {
        ...(next[row.code] ?? { code: row.code, isNew: false }),
        [key]: value,
      };
      if (!change.isNew) {
        const original = originals.current[row.code];
        if (original && String(original[key as keyof Code] ?? "") === value)
          delete change[key];
        if (Object.keys(change).length === 2) {
          delete next[row.code];
          return next;
        }
      }
      next[row.code] = change;
      return next;
    });
  }
  function display(row: Code): Code {
    return { ...row, ...drafts[row.code] };
  }
  const newRows = Object.values(drafts)
    .filter((change) => change.isNew)
    .map((change) => {
      const base: Code = {
        code: change.code,
        name: "",
        sortOrder: 0,
        useYn: "Y",
        addVal1: null,
        addVal2: null,
        addVal3: null,
        codeVal: null,
        unitNm: null,
      };
      return { ...base, ...change };
    });
  const rows = [...(data?.items ?? []).map(display), ...newRows];
  function rowError(code: string, key: string) {
    const index = Object.keys(drafts).indexOf(code);
    return (
      fields[`changes[${index}].${key}`] ?? fields[`changes.${index}.${key}`]
    );
  }
  const editable = (
    row: Code,
    key: "name" | "codeVal" | "addVal1" | "addVal2",
    label: string,
    readOnly = false,
  ) => (
    <Text
      label={`${row.code} ${label}`}
      value={row[key] ?? ""}
      readOnly={readOnly || pending}
      error={rowError(row.code, key)}
      inputMode={key === "codeVal" ? "decimal" : undefined}
      className={key === "codeVal" ? "sicost-decimal" : undefined}
      onChange={(value) => patch(row, key, value)}
    />
  );
  const columns: Column<Code>[] = [
    {
      id: "code",
      label: "코드",
      render: (row) => (
        <span className={row.useYn === "N" ? "codegroups-inactive" : ""}>
          {row.code}
        </span>
      ),
    },
    {
      id: "name",
      label: "이름",
      render: (row) => editable(row, "name", "이름", Boolean(valueMode)),
    },
  ];
  if (valueMode)
    columns.push(
      {
        id: "codeVal",
        label: "값",
        render: (row) => editable(row, "codeVal", "값"),
      },
      { id: "unitNm", label: "단위", render: (row) => row.unitNm },
    );
  else {
    extras.forEach((label, index) =>
      columns.push({
        id: `addVal${index + 1}`,
        label,
        render: (row) =>
          editable(row, index === 0 ? "addVal1" : "addVal2", label, system),
      }),
    );
    columns.push(
      {
        id: "sortOrder",
        label: "순서",
        align: "center",
        render: (row) =>
          drafts[row.code]?.isNew ? "저장 시 부여" : row.sortOrder,
      },
      {
        id: "useYn",
        label: "사용",
        align: "center",
        render: (row) => (
          <>
            <input
              data-testid={`common-codes-use-${row.code.toLowerCase().replace(/_/g, "-")}`}
              type="checkbox"
              aria-label={`${row.code} 사용`}
              aria-invalid={Boolean(rowError(row.code, "useYn"))}
              aria-describedby={
                rowError(row.code, "useYn")
                  ? `codegroups-use-error-${row.code}`
                  : undefined
              }
              checked={row.useYn === "Y"}
              disabled={system || pending || drafts[row.code]?.isNew}
              onChange={(event) =>
                patch(row, "useYn", event.target.checked ? "Y" : "N")
              }
            />
            {rowError(row.code, "useYn") && (
              <span
                id={`codegroups-use-error-${row.code}`}
                role="alert"
                className="sicost-field-error"
              >
                {rowError(row.code, "useYn")}
              </span>
            )}
          </>
        ),
      },
    );
    if (!system)
      columns.push({
        id: "delete",
        label: "삭제",
        render: (row) => (
          <button
            data-testid={`common-codes-delete-${row.code.toLowerCase().replace(/_/g, "-")}`}
            type="button"
            aria-label={`${row.code} 삭제`}
            disabled={pending}
            onClick={() => {
              if (drafts[row.code]?.isNew)
                setDrafts((previous) => {
                  const next = { ...previous };
                  delete next[row.code];
                  return next;
                });
              else protectedAction(() => setDeleting(row));
            }}
          >
            삭제
          </button>
        ),
      });
  }
  async function save() {
    if (!group || !dirty || submitting.current) return;
    submitting.current = true;
    setPending(true);
    setError(undefined);
    setNotice("");
    try {
      const result = await api.saveGroupCodes(group.groupId, {
        expectedRevision: revision,
        changes: Object.values(drafts),
      });
      setDrafts({});
      setFields({});
      setRevision(result.groupRevision);
      originals.current = Object.fromEntries(
        result.items.map((row) => [row.code, row]),
      );
      const targetPage = Math.min(
        page,
        Math.max(1, Math.ceil(result.items.length / size)),
      );
      setPage(targetPage);
      setData({
        items: result.items.slice((targetPage - 1) * size, targetPage * size),
        total: result.items.length,
        page: targetPage,
        size,
        groupRevision: result.groupRevision,
      });
      if (result.changed) api.notifyCodeGroupsCommitted(group.groupId);
      setNotice(
        result.message ??
          (result.changed ? "저장했습니다." : "바뀐 내용이 없습니다."),
      );
      try {
        await loadCodes(group, targetPage, size, true);
        await loadGroups();
      } catch {
        setError(undefined);
        setNotice("저장됐지만 다시 불러오지 못했습니다. 다시 조회해 주세요.");
      }
    } catch (e) {
      fail(e);
    } finally {
      submitting.current = false;
      setPending(false);
    }
  }
  async function addGroup() {
    setFields({});
    const groupId = cleanCode(form.groupId ?? ""),
      name = (form.name ?? "").trim();
    const errors: Record<string, string> = {};
    if (!/^[A-Z][A-Z0-9_]{0,29}$/.test(groupId))
      errors.groupId = "대문자 시작, 영숫자/_ 최대 30자";
    if (!name || name.length > 50) errors.name = "그룹 이름은 1~50자입니다.";
    if (Object.keys(errors).length) {
      setFields(errors);
      return;
    }
    setPending(true);
    setError(undefined);
    try {
      await api.createCodeGroup({ groupId, name });
      setModal(null);
      setTab("codes");
      api.notifyCodeGroupsCommitted(groupId);
      try {
        const list = await loadGroups();
        const next = list.find((item) => item.groupId === groupId);
        if (next) select(next);
        setNotice("그룹을 추가했습니다.");
      } catch {
        setNotice("저장됐지만 다시 불러오지 못했습니다. 다시 조회해 주세요.");
      }
    } catch (e) {
      fail(e);
      if (e instanceof ApiError) {
        setFields({
          ...e.fieldMessages,
          ...(e.body.code === "DUPLICATE_GROUP"
            ? { groupId: e.message }
            : e.body.code === "DUPLICATE_GROUP_NAME"
              ? { name: e.message }
              : {}),
        });
      }
    } finally {
      setPending(false);
    }
  }
  function addCode() {
    const code = cleanCode(form.code ?? ""),
      name = (form.name ?? "").trim();
    const errors: Record<string, string> = {};
    if (!/^[A-Z0-9_]{1,20}$/.test(code))
      errors.code = "영숫자/_ 1~20자로 입력하세요.";
    if (!name || name.length > 100) errors.name = "이름은 1~100자입니다.";
    if (originals.current[code] || drafts[code])
      errors.code = "이미 등록된 코드입니다.";
    if (!valueMode)
      extras.forEach((_, i) => {
        if (!(form[`addVal${i + 1}`] ?? "").trim())
          errors[`addVal${i + 1}`] = "필수 항목입니다.";
      });
    setFields(errors);
    if (Object.keys(errors).length) return;
    const change: CodeChange = { code, isNew: true, name };
    if (valueMode) {
      try {
        change.codeVal = normalizeDecimal(form.codeVal ?? "", true);
      } catch (e) {
        setFields({
          codeVal: e instanceof Error ? e.message : "값을 확인해 주세요.",
        });
        return;
      }
      change.unitNm = form.unitNm ?? "원";
    } else
      extras.forEach((_, i) => {
        if (i === 0) change.addVal1 = form.addVal1;
        else change.addVal2 = form.addVal2;
      });
    setDrafts((previous) => ({ ...previous, [code]: change }));
    setModal(null);
  }
  async function deleteCode() {
    if (!group || !deleting) return;
    setPending(true);
    setError(undefined);
    try {
      await api.deleteGroupCode(group.groupId, deleting.code, revision);
      setDeleting(undefined);
      api.notifyCodeGroupsCommitted(group.groupId);
      try {
        await loadCodes(group, 1, size, true);
        setPage(1);
        await loadGroups();
        setNotice("삭제했습니다.");
      } catch {
        setNotice("삭제됐지만 다시 불러오지 못했습니다. 다시 조회해 주세요.");
      }
    } catch (e) {
      fail(e);
      throw e;
    } finally {
      setPending(false);
    }
  }
  function retry() {
    if (group) void loadCodes(group, page, size).catch(() => {});
    else
      void loadGroups()
        .then((list) => setGroup(list[0]))
        .catch(fail);
  }
  const field = (key: string, label: string, required = false) => (
    <Text
      key={key}
      label={label}
      value={form[key] ?? ""}
      required={required}
      error={fields[key]}
      maxLength={
        key === "groupId"
          ? 30
          : key === "code"
            ? 20
            : key === "name"
              ? modal === "group"
                ? 50
                : 100
              : undefined
      }
      onChange={(value) =>
        setForm((previous) => ({ ...previous, [key]: value }))
      }
    />
  );
  return (
    <section
      data-testid="common-codes-screen"
      className={`codegroups-screen${embedded ? " codegroups-embedded" : ""}`}
      data-screen-id="SCR-UI-27"
    >
      {!embedded && (
        <div className="codegroups-pc-notice">
          이 화면은 PC에서 이용해 주세요.
        </div>
      )}
      <div className="codegroups-desktop">
        <header className="codegroups-title">
          {!embedded && (
            <div>
              <p>시스템 › 공통코드</p>
              <h1>
                공통코드 <small>UI-27</small>
              </h1>
            </div>
          )}
          <button
            data-testid="common-codes-add-group"
            aria-label="그룹 추가"
            disabled={pending}
            onClick={() =>
              protectedAction(() => {
                setForm({});
                setFields({});
                setModal("group");
              })
            }
          >
            <Icon name="plus" size={16} /> 그룹 추가
          </button>
        </header>
        <nav className="codegroups-tabs" aria-label="공통코드 하위 탭">
          {tabs.map((item) => (
            <button
              data-testid={`common-codes-tab-${item.id}`}
              key={item.id}
              disabled={pending || (item.id !== "codes" && !panels?.[item.id])}
              aria-current={tab === item.id ? "page" : undefined}
              onClick={() => {
                if (item.id !== tab) guard(() => setTab(item.id));
              }}
            >
              {item.label}
            </button>
          ))}
        </nav>
        {error != null && (
          <ErrorBanner
            message={
              error instanceof Error
                ? error.message
                : "자료를 불러오지 못했습니다."
            }
            requestId={
              error instanceof ApiError ? error.body.requestId : undefined
            }
            onRetry={retry}
            pending={pending}
          />
        )}
        {notice && (
          <p role="status" className="sicost-banner sicost-tone-info">
            {notice}
            {notice.includes("다시 불러오지") && (
              <button data-testid="common-codes-retry" onClick={retry}>
                다시 조회
              </button>
            )}
          </p>
        )}
        {tab === "codes" ? (
          <>
            <div className="codegroups-split">
              <nav className="codegroups-list" aria-label="코드 그룹">
                {!groups.length && loading && (
                  <div role="status" className="sicost-empty">
                    그룹을 불러오는 중입니다.
                  </div>
                )}
                {groups.map((item) => (
                  <button
                    data-testid={`common-codes-group-${item.groupId.toLowerCase().replace(/_/g, "-")}`}
                    key={item.groupId}
                    aria-current={
                      item.groupId === group?.groupId ? "true" : undefined
                    }
                    disabled={pending}
                    onClick={() => {
                      if (item.groupId !== group?.groupId)
                        guard(() => select(item));
                    }}
                  >
                    <span>{item.name}</span>
                    <small>{item.codeCount}</small>
                    {item.sysCodeYn === "Y" && <CodeGroupLockMark />}
                  </button>
                ))}
                {!groups.length && !loading && !error && (
                  <p className="sicost-empty">등록된 그룹이 없습니다.</p>
                )}
              </nav>
              <div className="codegroups-editor">
                {system && (
                  <p className="sicost-banner sicost-tone-lock">
                    <CodeGroupLockMark /> 계산 규칙에 쓰는 시스템 코드입니다.
                    이름만 바꿀 수 있습니다. 추가·삭제·사용 끄기는 할 수
                    없습니다.
                  </p>
                )}
                <div className="codegroups-toolbar">
                  <p>
                    <strong>{group?.name}</strong>{" "}
                    <span>
                      {group?.groupId}
                      {data ? ` · ${data.total}개` : ""}
                    </span>
                  </p>
                  <div className="codegroups-table-actions">
                    {onColumnsChange && (
                      <button
                        data-testid="common-codes-columns-button"
                        onClick={onColumnsChange}
                      >
                        열 설정
                      </button>
                    )}
                    {onDensityChange && (
                      <button
                        data-testid="common-codes-density-button"
                        onClick={onDensityChange}
                        aria-pressed={dense}
                      >
                        목록 밀도
                      </button>
                    )}
                    {group && !system && (
                      <button
                        data-testid="common-codes-add-code"
                        disabled={pending || loading}
                        onClick={() => {
                          setForm(
                            valueMode ? { codeVal: "0", unitNm: "원" } : {},
                          );
                          setFields({});
                          setModal("code");
                        }}
                      >
                        {valueMode ? "수당코드 추가" : "코드 추가"}
                      </button>
                    )}
                  </div>
                </div>
                {(!error || data) && (
                  <DataTable
                    rows={rows}
                    rowKey={(row) => row.code}
                    columns={
                      columnView
                        ? columns
                            .filter(
                              (column) =>
                                !columnView.hidden.includes(column.id) ||
                                ["code", "name", "delete"].includes(column.id),
                            )
                            .sort((a, b) => {
                              const position = (id: string) =>
                                id === "delete"
                                  ? Number.MAX_SAFE_INTEGER
                                  : columnView.order.indexOf(id);
                              return position(a.id) - position(b.id);
                            })
                        : columns
                    }
                    dense={dense}
                    loading={loading}
                    empty="등록된 코드가 없습니다."
                    page={page}
                    size={size}
                    total={(data?.total ?? 0) + newRows.length}
                    onPageChange={setPage}
                    onSizeChange={(next) => {
                      void Promise.resolve()
                        .then(() => onPageSizeChange?.(next))
                        .then(() => {
                          setSize(next);
                          setPage(1);
                        })
                        .catch(fail);
                    }}
                  />
                )}
                <p className="codegroups-help">
                  이름을 바꾸면 저장 즉시 선택 목록에 반영되고, 지난 자료에도 새
                  이름으로 보입니다(코드별 적용 시작일은 두지 않음). 한 번도
                  쓰지 않은 코드만 삭제할 수 있습니다. 목록에서 사라지고 변경
                  이력에 남습니다. 쓴 코드는 지울 수 없고 사용만 끕니다. 그룹
                  자체의 이름 바꾸기·사용 끄기는 없습니다.
                </p>
              </div>
            </div>
            <footer className="codegroups-footer">
              <button
                data-testid="common-codes-cancel"
                disabled={pending || !dirty}
                onClick={reset}
              >
                취소
              </button>
              <button
                data-testid="common-codes-save"
                className="sicost-primary"
                disabled={pending || !dirty || loading}
                onClick={() => void save()}
              >
                {pending ? "저장 중…" : "저장"}
              </button>
            </footer>
          </>
        ) : (
          panels?.[tab]
        )}
      </div>
      <Dialog
        open={modal === "group"}
        title="코드 그룹 추가"
        pending={pending}
        confirmLabel="추가"
        onCancel={() => setModal(null)}
        onConfirm={addGroup}
      >
        {field("groupId", "그룹 ID", true)}
        <p>대문자 시작·영숫자/_·최대30자·저장 후 변경 불가</p>
        {field("name", "그룹 이름", true)}
      </Dialog>
      <Dialog
        open={modal === "code"}
        title={valueMode ? "수당코드 추가" : `코드 추가 · ${group?.name ?? ""}`}
        confirmLabel="추가"
        onCancel={() => setModal(null)}
        onConfirm={addCode}
      >
        {field("code", "코드", true)}
        {field("name", "이름", true)}
        {valueMode ? (
          <>
            {field("codeVal", "값")}
            <label>
              단위{" "}
              <select
                data-testid="common-codes-unit"
                aria-label="단위"
                value={form.unitNm ?? "원"}
                onChange={(e) =>
                  setForm((previous) => ({
                    ...previous,
                    unitNm: e.target.value,
                  }))
                }
              >
                <option>원</option>
                <option>%</option>
              </select>
            </label>
            <p>값 기본/빈값 0 · 최대 4소수</p>
          </>
        ) : (
          extras.map((label, i) => field(`addVal${i + 1}`, label, true))
        )}
        <p>추가는 편집 목록에만 반영됩니다. 하단 저장을 눌러 저장하세요.</p>
      </Dialog>
      <Dialog
        open={modal === "blocked"}
        title="저장하지 않은 내용이 있습니다."
        confirmLabel="계속 작성"
        onConfirm={() => setModal(null)}
        onCancel={() => setModal(null)}
      >
        <p>코드를 먼저 저장하거나 하단 취소로 되돌린 후 다시 실행해 주세요.</p>
      </Dialog>
      <Dialog
        open={modal === "move"}
        title="저장하지 않은 내용이 있습니다."
        confirmLabel="나가기"
        cancelLabel="계속 작성"
        onCancel={() => setModal(null)}
        onConfirm={() => {
          reset();
          destination?.();
          setModal(null);
        }}
      >
        <p>이동하면 저장하지 않은 내용을 버립니다.</p>
      </Dialog>
      <Dialog
        open={Boolean(deleting)}
        title="코드 삭제 확인"
        pending={pending}
        confirmLabel="삭제 확인"
        onCancel={() => setDeleting(undefined)}
        onConfirm={deleteCode}
      >
        <p>
          {deleting?.code} · {deleting?.name}
        </p>
        <p>
          삭제한 코드는 목록에서 사라지며 같은 코드를 다시 등록할 수 없습니다.
        </p>
        {error instanceof ApiError && error.body.details?.references && (
          <ul>
            {error.body.details.references.map((reference, i) => (
              <li data-testid={`common-codes-delete-reference-${i}`} key={i}>
                {JSON.stringify(reference)}
              </li>
            ))}
          </ul>
        )}
      </Dialog>
    </section>
  );
}
