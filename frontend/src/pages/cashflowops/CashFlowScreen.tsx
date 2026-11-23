import { FinancialWarnings } from "../../shared/FinancialWarnings";
import { Icon } from "../../shared/Icon";
import { PeriodStatus } from "../../shared/PeriodStatus";
import { useEffect, useRef, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { ApiError } from "../../shared/api";
import { ErrorBanner } from "../../shared/Feedback";
import { formatAmount, type AmountUnit } from "../../shared/format";
import { SharedScreenFrame } from "../shared/FollowUpScreen";
import { CashFlowStatementTable } from "../shared/FinancialStatementTable";
import { flattenMenus, useSharedScreen } from "../shared/SharedScreenContext";
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
const cashRowHelp: Record<api.CashItem, string> = {
  CASH_SALES:
    "등록된 입금은 입금일이 속한 달에 반영합니다. 마감 전에는 아직 받지 못한 청구 금액도 입금 예정일이 속한 달에 반영합니다.",
  MISC: "재무 항목에 등록한 영업외수익을 입출금일이 속한 달에 금액과 부가세를 합해 반영합니다.",
  LOAN: "재무 항목에 등록한 대여금입니다. 받은 금액은 더하고 갚은 금액은 뺍니다.",
  REG_PAY: "급여 업로드 자료에서 정규직 실지급액을 가져옵니다.",
  CON_FL_PAY:
    "급여 업로드 자료에서 계약직·프리랜서 지급액을 가져옵니다. 프리랜서는 급여와 수당에서 원천세를 뺀 금액을 반영합니다.",
  OS_PAY: "급여 업로드 자료에 등록된 외주 용역비 공급가액에 부가세 10%를 더해 반영합니다.",
  UNPAID_LABOR: "재무 항목의 계좌이체 내역 중 ‘특별비’로 등록한 금액을 이체일이 속한 달에 반영합니다.",
  EXPENSE:
    "판관비·영업외비용, 법인카드 대금과 재무 항목의 ‘계좌이체’ 지출을 반영합니다. 전월 급여의 4대보험·원천세·근로소득세도 이 항목에 포함됩니다.",
  CORP_TAX: "재무 항목에 등록한 법인세입니다. 납부한 금액은 실제 납부월에, 납부 예정 금액은 예정월에 반영합니다.",
};
const cashFlowGuide = (
  <dl data-testid="cash-flow-guide">
    <div>
      <dt>조회 기준</dt>
      <dd>
        마감한 달은 실적, 마감 전 달은 예정(회색 글자)입니다. 금액은 부가세를
        포함합니다. 자금수지 시작 월·기초 잔액·최소 운영자금은 운영 기준값에서
        관리자가 정합니다.
      </dd>
    </div>
  </dl>
);
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
  const { menus } = useSharedScreen("UI-21");
  const canOpenStandards = flattenMenus(menus).some(
    (menu) => menu.screenId === "UI-27",
  );
  const incoming = location.state as {
    cashFlowSource?: CashSelection;
    fromDashboard?: boolean;
    month?: string;
  } | null;
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
  const requestPending = useRef(false),
    lastReceived = useRef(0);
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
    const focus = () => {
      if (
        document.visibilityState === "visible" &&
        !requestPending.current &&
        Date.now() - lastReceived.current >= 30_000
      )
        changed();
    };
    window.addEventListener("focus", focus);
    window.addEventListener("resize", resize);
    events.forEach((name) => window.addEventListener(name, changed));
    return () => {
      window.removeEventListener("sicost:screen-period-changed", period);
      window.removeEventListener("focus", focus);
      window.removeEventListener("resize", resize);
      events.forEach((name) => window.removeEventListener(name, changed));
    };
  }, []);
  useEffect(() => {
    if (restoredUnit.current === unitCode(unit)) {
      restoredUnit.current = undefined;
      requestPending.current = false;
      setLoading(false);
      return;
    }
    const abort = new AbortController();
    requestPending.current = true;
    setLoading(true);
    setError(undefined);
    setRetainResult(false);
    void api
      .getCashFlow(year, unitCode(unit), abort.signal)
      .then((value) => {
        if (!abort.signal.aborted) {
          setData(value);
          lastReceived.current = Date.now();
        }
      })
      .catch((e) => {
        if (abort.signal.aborted) return;
        setError(e);
        if (e instanceof ApiError && (e.status === 401 || e.status === 403)) {
          setData(undefined);
          setRetainResult(false);
          return;
        }
        if (data) setRetainResult(true);
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
        if (!abort.signal.aborted) {
          requestPending.current = false;
          setLoading(false);
        }
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
  const issues = (displayed?.issues ?? []).map((issue) =>
    [issue.month, issue.message, issue.sourceId && `(원천 ${issue.sourceId})`]
      .filter(Boolean)
      .join(" "),
  );
  const cells = displayed
    ? [...displayed.months, ...displayed.periodTotals]
    : [];
  const row = (
    key: string,
    label: string,
    field: "openingBalance" | "incomeTotal" | "expenseTotal" | "closingBalance",
  ) => ({ key, label, values: cells.map((c) => c[field]) });
  const category = (key: string, label: string, help?: string) => ({
    key,
    label,
    help,
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
          help: cashRowHelp[item],
          sourceItem: item,
          values: cells.map(
            (c) => c.items.find((i) => i.item === item)?.amount ?? null,
          ),
        })),
        row("income", "수입 합계", "incomeTotal"),
        category(
          "expense-section",
          "지출",
          "지급은 모두 완료된 것으로 보고 집계합니다. 실제와 차이가 있으면 재무 항목에 조정할 내역을 추가합니다.",
        ),
        ...api.cashItems.slice(3).map((item) => ({
          key: item,
          label: api.cashLabels[item],
          help: cashRowHelp[item],
          sourceItem: item,
          values: cells.map(
            (c) => c.items.find((i) => i.item === item)?.amount ?? null,
          ),
        })),
        row("expense", "지출 합계", "expenseTotal"),
        row("balance", "이월금", "closingBalance"),
      ]
    : [];
  function openReceivables() {
    if (!displayed) return;
    navigate("/UI-13", {
      state: {
        section: "receivables",
        month: displayed.asOfDate.slice(0, 7),
        fromCashFlow: true,
      },
    });
  }
  return (
    <SharedScreenFrame
      screenId="UI-21"
      screenGuide={cashFlowGuide}
      headerActions={
        <div className="sicost-title-action-group operating-actions">
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
          {!mobile && (
            <button
              type="button"
              data-testid="cash-export"
              disabled={pending || loading || !displayed || error != null}
              onClick={() => void download()}
            >
              {pending ? "파일 생성 중…" : "엑셀 다운로드"}
            </button>
          )}
        </div>
      }
    >
      {incoming?.fromDashboard &&
        incoming.month &&
        /^\d{4}-(0[1-9]|1[0-2])$/.test(incoming.month) && (
          <p className="sicost-banner sicost-tone-info">
            대시보드 요청월 {incoming.month}
            {data && incoming.month.slice(0, 4) !== String(data.year)
              ? " · 조회 가능 연도 밖입니다. 현재 허용 연도를 표시합니다."
              : " · 해당 월의 자금 행을 확인해 주세요."}
          </p>
        )}
      <div
        className="operating-profit cash-flow"
        data-testid="cash-flow-screen"
      >
        {loading && !data && (
          <p role="status">자금수지표를 불러오는 중입니다.</p>
        )}
        {data && loading && (
          <p role="status" className="sicost-refresh-status">
            갱신 중…
          </p>
        )}
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
            <PeriodStatus className="sicost-period-summary cash-period-summary">
              <span className="sicost-period-summary-item cash-period-as-of">
                <span className="sicost-period-summary-label">기준일</span>
                <b>{displayed.asOfDate}</b>
              </span>
              <span className="sicost-period-summary-item">
                <span className="sicost-period-summary-label">시작월</span>
                <b>{displayed.baseMonth}</b>
              </span>
              <span className="sicost-period-summary-item">
                <span className="sicost-period-summary-label">적용월</span>
                <b>{displayed.minimumCashMonth}</b>
              </span>
              <span className="sicost-period-summary-item">
                <span className="sicost-period-summary-label">기초 잔액</span>
                <b>{formatAmount(displayed.baseAmount, "원")}원</b>
              </span>
              <span className="sicost-period-summary-item">
                <span className="sicost-period-summary-label">최소 운영자금</span>
                <b>{formatAmount(displayed.minimumCash, "원")}원</b>
              </span>
            </PeriodStatus>
            {displayed.shortages.length > 0 && (
              <div
                className="cash-shortage-alert"
                role="alert"
                data-testid="cash-shortage-alert"
              >
                <Icon name="warning" size={21} />
                <div className="cash-shortage-content">
                  <h2>월말 자금 부족 예상</h2>
                  <p>
                    {displayed.shortages.map((point) => `${Number(point.month.slice(5))}월`).join(", ")}{" "}
                    월말 잔액이 최소 운영자금({formatAmount(displayed.minimumCash, "원")}원)보다 적을 것으로 예상됩니다.
                  </p>
                  <ul aria-label="월별 예상 잔액">
                    {displayed.shortages.map((point) => (
                      <li key={point.month}>
                        <span>{Number(point.month.slice(5))}월</span>
                        <strong>{formatAmount(point.balance, "원")}원</strong>
                      </li>
                    ))}
                  </ul>
                </div>
                <button
                  type="button"
                  data-testid="cash-shortage-receivables"
                  onClick={openReceivables}
                >
                  미수금 보기
                </button>
              </div>
            )}
            {(displayed.shortages.length === 0 || canOpenStandards || issues.length > 0) && (
              <div className="operating-toolbar">
                {displayed.shortages.length === 0 && (
                  <button type="button" data-testid="cash-receivables" onClick={openReceivables}>
                    미수금 보기
                  </button>
                )}
                {canOpenStandards && (
                  <button
                    type="button"
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
                    기초 잔액·최소 운영자금 설정
                  </button>
                )}
                {issues.length > 0 && (
                  <FinancialWarnings
                    warnings={issues}
                    description="표에서 해당 월의 금액을 눌러 계산 근거를 확인할 수 있습니다."
                  />
                )}
              </div>
            )}
            <BalanceTrendChart
              points={displayed.chartPoints}
              minimum={displayed.minimumCash}
            />
            <div className="cash-statement-heading">
              <h2>월별 자금수지</h2>
              <span>부가세 포함</span>
            </div>
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
