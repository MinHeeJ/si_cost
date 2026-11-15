import { formatAmount, formatRate, type AmountUnit } from "../../shared/format";
import { useEffect, useRef, useState } from "react";
import { ApiError } from "../../shared/api";
import { Dialog } from "../../shared/Dialog";
import { useCommitRefresh } from "../../shared/useCommitRefresh";
import { getSharedCodes, type SharedCodeLookup } from "../shared/sharedApi";
import { AssignmentCreateForm } from "../assignmentsops/AssignmentCreateForm";
import { AssignmentError } from "../assignmentsops/AssignmentDialogs";
import {
  projectOptions,
  type Create,
} from "../assignmentsops/assignmentActionsApi";
import * as api from "./recommendationActionsApi";
import "./recommendationCandidates.css";

export type RecommendationCandidatesProps = {
  rcmdId: string | null;
  type: "AI" | "NEXT";
  target: { type: "OPPORTUNITY" | "PROJECT"; id: string; name: string };
  candidates: api.RecommendationCandidate[];
  selectedNeedSn: string | null;
  amountUnit?: AmountUnit;
  selectedNeedLabel?: string;
  year: number;
  today: string;
  loading?: boolean;
  error?: unknown;
  actionsEnabled?: boolean;
  onRefresh(): Promise<void>;
  onReset?(): void;
  onDeveloperDetails(id: string): void;
  onTargetDetails(type: "OPPORTUNITY" | "PROJECT", id: string): void;
};
const itemLabels: Record<string, string> = {
  JOB: "직무",
  SKILL: "스킬",
  CUST: "고객",
  GRADE: "등급",
  PERF: "성과",
  COST: "비용",
  AVAIL: "가용일",
  LOC: "근무지",
  GAP: "공백",
  PROB: "수주 확률",
};
function costBasis(row: api.RecommendationCandidate, unit: AmountUnit, fallback: string) {
  const detail = row.scoreDetail;
  if (!detail || typeof detail !== "object" || !("c" in detail) || !("u" in detail)) return fallback;
  const c = detail.c, u = detail.u;
  if (!(c === null || typeof c === "string") || !(u === null || typeof u === "string")) return fallback;
  return <>{"m" in detail && typeof detail.m === "string" ? `원가월 ${detail.m} · ` : ""}인건비 {formatAmount(c, unit)} {unit} · 단가 {formatAmount(u, unit)} {unit}{"f" in detail && detail.f === 1 ? " · 예정" : ""}{c === null || u === null ? " · 원천 미확인" : ""}</>;
}
export function WorkforceRecommendationCandidates(
  props: RecommendationCandidatesProps,
) {
  const {
    rcmdId,
    type,
    target,
    candidates,
    selectedNeedSn,
    loading = false,
    actionsEnabled = true,
  } = props;
  const [excluding, setExcluding] = useState<api.RecommendationCandidate>();
  const [reason, setReason] = useState("");
  const [reasons, setReasons] = useState<SharedCodeLookup[]>([]);
  const [codesLoading, setCodesLoading] = useState(false);
  const [error, setError] = useState<unknown>();
  const [fieldError, setFieldError] = useState("");
  const [notice, setNotice] = useState("");
  const [hidden, setHidden] = useState<number[]>([]);
  const [chosen, setChosen] = useState<
    Record<number, api.RecommendationSelection>
  >({});
  const [confirming, setConfirming] = useState<{
    row: api.RecommendationCandidate;
    needSn: string;
    request: api.RecommendationTentativeRequest;
    warnings: api.RecommendationWarning[];
  }>();
  const [assignment, setAssignment] = useState<Create>();
  const [opening, setOpening] = useState(false);
  const signature = `${rcmdId}:${target.type}:${target.id}`;
  const current = useRef(signature);
  current.current = signature;
  const active = useRef(false);
  const { commit, pending, message } = useCommitRefresh(
    "저장됐지만 다시 불러오지 못했습니다",
  );
  useEffect(() => {
    setExcluding(undefined);
    setConfirming(undefined);
    setAssignment(undefined);
    setHidden([]);
    setChosen({});
    setError(undefined);
    setNotice("");
  }, [signature]);
  useEffect(() => {
    if (!excluding) return;
    let live = true;
    setCodesLoading(true);
    setReasons([]);
    void getSharedCodes("RCMD_EXCL")
      .then((r) => {
        if (live)
          setReasons(
            r.items.filter((x) =>
              ["SCHD", "HOPE", "CUST", "ETC"].includes(x.code),
            ),
          );
      })
      .catch((e) => {
        if (live) setError(e);
      })
      .finally(() => {
        if (live) setCodesLoading(false);
      });
    return () => {
      live = false;
    };
  }, [excluding]);
  const disabled = pending || opening || loading || !actionsEnabled || !rcmdId;
  async function save(
    row: api.RecommendationCandidate,
    request: () => Promise<api.RecommendationActionResult>,
  ) {
    if (active.current || disabled) return;
    active.current = true;
    const started = signature;
    setError(undefined);
    setFieldError("");
    try {
      await commit(
        async () => {
          const result = await request();
          api.notifyRecommendationCommitted(result);
          if (current.current !== started) return;
          setExcluding(undefined);
          setConfirming(undefined);
          setNotice(result.message);
          if (result.selection.actionCode === "EXCL")
            setHidden((old) => [...old, row.rank]);
          else setChosen((old) => ({ ...old, [row.rank]: result.selection }));
        },
        async () => {
          if (current.current === started) await props.onRefresh();
        },
      );
    } catch (e) {
      if (current.current !== started) return;
      if (e instanceof ApiError) {
        const details = e.body.details as
          | { workforceRecommendationWarnings?: api.RecommendationWarning[] }
          | undefined;
        const warnings = details?.workforceRecommendationWarnings;
        if (
          warnings?.length &&
          warnings.every(
            (w) => typeof w.code === "string" && typeof w.message === "string",
          )
        ) {
          const needSn = type === "NEXT" ? row.needSn : selectedNeedSn;
          if (needSn)
            setConfirming({
              row,
              needSn,
              request: tentativeBody(row, needSn, []),
              warnings,
            });
          return;
        }
        if (e.body.field === "reasonCode") setFieldError(e.message);
      }
      setError(e);
    } finally {
      active.current = false;
    }
  }
  function tentativeBody(
    row: api.RecommendationCandidate,
    needSn: string,
    warnings: string[],
  ): api.RecommendationTentativeRequest {
    return {
      needSn,
      expectedRevision: row.revision,
      targetRevision: row.targetRevision,
      candidateRevision: row.candidateRevision,
      confirmedWarnings: warnings,
    };
  }
  async function openAssignment(row: api.RecommendationCandidate) {
    if (disabled) return;
    const started = signature;
    setOpening(true);
    setError(undefined);
    try {
      const response = await projectOptions("");
      if (current.current !== started) return;
      const project = response.items.find((p) => p.id === target.id);
      if (!project)
        throw new Error("배정 가능한 프로젝트를 다시 조회해 주세요.");
      const monthStart = `${row.startMonth}-01`;
      setAssignment({
        year: props.year,
        projectId: target.id,
        developerId: row.developer.developerId,
        jobCode: row.jobCode,
        startDate:
          monthStart > project.startDate ? monthStart : project.startDate,
        endDate: project.endDate,
        contractMm: "1.00",
        billingUnitPrice: "",
        inputType: "NEW",
        contractDeveloperId: null,
        recommendationRef: { rcmdId: rcmdId!, rank: row.rank },
      });
    } catch (e) {
      if (current.current === started) setError(e);
    } finally {
      setOpening(false);
    }
  }
  return (
    <section
      className="recommendation-candidates"
      data-testid="recommendation-candidates"
      data-screen-id={type === "AI" ? "SCR-UI-30" : "SCR-UI-05"}
      aria-busy={loading || pending}
    >
      <p className="sicost-banner sicost-tone-info">
        추천은 참고용입니다. 배정은 담당자가 정합니다
      </p>
      <AssignmentError
        error={props.error ?? error}
        retry={() => {
          void props.onRefresh().catch(setError);
        }}
      />
      {(notice || message) && (
        <p role="status">
          {message === "저장됐지만 다시 불러오지 못했습니다"
            ? message
            : notice || message}
        </p>
      )}
      {message === "저장됐지만 다시 불러오지 못했습니다" && (
        <button
          type="button"
          data-testid="recommendation-refresh"
          onClick={() => {
            void props
              .onRefresh()
              .then(() => setNotice("다시 조회했습니다."))
              .catch(setError);
          }}
        >
          재조회
        </button>
      )}
      {loading ? (
        <p role="status">추천을 불러오는 중입니다.</p>
      ) : candidates.filter(
          (row) =>
            !hidden.includes(row.rank) && row.selection.actionCode !== "EXCL",
        ).length === 0 ? (
        <div className="recommendation-empty">
          <h2>맞는 인력이 없습니다.</h2>
          <p>조건을 확인한 뒤 다시 추천해 주세요.</p>
          {props.onReset && <button type="button" onClick={props.onReset}>조건 초기화</button>}
        </div>
      ) : (
        candidates
          .filter(
            (row) =>
              !hidden.includes(row.rank) && row.selection.actionCode !== "EXCL",
          )
          .map((row) => {
            const selection = chosen[row.rank] ?? row.selection;
            const next = row.nextPosition;
            const selected =
              !!selection.actionCode || !!next?.alreadyTentativelyAssigned;
            const needSn = type === "NEXT" ? row.needSn : selectedNeedSn;
            return (
              <article
                className="recommendation-card"
                data-testid={`recommendation-card-${row.rank}`}
                key={row.rank}
              >
                <header>
                  <span className="recommendation-rank">순위 {row.rank}</span>
                  <button
                    type="button"
                    className="recommendation-link"
                    data-testid={`recommendation-person-${row.rank}`}
                    onClick={() =>
                      props.onDeveloperDetails(row.developer.detailId)
                    }
                  >
                    {row.developer.name}
                  </button>
                  <span>{row.developer.managementNumber}</span>
                  <strong className="recommendation-total">
                    총점 {row.totalScore}
                  </strong>
                </header>
                <p>
                  {row.developer.jobCode} /{" "}
                  {row.developer.gradeCode ?? "미확인"} /{" "}
                  {row.developer.employeeTypeCode}
                </p>
                {type === "NEXT" && next ? (
                  <>
                    <button
                      type="button"
                      className="recommendation-link"
                      data-testid={`recommendation-target-${row.rank}`}
                      onClick={() =>
                        row.opportunityId &&
                        props.onTargetDetails("OPPORTUNITY", row.opportunityId)
                      }
                    >
                      {next.opportunityName}
                    </button>
                    <p>
                      {next.customerName ?? "고객 미확인"} ·{" "}
                      {next.salesStepCode} · 수주 확률 {formatRate(next.probability)}
                    </p>
                    <p>
                      원 시작월 {next.originalStartMonth} · 실제 예정월{" "}
                      {next.actualStartMonth} · 필요 등급 {next.gradeCode} ·
                      남은 자리 {next.remainingCount}명 · 공백 {next.gapMonths}
                      개월 · 대기 {next.waitDays}일
                    </p>
                    <div className="recommendation-chips">
                      {next.reasonChips.map((chip, i) => (
                        <span
                          className="sicost-badge sicost-tone-info"
                          key={`${i}-${chip}`}
                        >
                          {chip}
                        </span>
                      ))}
                    </div>
                  </>
                ) : (
                  <p>
                    적용:{" "}
                    <button
                      type="button"
                      className="recommendation-link"
                      data-testid={`recommendation-target-${row.rank}`}
                      onClick={() =>
                        props.onTargetDetails(target.type, target.id)
                      }
                    >
                      {target.name}
                    </button>{" "}
                    / {row.jobCode} / {row.startMonth} /{" "}
                    {row.requestedGradeCode}
                  </p>
                )}
                {row.otherTentativeAssignments.map((t) => (
                  <p
                    className="sicost-banner sicost-tone-warn"
                    key={t.tentativeAssignmentId}
                  >
                    다른 기회 가배정:{" "}
                    <button
                      type="button"
                      className="recommendation-link"
                      data-testid={`recommendation-other-${row.rank}-${t.tentativeAssignmentId}`}
                      onClick={() =>
                        props.onTargetDetails("OPPORTUNITY", t.opportunityId)
                      }
                    >
                      {t.opportunityName}
                    </button>{" "}
                    / {t.needSn}행
                  </p>
                ))}
                <div className="recommendation-scores">
                  {row.scoreItems.map((item) => (
                    <div
                      className="recommendation-score"
                      title={item.basis}
                      key={item.itemCode}
                    >
                      <span>{itemLabels[item.itemCode] ?? item.itemCode}</span>
                      <progress
                        max={item.weight === "0" ? "1" : item.weight}
                        value={item.score}
                        aria-label={`${itemLabels[item.itemCode] ?? item.itemCode} 득점`}
                      />
                      <span>
                        {item.score}/{item.weight}
                      </span>
                      <small>{item.itemCode === "COST" ? costBasis(row, props.amountUnit ?? "천원", item.basis) : item.basis}</small>
                    </div>
                  ))}
                </div>
                <p>{row.reason}</p>
                <footer>
                  {selected ? (
                    <span
                      className={`sicost-badge ${selection.actionCode === "ASGN" ? "sicost-tone-ok" : "sicost-tone-info"}`}
                    >
                      {selection.actionCode === "ASGN" ? "배정됨" : "가배정됨"}
                      {selection.finalNeedSn
                        ? ` · ${selection.finalNeedSn}행`
                        : ""}
                    </span>
                  ) : (
                    <>
                      <button
                        type="button"
                        data-testid={`recommendation-assign-${row.rank}`}
                        disabled={
                          disabled ||
                          ((target.type === "OPPORTUNITY" || type === "NEXT") &&
                            !needSn)
                        }
                        onClick={() => {
                          setNotice("");
                          if (type === "AI" && target.type === "PROJECT")
                            void openAssignment(row);
                          else if (needSn && rcmdId)
                            void save(row, () =>
                              api.createWorkforceRecommendationTentativeAssignment(
                                rcmdId,
                                row.rank,
                                tentativeBody(row, needSn, []),
                              ),
                            );
                        }}
                      >
                        {type === "AI" && target.type === "PROJECT"
                          ? "배정"
                          : "가배정"}
                      </button>
                      {type === "AI" && (
                        <button
                          type="button"
                          data-testid={`recommendation-exclude-${row.rank}`}
                          disabled={disabled}
                          onClick={() => {
                            setExcluding(row);
                            setReason("");
                            setFieldError("");
                            setError(undefined);
                          }}
                        >
                          제외
                        </button>
                      )}
                    </>
                  )}
                </footer>
              </article>
            );
          })
      )}
      {excluding && (
        <Dialog
          open
          title={`후보 제외: ${excluding.developer.name}`}
          pending={pending}
          confirmLabel="확정"
          confirmDisabled={codesLoading || !reasons.length || !actionsEnabled}
          onCancel={() => setExcluding(undefined)}
          onConfirm={async () => {
            if (!reason) {
              setFieldError("사유를 선택해 주세요.");
              return;
            }
            await save(excluding, () =>
              api.createWorkforceRecommendationExclusion(
                rcmdId!,
                excluding.rank,
                { reasonCode: reason, expectedRevision: excluding.revision },
              ),
            );
          }}
        >
          <div
            data-screen-id="SCR-UI-30-M1"
            data-testid="recommendation-exclusion-modal"
          >
            <p>
              {target.name} / {excluding.jobCode}
            </p>
            <label>
              제외 사유*
              <select
                data-testid="recommendation-exclusion-reason"
                value={reason}
                aria-invalid={!!fieldError}
                onChange={(e) => {
                  setReason(e.target.value);
                  setFieldError("");
                }}
              >
                <option value="">사유를 선택해 주세요</option>
                {reasons.map((r) => (
                  <option value={r.code} key={r.code}>
                    {r.name}
                  </option>
                ))}
              </select>
            </label>
            {codesLoading && <p role="status">사유를 불러오는 중입니다.</p>}
            {fieldError && <p role="alert">{fieldError}</p>}
            <AssignmentError error={error} />
            <p>같은 대상·적용 직무에서 다시 추천해도 제외됩니다.</p>
          </div>
        </Dialog>
      )}
      {confirming && (
        <Dialog
          open
          title={`가배정 확인: ${confirming.row.developer.name}`}
          pending={pending}
          confirmLabel="확인 후 가배정"
          confirmDisabled={
            !actionsEnabled ||
            (type === "AI" && selectedNeedSn !== confirming.needSn)
          }
          onCancel={() => setConfirming(undefined)}
          onConfirm={() =>
            save(confirming.row, () =>
              api.createWorkforceRecommendationTentativeAssignment(
                rcmdId!,
                confirming.row.rank,
                {
                  ...confirming.request,
                  confirmedWarnings: confirming.warnings.map((w) => w.code),
                },
              ),
            )
          }
        >
          <div
            data-testid="recommendation-warning-modal"
            data-screen-id="SCR-UI-30-M2"
          >
            <p>
              현재 선택 자리 {confirming.needSn} /{" "}
              {props.selectedNeedLabel ?? confirming.row.jobCode}
            </p>
            {confirming.warnings.map((w) => (
              <p className="sicost-banner sicost-tone-warn" key={w.code}>
                {w.message}
              </p>
            ))}
            <AssignmentError error={error} />
          </div>
        </Dialog>
      )}
      {assignment && (
        <AssignmentCreateForm
          year={props.year}
          today={props.today}
          initialValues={assignment}
          onClose={() => setAssignment(undefined)}
          onSaved={async () => {
            setAssignment(undefined);
            await props.onRefresh();
          }}
        />
      )}
    </section>
  );
}
