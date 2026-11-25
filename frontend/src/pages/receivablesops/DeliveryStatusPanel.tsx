import { ScreenLoading } from "../../shared/ScreenLoading";
import { FieldHelp } from "../../shared/FieldHelp";
import { useEffect, useState } from "react";
import { Pagination } from "../../shared/Pagination";
import { DetailDrawer } from "../../shared/DetailDrawer";
import { ApiError } from "../../shared/api";
import { ErrorBanner, StatusBadge, type Tone } from "../../shared/Feedback";
import * as api from "./dunningDeliveryApi";
import "./dunningDelivery.css";

const states: Record<api.DeliveryTask["state"], { text: string; tone: Tone }> =
  {
    PENDING: { text: "대기", tone: "neutral" },
    SENDING: { text: "처리중", tone: "info" },
    SENT: { text: "성공", tone: "ok" },
    FAILED: { text: "실패", tone: "bad" },
    UNKNOWN: { text: "확인필요", tone: "warn" },
    SKIPPED: { text: "건너뜀", tone: "lock" },
  };
export function DeliveryStatusPanel({
  billId,
  onClose,
  onRemind,
  focusSerial,
}: {
  billId: string;
  onClose(): void;
  onRemind?(): void;
  focusSerial?: number;
}) {
  const [page, setPage] = useState(1),
    [size, setSize] = useState<20 | 50 | 100>(50);
  const [data, setData] = useState<api.DeliveryPage>(),
    [error, setError] = useState<unknown>();
  const [loading, setLoading] = useState(true),
    [reload, setReload] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    let timer: ReturnType<typeof setTimeout> | undefined;
    async function load(background = false) {
      try {
        const value = await (background
          ? api.getReceivableDeliveries(
              billId,
              page,
              size,
              controller.signal,
              true,
            )
          : api.getReceivableDeliveries(billId, page, size, controller.signal));
        if (controller.signal.aborted) return;
        setData(value);
        setError(undefined);
        if (value.rounds.some((round) => !round.terminal))
          timer = setTimeout(() => void load(true), 2000);
      } catch (e) {
        if (!controller.signal.aborted) setError(e);
      } finally {
        if (!controller.signal.aborted) setLoading(false);
      }
    }
    setLoading(true);
    void load();
    return () => {
      controller.abort();
      if (timer) clearTimeout(timer);
    };
  }, [billId, page, size, reload]);
  return (
    <DetailDrawer
      title={`전달상태 ${billId}`}
      onClose={onClose}
      testId="delivery-status-panel"
      closeTestId="delivery-status-close"
      actions={
        <button
          type="button"
          data-testid="delivery-reload"
          onClick={() => setReload((v) => v + 1)}
        >
          재조회
        </button>
      }
    >
      <section
        className="dunning-delivery"
        data-testid="delivery-status-content"
      >
        {loading && <ScreenLoading message="전달상태를 불러오는 중입니다." />}
        {!!error && (
          <ErrorBanner
            message={
              error instanceof Error
                ? error.message
                : "전달상태를 불러오지 못했습니다."
            }
            requestId={
              error instanceof ApiError ? error.body.requestId : undefined
            }
            onRetry={() => setReload((v) => v + 1)}
          />
        )}
        {data && !error && (
          <>
            <p>
              기준시각 {data.observedAt.replace("T", " ")} · 전체 {data.total}
              회차
            </p>
            {!data.rounds.length && (
              <p className="dunning-empty">발송 이력이 없습니다.</p>
            )}
            {data.rounds.map((round) => (
              <details
                key={`${billId}:${round.reservedSn}`}
                open={focusSerial === round.reservedSn ? true : undefined}
                data-testid={`delivery-round-${round.reservedSn}`}
                className="dunning-round"
              >
                <summary
                  data-testid={`delivery-round-toggle-${round.reservedSn}`}
                >
                  #{round.reservedSn} · {round.step}단계 ·{" "}
                  {round.resend ? "다시알림" : "자동알림"} ·{" "}
                  {round.terminal ? "처리 종료" : "요청 저장 · 전달 처리 중"}
                </summary>
                {round.history ? (
                  <p>
                    {round.history.sendDate} · {round.history.sendMethodCode} ·{" "}
                    {round.history.receiverDescription}
                  </p>
                ) : (
                  <p>발송완료 이력 없음</p>
                )}
                {!round.tasks.length && <p>당시주소 미기록 · 기존 발송이력</p>}
                {!!round.tasks.length && (
                  <div className="sicost-table-viewport">
                    <table className="sicost-table">
                      <thead>
                        <tr>
                          {[
                            "당시이름",
                            "당시주소",
                            "채널",
                            "상태",
                            "성공시각",
                            "오류코드/사유",
                          ].map((label) => (
                            <th key={label}>{label}<FieldHelp label={label} kind="B" /></th>
                          ))}
                        </tr>
                      </thead>
                      <tbody>
                        {round.tasks.map((task) => (
                          <tr
                            key={task.taskId}
                            data-testid={`delivery-task-${task.taskId}`}
                          >
                            <td>
                              {task.userName} ({task.userId})
                            </td>
                            <td>{task.emailAddress ?? "–"}</td>
                            <td>
                              {task.channelCode === "SYS" ? "시스템" : "메일"}
                            </td>
                            <td>
                              <StatusBadge tone={states[task.state].tone}>
                                {task.state === "SENT"
                                  ? `${task.channelCode === "SYS" ? "시스템" : "메일"} 성공`
                                  : states[task.state].text}
                              </StatusBadge>
                            </td>
                            <td>{task.sentAt?.replace("T", " ") ?? "–"}</td>
                            <td>
                              {task.errorCode} {task.errorMessage}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </details>
            ))}
            <Pagination
              page={page}
              size={size}
              total={data.total}
              unit="회"
              label="전달 이력 페이지"
              testId="delivery"
              disabled={loading}
              onPageChange={setPage}
              onSizeChange={(nextSize) => {
                setSize(nextSize);
                setPage(1);
              }}
            />
          </>
        )}
        <p className="sicost-banner sicost-tone-info">
          시스템 성공은 메일 성공과 다릅니다. 확인필요(UNKNOWN)는 자동
          재발송하지 않습니다. 조회·펼치기는 발송하지 않습니다.
        </p>
        {onRemind && (
          <button
            type="button"
            data-testid="delivery-remind"
            onClick={onRemind}
          >
            다시알림
          </button>
        )}
      </section>
    </DetailDrawer>
  );
}
