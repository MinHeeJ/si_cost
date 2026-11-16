import {
  createContext,
  useContext,
  useLayoutEffect,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { useLocation } from "react-router-dom";
import * as api from "./analysisClient";
import type { PeriodValue } from "./sharedApi";
function useAnalysisState() {
  const location = useLocation(),
    periods = useRef<Record<string, string>>({}),
    targets = useRef<Record<string, api.AnalysisContext>>({});
  const [question, setQuestion] = useState(""),
    [pending, setPending] = useState(false),
    [progress, setProgress] = useState<api.AnalysisProgress>(),
    [result, setResult] = useState<api.AnalysisResult>(),
    [qnaId, setQnaId] = useState<string>(),
    [history, setHistory] = useState<api.AnalysisSummary[]>([]),
    [cursor, setCursor] = useState<string | null>(null),
    [error, setError] = useState(""),
    [historyError, setHistoryError] = useState(""),
    [snapshot, setSnapshot] = useState(""),
    [lastQuestion, setLastQuestion] = useState("");
  const busy = useRef(false),
    reasking = useRef(false),
    loading = useRef(false),
    initialized = useRef(false);
  useLayoutEffect(() => {
    const change = (e: Event) => {
      const { screenId, value, context } = (
        e as CustomEvent<{
          screenId: string;
          value: PeriodValue;
          context?: { currentMonth: string };
        }>
      ).detail;
      if (value.type === "YEAR_CUM") periods.current[screenId] = value.month;
      else if (value.type === "YEAR_MONTH")
        periods.current[screenId] = value.value;
      else if (value.type === "YEAR")
        periods.current[screenId] = context?.currentMonth.startsWith(
          value.value + "-",
        )
          ? context.currentMonth
          : value.value + "-12";
    };
    const target = (e: Event) => {
      const { screenId, ...context } = (
        e as CustomEvent<api.AnalysisContext & { screenId: string }>
      ).detail;
      targets.current[screenId] = context;
    };
    window.addEventListener("sicost:screen-period-changed", change);
    window.addEventListener("sicost:analysis-context", target);
    return () => {
      window.removeEventListener("sicost:screen-period-changed", change);
      window.removeEventListener("sicost:analysis-context", target);
    };
  }, []);
  async function refresh(more = false) {
    if (loading.current) return;
    loading.current = true;
    setHistoryError("");
    try {
      const page = await api.listQuestions(
        more ? (cursor ?? undefined) : undefined,
      );
      setHistory((old) => (more ? [...old, ...page.items] : page.items));
      setCursor(page.nextCursor);
    } catch (e) {
      setHistoryError(e instanceof Error ? e.message : "기록 조회 실패");
    } finally {
      loading.current = false;
    }
  }
  async function initialize() {
    if (initialized.current) return;
    initialized.current = true;
    await refresh();
  }
  async function send(text = question, context?: api.AnalysisContext) {
    if (
      busy.current ||
      ![...text.trim()].length ||
      [...text.trim()].length > 1000
    )
      return;
    busy.current = true;
    setPending(true);
    setError("");
    setProgress(undefined);
    setLastQuestion(text.trim());
    const screenId = location.pathname.slice(1),
      state = location.state as api.AnalysisContext | null;
    const fixed = context ?? {
      screenId,
      ...(state?.projectId ? { projectId: state.projectId } : {}),
      ...(state?.developerId ? { developerId: state.developerId } : {}),
      ...(periods.current[screenId]
        ? { baseMonth: periods.current[screenId] }
        : {}),
      ...targets.current[screenId],
    };
    try {
      const final = await api.sendQuestion(
        text.trim(),
        structuredClone(fixed),
        setProgress,
      );
      if (final.persisted && final.result) {
        setResult(final.result);
        setQnaId(final.qnaId ?? undefined);
        setSnapshot("");
        setQuestion("");
      } else
        setError(
          `${final.message ?? "저장하지 못했습니다."} 문의 번호: ${final.serverErrorId ?? "–"}`,
        );
      await refresh();
    } catch (e) {
      setError(
        `${e instanceof Error ? e.message : "연결 오류"} 완료 여부가 불명확하면 질문 기록을 확인하세요. 자동 재전송하지 않습니다.`,
      );
    } finally {
      busy.current = false;
      setPending(false);
    }
  }
  async function view(id: string) {
    setError("");
    try {
      const detail = await api.getQuestion(id);
      setResult(detail.result);
      setQnaId(id);
      setSnapshot(detail.snapshotNotice);
      setLastQuestion(detail.question);
    } catch (e) {
      setError(e instanceof Error ? e.message : "답변 조회 실패");
    }
  }
  async function askAgain(id: string) {
    if (busy.current || reasking.current) return;
    reasking.current = true;
    try {
      const detail = await api.getQuestion(id);
      await send(detail.question, detail.result.context);
    } catch (e) {
      setError(e instanceof Error ? e.message : "재질문 실패");
    } finally {
      reasking.current = false;
    }
  }
  return {
    question,
    setQuestion,
    pending,
    progress,
    result,
    qnaId,
    history,
    cursor,
    error,
    setError,
    historyError,
    snapshot,
    lastQuestion,
    send,
    view,
    askAgain,
    refresh,
    initialize,
  };
}
const AnalysisContext = createContext<ReturnType<
  typeof useAnalysisState
> | null>(null);
export function AnalysisAssistantProvider({
  children,
}: {
  children: ReactNode;
}) {
  const state = useAnalysisState();
  return (
    <AnalysisContext.Provider value={state}>
      {children}
    </AnalysisContext.Provider>
  );
}
export function useAnalysisAssistant() {
  const state = useContext(AnalysisContext);
  if (!state) throw new Error("AnalysisAssistantProvider가 필요합니다.");
  return state;
}
