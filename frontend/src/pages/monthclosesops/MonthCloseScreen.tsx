import { ScreenTitleActions } from "../../shared/ScreenTitleActions";
import { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { SharedScreenFrame } from "../shared/FollowUpScreen";
import { ApiError } from "../../shared/api";
import { DataTable, type Column } from "../../shared/DataTable";
import { Dialog } from "../../shared/Dialog";
import { Pagination } from "../../shared/Pagination";
import { PeriodStatus } from "../../shared/PeriodStatus";
import { ScreenLoading } from "../../shared/ScreenLoading";
import { ErrorBanner, StatusBadge } from "../../shared/Feedback";
import { useCommitRefresh } from "../../shared/useCommitRefresh";
import type { PeriodContext } from "../shared/sharedApi";
import { formatDecimal, formatDateTime } from "../../shared/format";
import * as api from "./monthCloseApi";
import "./monthClose.css";

const monthCloseGuide = (
  <>
    <section>
      <h3>마감 점검</h3>
      <p>
        선택한 달의 급여, 투입, 청구, 재무 항목, 영업 파트 정산을 점검합니다.
        확인이 필요한 항목의 [확인하기]를 누르면 해당 업무 화면으로 이동합니다.
        [다시 점검]을 누르면 점검 결과를 저장하며, 저장 검사 시각은 각 항목에서
        확인할 수 있습니다.
      </p>
    </section>
    <section>
      <h3>마감과 변경 이력</h3>
      <p>
        마감 이후 수정이 필요하면 다음 달에 전월 조정을 입력합니다. 마감 이력에서
        선택한 연도의 마감·해제 기록, 처리 사유, 마감 당시 남은 점검을 확인할 수
        있습니다.
      </p>
    </section>
  </>
);

const historyColumns: Column<api.CloseHistory>[] = [
  { id: "month", label: "연월", align: "center", render: (row) => row.month },
  {
    id: "action",
    label: "작업",
    align: "center",
    render: (row) => (
      <StatusBadge tone={row.action === "CLOSE" ? "lock" : "info"}>
        {row.action === "CLOSE" ? "마감" : "해제"}
      </StatusBadge>
    ),
  },
  {
    id: "actor",
    label: "처리자",
    align: "center",
    render: (row) => row.actorName ?? "–",
  },
  { id: "date", label: "일시", align: "center", render: (row) => row.occurredAt },
  { id: "reason", label: "사유", render: (row) => row.reason ?? "–" },
  { id: "checks", label: "남은 점검", render: (row) => row.remainingChecks ?? "–" },
];

const failedRefresh =
  "저장됐지만 다시 불러오지 못했습니다. 다시 조회해 주세요.";
const errorText = (error: unknown) =>
  error instanceof ApiError
    ? `${error.message} · ${error.body.field ?? ""} · 문의 번호: ${error.body.requestId}`
    : error instanceof Error
      ? error.message
      : "요청에 실패했습니다.";
function Issues({ items }: { items: api.CloseIssue[] }) {
  return (
    <>
      {items.map((item, index) => (
        <p
          role="alert"
          className="sicost-field-error"
          key={`${item.code}-${index}`}
        >
          {item.message}
        </p>
      ))}
    </>
  );
}
export function CloseCheckCards({
  items,
  onLink,
  closed = false,
  testIdPrefix = "month-close-check",
}: {
  items: api.CloseCheck[];
  onLink?: (check: api.CloseCheck) => void;
  closed?: boolean;
  testIdPrefix?: string;
}) {
  return (
    <div className="month-close-grid">
      {items.map((check) => (
        <article
          key={check.code}
          className={`month-close-check ${closed ? "ok" : check.status.toLowerCase()}`}
          data-testid={`${testIdPrefix}-${check.code.toLowerCase().replaceAll("_", "-")}`}
        >
          <div className="month-close-heading">
            <h3>{check.name}</h3>
            <StatusBadge
              tone={!closed && check.status === "WARN" ? "warn" : "ok"}
            >
              {closed
                ? "확인 완료"
                : check.status === "WARN"
                  ? "확인 필요"
                  : "정상"}
            </StatusBadge>
          </div>
          {!closed && (
            <strong>
              {check.count ?? "–"}
              {check.code === "PAY" || check.code === "MM_OVER"
                ? "명"
                : check.code === "SETL" || check.code === "BILL_MISS"
                  ? "개"
                  : "건"}
            </strong>
          )}
          {(!closed || check.code === "PAY") && (
            <p>
              {closed
                ? `${check.count ?? "–"}명 업로드 완료`
                : (check.description ?? "–")}
            </p>
          )}
          {check.checkedAt && <small>마지막 저장 검사 {check.checkedAt}</small>}
          {!closed && check.linkScreenId && onLink && (
            <button
              type="button"
              data-action-id={`B143-14-LINK-${({ MM_OVER: "MM", BILL: "BILL", FI: "FI", SETL: "SETL", BILL_MISS: "BILLMISS" } as Record<string, string>)[check.code]}`}
              data-testid={`month-close-link-${check.code.toLowerCase().replaceAll("_", "-")}`}
              onClick={() => onLink(check)}
            >
              확인하기
            </button>
          )}
        </article>
      ))}
    </div>
  );
}
export function ClosePreviewDialog({
  preview,
  pending,
  onConfirm,
  onCancel,
}: {
  preview: api.ClosePreview;
  pending: boolean;
  onConfirm(accepted: boolean): Promise<void>;
  onCancel(): void;
}) {
  const [accepted, setAccepted] = useState(false);
  const automatic = preview.parts.filter((part) => part.willAutoConfirm);
  return (
    <Dialog
      open
      title={`${preview.month}를 마감할까요?`}
      wide
      testId="month-close-preview-dialog"
      confirmTestId="month-close-submit-button"
      cancelTestId="month-close-cancel-button"
      confirmActionId="B143-14-CLOSE-SUBMIT"
      cancelActionId="B143-14-CLOSE-CANCEL"
      pending={pending}
      onCancel={onCancel}
      onConfirm={() => onConfirm(accepted)}
      confirmLabel="마감하기"
      confirmDisabled={
        !preview.canClose ||
        preview.blockingIssues.length > 0 ||
        (preview.warningCount > 0 && !accepted)
      }
    >
      <section
        data-screen-id="SCR-UI-14-M1"
        data-testid="month-close-preview-panel"
      >
        <Issues items={preview.blockingIssues} />
        <p>마감하면 이 달 자료는 고칠 수 없습니다.</p>
        <p>
          {preview.warningCount > 0
            ? `확인할 항목 ${preview.warningCount}개(${preview.remainingCheckNames.join(", ")})가 남아 있습니다. 남은 항목은 마감 이력에 함께 남습니다.`
            : "점검 항목이 모두 정상입니다."}
        </p>
        {automatic.length > 0 && (
          <p>
            확정하지 않은 영업 파트 정산 {automatic.length}개(
            {automatic.map((part) => part.partName).join(", ")})는 마감과 함께
            자동 확정됩니다(마감 뒤 틀린 금액은 다음 달에 반영).
          </p>
        )}
        {preview.warningCount > 0 && (
          <label>
            <input
              type="checkbox"
              data-testid="month-close-accept-warnings"
              data-action-id="B143-14-WARN-ACCEPT"
              checked={accepted}
              disabled={pending}
              onChange={(e) => setAccepted(e.target.checked)}
            />
            남은 항목을 확인했고 마감합니다
          </label>
        )}
      </section>
    </Dialog>
  );
}
export function ReopenDialog({
  preview,
  pending,
  onConfirm,
  onCancel,
}: {
  preview: api.ReopenPreview;
  pending: boolean;
  onConfirm(reason: string, accepted: boolean): Promise<void>;
  onCancel(): void;
}) {
  const [reason, setReason] = useState("");
  const [accepted, setAccepted] = useState(false);
  const valid = reason.trim().length > 0 && [...reason.trim()].length <= 500;
  return (
    <Dialog
      open
      title={`${preview.month} 마감을 해제할까요?`}
      wide
      testId="month-close-reopen-dialog"
      confirmTestId="month-close-reopen-submit-button"
      cancelTestId="month-close-reopen-cancel-button"
      confirmActionId="B143-14-REOPEN-SUBMIT"
      cancelActionId="B143-14-REOPEN-CANCEL"
      pending={pending}
      onCancel={onCancel}
      onConfirm={() => onConfirm(reason.trim(), accepted)}
      confirmLabel="마감 해제"
      confirmDisabled={
        !preview.canReopen ||
        preview.blockingIssues.length > 0 ||
        !valid ||
        (preview.adjustmentCount > 0 && !accepted)
      }
    >
      <section
        data-screen-id="SCR-UI-14-M2"
        data-testid="month-close-reopen-panel"
      >
        <Issues items={preview.blockingIssues} />
        <p>
          해제하면 이 달 자료를 다시 고칠 수 있습니다. 다시 마감할 때 이 달
          숫자를 새로 고정하고, 법인세 월할은 이 달부터 마감 안 된 달끼리 다시
          나눕니다. 영업 파트 정산 확정은 그대로입니다. 사유는 기록에 남습니다.
        </p>
        {preview.adjustmentCount > 0 && (
          <>
            <p>
              이 달을 고친 전월 조정이 {preview.adjustmentCount}건(
              {formatDecimal(preview.adjustmentAmount)}원,{" "}
              {preview.adjustmentMonths.join(", ")} 반영) 있습니다. 이 달을 직접
              고치면 같은 정정이 두 번 들어가지 않게 그 줄은 재무 항목에서 지워
              주세요.
            </p>
            <div className="month-close-adjustments">
              <table aria-label="후속조정">
                <thead>
                  <tr>
                    {["반영 월", "금액", "사유"].map((h) => (
                      <th key={h}>{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {preview.adjustments.map((a) => (
                    <tr
                      key={a.adjustmentId}
                      data-testid={`month-close-adjustment-${a.adjustmentId}`}
                    >
                      <td>{a.applyMonth}</td>
                      <td>{formatDecimal(a.amount)}</td>
                      <td>{a.reason}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </>
        )}
        <label className="sicost-field">
          해제 사유*
          <textarea
            data-testid="month-close-reason"
            data-action-id="B143-14-REASON"
            placeholder="예: 9월 청구 누락 2건 반영"
            value={reason}
            disabled={pending}
            aria-invalid={reason !== "" && !valid}
            onChange={(e) => setReason(e.target.value)}
          />
        </label>
        {!valid && (
          <p className="sicost-field-error">
            해제 사유를 공백 제외 1~500자로 입력해 주세요.
          </p>
        )}
        {preview.adjustmentCount > 0 && (
          <label>
            <input
              type="checkbox"
              data-testid="month-close-accept-adjustments"
              data-action-id="B143-14-ADJ-ACCEPT"
              checked={accepted}
              disabled={pending}
              onChange={(e) => setAccepted(e.target.checked)}
            />
            전월 조정을 확인했습니다
          </label>
        )}
      </section>
    </Dialog>
  );
}
export function MonthCloseScreen() {
  const navigate = useNavigate();
  const [month, setMonth] = useState("");
  const [context, setContext] = useState<PeriodContext | null>(null);
  const [data, setData] = useState<api.MonthCloses | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(50);
  const [preview, setPreview] = useState<api.ClosePreview | null>(null);
  const [reopen, setReopen] = useState<api.ReopenPreview | null>(null);
  const [opening, setOpening] = useState(false);
  const [needsReload, setNeedsReload] = useState(false);
  const action = useRef(false);
  const generation = useRef(0);
  const currentMonth = useRef(month);
  currentMonth.current = month;
  const { pending, message, commit } = useCommitRefresh(failedRefresh);
  const blocked = pending || opening || needsReload;
  useEffect(() => {
    const changed = (event: Event) => {
      const detail = (
        event as CustomEvent<{
          screenId: string;
          value: { value: string };
          context: PeriodContext;
        }>
      ).detail;
      if (detail?.screenId !== "UI-14") return;
      setContext(detail.context);
      if (detail.value.value === currentMonth.current) return;
      generation.current++;
      currentMonth.current = detail.value.value;
      setMonth(detail.value.value);
      setPage(1);
      setPreview(null);
      setReopen(null);
      setData(null);
      setNeedsReload(false);
    };
    window.addEventListener("sicost:screen-period-changed", changed);
    return () =>
      window.removeEventListener("sicost:screen-period-changed", changed);
  }, []);
  useEffect(() => {
    const changing = (event: Event) => {
      if (
        (event as CustomEvent<{ screenId: string }>).detail?.screenId ===
          "UI-14" &&
        (pending || opening || preview || reopen)
      )
        event.preventDefault();
    };
    window.addEventListener("sicost:screen-period-changing", changing);
    return () =>
      window.removeEventListener("sicost:screen-period-changing", changing);
  }, [pending, opening, preview, reopen]);
  const reload = useCallback(async () => {
    if (!month) return;
    const request = ++generation.current;
    setLoading(true);
    setError("");
    try {
      const result = await api.getMonthCloses(month, page, size);
      if (request === generation.current && currentMonth.current === month) {
        setData(result);
        setNeedsReload(false);
      }
    } catch (e) {
      if (request === generation.current) setError(errorText(e));
      throw e;
    } finally {
      if (request === generation.current) setLoading(false);
    }
  }, [month, page, size]);
  useEffect(() => {
    void reload().catch(() => {});
    return () => {
      generation.current++;
    };
  }, [reload]);
  useEffect(() => {
    const refresh = () => {
      if (!action.current) void reload().catch(() => {});
    };
    window.addEventListener(api.MONTH_CLOSE_COMMITTED, refresh);
    window.addEventListener("focus", refresh);
    return () => {
      window.removeEventListener(api.MONTH_CLOSE_COMMITTED, refresh);
      window.removeEventListener("focus", refresh);
    };
  }, [reload]);
  const save = async (operation: () => Promise<unknown>) => {
    if (action.current || blocked) return;
    action.current = true;
    setError("");
    try {
      await commit(async () => {
        await operation();
        setPreview(null);
        setReopen(null);
        setNeedsReload(true);
        api.publishMonthCloseCommit(month);
      }, reload);
    } catch (e) {
      setError(errorText(e));
      if (e instanceof ApiError && e.body.code === "STALE_WRITE") {
        setPreview(null);
        setReopen(null);
        setNeedsReload(true);
      } else if (!(e instanceof ApiError)) setNeedsReload(true);
      throw e;
    } finally {
      action.current = false;
    }
  };
  const open = async (kind: "close" | "reopen") => {
    if (!data || blocked || action.current) return;
    action.current = true;
    setOpening(true);
    setError("");
    try {
      if (kind === "close")
        setPreview(await api.createMonthClosePreview(month, data.revision));
      else
        setReopen(
          await api.createMonthCloseReopenPreview(month, data.revision),
        );
    } catch (e) {
      setError(errorText(e));
      if (e instanceof ApiError && e.body.code === "STALE_WRITE")
        setNeedsReload(true);
    } finally {
      setOpening(false);
      action.current = false;
    }
  };
  const link = (check: api.CloseCheck) => {
    if (!check.linkScreenId || !context) return;
    if (
      ["SETL", "BILL_MISS"].includes(check.code) &&
      check.linkMonth.slice(0, 4) !== context.currentMonth.slice(0, 4)
    ) {
      setError(
        "해당 업무 화면의 과거 연도는 조회 전용입니다. 기존 화면의 기간 범위를 확인해 주세요.",
      );
      return;
    }
    navigate(`/${check.linkScreenId}`, {
      state: {
        month: check.linkMonth,
        fromMonthClose: true,
        filter: check.linkFilter,
        partId: check.code === "SETL" ? check.linkFilter : undefined,
        section: check.code === "BILL_MISS" ? "checks" : undefined,
      },
    });
  };

  return (
    <SharedScreenFrame
      screenId="UI-14"
      screenGuide={monthCloseGuide}
      periodBarOptions={{
        showMonthCloseStatus: false,
        loadingFallback: <ScreenLoading message="월 마감 자료를 불러오는 중입니다." />,
      }}
    >
      <div
        className="month-close"
        data-screen-id="UI-14"
        data-testid="month-close-screen"
      >
        <ScreenTitleActions>
          <div className="month-close-actions">
            <button
              type="button"
              data-testid="month-close-reload-button"
              disabled={pending || loading || opening || !month}
              onClick={() => void reload().catch(() => {})}
            >
              다시 조회
            </button>
            {data?.status === "WORK" && (
              <>
                <button
                  data-testid="month-close-check-button"
                  data-action-id="B143-14-CHECK"
                  disabled={
                    blocked ||
                    loading ||
                    !data.managementAllowed ||
                    !data.canCheck
                  }
                  onClick={() =>
                    void save(() =>
                      api.saveMonthCloseChecks(month, data.revision),
                    ).catch(() => {})
                  }
                >
                  다시 점검
                </button>
                <button
                  className="sicost-primary"
                  data-testid="month-close-close-button"
                  data-action-id="B143-14-CLOSE-OPEN"
                  disabled={
                    blocked ||
                    loading ||
                    !data.managementAllowed ||
                    !data.canClose
                  }
                  onClick={() => void open("close")}
                >
                  마감하기
                </button>
              </>
            )}
            {data?.status === "CLOSE" && (
              <>
                {data.canReopen &&
                  data.month === data.latestCloseMonth && (
                    <button
                      data-testid="month-close-reopen-button"
                      data-action-id="B143-14-REOPEN-OPEN"
                      disabled={
                        blocked || loading || !data.managementAllowed
                      }
                      onClick={() => void open("reopen")}
                    >
                      마감 해제
                    </button>
                  )}
              </>
            )}
          </div>
        </ScreenTitleActions>
        {error && (
          <ErrorBanner
            message={error}
            retryTestId="month-close-error-retry"
            pending={pending || loading}
            onRetry={() => void reload().catch(() => {})}
          />
        )}
        {message && (
          <p className="sicost-banner sicost-tone-warn" role="status">{message}</p>
        )}
        {opening && (
          <ScreenLoading inline message="최신 영향을 확인하는 중입니다." />
        )}
        {month && !error && (loading || !data) && (
          <ScreenLoading
            inline={Boolean(data)}
            message="월 마감 자료를 불러오는 중입니다."
            testId="month-close-loading"
          />
        )}
        {data && data.month === month && (
          <>
            <PeriodStatus className="sicost-period-summary">
              <span className="sicost-period-summary-item">
                <StatusBadge tone={data.status === "CLOSE" ? "lock" : "info"}>
                  {data.status === "CLOSE" ? "마감" : "미마감"}
                </StatusBadge>
              </span>
              <span className="sicost-period-summary-item">
                <span className="sicost-period-summary-label">다음 마감월</span>
                <b>{data.nextCloseMonth}</b>
              </span>
              <span className="sicost-period-summary-item">
                <span className="sicost-period-summary-label">최신 마감월</span>
                <b>{data.latestCloseMonth ?? "없음"}</b>
              </span>
            </PeriodStatus>

            {data.status === "CLOSE" && (
              <div
                className="month-close-lock"
                data-testid="month-close-closed-notice"
              >
                <p>
                  {data.month}는 마감되었습니다({formatDateTime(data.closedAt)}{" "}
                  · {data.closedByName ?? "–"}). 고칠 내용은 다음 달 전월
                  조정으로 입력하세요.
                </p>
                <small>
                  {data.month === data.latestCloseMonth
                    ? "마감 해제는 시스템 관리자만 할 수 있습니다"
                    : "가장 최근에 마감한 달만 해제할 수 있습니다"}
                </small>
              </div>
            )}
            <section className="month-close-card sicost-section-card">
              <header className="sicost-section-heading">
                <h2>마감 점검</h2>
                {data.status === "WORK" ? (
                  <div
                    className="month-close-check-summary"
                    role="status"
                    data-testid="month-close-check-summary"
                  >
                    <span className="month-close-section-note">전체 {data.checks.length}개</span>
                    <StatusBadge tone={data.warningCount > 0 ? "warn" : "ok"}>
                      {data.warningCount > 0 ? `확인 필요 ${data.warningCount}개` : "모두 정상"}
                    </StatusBadge>
                  </div>
                ) : (
                  <span className="month-close-section-note">
                    마감 당시 남은 항목은 마감 이력에서 볼 수 있습니다.
                  </span>
                )}
              </header>
              <CloseCheckCards
                items={data.checks}
                onLink={link}
                closed={data.status === "CLOSE"}
              />
              <Issues items={data.issues} />
            </section>
            <section className="month-close-card sicost-section-card">
              <header className="sicost-section-heading">
                <h2>{month.slice(0, 4)}년 마감 이력</h2>
              </header>
              <DataTable
                testId="month-close-history"
                tableLabel="마감 이력"
                rows={data.history.items}
                columns={historyColumns}
                rowKey={(row) => `${row.month}-${row.historySequence}`}
                empty="선택연도 마감 이력이 없습니다."
                emptyAction={
                  <button
                    type="button"
                    data-testid="month-close-reset-button"
                    onClick={() =>
                      window.dispatchEvent(new Event("sicost:month-close-reset-period"))
                    }
                  >
                    조건 초기화
                  </button>
                }
              />
              {data.history.total > 0 && (
                <Pagination
                  page={data.history.page}
                  size={data.history.size}
                  total={data.history.total}
                  testId="month-close-history"
                  disabled={loading || blocked}
                  actionIds={{
                    previous: "B143-14-HISTORY-PREV",
                    next: "B143-14-HISTORY-NEXT",
                    size: "B143-14-HISTORY-SIZE",
                  }}
                  onPageChange={setPage}
                  onSizeChange={(nextSize) => {
                    setSize(nextSize);
                    setPage(1);
                  }}
                />
              )}
            </section>
          </>
        )}
        {preview && (
          <ClosePreviewDialog
            key={preview.revision}
            preview={preview}
            pending={pending}
            onCancel={() => setPreview(null)}
            onConfirm={(accepted) =>
              save(() =>
                api.saveMonthClose(month, {
                  expectedRevision: preview.revision,
                  operationId: crypto.randomUUID(),
                  acceptWarnings: accepted,
                }),
              )
            }
          />
        )}
        {reopen && (
          <ReopenDialog
            key={reopen.revision}
            preview={reopen}
            pending={pending}
            onCancel={() => setReopen(null)}
            onConfirm={(reason, accepted) =>
              save(() =>
                api.saveMonthCloseReopen(month, {
                  expectedRevision: reopen.revision,
                  operationId: crypto.randomUUID(),
                  reason,
                  acceptAdjustments: accepted,
                }),
              )
            }
          />
        )}
      </div>
    </SharedScreenFrame>
  );
}
