import { useEffect, useState } from "react";
import { DataTable } from "../../shared/DataTable";
import { Dialog } from "../../shared/Dialog";
import { StatusBadge } from "../../shared/Feedback";
import { FilterBar } from "../../shared/FilterBar";
import { projectStatusTone } from "../projects/projectFormat";
import { getProjects, type ProjectListItem, type ProjectsPage } from "../projects/projectsApi";

const statusNames: Record<ProjectListItem["status"], string> = {
  PRE: "수주예정",
  ING: "진행",
  END: "종료",
  LOSE: "실주",
};

export function BillProjectPicker({
  initialSearch,
  onSelect,
  onClose,
}: {
  initialSearch: string;
  onSelect(project: ProjectListItem): void;
  onClose(): void;
}) {
  const [search, setSearch] = useState(initialSearch);
  const [query, setQuery] = useState({ q: initialSearch.trim(), page: 1 });
  const [data, setData] = useState<ProjectsPage>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError("");
    void getProjects({ ...query, size: 20 }, controller.signal)
      .then((result) => {
        if (!controller.signal.aborted) setData(result);
      })
      .catch((failure: unknown) => {
        if (!controller.signal.aborted) {
          setError(failure instanceof Error ? failure.message : "프로젝트를 불러오지 못했습니다.");
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [query]);

  return (
    <Dialog
      open
      title="프로젝트 선택"
      className="bill-form-modal"
      onCancel={onClose}
      onConfirm={onClose}
      confirmLabel="닫기"
      hideCancel
    >
      <section className="bill-project-picker" data-testid="bill-project-dialog">
        <FilterBar
          embedded
          onSubmit={() => setQuery({ q: search.trim(), page: 1 })}
          onReset={() => {
            setSearch("");
            setQuery({ q: "", page: 1 });
          }}
        >
          <label className="sicost-field">
            프로젝트명
            <input
              type="search"
              value={search}
              maxLength={100}
              onChange={(event) => setSearch(event.target.value)}
            />
          </label>
        </FilterBar>
        <DataTable
          rows={loading || error ? [] : (data?.items ?? [])}
          rowKey={(project) => project.id}
          tableLabel="선택 가능한 프로젝트"
          loading={loading}
          loadingMessage="프로젝트를 불러오는 중입니다."
          error={error || undefined}
          onRetry={() => setQuery((current) => ({ ...current }))}
          empty="검색된 프로젝트가 없습니다."
          page={data && data.total > 20 ? query.page : undefined}
          size={20}
          total={data?.total ?? 0}
          onPageChange={(page) => setQuery((current) => ({ ...current, page }))}
          columns={[
            {
              id: "name",
              label: "프로젝트명",
              render: (project) => (
                <span className="bill-project-picker-name">
                  <strong>{project.name}</strong>
                  <small>{project.id}{project.customerName ? ` · ${project.customerName}` : ""}</small>
                </span>
              ),
            },
            {
              id: "status",
              label: "상태",
              align: "center",
              render: (project) => (
                <StatusBadge tone={projectStatusTone[project.status]}>
                  {statusNames[project.status]}
                </StatusBadge>
              ),
            },
            {
              id: "select",
              label: "선택",
              align: "center",
              render: (project) => (
                <button
                  type="button"
                  aria-label={`${project.name} 선택`}
                  data-testid={`bill-project-pick-${project.id.toLowerCase().replace(/[^a-z0-9-]/g, "-")}`}
                  onClick={() => onSelect(project)}
                >
                  선택
                </button>
              ),
            },
          ]}
        />
      </section>
    </Dialog>
  );
}
