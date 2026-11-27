import { FieldHelp } from "../../shared/FieldHelp";
import { useEffect, useState } from "react";
import { createPortal } from "react-dom";
import { Link } from "react-router-dom";
import { ApiError } from "../../shared/api";
import { FeedbackNotice } from "../../shared/Feedback";
import { SectionCard } from "../../shared/Surface";
import { Dialog } from "../../shared/Dialog";
import { useCommitRefresh } from "../../shared/useCommitRefresh";
import { formatAmount, formatDecimal, formatRate } from "../../shared/format";
import { SalesCommandError } from "./SalesQuotePanel";
import * as api from "./salesCommandsApi";
import { BusinessName } from "../../shared/BusinessName";
import { NumericInput } from "../../shared/NumericInput";
import { useCodeNames } from "../../shared/codeNames";

export const stageNames: Record<api.Stage, string> = {
  FIND: "발굴",
  PROP: "제안",
  NEGO: "협상",
  PRE: "계약 직전",
};
type Action = "OK" | "REJ" | "lose" | "stop" | "resume" | "win";
const titles: Record<Action, string> = {
  OK: "견적 승인",
  REJ: "견적 반려",
  lose: "이 기회를 실주 처리할까요?",
  stop: "이 기회를 삭제할까요?",
  resume: "다시 사용",
  win: "수주 처리",
};
function initialWin(o: api.Opportunity): api.WinInput {
  const [year, month] = o.startMonth.split("-").map(Number);
  const end = new Date(0);
  end.setUTCFullYear(year, month - 1 + (o.months ?? 6), 0);
  return {
    contractAmount: o.amount,
    contractType: o.contractType ?? "TK",
    startDate: `${o.startMonth}-01`,
    endDate: end.toISOString().slice(0, 10),
    projectStatus: "ING",
  };
}
function positiveWon(value: string): string | undefined {
  const trimmed = value.trim();
  if (!/^(?:[1-9][0-9]*|[1-9][0-9]{0,2}(?:,[0-9]{3})+)$/.test(trimmed)) return;
  const digits = trimmed.replaceAll(",", "");
  return digits.length <= 15 ? digits : undefined;
}
function quotePlans(quote: api.Quote) {
  const jobs = new Map<string, bigint>();
  for (const row of quote.rows) {
    if (
      !row.jobCode ||
      row.quantityMm == null ||
      !/^\d+(?:\.\d{1,2})?$/.test(row.quantityMm)
    )
      return;
    const [whole, fraction = ""] = row.quantityMm.split(".");
    jobs.set(
      row.jobCode,
      (jobs.get(row.jobCode) ?? 0n) + BigInt(whole + fraction.padEnd(2, "0")),
    );
  }
  return [...jobs].map(([job, value]) => ({
    job,
    mm: `${value / 100n}.${String(value % 100n).padStart(2, "0")}`,
  }));
}
export function OpportunityRejectionNotice({
  opportunity,
}: {
  opportunity: api.Opportunity;
}) {
  if (opportunity.approvalStatus !== "REJ") return null;
  const rejection = opportunity.approvalHistory
    .filter((entry) => entry.action === "REJ")
    .sort((a, b) => b.histSn - a.histSn)[0];

  return (
    <FeedbackNotice
      tone="warn"
      title={
        rejection
          ? `견적 반려 (${rejection.registeredAt.slice(5, 10)})`
          : "견적 반려"
      }
      testId="quote-rejection-banner"
    >
      {rejection?.reason?.trim() ? `사유: ${rejection.reason}` : undefined}
    </FeedbackNotice>
  );
}

