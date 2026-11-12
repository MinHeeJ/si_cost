import { useCallback, useEffect, useRef, useState } from "react";
import { ApiError } from "../../shared/api";
import { ErrorBanner } from "../../shared/Feedback";
import {
  ProjectPartDialog,
  type ProjectPartDialogProps,
} from "./ProjectPartDialog";
import { ProjectPartTimeline } from "./ProjectPartTimeline";
import * as api from "./projectPartsApi";
import "./projectParts.css";

export type ProjectPartsPanelProps = Omit<ProjectPartDialogProps, "onClose"> & {
  currentMonth: string;
  refreshKey?: number;
};
export function ProjectPartsPanel(props: ProjectPartsPanelProps) {
  const [data, setData] = useState<api.ProjectPartsData>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>();
  const [editing, setEditing] = useState(false);
  const generation = useRef(0);
  const locked =
    props.readOnly ||
    props.year < props.currentYear ||
    ["END", "LOSE"].includes(props.effectiveStatus) ||
    props.contractType === "IN";
  const load = useCallback(async () => {
    const version = ++generation.current;
    setLoading(true);
    setError(undefined);
    try {
      const value = await api.getProjectParts(props.projectId);
      if (generation.current === version) setData(value);
    } catch (e) {
      if (generation.current === version) setError(e);
      throw e;
    } finally {
      if (generation.current === version) setLoading(false);
    }
  }, [props.projectId]);
  useEffect(() => {
    void load().catch(() => undefined);
    return () => {
      generation.current++;
    };
  }, [load, props.refreshKey]);
  return (
    <section
      className="project-parts-content"
      data-testid="project-parts-panel"
      data-screen-id="SCR-UI-01"
    >
      <header className="project-parts-heading">
        <h2>영업 파트 이력</h2>
        {!locked && (
          <button
            type="button"
            data-testid="project-part-change"
            disabled={loading || !data?.retainedBasePart}
            onClick={() => setEditing(true)}
          >
            변경
          </button>
        )}
      </header>
      {locked && (
        <p className="sicost-banner sicost-tone-lock">
          선택 연도 또는 프로젝트 상태에 따라 조회만 할 수 있습니다.
        </p>
      )}
      {loading && <p role="status">영업 파트를 불러오는 중입니다.</p>}
      {error != null ? (
        <ErrorBanner
          message={
            error instanceof Error
              ? error.message
              : "영업 파트를 불러오지 못했습니다."
          }
          requestId={
            error instanceof ApiError ? error.body.requestId : undefined
          }
          onRetry={() => void load().catch(() => undefined)}
        />
      ) : (
        data && (
          <ProjectPartTimeline
            items={data.items}
            currentMonth={props.currentMonth}
          />
        )
      )}
      {editing && (
        <ProjectPartDialog
          key={props.projectId}
          {...props}
          onClose={() => setEditing(false)}
          onSaved={async (result) => {
            setData(result.value);
            await props.onSaved(result);
            await load();
          }}
        />
      )}
    </section>
  );
}
