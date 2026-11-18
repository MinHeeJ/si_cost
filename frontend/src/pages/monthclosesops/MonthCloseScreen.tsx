import { ScreenTitleActions } from "../../shared/ScreenTitleActions";
import { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { SharedScreenFrame } from "../shared/FollowUpScreen";
import { ApiError } from "../../shared/api";
import { Dialog } from "../../shared/Dialog";
import { ErrorBanner, StatusBadge } from "../../shared/Feedback";
import { useCommitRefresh } from "../../shared/useCommitRefresh";
import { getPeriodContext, type PeriodContext } from "../shared/sharedApi";
import * as api from "./monthCloseApi";
import "./monthClose.css";

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
        <p role="alert" key={`${item.code}-${index}`}>
          {item.message} {item.sourceType} {item.sourceId} {item.field}
        </p>
      ))}
    </>
  );
}
function Snapshots({ items }: { items: api.CloseSnapshot[] }) {
  return (
    <ul>
      {items.map((item) => (
        <li
          key={item.table}
          data-testid={`month-close-snapshot-${item.table.toLowerCase().replaceAll("_", "-")}`}
        >
          {item.table} {item.rowCount}행 · 독립고정 보존{" "}
          {item.preservedIndependentFixedCount}행
        </li>
      ))}
    </ul>
  );
}
export function CloseCheckCards({
  items,
  onLink,
  testIdPrefix = "month-close-check",
}: {
  items: api.CloseCheck[];
  onLink?: (check: api.CloseCheck) => void;
  testIdPrefix?: string;
}) {
  return (
    <div className="month-close-grid">
      {items.map((check) => (
        <article
          key={check.code}
          className={`month-close-check ${check.status.toLowerCase()}`}
          data-testid={`${testIdPrefix}-${check.code.toLowerCase().replaceAll("_", "-")}`}
        >
          <div className="month-close-heading">
            <h3>{check.name}</h3>
            <StatusBadge tone={check.status === "WARN" ? "warn" : "ok"}>
              {check.status === "WARN" ? "확인 필요" : "정상"}
            </StatusBadge>
          </div>
          <strong>
            {check.count ?? "–"}
            {check.code === "PAY" || check.code === "MM_OVER"
              ? "명"
              : check.code === "SETL" || check.code === "BILL_MISS"
                ? "개"
                : "건"}
          </strong>
          <p>{check.description ?? "–"}</p>
          {check.checkedAt && <small>마지막 저장 검사 {check.checkedAt}</small>}
          {check.linkScreenId && onLink && (
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
  const cash = preview.cashImpact;
  return (
    <Dialog
      open
      title={`${preview.month} 월 마감 확인`}
      wide
      testId="month-close-preview-dialog"
      confirmTestId="month-close-submit-button"
      cancelTestId="month-close-cancel-button"
      confirmActionId="B143-14-CLOSE-SUBMIT"
      cancelActionId="B143-14-CLOSE-CANCEL"
      pending={pending}
      onCancel={onCancel}
      onConfirm={() => onConfirm(accepted)}
      confirmLabel="확인 후 마감"
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
        <CloseCheckCards
          items={preview.checks}
          testIdPrefix="month-close-preview-check"
        />
        <p>
          남은 {preview.warningCount}개:{" "}
          {preview.remainingCheckNames.join(", ") || "없음"}
        </p>
        <p>
          대상파트 {preview.eligiblePartCount} · 자동확정예정{" "}
          {preview.autoConfirmCount}
        </p>
        <ul>
          {preview.parts.map((part) => (
            <li
              key={part.partId}
              data-testid={`month-close-part-${part.partId}`}
            >
              {part.partId} {part.partName} {part.status} ·{" "}
              {part.willAutoConfirm ? "AUTO 예정" : "기존 정산 유지"}
              <Issues items={part.issues} />
            </li>
          ))}
        </ul>
        <Snapshots items={preview.snapshots} />
        <p>예정급여 사용: {preview.usesEstimatedPayroll ? "예" : "아니오"}</p>
        <Issues items={preview.blockingIssues} />
        <div className="month-close-impact">
          <h3>현금 실적전환 영향 (원)</h3>
          <p>
            제외 미수 {cash.excludedUnpaidBills ?? "미확인"} · 제외 미확인세금{" "}
            {cash.excludedUnconfirmedTaxes ?? "미확인"}
          </p>
          <p>
            수입 {cash.incomeBefore ?? "미확인"} →{" "}
            {cash.incomeAfter ?? "미확인"}
          </p>
          <p>
            납부 {cash.taxPaymentBefore ?? "미확인"} →{" "}
            {cash.taxPaymentAfter ?? "미확인"}
          </p>
          <p>
            월말잔액 {cash.closingBalanceBefore ?? "미확인"} →{" "}
            {cash.closingBalanceAfter ?? "미확인"}
          </p>
        </div>
        <p>
          실제 입금·납부 실행이나 미래월 이동이 아닙니다. 기존 정산은
          재확정하거나 덮어쓰지 않습니다.
        </p>
        <p>
          이전 전월조정은 UI-10에서 별도 정리하고 중복 보정 여부를 확인하세요.
        </p>
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
            남은 WARN을 확인하고 마감합니다
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
      title={`${preview.month} 마감 해제`}
      wide
      testId="month-close-reopen-dialog"
      confirmTestId="month-close-reopen-submit-button"
      cancelTestId="month-close-reopen-cancel-button"
      confirmActionId="B143-14-REOPEN-SUBMIT"
      cancelActionId="B143-14-REOPEN-CANCEL"
      pending={pending}
      onCancel={onCancel}
      onConfirm={() => onConfirm(reason.trim(), accepted)}
      confirmLabel="확인 후 해제"
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
        <p>
          기존 마감자 {preview.closedBy} · 일시 {preview.closedAt}
        </p>
        <Snapshots items={preview.snapshots} />
        <p>정산확정 유지: CNFM/AUTO 및 상세는 자동 취소되지 않습니다.</p>
        <p>
          모든 후속조정 {preview.adjustmentCount}건 · {preview.adjustmentAmount}
          원 · 적용월 {preview.adjustmentMonths.join(", ") || "없음"}
        </p>
        <div className="month-close-adjustments">
          <table aria-label="후속조정">
            <thead>
              <tr>
                {["ID", "대상월", "적용월", "금액", "사유"].map((h) => (
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
                  <td>{a.adjustmentId}</td>
                  <td>{a.targetMonth}</td>
                  <td>{a.applyMonth}</td>
                  <td>{a.amount}</td>
                  <td>{a.reason}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <p>
          기존 조정은 자동 삭제·상계하지 않습니다. UI-10에서 중복 보정 여부를
          확인하세요.
        </p>
        <label className="sicost-field">
          해제 사유*
          <textarea
            data-testid="month-close-reason"
            data-action-id="B143-14-REASON"
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
            후속조정 영향과 정산 유지를 확인했습니다
          </label>
        )}
        <Issues items={preview.blockingIssues} />
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
      setMonth(detail.value.value);
      setContext(detail.context);
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
  const adjust = async () => {
    if (!data) return;
    try {
      const ctx = await getPeriodContext();
      const first = [
        data.nextCloseMonth,
        `${ctx.currentMonth.slice(0, 4)}-01`,
        ctx.cashBaseMonth,
      ]
        .sort()
        .at(-1)!;
      if (first > ctx.currentMonth) {
        setError("현재 입력 가능한 미마감월이 없습니다.");
        return;
      }
      navigate("/UI-10", {
        state: {
          section: "adjustments",
          month: first,
          targetMonth: data.month,
        },
      });
    } catch (e) {
      setError(errorText(e));
    }
  };
  return (
    <SharedScreenFrame screenId="UI-14">
      <div
        className="month-close"
        data-screen-id="UI-14"
        data-testid="month-close-screen"
      >
        {error && (
          <ErrorBanner
            message={error}
            retryTestId="month-close-error-retry"
            pending={pending || loading}
            onRetry={() => void reload().catch(() => {})}
          />
        )}
        {message && <p role="status">{message}</p>}
        {opening && <p role="status">최신 영향을 확인하는 중입니다.</p>}
        {loading && <p role="status">월 마감을 불러오는 중입니다.</p>}
        {!month && <p role="status">관리 기간을 불러오는 중입니다.</p>}
        {data && data.month === month && (
          <>
            <div className="month-close-heading">
              <p>
                다음 마감월 {data.nextCloseMonth} / 최신 마감월{" "}
                {data.latestCloseMonth ?? "없음"}
              </p>
              <StatusBadge tone={data.status === "CLOSE" ? "lock" : "info"}>
                {data.status === "CLOSE" ? "마감" : "미마감"}
              </StatusBadge>
            </div>
            <p>
              관리{" "}
              {data.managementAllowed ? "가능 · 과거 운영연도 포함" : "불가"}
            </p>
            {data.status === "CLOSE" && (
              <div className="month-close-lock">
                마감자 {data.closedBy} · 마감일시 {data.closedAt} · 원월
                금액/현금은 읽기 전용입니다.
              </div>
            )}
            <section className="month-close-card">
              <div className="month-close-heading">
                <h2>마감 점검</h2>
                <ScreenTitleActions>
                  <div className="month-close-actions">
                    {data.status === "WORK" && (
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
                    {data.status === "CLOSE" && (
                      <>
                        {data.canReopen && (
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
                        <button
                          data-testid="month-close-adjust-button"
                          data-action-id="B143-14-ADJUST"
                          disabled={blocked}
                          onClick={() => void adjust()}
                        >
                          전월 조정 입력
                        </button>
                      </>
                    )}
                  </div>
                </ScreenTitleActions>
              </div>
              <p>
                점검 6개 중 {data.warningCount}개 확인 필요 ·{" "}
                {data.checkMode === "CLOSE_SNAPSHOT"
                  ? "마감 당시 6점검"
                  : "현재 계산 · 마지막 저장 검사 시각은 항목별 표시"}
              </p>
              <CloseCheckCards items={data.checks} onLink={link} />
              <Issues items={data.issues} />
            </section>
            <section className="month-close-card">
              <h2>{month.slice(0, 4)}년 마감 이력</h2>
              {data.history.items.length === 0 ? (
                <div className="month-close-empty">
                  <p>선택연도 마감 이력이 없습니다.</p>
                  <button
                    type="button"
                    onClick={() =>
                      window.dispatchEvent(
                        new Event("sicost:month-close-reset-period"),
                      )
                    }
                  >
                    조건 초기화
                  </button>
                </div>
              ) : (
                <div className="month-close-table">
                  <table aria-label="마감 이력">
                    <thead>
                      <tr>
                        {[
                          "연월",
                          "작업",
                          "처리자",
                          "일시",
                          "사유",
                          "남은 점검",
                        ].map((h) => (
                          <th key={h}>{h}</th>
                        ))}
                      </tr>
                    </thead>
                    <tbody>
                      {data.history.items.map((h) => (
                        <tr
                          key={`${h.month}-${h.historySequence}`}
                          data-testid={`month-close-history-${h.month}-${h.historySequence}`}
                        >
                          <td>{h.month}</td>
                          <td>
                            <StatusBadge
                              tone={h.action === "CLOSE" ? "lock" : "info"}
                            >
                              {h.action === "CLOSE" ? "마감" : "해제"}
                            </StatusBadge>
                          </td>
                          <td>{h.actorId}</td>
                          <td>{h.occurredAt}</td>
                          <td>{h.reason ?? "–"}</td>
                          <td>{h.remainingChecks ?? "–"}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
              <div className="month-close-actions">
                <span>
                  전체 {data.history.total}건 · {data.history.page}페이지
                </span>
                <button
                  data-testid="month-close-history-previous"
                  data-action-id="B143-14-HISTORY-PREV"
                  disabled={loading || blocked || page <= 1}
                  onClick={() => setPage((p) => p - 1)}
                >
                  이전 이력
                </button>
                <button
                  data-testid="month-close-history-next"
                  data-action-id="B143-14-HISTORY-NEXT"
                  disabled={loading || blocked || !data.history.hasMore}
                  onClick={() => setPage((p) => p + 1)}
                >
                  다음 이력
                </button>
                <label>
                  이력 크기{" "}
                  <select
                    data-testid="month-close-history-size"
                    data-action-id="B143-14-HISTORY-SIZE"
                    value={size}
                    disabled={loading || blocked}
                    onChange={(e) => {
                      setSize(Number(e.target.value));
                      setPage(1);
                    }}
                  >
                    {[20, 50, 100].map((n) => (
                      <option key={n}>{n}</option>
                    ))}
                  </select>
                </label>
              </div>
            </section>
          </>
        )}
        <button
          data-testid="month-close-reload-button"
          disabled={pending || loading || !month}
          onClick={() => void reload().catch(() => {})}
        >
          다시 조회
        </button>
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
