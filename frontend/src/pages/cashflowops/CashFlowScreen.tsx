import { useEffect, useRef, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { ApiError } from "../../shared/api";
import { ErrorBanner } from "../../shared/Feedback";
import { formatAmount, type AmountUnit } from "../../shared/format";
import { SharedScreenFrame } from "../shared/FollowUpScreen";
import { CashFlowStatementTable } from "../shared/FinancialStatementTable";
import { SourceBreakdown, type CashSelection } from "./SourceBreakdown";
import { BalanceTrendChart } from "./BalanceTrendChart";
import * as api from "./cashFlowApi";
import "../operating/operatingProfit.css";
import "./cashFlow.css";
const events = [
  "sicost:financial-committed",
  "sicost:financial-items-committed",
  "sicost:corporate-tax-committed",
  "sicost:adjustments-committed",
  "sicost:bills-committed",
  "sicost:billing-committed",
  "sicost:payroll-committed",
  "sicost:developers-committed",
  "sicost:operating-standards-committed",
  "sicost:month-close-committed",
];
const unitCode = (unit: AmountUnit): api.CashUnit =>
  unit === "원" ? "WON" : unit === "천원" ? "THOUSAND_WON" : "MILLION_WON";
// SCR-UI-21
export function CashFlowScreen({
  amountUnit,
  onAmountUnitChange,
}: {
  amountUnit?: AmountUnit;
  onAmountUnitChange?(value: AmountUnit): void;
} = {}) {
  const navigate = useNavigate(),
    location = useLocation();
  const incoming = location.state as { cashFlowSource?: CashSelection } | null;
  const [year, setYear] = useState<number>(),
    [localUnit, setLocalUnit] = useState<AmountUnit>("천원"),
    [data, setData] = useState<api.CashFlowResult>();
  const [loading, setLoading] = useState(true),
    [error, setError] = useState<unknown>(),
    [downloadError, setDownloadError] = useState<unknown>(),
    [refresh, setRefresh] = useState(0);
  const [selection, setSelection] = useState<CashSelection | undefined>(
      incoming?.cashFlowSource,
    ),
    [notice, setNotice] = useState(""),
    [pending, setPending] = useState(false),
    [mobile, setMobile] = useState(() => window.innerWidth < 768);
  const trigger = useRef<HTMLButtonElement>(),
    exporting = useRef(false),
    unit = amountUnit ?? localUnit;
  const restoredUnit = useRef<api.CashUnit>();
  const [retainResult, setRetainResult] = useState(false);
  useEffect(() => {
    const period = (event: Event) => {
      const detail = (
        event as CustomEvent<{
          screenId: string;
          value: { type: string; value: string };
        }>
      ).detail;
      if (detail?.screenId === "UI-21" && detail.value.type === "YEAR") {
        setYear(Number(detail.value.value));
        setSelection((previous) =>
          previous?.month.slice(0, 4) === detail.value.value
            ? previous
            : undefined,
        );
      }
    };
    const changed = () => setRefresh((n) => n + 1),
      resize = () => setMobile(window.innerWidth < 768);
    window.addEventListener("sicost:screen-period-changed", period);
    window.addEventListener("focus", changed);
    window.addEventListener("resize", resize);
    events.forEach((name) => window.addEventListener(name, changed));
    return () => {
      window.removeEventListener("sicost:screen-period-changed", period);
      window.removeEventListener("focus", changed);
      window.removeEventListener("resize", resize);
      events.forEach((name) => window.removeEventListener(name, changed));
    };
  }, []);
  useEffect(() => {
    if (restoredUnit.current === unitCode(unit)) {
      restoredUnit.current = undefined;
      setLoading(false);
      return;
    }
    const abort = new AbortController();
    setLoading(true);
    setError(undefined);
    setRetainResult(false);
    void api
      .getCashFlow(year, unitCode(unit), abort.signal)
      .then((value) => {
        if (!abort.signal.aborted) setData(value);
      })
      .catch((e) => {
        if (abort.signal.aborted) return;
        setError(e);
        if (
          data &&
          (year === undefined || data.year === year) &&
          data.unit !== unitCode(unit)
        ) {
          const previous: AmountUnit =
            data.unit === "WON"
              ? "원"
              : data.unit === "MILLION_WON"
                ? "백만원"
                : "천원";
          restoredUnit.current = data.unit;
          setRetainResult(true);
          if (onAmountUnitChange) onAmountUnitChange(previous);
          else setLocalUnit(previous);
        }
      })
      .finally(() => {
        if (!abort.signal.aborted) setLoading(false);
      });
    return () => abort.abort();
  }, [year, unit, refresh]);
  function choose(value: CashSelection | undefined) {
    setNotice("");
    setSelection(value);
    navigate(location.pathname, {
      replace: true,
      state: { ...location.state, cashFlowSource: value },
    });
  }
  function source(s: api.CashSource) {
    if (
      !data ||
      !s.sourceLink ||
      !["/UI-10", "/UI-13", "/UI-02", "/UI-12", "/UI-19"].includes(s.sourceLink)
    )
      return;
    if (
      s.attributionMonth > data.asOfDate.slice(0, 7) ||
      (s.sourceLink !== "/UI-10" &&
        s.attributionMonth.slice(0, 4) !== data.asOfDate.slice(0, 4))
    ) {
      setNotice(
        "목적 화면의 조회 기간 범위를 벗어나 이동할 수 없습니다. 기간 설정은 변경하지 않았습니다.",
      );
      return;
    }
    navigate(s.sourceLink, {
      state: {
        month: s.attributionMonth,
        targetMonth: s.sourceMonth,
        sourceType: s.sourceType,
        sourceId: s.sourceId,
        section:
          s.item === "CORP_TAX"
            ? "tax"
            : s.sourceType === "TB_BL_BILL"
              ? "checks"
              : undefined,
        filter: s.sourceType === "TB_BL_BILL" ? "missing" : undefined,
        fromCashFlow: true,
      },
    });
  }
  async function download() {
    if (exporting.current || !data || mobile) return;
    exporting.current = true;
    setPending(true);
    setDownloadError(undefined);
    try {
      await api.getCashFlowExport(data.year, data.unit);
    } catch (e) {
      setDownloadError(e);
    } finally {
      exporting.current = false;
      setPending(false);
    }
  }
  const displayed =
    data && (year === undefined || data.year === year) ? data : undefined;
  const cells = displayed
    ? [...displayed.months, ...displayed.periodTotals]
    : [];
  const row = (
    key: string,
    label: string,
    field: "openingBalance" | "incomeTotal" | "expenseTotal" | "closingBalance",
  ) => ({ key, label, values: cells.map((c) => c[field]) });
  const category = (key: string, label: string) => ({
    key,
    label,
    section: true,
    values: cells.map(() => null),
  });
  const rows = displayed
    ? [
        row("opening", "전월이월금", "openingBalance"),
        category("income-section", "수입"),
        ...api.cashItems.slice(0, 3).map((item) => ({
          key: item,
          label: api.cashLabels[item],
          sourceItem: item,
          values: cells.map(
            (c) => c.items.find((i) => i.item === item)?.amount ?? null,
          ),
        })),
        row("income", "수입 합계", "incomeTotal"),
        category("expense-section", "지출"),
        ...api.cashItems.slice(3).map((item) => ({
          key: item,
          label: api.cashLabels[item],
          sourceItem: item,
          values: cells.map(
            (c) => c.items.find((i) => i.item === item)?.amount ?? null,
          ),
        })),
        row("expense", "지출 합계", "expenseTotal"),
        row("balance", "월말 잔액", "closingBalance"),
      ]
    : [];
  return (
    <SharedScreenFrame screenId="UI-21">
      <div
        className="operating-profit cash-flow"
        data-testid="cash-flow-screen"
      >
        <div className="operating-toolbar">
          {!mobile && (
            <button
              data-testid="cash-export"
              disabled={pending || loading || !displayed || error != null}
              onClick={() => void download()}
            >
              {pending ? "파일 생성 중…" : "엑셀 다운로드"}
            </button>
          )}
          <label>
            단위{" "}
            <select
              data-testid="cash-unit"
              aria-label="단위"
              value={unit}
              disabled={pending || loading}
              onChange={(e) => {
                const next = e.target.value as AmountUnit;
                if (onAmountUnitChange) onAmountUnitChange(next);
                else setLocalUnit(next);
              }}
            >
              {["원", "천원", "백만원"].map((value) => (
                <option key={value}>{value}</option>
              ))}
            </select>
          </label>
        </div>
        {loading && <p role="status">자금수지표를 불러오는 중입니다.</p>}
        {error != null && (
          <ErrorBanner
            message={
              error instanceof Error
                ? error.message
                : "현금표를 조회하지 못했습니다."
            }
            requestId={
              error instanceof ApiError ? error.body.requestId : undefined
            }
            onRetry={() => setRefresh((n) => n + 1)}
          />
        )}
        {downloadError != null && (
          <ErrorBanner
            message={
              downloadError instanceof Error
                ? downloadError.message
                : "다운로드에 실패했습니다."
            }
            requestId={
              downloadError instanceof ApiError
                ? downloadError.body.requestId
                : undefined
            }
          />
        )}
        {notice && !selection && (
          <p role="status" className="operating-banner">
            {notice}
          </p>
        )}
        {displayed && (!error || retainResult) && (
          <>
            <p className="operating-muted">
              기준일 {displayed.asOfDate} · 시작월 {displayed.baseMonth} ·
              기초잔액 {formatAmount(displayed.baseAmount, "원")}원 · 최소
              운영자금 {formatAmount(displayed.minimumCash, "원")}원 (기준월{" "}
              {displayed.minimumCashMonth})
            </p>
            {displayed.shortages.length > 0 && (
              <div className="operating-banner">
                자금 부족 예정월:{" "}
                {displayed.shortages
                  .map((p) => `${p.month} (${formatAmount(p.balance, "원")}원)`)
                  .join(" · ")}
              </div>
            )}
            {displayed.issues.length > 0 && (
              <div className="operating-banner">
                미확인 원천이 있습니다. 금액셀의 계산근거를 확인해 주세요.
                {displayed.issues.map((i, n) => (
                  <span key={n}>
                    {i.month} {i.sourceId} {i.message}
                  </span>
                ))}
              </div>
            )}
            <BalanceTrendChart
              points={displayed.chartPoints}
              minimum={displayed.minimumCash}
            />
            <p>{displayed.year}년 자금수지 (부가세 포함)</p>
            <CashFlowStatementTable
              columns={[
                ...displayed.months.map((m) => ({
                  key: m.month,
                  label: `${Number(m.month.slice(5))}월`,
                  status: m.status,
                  sourceMonth: m.month,
                  shortage: m.shortage,
                })),
                ...displayed.periodTotals.map((p) => ({
                  key: p.period,
                  label:
                    p.period === "FIRST_HALF"
                      ? "상반기"
                      : p.period === "SECOND_HALF"
                        ? "하반기"
                        : "연간",
                })),
              ]}
              rows={rows}
              unit={unit}
              onSource={(month, item, element) => {
                trigger.current = element;
                choose({
                  month,
                  item: item as api.CashItem,
                  page: 1,
                  size: 50,
                });
              }}
            />
            <div className="operating-toolbar">
              <button
                data-testid="cash-receivables"
                onClick={() =>
                  navigate("/UI-13", {
                    state: {
                      section: "receivables",
                      month: displayed.asOfDate.slice(0, 7),
                      fromCashFlow: true,
                    },
                  })
                }
              >
                미수금 보기
              </button>
              <button
                data-testid="cash-standards"
                onClick={() =>
                  navigate("/UI-27", {
                    state: {
                      section: "standards",
                      key: "MIN_CASH",
                      month: displayed.minimumCashMonth,
                    },
                  })
                }
              >
                기준 확인
              </button>
            </div>
            {selection && (
              <SourceBreakdown
                selection={selection}
                notice={notice}
                unit={unit}
                refresh={refresh}
                onChange={choose}
                onClose={() => {
                  choose(undefined);
                  trigger.current?.focus();
                }}
                onSource={source}
              />
            )}
          </>
        )}
      </div>
    </SharedScreenFrame>
  );
}