export function SalesOpportunityActions({
  opportunity: o,
  refresh,
  approvalTarget,
  showRejection = true,
}: {
  approvalTarget?: HTMLElement | null;
  showRejection?: boolean;
  opportunity: api.Opportunity;
  refresh(): Promise<unknown>;
}) {
  const name = useCodeNames("JOB");
  const [action, setAction] = useState<Action>(),
    [error, setError] = useState<unknown>(),
    [fields, setFields] = useState<Record<string, string>>({});
  const [reason, setReason] = useState(""),
    [reasonCode, setReasonCode] = useState(""),
    [memo, setMemo] = useState("");
  const [lossReasons, setLossReasons] = useState<
      { code: string; name: string }[]
    >([]),
    [reasonError, setReasonError] = useState<unknown>();
  const [win, setWin] = useState(() => initialWin(o)),
    [stage, setStage] = useState(o.stage),
    [probability, setProbability] = useState(o.probability);
  const { pending, message, outcome, commit } = useCommitRefresh(
    "저장은 됐지만 다시 불러오지 못했습니다",
  );
  const [approvalFeedback, setApprovalFeedback] = useState(false);
  const [projectId, setProjectId] = useState<string>();
  useEffect(() => {
    setStage(o.stage);
    setProbability(o.probability);
  }, [o.id, o.stage, o.probability]);
  useEffect(() => {
    if (action !== "lose") return;
    let live = true;
    setReasonError(undefined);
    void api
      .getLossReasons()
      .then((v) => {
        if (live) setLossReasons(v.items);
      })
      .catch((e) => {
        if (live) setReasonError(e);
      });
    return () => {
      live = false;
    };
  }, [action]);
  const plans = quotePlans(o.quote);
  const contractAmount = positiveWon(win.contractAmount);
  const difference =
    contractAmount && o.quote.revenue != null && /^\d+$/.test(o.quote.revenue)
      ? (BigInt(contractAmount) - BigInt(o.quote.revenue)).toString()
      : null;
  const editable = o.status === "ING";
  const quoteKnown =
    o.quote.storedProfitRate != null &&
    !["UNKNOWN", "INCOMPLETE"].includes(o.quote.judgement);
  const canAdvance = quoteKnown && !["WAIT", "REJ"].includes(o.approvalStatus);
  function open(next: Action) {
    setAction(next);
    setError(undefined);
    setFields({});
    setReason("");
    setMemo("");
    setReasonCode("");
    setWin(initialWin(o));
  }
  async function execute(save: () => Promise<unknown>, close = true) {
    setApprovalFeedback(action === "OK" || action === "REJ");
    setError(undefined);
    setFields({});
    try {
      await commit(save, refresh);
      if (close) setAction(undefined);
    } catch (e) {
      setError(e);
      if (e instanceof ApiError) setFields(e.fieldMessages);
    }
  }
  async function submit() {
    if (!action) return;
    const errors: Record<string, string> = {};
    if (action === "REJ" && [...reason].length > 300)
      errors.reason = "사유는 300자 이하로 입력해 주세요.";
    if (action === "lose") {
      if (!reasonCode) errors.reasonCode = "실주 사유를 선택해 주세요.";
      if ([...memo].length > 500)
        errors.memo = "메모는 500자 이하로 입력해 주세요.";
    }
    if (action === "win") {
      if (!contractAmount)
        errors.contractAmount = "양수 원정수 15자리 이하로 입력해 주세요.";
      if (!win.startDate) errors.startDate = "시작일을 입력해 주세요.";
      if (!win.endDate || win.endDate <= win.startDate)
        errors.endDate = "종료일은 시작일보다 뒤여야 합니다.";
    }
    if (Object.keys(errors).length) {
      setFields(errors);
      return;
    }
    await execute(async () => {
      if (action === "OK" || action === "REJ")
        return api.createSalesOpportunityApproval(o, action, reason || null);
      if (action === "lose")
        return api.createSalesOpportunityLoss(o, reasonCode, memo || null);
      if (action === "stop") return api.saveSalesOpportunityStop(o);
      if (action === "resume") return api.saveSalesOpportunityResume(o);
      const result = await api.createSalesOpportunityWin(o, {
        ...win,
        contractAmount: contractAmount!,
      });
      setProjectId(result.projectId);
      return result;
    });
  }
  function stageChange(next: api.Stage) {
    setStage(next);
    void execute(
      () => api.saveSalesOpportunityStage(o, { stage: next }),
      false,
    );
  }
  const feedback = (
    <>
      {message && outcome?.kind !== "save-failed" && (
        <p role="status">{message}</p>
      )}
      {message === "저장은 됐지만 다시 불러오지 못했습니다" && (
        <button
          data-testid="command-refresh-retry-button"
          onClick={() => {
            void refresh().catch(setError);
          }}
        >
          다시 조회
        </button>
      )}
      {error != null && !action && <SalesCommandError error={error} />}
    </>
  );
  const approval = (
    <>
      {approvalFeedback && feedback}
      {editable && o.approvalStatus === "WAIT" && (
        <SectionCard
          title="견적 승인"
          className="sales-opportunity-approval-card"
        >
          <p>
            승인 대상 이익률{" "}
            <strong>{formatRate(o.quote.storedProfitRate)}</strong>
            {" · "}승인 기준 {formatRate(o.quote.approvalThreshold)}
          </p>
          {["UNKNOWN", "INCOMPLETE"].includes(o.quote.judgement) && (
            <p>견적 원가가 확인되면 승인하거나 반려할 수 있습니다.</p>
          )}
          <div className="sales-opportunity-management-buttons">
            <button
              type="button"
              className="sicost-primary"
              data-testid="approve-open-button"
              disabled={pending || !quoteKnown}
              onClick={() => open("OK")}
            >
              승인
            </button>
            <button
              type="button"
              data-testid="reject-open-button"
              disabled={pending || !quoteKnown}
              onClick={() => open("REJ")}
            >
              반려
            </button>
          </div>
        </SectionCard>
      )}
    </>
  );
  return (
    <section
      className="sales-command-actions"
      data-testid="sales-opportunity-actions"
    >
      {showRejection && <OpportunityRejectionNotice opportunity={o} />}
      {!approvalFeedback && feedback}
      {approvalTarget === undefined
        ? approval
        : approvalTarget && createPortal(approval, approvalTarget)}
      {projectId && (
        <Link
          data-testid="win-project-link"
          to={`/UI-01?${new URLSearchParams({ projectId })}`}
        >
          프로젝트 {projectId} 보기
        </Link>
      )}
      {editable && (
        <>
          <SectionCard
            title="단계·수주 확률"
            className="sales-opportunity-management-card"
          >
            <label>
              단계 선택{" "}
              <select
                data-testid="opportunity-stage-select"
                value={stage}
                disabled={pending}
                onChange={(e) => stageChange(e.target.value as api.Stage)}
              >
                {Object.entries(stageNames).map(([value, label]) => (
                  <option
                    key={value}
                    value={value}
                    disabled={
                      !canAdvance &&
                      ["NEGO", "PRE"].includes(value) &&
                      value !== o.stage
                    }
                  >
                    {label}
                  </option>
                ))}
              </select>
            </label>
            <div className="sales-opportunity-probability-editor">
              <label>
                <span>
                  수주 확률 <FieldHelp label="수주 확률" />
                </span>
                <NumericInput
                  data-testid="opportunity-probability-input"
                  value={probability}
                  onChange={(e) => setProbability(e.target.value)}
                  inputMode="decimal"
                  disabled={pending}
                />
              </label>
              <span className="sales-opportunity-probability-mode">
                {o.manualProbability ? "수동" : "기본"}
              </span>
            </div>
            <div className="sales-opportunity-management-buttons">
              <button
                type="button"
                className="sicost-primary"
                data-testid="manual-probability-button"
                disabled={
                  pending ||
                  !/^(100(\.0{1,2})?|([0-9]|[1-9][0-9])(\.[0-9]{1,2})?)$/.test(
                    probability,
                  )
                }
                onClick={() =>
                  void execute(
                    () =>
                      api.saveSalesOpportunityStage(o, {
                        stage: o.stage,
                        probability,
                        manualProbability: true,
                      }),
                    false,
                  )
                }
              >
                확률 직접 변경
              </button>
              <button
                type="button"
                data-testid="default-probability-button"
                disabled={pending}
                onClick={() =>
                  void execute(
                    () =>
                      api.saveSalesOpportunityStage(o, {
                        stage: o.stage,
                        manualProbability: false,
                      }),
                    false,
                  )
                }
              >
                기본 확률 사용
              </button>
            </div>
          </SectionCard>
          {!canAdvance && (
            <p className="sales-command-warning">
              견적 원가 미확인 또는 승인 대기/반려: 협상·계약 직전 신규 진입과
              수주를 할 수 없습니다. 현재 단계는 유지됩니다.
            </p>
          )}
          <SectionCard
            title="기회 처리"
            className="sales-opportunity-management-card"
          >
            <div className="sales-opportunity-management-buttons">
              <button
                type="button"
                data-testid="win-open-button"
                disabled={pending || !canAdvance}
                onClick={() => open("win")}
              >
                수주 처리
              </button>
              <button
                type="button"
                data-testid="lose-open-button"
                disabled={pending}
                onClick={() => open("lose")}
              >
                실주 처리
              </button>
              <button
                type="button"
                className="sales-opportunity-management-danger"
                data-testid="stop-open-button"
                disabled={pending}
                onClick={() => open("stop")}
              >
                삭제
              </button>
            </div>
          </SectionCard>
        </>
      )}
      {o.status === "STOP" && (
        <SectionCard
          title="기회 처리"
          className="sales-opportunity-management-card"
        >
          <div className="sales-opportunity-management-buttons">
            <button
              type="button"
              data-testid="resume-open-button"
              disabled={pending}
              onClick={() => open("resume")}
            >
              다시 사용
            </button>
          </div>
        </SectionCard>
      )}
      {["WIN", "LOSE"].includes(o.status) && (
        <p>종료 기회는 읽기 전용입니다. 다시 열 수 없습니다.</p>
      )}
      {action && (
        <Dialog
          open
          title={titles[action]}
          onCancel={() => setAction(undefined)}
          onConfirm={submit}
          pending={pending}
          feedback={
            error != null && (
              <SalesCommandError
                error={error}
                quoteChangedTitle={
                  action === "win"
                    ? "수주 처리 전에 견적을 다시 저장해 주세요"
                    : undefined
                }
              />
            )
          }
          confirmLabel={
            action === "win"
              ? "수주 확정"
              : action === "stop"
                ? "삭제"
                : action === "lose"
                  ? "실주 처리"
                  : "확인"
          }
          danger={action === "stop" || action === "lose"}
          confirmDisabled={
            action === "lose" && (!!reasonError || lossReasons.length === 0)
          }
        >
          <div data-testid={`sales-${action.toLowerCase()}-dialog`}>
            <h3>{o.name}</h3>
            {(action === "OK" || action === "REJ") && (
              <>
                <p>
                  현재 저장견적률 {o.quote.storedProfitRate ?? "–"}% · 승인기준{" "}
                  {o.quote.approvalThreshold ?? "–"}%
                </p>
                <p>현재 견적과 승인 이력을 함께 저장합니다.</p>
              </>
            )}
            {action === "REJ" && (
              <label>
                반려 사유 (선택, 300자)
                <textarea
                  aria-label="반려 사유 (선택, 300자)"
                  data-testid="rejection-reason-input"
                  value={reason}
                  onChange={(e) => setReason(e.target.value)}
                  aria-invalid={!!fields.reason}
                />
                {fields.reason && <span role="alert">{fields.reason}</span>}
              </label>
            )}
            {action === "lose" && (
              <>
                {reasonError != null && (
                  <SalesCommandError error={reasonError} />
                )}
                <label>
                  실주 사유
                  <select
                    aria-label="실주 사유"
                    data-testid="loss-reason-select"
                    value={reasonCode}
                    onChange={(e) => setReasonCode(e.target.value)}
                    aria-invalid={!!fields.reasonCode}
                  >
                    <option value="">선택</option>
                    {lossReasons.map((r) => (
                      <option key={r.code} value={r.code}>
                        {r.name}
                      </option>
                    ))}
                  </select>
                  {fields.reasonCode && (
                    <span role="alert">{fields.reasonCode}</span>
                  )}
                </label>
                <label>
                  메모
                  <FieldHelp label="메모" />
                  <textarea
                    aria-label="메모"
                    data-testid="loss-memo-input"
                    value={memo}
                    onChange={(e) => setMemo(e.target.value)}
                    aria-invalid={!!fields.memo}
                  />
                  {fields.memo && <span role="alert">{fields.memo}</span>}
                </label>
                <p>
                  가배정을 모두 해제하고 실주 결과와 서버 날짜를 저장합니다.
                </p>
              </>
            )}
            {action === "stop" && (
              <p>
                물리 삭제하지 않습니다. 사용 중지하고 가배정을 해제합니다. 다시
                사용해도 옛 가배정은 복원하지 않습니다.
              </p>
            )}
            {action === "resume" && (
              <p>
                견적을 다시 평가하고 진행 기회로 복원합니다. 해제한 가배정은
                복원하지 않습니다.
              </p>
            )}
            {action === "win" && (
              <>
                <p>
                  고객 <BusinessName kind="client" id={o.customerId} /> ·
                  영업파트 <BusinessName kind="part" id={o.partId} /> · 근무지{" "}
                  {o.workplace ?? "–"} (읽기 전용)
                </p>
                <p>
                  기회 예상액 {formatAmount(o.amount, "원")} 원 · 견적매출{" "}
                  {formatAmount(o.quote.revenue, "원")} 원
                </p>
                <label>
                  계약금액
                  <FieldHelp label="계약금액" />
                  <input
                    data-testid="win-amount-input"
                    value={win.contractAmount}
                    onChange={(e) =>
                      setWin({ ...win, contractAmount: e.target.value })
                    }
                    aria-invalid={!!fields.contractAmount}
                  />
                  {fields.contractAmount && (
                    <span role="alert">{fields.contractAmount}</span>
                  )}
                </label>
                <label>
                  계약유형
                  <select
                    data-testid="win-type-select"
                    value={win.contractType}
                    onChange={(e) =>
                      setWin({
                        ...win,
                        contractType: e.target
                          .value as api.WinInput["contractType"],
                      })
                    }
                  >
                    <option value="TK">턴키</option>
                    <option value="MO">월단위</option>
                    <option value="DP">파견</option>
                  </select>
                </label>
                <label>
                  시작일
                  <input
                    data-testid="win-start-input"
                    type="date"
                    value={win.startDate}
                    onChange={(e) =>
                      setWin({ ...win, startDate: e.target.value })
                    }
                    aria-invalid={!!fields.startDate}
                  />
                  {fields.startDate && (
                    <span role="alert">{fields.startDate}</span>
                  )}
                </label>
                <label>
                  종료일
                  <input
                    data-testid="win-end-input"
                    type="date"
                    value={win.endDate}
                    onChange={(e) =>
                      setWin({ ...win, endDate: e.target.value })
                    }
                    aria-invalid={!!fields.endDate}
                  />
                  {fields.endDate && <span role="alert">{fields.endDate}</span>}
                </label>
                <label>
                  프로젝트 상태
                  <select
                    data-testid="win-status-select"
                    value={win.projectStatus}
                    onChange={(e) =>
                      setWin({
                        ...win,
                        projectStatus: e.target
                          .value as api.WinInput["projectStatus"],
                      })
                    }
                  >
                    <option value="ING">진행</option>
                    <option value="PRE">수주 예정</option>
                  </select>
                </label>
                <h4>계획 근거 / 가배정 후보</h4>
                {o.needs.map((n, i) => (
                  <p
                    key={n.needSn ?? i}
                    data-testid={`win-need-${n.needSn ?? i}`}
                  >
                    {name("JOB", n.jobCode)}: {n.count}명 × {n.monthlyMm} MM ×{" "}
                    {n.months}개월
                  </p>
                ))}
                <p>
                  후보{" "}
                  {
                    o.tentativeAssignments.filter((t) => t.status === "ACT")
                      .length
                  }
                  명 → 배정 후보 (실제 배정은 UI-12에서 확정)
                </p>
                <div data-testid="win-plan-totals">
                  <h4>직무별 계획 합계</h4>
                  {plans?.map((p) => (
                    <p key={p.job}>
                      {name("JOB", p.job)}: {formatDecimal(p.mm)} MM
                    </p>
                  )) ?? "–"}
                </div>
                <p data-testid="win-amount-difference">
                  최종 계약금액 − 견적매출: {formatAmount(difference, "원")} 원
                </p>
                <p>
                  서버 견적의 수량을 합산합니다. 계약기간으로 계획을 임의
                  안분하지 않습니다.
                </p>
              </>
            )}
          </div>
        </Dialog>
      )}
    </section>
  );
}
