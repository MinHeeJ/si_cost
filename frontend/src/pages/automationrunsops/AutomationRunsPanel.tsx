import { useCallback, useEffect, useRef, useState } from "react";
import { ApiError } from "../../shared/api";
import { DataTable, type Column } from "../../shared/DataTable";
import { DetailDrawer } from "../../shared/DetailDrawer";
import { ErrorBanner, StatusBadge } from "../../shared/Feedback";
import {
  createAutomationRunToday,
  getAutomationRuns,
  type AutomationJobCode,
  type AutomationPage,
  type AutomationRun,
} from "./automationRunsApi";

type Props = { open: boolean; onClose(): void; onOpenReceivables(): void };
const jobs: Record<AutomationJobCode, string> = {
  DAILY_OPERATIONS: "02시 일일 업무",
  RECEIVABLE_DUNNING: "09시 미수 알림",
};
const states = { RUNNING: "실행 중", SUCCEEDED: "완료", FAILED: "실패" };
const columns: Column<AutomationRun>[] = [
  { id: "date", label: "일자", render: (r) => r.runDate },
  { id: "job", label: "작업(02시/09시)", render: (r) => jobs[r.jobCode] },
  { id: "step", label: "완료 단계", render: (r) => r.lastStep },
  {
    id: "state",
    label: "상태",
    render: (r) => (
      <StatusBadge
        tone={
          r.state === "SUCCEEDED" ? "ok" : r.state === "FAILED" ? "bad" : "info"
        }
      >
        {states[r.state]}
      </StatusBadge>
    ),
  },
  { id: "start", label: "최초 시작", render: (r) => r.startedAt },
  { id: "end", label: "종료", render: (r) => r.endedAt ?? "–" },
  { id: "error", label: "실패 사유", render: (r) => r.errorMessage ?? "–" },
];
export function AutomationRunsPanel(props: Props) {
  return props.open ? <AutomationRunsContent {...props} /> : null;
}
function AutomationRunsContent({ onClose, onOpenReceivables }: Props) {
  const [page, setPage] = useState(1);
  const [size, setSize] = useState<20 | 50 | 100>(50);
  const [data, setData] = useState<AutomationPage>();
  const [loading, setLoading] = useState(true);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<unknown>();
  const [message, setMessage] = useState("");
  const mounted = useRef(true);
  const commandActive = useRef(false);
  const request = useRef<AbortController>();
  const today = data
    ? new Intl.DateTimeFormat("en-CA", {
        timeZone: "Asia/Seoul",
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
      }).format(new Date(data.observedAt))
    : undefined;
  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
      request.current?.abort();
    };
  }, []);
  const load = useCallback(
    async (background = false) => {
      request.current?.abort();
      const current = new AbortController();
      request.current = current;
      setLoading(true);
      try {
        const result = await (background
          ? getAutomationRuns({ page, size }, current.signal, true)
          : getAutomationRuns({ page, size }, current.signal));
        if (!current.signal.aborted && mounted.current) {
          setData(result);
          setError(undefined);
        }
      } catch (failure) {
        if (!current.signal.aborted && mounted.current) setError(failure);
      } finally {
        if (!current.signal.aborted && mounted.current) setLoading(false);
      }
    },
    [page, size],
  );
  useEffect(() => {
    void load();
    return () => request.current?.abort();
  }, [load]);
  useEffect(() => {
    if (loading || error || !data?.items.some((r) => r.state === "RUNNING"))
      return;
    const timer = window.setTimeout(() => void load(true), 2000);
    return () => window.clearTimeout(timer);
  }, [data, loading, error, load]);
  const run = async (jobCode: AutomationJobCode) => {
    if (commandActive.current || !data?.manualRunEnabled) return;
    commandActive.current = true;
    setPending(true);
    setMessage("");
    try {
      const result = await createAutomationRunToday(jobCode);
      if (!mounted.current) return;
      setMessage(
        result.completed
          ? "오늘 완료한 기존 결과입니다. 다시 실행하지 않습니다."
          : "실행을 접수했습니다. 전체 완료나 메일 성공을 뜻하지 않습니다.",
      );
      await load();
    } catch (failure) {
      if (!mounted.current) return;
      if (failure instanceof ApiError && failure.status === 404)
        setData((old) => (old ? { ...old, manualRunEnabled: false } : old));
      setError(failure);
    } finally {
      commandActive.current = false;
      if (mounted.current) setPending(false);
    }
  };
  return (
    <DetailDrawer
      title="자동처리상태"
      onClose={onClose}
      testId="automation-runs-panel"
      closeTestId="automation-runs-close"
      actions={
        <button
          type="button"
          data-testid="automation-runs-refresh"
          disabled={loading}
          onClick={() => void load()}
        >
          재조회
        </button>
      }
    >
      <section data-testid="automation-runs-content">
        {error != null && (
          <ErrorBanner
            message={
              error instanceof Error
                ? error.message
                : "자동 처리 상태를 불러오지 못했습니다."
            }
            requestId={
              error instanceof ApiError ? error.body.requestId : undefined
            }
            onRetry={() => void load()}
            retryTestId="automation-runs-retry"
            pending={loading}
          />
        )}
        {(data || !error) && (
          <DataTable
            rows={data?.items ?? []}
            columns={columns}
            rowKey={(r) => `${r.jobCode}-${r.runDate}`}
            loading={loading}
            empty="자동 처리 실행 기록이 없습니다."
            emptyAction={
              <button
                data-testid="automation-runs-reset-button"
                type="button"
                onClick={() => {
                  setPage(1);
                  void load();
                }}
              >
                조건 초기화
              </button>
            }
            page={page}
            size={size}
            total={data?.total ?? 0}
            onPageChange={setPage}
            onSizeChange={(next) => {
              setSize(next);
              setPage(1);
            }}
            testId="automation-runs-table"
          />
        )}
        {data?.manualRunEnabled && (
          <div className="sicost-actions">
            <button
              type="button"
              data-testid="automation-run-daily"
              disabled={
                pending ||
                loading ||
                data.items.some(
                  (r) =>
                    r.runDate === today &&
                    r.jobCode === "DAILY_OPERATIONS" &&
                    r.state === "RUNNING",
                )
              }
              onClick={() => void run("DAILY_OPERATIONS")}
            >
              오늘02시작업실행
            </button>
            <button
              type="button"
              data-testid="automation-run-dunning"
              disabled={
                pending ||
                loading ||
                data.items.some(
                  (r) =>
                    r.runDate === today &&
                    r.jobCode === "RECEIVABLE_DUNNING" &&
                    r.state === "RUNNING",
                )
              }
              onClick={() => void run("RECEIVABLE_DUNNING")}
            >
              오늘09시작업실행
            </button>
          </div>
        )}
        {message && (
          <p role="status" className="sicost-banner sicost-tone-info">
            {message}
          </p>
        )}
        <p>
          요청저장/실행중 ≠ 전체완료·메일성공 / 오류는 원청구의 전달상태에서
          확인
        </p>
        <button
          type="button"
          data-testid="automation-runs-deliveries"
          onClick={() => onOpenReceivables()}
        >
          청구·입금에서 전달상태 확인
        </button>
      </section>
    </DetailDrawer>
  );
}
