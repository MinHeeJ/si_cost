import { useEffect, useRef, useState } from "react";
import * as api from "./clientActionsApi";

export function useClientSelection(filters: api.ClientFilters, size: number) {
  const context = JSON.stringify([
    filters.type,
    filters.useYn,
    filters.q,
    filters.sort,
    filters.direction,
    size,
  ]);
  const [snapshot, setSnapshot] = useState<{
    context: string;
    items: Map<string, string>;
  }>({ context, items: new Map() });
  const [loading, setLoading] = useState(false),
    [error, setError] = useState("");
  const controller = useRef<AbortController>(),
    generation = useRef(0),
    current = useRef(context);
  current.current = context;
  const items =
    snapshot.context === context ? snapshot.items : new Map<string, string>();
  useEffect(() => {
    generation.current++;
    controller.current?.abort();
    setLoading(false);
    setError("");
    setSnapshot({ context, items: new Map() });
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
  function toggle(row: api.ClientSelectionItem, selected: boolean) {
    setSnapshot((previous) => {
      const next = new Map(previous.context === context ? previous.items : []);
      if (selected) {
        if (!next.has(row.id)) next.set(row.id, row.revision);
      } else next.delete(row.id);
      return { context, items: next };
    });
  }
  function selectPage(rows: api.ClientSelectionItem[], selected: boolean) {
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
    const version = ++generation.current,
      requestedContext = context;
    controller.current?.abort();
    controller.current = new AbortController();
    setLoading(true);
    setError("");
    try {
      const result = await api.getClientSelection(
        filters,
        controller.current.signal,
      );
      if (
        generation.current !== version ||
        current.current !== requestedContext
      )
        return;
      if (
        result.total !== result.items.length ||
        new Set(result.items.map((item) => item.id)).size !== result.total
      )
        throw new Error(
          "선택 대상 수가 일치하지 않습니다. 다시 조회해 주세요.",
        );
      setSnapshot({
        context,
        items: new Map(result.items.map((row) => [row.id, row.revision])),
      });
    } catch (reason) {
      if (generation.current === version)
        setError(
          reason instanceof Error
            ? reason.message
            : "전체 선택 대상을 불러오지 못했습니다.",
        );
    } finally {
      if (generation.current === version) setLoading(false);
    }
  }
  return {
    items,
    count: items.size,
    targets: [...items].map(([id, expectedRevision]) => ({
      id,
      expectedRevision,
    })),
    loading,
    error,
    toggle,
    selectPage,
    selectAll,
    clear,
    context,
  };
}
export type ClientSelectionState = ReturnType<typeof useClientSelection>;
