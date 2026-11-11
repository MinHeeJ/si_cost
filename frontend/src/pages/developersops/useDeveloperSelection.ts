import { useEffect, useRef, useState } from "react";
import * as api from "./peopleClient";

export function useDeveloperSelection(
  filters: api.DeveloperFilters,
  size: number,
) {
  const context = JSON.stringify([
    filters.employeeType,
    filters.jobCode,
    filters.directClass,
    filters.workStatus,
    filters.q,
    filters.includeStopped,
    filters.sort,
    filters.direction,
    size,
  ]);
  const [snapshot, setSnapshot] = useState<{
    context: string;
    items: Map<string, string>;
  }>({ context, items: new Map() });
  const [loading, setLoading] = useState(false),
    [error, setError] = useState<unknown>();
  const controller = useRef<AbortController>(),
    generation = useRef(0),
    current = useRef(context);
  current.current = context;
  const items =
    snapshot.context === context ? snapshot.items : new Map<string, string>();
  useEffect(() => {
    clear();
    setError(undefined);
    return () => {
      generation.current++;
      controller.current?.abort();
    };
  }, [context]);
  function clear() {
    generation.current++;
    controller.current?.abort();
    setLoading(false);
    setSnapshot({ context, items: new Map() });
  }
  function selectPage(rows: api.DeveloperSelectionItem[], selected: boolean) {
    setSnapshot((previous) => {
      const next = new Map(previous.context === context ? previous.items : []);
      rows.forEach((row) => {
        if (selected) {
          if (!next.has(row.id)) next.set(row.id, row.revision);
        } else next.delete(row.id);
      });
      return { context, items: next };
    });
  }
  async function selectAll() {
    if (loading) return;
    const version = ++generation.current;
    controller.current?.abort();
    controller.current = new AbortController();
    setLoading(true);
    setError(undefined);
    try {
      const result = await api.getDeveloperSelection(
        filters,
        controller.current.signal,
      );
      if (version !== generation.current || current.current !== context) return;
      if (
        new Set(result.items.map((row) => row.id)).size !== result.items.length
      )
        throw new Error("선택 대상이 중복됩니다. 다시 조회해 주세요.");
      setSnapshot({
        context,
        items: new Map(result.items.map((row) => [row.id, row.revision])),
      });
    } catch (reason) {
      if (version === generation.current) setError(reason);
    } finally {
      if (version === generation.current) setLoading(false);
    }
  }
  return {
    items,
    context,
    loading,
    error,
    count: items.size,
    targets: [...items].map(([id, revision]) => ({ id, revision })),
    selectPage,
    toggle: (row: api.DeveloperSelectionItem, checked: boolean) =>
      selectPage([row], checked),
    clear,
    selectAll,
  };
}
export type DeveloperSelectionState = ReturnType<typeof useDeveloperSelection>;
