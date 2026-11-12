import { useCallback, useEffect, useRef, useState } from "react";
import { ApiError } from "../../shared/api";
import { DataTable, type Column } from "../../shared/DataTable";
import { Dialog } from "../../shared/Dialog";
import { ErrorBanner, StatusBadge } from "../../shared/Feedback";
import { validMonth } from "../../shared/format";
import { PayrollUploadHistoryTable } from "../payrollops/PayrollUploadHistoryTable";
import { projectAmount } from "../projects/projectFormat";
import {
  getMonthClose,
  getPeriodContext,
  getSharedCodes,
  type PeriodContext,
} from "../shared/sharedApi";
import {
  getUploadIssues,
  type UploadIssuesPage,
} from "../uploadsops/uploadIssuesApi";
import { UploadIssuesPanel } from "../uploadsops/UploadIssuesPanel";
import { PayrollReceipt, PayrollUploadDialog } from "./PayrollUploadDialog";
import * as api from "./payrollApi";
import "./payroll.css";

type Unit = "원" | "천원" | "백만원";
const amountFields = [
  ["payAmount", "입력급여"],
  ["companyInsuranceAmount", "회사보험"],
  ["selfInsuranceAmount", "본인보험"],
  ["incomeTaxAmount", "근로소득세"],
  ["vendorSupplyAmount", "외주공급가"],
  ["allowanceAmount", "계산수당"],
  ["retirementReserveAmount", "퇴직적립"],
  ["withholdingTaxAmount", "계산원천세"],
  ["realPayAmount", "실지급액"],
] as const;
const exportColumns = [
  "month",
  "developerId",
  "managementNo",
  "developerName",
  "employeeType",
  "directClass",
  ...amountFields.map(([field]) => field),
  "uploadId",
];

