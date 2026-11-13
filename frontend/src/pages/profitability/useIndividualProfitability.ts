import { useCallback, useEffect, useRef, useState } from "react";
import { ApiError } from "../../shared/api";
import {
  getIndividualProfitability,
  defaultProfitabilityQuery,
  type IndividualProfitabilityData,
  type ProfitabilityPerson,
  type ProfitabilityQuery,
} from "./individualProfitabilityApi";

export function useIndividualProfitability(initialSize: 20 | 50 | 100 = 50) {
  const [mobile, setMobile] = useState(() => window.innerWidth < 768);
  const [query, setQuery] = useState<ProfitabilityQuery>(() => ({
    ...defaultProfitabilityQuery(),
    size: mobile ? 20 : initialSize,
  }));
  const [data, setData] = useState<IndividualProfitabilityData | null>(null);
  const [cards, setCards] = useState<ProfitabilityPerson[]>([]);
  const [loading, setLoading] = useState(true),
    [error, setError] = useState<unknown>(null);
  const [revision, setRevision] = useState(0);
  const sequence = useRef(0);
  const currentYear = useRef<number>();
  useEffect(() => {
    const resize = () => setMobile(window.innerWidth < 768);
    window.addEventListener("resize", resize);
    return () => window.removeEventListener("resize", resize);
  }, []);
  useEffect(() => {
    setQuery((q) => ({ ...q, page: 1, size: mobile ? 20 : initialSize }));
  }, [mobile, initialSize]);
  useEffect(() => {
    const period = (event: Event) => {
      const detail = (
        event as CustomEvent<{
          screenId: string;
          value: { type: string; value?: string };
          context?: { currentMonth: string };
        }>
      ).detail;
      if (
        detail?.screenId !== "UI-03" ||
        detail.value.type !== "YEAR" ||
        !detail.value.value
      )
        return;
      const year = Number(detail.value.value);
      if (detail.context)
        currentYear.current = Number(detail.context.currentMonth.slice(0, 4));
      setQuery((q) => (q.year === year ? q : { ...q, year, page: 1 }));
    };
    window.addEventListener("sicost:screen-period-changed", period);
    return () =>
      window.removeEventListener("sicost:screen-period-changed", period);
  }, []);
  const refresh = useCallback(() => setRevision((n) => n + 1), []);
  useEffect(() => {
    const events = [
      "sicost:projects-committed",
      "sicost:clients-committed",
      "sicost:sales-parts-committed",
      "sicost:operating-standards-committed",
      "sicost:codegroups-committed",
      "sicost:assignments-committed",
    ];
    events.forEach((e) => window.addEventListener(e, refresh));
    return () => events.forEach((e) => window.removeEventListener(e, refresh));
  }, [refresh]);
  useEffect(() => {
    const controller = new AbortController(),
      ticket = ++sequence.current;
    setLoading(true);
    setError(null);
    // Never render stale amounts as if they belonged to the new query context.
    setData(null);
    if (!mobile || query.page === 1) setCards([]);
    void getIndividualProfitability(query, controller.signal)
      .then((result) => {
        if (ticket !== sequence.current || controller.signal.aborted) return;
        setData(result);
        setCards((previous) =>
          mobile && query.page > 1
            ? [
                ...new Map(
                  [...previous, ...result.items].map((p) => [p.personId, p]),
                ).values(),
              ]
            : result.items,
        );
      })
      .catch((e: unknown) => {
        if (!controller.signal.aborted && ticket === sequence.current)
          setError(e);
      })
      .finally(() => {
        if (!controller.signal.aborted && ticket === sequence.current)
          setLoading(false);
      });
    return () => controller.abort();
  }, [query, mobile, revision]);
  const apply = (next: ProfitabilityQuery) =>
    setQuery({
      ...next,
      name: next.name?.trim(),
      page: 1,
      size: mobile ? 20 : next.size,
    });
  const reset = () => {
    setQuery({
      ...defaultProfitabilityQuery(),
      year: currentYear.current,
      size: mobile ? 20 : initialSize,
    });
    window.dispatchEvent(new Event("sicost:profitability-reset-period"));
  };
  return {
    query,
    setQuery,
    apply,
    reset,
    data,
    cards,
    mobile,
    loading,
    error,
    errorMessage:
      error instanceof Error
        ? error.message
        : error
          ? "조회하지 못했습니다."
          : "",
    fieldMessages: error instanceof ApiError ? error.fieldMessages : {},
    refresh,
  };
}
