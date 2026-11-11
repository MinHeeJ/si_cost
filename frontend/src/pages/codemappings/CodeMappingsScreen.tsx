import { useEffect, useRef, useState, type Key } from "react";
import { ApiError } from "../../shared/api";
import { CODE_GROUPS_COMMITTED } from "../codegroups/codeGroupsApi";
import { CodeSelect } from "../../shared/CodeSelect";
import { DataTable, type Column } from "../../shared/DataTable";
import { ErrorBanner } from "../../shared/Feedback";
import { Text } from "../../shared/Text";
import {
  COMMITTED_REFRESH_FAILED,
  useCommitRefresh,
} from "../../shared/useCommitRefresh";
import {
  CODE_MAPPINGS_COMMITTED,
  createCodeMapping,
  deleteCodeMappings,
  getCodeMappings,
  getMappingGroups,
  type CodeMapping,
  type MappingGroup,
  type MappingPage,
  type MappingQuery,
} from "./codeMappingsApi";
import { MappingCreateDialog } from "./MappingCreateDialog";
import { MappingDeleteDialog } from "./MappingDeleteDialog";
import "../../shared/common.css";
import "./codeMappings.css";

export type CodeMappingsScreenProps = {
  pageSize?: 20 | 50 | 100;
  dense?: boolean;
  columnOrder?: string[];
  onPageSizeChange?(size: 20 | 50 | 100): Promise<void>;
  onColumnsChange?(): void;
  onDenseChange?(): void;
  onCommitted?(): Promise<void>;
};
const columns: Column<CodeMapping>[] = [
  { id: "sourceText", label: "엑셀 표기", render: (row) => row.sourceText },
  { id: "groupId", label: "코드 그룹", render: (row) => row.groupId },
  {
    id: "code",
    label: "바꿀 코드 / 이름",
    render: (row) => `${row.code} · ${row.codeName}`,
  },
];
export function CodeMappingsScreen({
  pageSize = 50,
  dense,
  columnOrder,
  onPageSizeChange,
  onColumnsChange,
  onDenseChange,
  onCommitted,
}: CodeMappingsScreenProps) {
  const [query, setQuery] = useState<MappingQuery>({ page: 1, size: pageSize });
  const [sourceText, setSourceText] = useState(""),
    [groupId, setGroupId] = useState("");
  const [data, setData] = useState<MappingPage | null>(null);
  const [groups, setGroups] = useState<MappingGroup[]>([]);
  const [groupError, setGroupError] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [selected, setSelected] = useState<CodeMapping[]>([]);
  const [dialog, setDialog] = useState<"create" | "delete" | null>(null);
  const [settingError, setSettingError] = useState<string | null>(null);
  const [settingPending, setSettingPending] = useState(false);
  const activeRead = useRef(0);
  const { commit, pending, message } = useCommitRefresh();
  const load = async (requested: MappingQuery = query) => {
    const version = ++activeRead.current;
    setLoading(true);
    setError(null);
    try {
      const result = await getCodeMappings(requested);
      if (activeRead.current === version) setData(result);
    } catch (e) {
      if (activeRead.current === version)
        setError(
          e instanceof ApiError
            ? `${e.message} 문의 번호: ${e.body.requestId}`
            : e instanceof Error
              ? e.message
              : "표기를 불러오지 못했습니다.",
        );
      throw e;
    } finally {
      if (activeRead.current === version) setLoading(false);
    }
  };
  const loadGroups = async () => {
    setGroupError(null);
    try {
      setGroups((await getMappingGroups()).items);
    } catch (e) {
      setGroupError(
        e instanceof Error ? e.message : "그룹을 불러오지 못했습니다.",
      );
    }
  };
  useEffect(() => {
    void loadGroups();
  }, []);
  useEffect(() => {
    const refresh = () => {
      void loadGroups();
    };
    window.addEventListener(CODE_GROUPS_COMMITTED, refresh);
    return () => window.removeEventListener(CODE_GROUPS_COMMITTED, refresh);
  }, []);
  useEffect(() => {
    void load().catch(() => {});
    return () => {
      activeRead.current++;
    };
  }, [query]);
  useEffect(() => {
    setQuery((q) =>
      q.size === pageSize ? q : { ...q, page: 1, size: pageSize },
    );
  }, [pageSize]);
  const refresh = async () => {
    window.dispatchEvent(new CustomEvent(CODE_MAPPINGS_COMMITTED));
    await Promise.all([load(), onCommitted?.()]);
  };
  const sizeChange = async (size: 20 | 50 | 100) => {
    if (settingPending || pending) return;
    setSettingPending(true);
    setSettingError(null);
    try {
      await onPageSizeChange?.(size);
      setSelected([]);
      setQuery((q) => ({ ...q, page: 1, size }));
    } catch (e) {
      setSettingError(
        e instanceof Error ? e.message : "목록 설정을 저장하지 못했습니다.",
      );
    } finally {
      setSettingPending(false);
    }
  };
  const select = (keys: Key[]) => {
    const known = new Map(
      [...selected, ...(data?.items ?? [])].map((row) => [row.mapId, row]),
    );
    setSelected(
      keys.flatMap((key) => {
        const row = known.get(Number(key));
        return row ? [row] : [];
      }),
    );
  };
  const orderedColumns =
    columnOrder?.length === columns.length &&
    new Set(columnOrder).size === columns.length &&
    columnOrder.every((id) => columns.some((c) => c.id === id))
      ? columnOrder.map((id) => columns.find((c) => c.id === id)!)
      : columns;
  return (
    <section
      data-testid="code-mappings-screen"
      data-screen-id="SCR-UI-27"
      aria-label="엑셀 표기 대응"
      className="sicost-mappings"
    >
      <form
        data-testid="code-mappings-search-form"
        className="sicost-mapping-search"
        onSubmit={(event) => {
          event.preventDefault();
          setSelected([]);
          setQuery((q) => ({
            ...q,
            sourceText,
            groupId: groupId || undefined,
            page: 1,
          }));
        }}
      >
        <Text
          label="엑셀 표기 검색"
          value={sourceText}
          onChange={setSourceText}
          maxLength={100}
          disabled={pending}
        />
        <CodeSelect
          label="그룹 검색"
          value={groupId}
          onChange={setGroupId}
          options={groups.map((g) => ({ code: g.groupId, name: g.name }))}
          disabled={pending || Boolean(groupError)}
        />
        <button
          data-testid="code-mappings-search"
          type="submit"
          disabled={pending || loading}
        >
          조회
        </button>
      </form>
      {groupError && (
        <ErrorBanner
          message={groupError}
          onRetry={() => {
            void loadGroups();
          }}
        />
      )}
      <div className="sicost-mapping-toolbar">
        <button
          data-testid="code-mappings-add"
          type="button"
          className="sicost-primary"
          disabled={pending || !groups.length || Boolean(groupError)}
          onClick={() => setDialog("create")}
        >
          추가
        </button>
        <button
          data-testid="code-mappings-delete-selected"
          type="button"
          disabled={pending || loading || !selected.length}
          onClick={() => setDialog("delete")}
        >
          선택 삭제
        </button>
        <span />
        {onColumnsChange && (
          <button
            data-testid="code-mappings-columns"
            type="button"
            onClick={onColumnsChange}
          >
            열 설정
          </button>
        )}
        {onDenseChange && (
          <button
            data-testid="code-mappings-density"
            type="button"
            onClick={onDenseChange}
          >
            목록 밀도
          </button>
        )}
      </div>
      {settingError && <ErrorBanner message={settingError} />}
      {message && dialog === null && (
        <p
          role="status"
          className={`sicost-banner ${message === "저장했습니다." ? "sicost-tone-ok" : "sicost-tone-info"}`}
        >
          {message === COMMITTED_REFRESH_FAILED
            ? "저장됐지만 다시 불러오지 못했습니다."
            : message}
        </p>
      )}
      <DataTable
        rows={data?.items ?? []}
        columns={orderedColumns}
        rowKey={(row) => row.mapId}
        loading={loading || settingPending}
        error={error ?? undefined}
        onRetry={() => {
          void load().catch(() => {});
        }}
        empty="등록된 표기가 없습니다."
        page={query.page}
        size={query.size}
        total={data?.total ?? 0}
        onPageChange={(page) => setQuery((q) => ({ ...q, page }))}
        onSizeChange={(size) => {
          void sizeChange(size);
        }}
        selectedKeys={selected.map((row) => row.mapId)}
        onSelectionChange={select}
        dense={dense}
      />
      <p className="sicost-mapping-hint">
        표기를 바꾸려면 삭제 후 다시 등록해 주세요. 기존 업무 자료는 변경되지
        않습니다.
      </p>
      {dialog === "create" && (
        <MappingCreateDialog
          groups={groups}
          pending={pending}
          onCancel={() => setDialog(null)}
          onConfirm={(body) =>
            commit(async () => {
              await createCodeMapping(body);
              setDialog(null);
            }, refresh)
          }
        />
      )}
      {dialog === "delete" && (
        <MappingDeleteDialog
          rows={selected}
          pending={pending}
          onCancel={() => setDialog(null)}
          onConfirm={() =>
            commit(async () => {
              await deleteCodeMappings({
                items: selected.map((row) => ({
                  mapId: row.mapId,
                  expectedRevision: row.revision,
                })),
              });
              setSelected([]);
              setDialog(null);
            }, refresh)
          }
        />
      )}
    </section>
  );
}