export function PayrollDialog({
  onClose,
  initialUpload = false,
  onDirtyChange,
}: {
  onClose(): void;
  initialUpload?: boolean;
  onDirtyChange?(dirty: boolean): void;
}) {
  const [period, setPeriod] = useState<PeriodContext>();
  const [month, setMonth] = useState("");
  const [contextError, setContextError] = useState<unknown>();
  const [closed, setClosed] = useState<boolean>();
  const [closeError, setCloseError] = useState<unknown>();
  const [contextAttempt, setContextAttempt] = useState(0);
  const [tab, setTab] = useState<"month" | "history">("month");
  const [upload, setUpload] = useState(initialUpload);
  const [query, setQuery] = useState({
    q: "",
    page: 1,
    size: 50 as 20 | 50 | 100,
  });
  const [draft, setDraft] = useState("");
  const [unit, setUnit] = useState<Unit>("천원");
  const [data, setData] = useState<api.PayrollPage>();
  const [history, setHistory] = useState<api.PayrollUploadsPage>();
  const [historyPage, setHistoryPage] = useState({
    page: 1,
    size: 50 as 20 | 50 | 100,
  });
  const [error, setError] = useState<unknown>();
  const [loading, setLoading] = useState(false);
  const [refreshKey, setRefreshKey] = useState(0);
  const [actionError, setActionError] = useState<unknown>();
  const [pending, setPending] = useState(false);
  const [savedResult, setSavedResult] = useState<{
    result: api.PayrollResult;
    issues: UploadIssuesPage;
  }>();
  const [codes, setCodes] = useState<
    Record<string, { code: string; name: string }[]>
  >({});
  const sequence = useRef(0);
  const actionBusy = useRef(false);
  const mobile = window.matchMedia?.("(max-width: 767px)").matches ?? false;
  useEffect(() => {
    let current = true;
    setContextError(undefined);
    void getPeriodContext()
      .then((value) => {
        if (current) {
          setPeriod(value);
          setMonth((m) => m || value.currentMonth);
        }
      })
      .catch((e) => {
        if (current) setContextError(e);
      });
    void Promise.all(
      ["EMP_TYPE", "DRCT_CLS"].map(
        async (group) =>
          [group, (await getSharedCodes(group, true)).items] as const,
      ),
    )
      .then((entries) => {
        if (current) setCodes(Object.fromEntries(entries));
      })
      .catch((e) => {
        if (current) setActionError(e);
      });
    return () => {
      current = false;
    };
  }, [contextAttempt]);
  useEffect(() => {
    let current = true;
    setClosed(undefined);
    setCloseError(undefined);
    if (validMonth(month))
      void getMonthClose(month, month)
        .then((result) => {
          if (current)
            setClosed(
              result.items.some(
                (r) => r.month === month && r.status === "마감",
              ),
            );
        })
        .catch((e) => {
          if (current) setCloseError(e);
        });
    return () => {
      current = false;
    };
  }, [month, contextAttempt, refreshKey]);
  const load = useCallback(async () => {
    if (!validMonth(month)) return;
    const version = ++sequence.current;
    setLoading(true);
    setError(undefined);
    try {
      if (tab === "month") {
        const value = await api.getPayroll({ month, ...query });
        if (version === sequence.current) setData(value);
      } else {
        const value = await api.getPayrollUploads(
          month,
          historyPage.page,
          historyPage.size,
        );
        if (version === sequence.current) setHistory(value);
      }
    } catch (e) {
      if (version === sequence.current) setError(e);
      throw e;
    } finally {
      if (version === sequence.current) setLoading(false);
    }
  }, [month, query, tab, historyPage]);
  useEffect(() => {
    void load().catch(() => {});
    return () => {
      sequence.current++;
    };
  }, [load, refreshKey]);
  const readonlyMessage =
    !period || closed === undefined
      ? "서버 기간·마감 상태 확인 전에는 검사·반영할 수 없습니다."
      : month.slice(0, 4) !== period.currentMonth.slice(0, 4)
        ? "지난 연도 또는 다음 연도 급여는 조회만 할 수 있습니다."
        : month < period.cashBaseMonth
          ? "이관된 월은 조회만 할 수 있습니다."
          : month > period.currentMonth
            ? "미래월은 조회만 할 수 있습니다."
            : closed
              ? "마감된 월은 조회만 할 수 있습니다."
              : "";
  const readOnly = Boolean(readonlyMessage) || !validMonth(month);
  function label(group: string, value: string) {
    return codes[group]?.find((r) => r.code === value)?.name ?? value;
  }
  async function action(work: () => Promise<void>) {
    if (actionBusy.current) return;
    actionBusy.current = true;
    setPending(true);
    setActionError(undefined);
    try {
      await work();
    } catch (e) {
      setActionError(e);
    } finally {
      actionBusy.current = false;
      setPending(false);
    }
  }
  const columns: Column<api.PayrollRow>[] = [
    {
      id: "developerName",
      label: "이름(관리번호)",
      render: (r) => (
        <span data-testid={`payroll-row-${r.developerId.toLowerCase()}`}>
          {r.developerName}
          <small className="payroll-sub">{r.managementNo}</small>
        </span>
      ),
    },
    {
      id: "employeeType",
      label: "저장직원구분",
      align: "center",
      render: (r) => (
        <StatusBadge tone="neutral">
          {label("EMP_TYPE", r.employeeType)}
        </StatusBadge>
      ),
    },
    {
      id: "directClass",
      label: "저장직간접",
      align: "center",
      render: (r) => label("DRCT_CLS", r.directClass),
    },
    ...amountFields.map(
      ([id, title]): Column<api.PayrollRow> => ({
        id,
        label: title,
        align: "right",
        render: (r) => projectAmount(r[id], unit),
      }),
    ),
  ];

  function banner(value: unknown, retry?: () => void) {
    return (
      value != null && (
        <ErrorBanner
          message={
            value instanceof Error
              ? value.message
              : "급여를 조회하지 못했습니다."
          }
          requestId={
            value instanceof ApiError ? value.body.requestId : undefined
          }
          onRetry={retry}
        />
      )
    );
  }
  return (
    <>
      <Dialog
        open
        title="월별 급여"
        wide
        onConfirm={onClose}
        onCancel={onClose}
        confirmLabel="닫기"
        cancelLabel="취소"
      >
        <section
          data-testid="payroll-dialog"
          data-screen-id="SCR-UI-02"
          className="payroll-dialog"
        >
          <div className="payroll-toolbar">
            <label className="sicost-field">
              연월
              <input
                type="month"
                data-testid="payroll-month"
                value={month}
                onChange={(e) => {
                  setMonth(e.target.value);
                  setQuery((q) => ({ ...q, page: 1 }));
                  setHistoryPage((p) => ({ ...p, page: 1 }));
                  setData(undefined);
                  setHistory(undefined);
                  setActionError(undefined);
                }}
              />
            </label>
            <label className="sicost-field">
              단위
              <select
                data-testid="payroll-unit"
                value={unit}
                onChange={(e) => setUnit(e.target.value as Unit)}
              >
                <option>원</option>
                <option>천원</option>
                <option>백만원</option>
              </select>
            </label>
            {!mobile && (
              <button
                type="button"
                data-testid="payroll-template"
                disabled={pending}
                onClick={() =>
                  void action(async () =>
                    api.saveDownload(await api.getPayrollTemplate()),
                  )
                }
              >
                급여 양식 내려받기
              </button>
            )}
          </div>
          {banner(contextError, () => setContextAttempt((n) => n + 1))}
          {banner(closeError, () => setContextAttempt((n) => n + 1))}
          {readOnly && (
            <p
              className="sicost-banner sicost-tone-lock"
              data-testid="payroll-read-only"
            >
              {readonlyMessage}
            </p>
          )}
          {!validMonth(month) && (
            <p role="status">
              서버 기준 연월을 확인하거나 유효한 연월을 선택해 주세요.
            </p>
          )}
          <div className="payroll-tabs" role="tablist" aria-label="급여 내역">
            <button
              type="button"
              role="tab"
              data-testid="payroll-month-tab"
              aria-selected={tab === "month"}
              onClick={() => setTab("month")}
            >
              월별 내역
            </button>
            <button
              type="button"
              role="tab"
              data-testid="payroll-history-tab"
              aria-selected={tab === "history"}
              onClick={() => setTab("history")}
            >
              업로드 기록
            </button>
            {!mobile && (
              <button
                type="button"
                data-testid="payroll-upload-open"
                disabled={!validMonth(month)}
                onClick={() => setUpload(true)}
              >
                파일 검사·반영
              </button>
            )}
          </div>
          {mobile && (
            <p className="sicost-banner sicost-tone-info">
              급여 업로드·다운로드는 PC에서 이용해 주세요.
            </p>
          )}
          {banner(actionError)}
          {banner(error, () => void load().catch(() => {}))}
          {tab === "month" ? (
            <section role="tabpanel" data-testid="payroll-month-panel">
              <div className="payroll-toolbar">
                <label className="sicost-field">
                  이름/관리번호
                  <input
                    data-testid="payroll-search"
                    maxLength={100}
                    value={draft}
                    onChange={(e) => setDraft(e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === "Enter") {
                        e.preventDefault();
                        setQuery((q) => ({ ...q, q: draft.trim(), page: 1 }));
                      }
                    }}
                  />
                </label>
                <button
                  type="button"
                  data-testid="payroll-search-button"
                  disabled={!validMonth(month)}
                  onClick={() =>
                    setQuery((q) => ({ ...q, q: draft.trim(), page: 1 }))
                  }
                >
                  조회
                </button>
                <button
                  type="button"
                  data-testid="payroll-reset"
                  onClick={() => {
                    setDraft("");
                    setQuery((q) => ({ ...q, q: "", page: 1 }));
                  }}
                >
                  초기화
                </button>
                {!mobile && (
                  <button
                    type="button"
                    data-testid="payroll-export"
                    disabled={pending || loading || !data || Boolean(error)}
                    onClick={() =>
                      void action(async () =>
                        api.saveDownload(
                          await api.exportPayroll(
                            { month, ...query },
                            exportColumns,
                            unit,
                          ),
                        ),
                      )
                    }
                  >
                    다운로드
                  </button>
                )}
              </div>
              <p className="payroll-sub">
                이름 ASC / DEV_ID ASC · 저장 snapshot · 급여 직접수정 없음
              </p>
              {!error && validMonth(month) && (
                <DataTable
                  rows={data?.items ?? []}
                  columns={columns}
                  rowKey={(r) => r.developerId}
                  loading={loading}
                  empty="선택월 급여 없음"
                  page={query.page}
                  size={query.size}
                  total={data?.total ?? 0}
                  onPageChange={(page) => setQuery((q) => ({ ...q, page }))}
                  onSizeChange={(size) =>
                    setQuery((q) => ({ ...q, page: 1, size }))
                  }
                />
              )}
            </section>
          ) : (
            <section role="tabpanel" data-testid="payroll-history-panel">
              <div className="payroll-toolbar">
                <p>B1 / UI-02 / 선택월 {month}</p>
                <button
                  type="button"
                  data-testid="payroll-history-refresh"
                  onClick={() => void load().catch(() => {})}
                >
                  재조회
                </button>
              </div>
              {!error && (
                <PayrollUploadHistoryTable
                  rows={history?.items ?? []}
                  loading={loading}
                  pending={pending}
                  onResult={(uploadId) =>
                    void action(async () => {
                      const [result, issues] = await Promise.all([
                        api.getPayrollResult(uploadId),
                        getUploadIssues(uploadId, "", 1, 50),
                      ]);
                      setSavedResult({ result, issues });
                    })
                  }
                  page={historyPage.page}
                  size={historyPage.size}
                  total={history?.total ?? 0}
                  onPageChange={(page) =>
                    setHistoryPage((p) => ({ ...p, page }))
                  }
                  onSizeChange={(size) => setHistoryPage({ page: 1, size })}
                />
              )}
            </section>
          )}
        </section>
      </Dialog>
      {upload && validMonth(month) && !mobile && (
        <PayrollUploadDialog
          key={month}
          month={month}
          readOnly={readOnly}
          readOnlyMessage={readonlyMessage}
          onClose={() => setUpload(false)}
          onDirtyChange={onDirtyChange}
          onApplied={async () => {
            setRefreshKey((n) => n + 1);
            await load();
          }}
        />
      )}
      {savedResult && (
        <Dialog
          open
          wide
          title="급여 업로드 결과"
          onConfirm={() => setSavedResult(undefined)}
          onCancel={() => setSavedResult(undefined)}
          confirmLabel="닫기"
        >
          <section data-testid="payroll-saved-result">
            <PayrollReceipt result={savedResult.result} />
            <p>
              전체 {savedResult.result.summary.totalRows}건 / 성공{" "}
              {savedResult.result.summary.successRows}건 / 경고{" "}
              {savedResult.result.summary.warningRows}건 / 오류{" "}
              {savedResult.result.summary.errorRows}건
            </p>
            {savedResult.result.status === "CHECKED" && (
              <p className="sicost-banner sicost-tone-info">
                검사 결과 조회만 가능합니다. 반영하려면 파일을 다시 선택하고
                검사해 주세요.
              </p>
            )}
            <UploadIssuesPanel
              uploadId={savedResult.result.uploadId}
              initialPage={savedResult.issues}
              screenId="UI-02"
            />
          </section>
        </Dialog>
      )}
    </>
  );
}

export function PayrollActions({
  onDirtyChange,
}: {
  onDirtyChange?(dirty: boolean): void;
}) {
  const [open, setOpen] = useState(false);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const busy = useRef(false);
  const mobile = window.matchMedia?.("(max-width: 767px)").matches ?? false;
  async function template() {
    if (busy.current) return;
    busy.current = true;
    setPending(true);
    setError("");
    try {
      api.saveDownload(await api.getPayrollTemplate());
    } catch (e) {
      setError(
        e instanceof ApiError
          ? `${e.message} (요청 ID: ${e.body.requestId})`
          : e instanceof Error
            ? e.message
            : "다운로드하지 못했습니다.",
      );
    } finally {
      busy.current = false;
      setPending(false);
    }
  }
  return (
    <span className="payroll-actions" data-testid="payroll-actions">
      {!mobile && (
        <button
          type="button"
          data-testid="payroll-template-open"
          disabled={pending}
          onClick={() => void template()}
        >
          급여 양식 내려받기
        </button>
      )}
      <button
        type="button"
        data-testid="payroll-open"
        onClick={() => setOpen(true)}
      >
        월별 급여
      </button>
      {error && <span role="alert">{error}</span>}
      {open && (
        <PayrollDialog
          onClose={() => setOpen(false)}
          onDirtyChange={onDirtyChange}
        />
      )}
    </span>
  );
}
