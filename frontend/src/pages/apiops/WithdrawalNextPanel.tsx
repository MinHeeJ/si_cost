import { useEffect, useRef, useState } from "react";
import { formatRate } from "../../shared/format";
import { Link } from "react-router-dom";
import { DetailDrawer } from "../../shared/DetailDrawer";
import { Dialog } from "../../shared/Dialog";
import { ErrorBanner, StatusBadge } from "../../shared/Feedback";
import { ApiError } from "../../shared/api";
import { useCommitRefresh } from "../../shared/useCommitRefresh";
import { getDeveloper } from "../developers/peopleClient";
import * as api from "./withdrawalsApi";

const scoreLabels: Record<string, string> = {
  JOB: "직무",
  GRADE: "등급",
  GAP: "공백",
  CUST: "고객",
  PROB: "확률",
};

export function WithdrawalError({
  error,
  retry,
}: {
  error: unknown;
  retry?: () => void;
}) {
  if (!error) return null;
  return (
    <ErrorBanner
      message={error instanceof Error ? error.message : "조회하지 못했습니다."}
      requestId={error instanceof ApiError ? error.body.requestId : undefined}
      onRetry={retry}
    />
  );
}
export function WithdrawalNextPanel({
  row,
  year,
  initial,
  onClose,
  onChanged,
}: {
  row: api.WithdrawalRow;
  year: number;
  initial: Promise<api.NextPositionData>;
  onClose(): void;
  onChanged(): Promise<void>;
}) {
  const [data, setData] = useState<api.NextPositionData>();
  const [skills, setSkills] = useState<string[]>();
  const [error, setError] = useState<unknown>();
  const [loading, setLoading] = useState(true);
  const [confirmation, setConfirmation] = useState<{
    candidate: api.NextPositionCandidate;
    warnings: api.WithdrawalWarning[];
  }>();
  const sequence = useRef(0);
  const round = useRef<string | null>(null);
  const { pending, message, commit } = useCommitRefresh();
  useEffect(() => {
    let live = true;
    initial
      .then((value) => {
        if (live) {
          setData(value);
          round.current = value.rcmdId;
        }
      })
      .catch((e) => {
        if (live) setError(e);
      })
      .finally(() => {
        if (live) setLoading(false);
      });
    void getDeveloper(row.developer.detailId)
      .then((d) => {
        if (live) setSkills(d.skills);
      })
      .catch((e) => {
        if (live) setError(e);
      });
    return () => {
      live = false;
      sequence.current++;
    };
  }, [initial, row.developer.detailId]);
  async function run() {
    const version = ++sequence.current;
    setLoading(true);
    setError(undefined);
    setData(undefined);
    try {
      const value = await api.createNextPositionRecommendation(row, year);
      if (version === sequence.current) {
        setData(value);
        round.current = value.rcmdId;
      }
    } catch (e) {
      if (version === sequence.current) setError(e);
    } finally {
      if (version === sequence.current) setLoading(false);
    }
  }
  async function refresh() {
    if (round.current)
      setData(await api.getWithdrawalRecommendation(round.current));
    await onChanged();
  }
  async function select(
    candidate: api.NextPositionCandidate,
    warnings: string[],
  ) {
    if (!data?.rcmdId || pending) return;
    setError(undefined);
    try {
      await commit(async () => {
        await api.selectWithdrawalPosition(data.rcmdId!, candidate, warnings);
        setData(
          (previous) =>
            previous && {
              ...previous,
              candidates: previous.candidates.map((item) =>
                item.rank === candidate.rank
                  ? { ...item, selection: { actionCode: "TENT" } }
                  : item,
              ),
            },
        );
        setConfirmation(undefined);
        api.withdrawalCommitted();
      }, refresh);
    } catch (e) {
      const current = api.withdrawalWarnings(e);
      if (current.length) {
        setConfirmation({ candidate, warnings: current });
        setError(e);
      } else setError(e);
    }
  }
  return (
    <DetailDrawer
      title={`다음 자리: ${row.developer.name}`}
      subtitle={`${row.assignmentId} · ${row.projectName} · 철수 ${row.withdrawalDate}`}
      pending={pending}
      onClose={onClose}
      testId="withdrawal-next-panel"
    >
      <div className="withdrawal-next" data-screen-id="SCR-UI-05-NEXT">
        <p>
          보유스킬 {skills?.join(", ") || "–"} · 연장가능성{" "}
          {row.extensionPossibility ?? "미정"}
        </p>
        <p>대기 시작: 철수일 {row.withdrawalDate} 다음 날</p>
        <button
          data-testid="withdrawal-next-rerun"
          disabled={loading || pending}
          onClick={() => void run()}
        >
          다시 추천
        </button>
        <WithdrawalError error={error} />
        {message && (
          <p role="status">
            {message}{" "}
            <button
              data-testid="withdrawal-next-refresh"
              disabled={pending}
              onClick={() => void refresh().catch(setError)}
            >
              재조회
            </button>
          </p>
        )}
        {loading ? (
          <p role="status">다음 자리 추천 중</p>
        ) : (
          data && (
            <>
              <h3>맞는 자리 {data.candidates.length}곳</h3>
              {!data.candidates.length && (
                <div className="sicost-empty">맞는 자리가 없습니다.</div>
              )}
              {data.candidates.map((candidate) => {
                const position = candidate.nextPosition;
                if (!position) return null;
                const selected =
                  position.alreadyTentativelyAssigned ||
                  candidate.selection.actionCode !== null;
                return (
                  <article
                    className="withdrawal-position"
                    key={candidate.rank}
                    data-testid={`withdrawal-position-${candidate.rank}`}
                  >
                    <h3>
                      {candidate.rank}.{" "}
                      <Link
                        data-testid={`withdrawal-position-link-${candidate.rank}`}
                        to={`/UI-29?${new URLSearchParams({ opportunityId: candidate.opportunityId ?? "" })}`}
                      >
                        {position.opportunityName}
                      </Link>
                    </h3>
                    <p>
                      수주확률 {formatRate(position.probability)} · 단계{" "}
                      {position.salesStepCode} · {candidate.jobCode} ·{" "}
                      {position.gradeCode}
                    </p>
                    <p>
                      자리 시작 {position.originalStartMonth} · 실제 예정{" "}
                      {position.actualStartMonth}
                    </p>
                    <p>
                      남은 인원 {position.remainingCount}명 · 고객{" "}
                      {position.customerName ?? "–"}
                    </p>
                    {candidate.scoreItems.map((item) => (
                      <div
                        data-testid={`withdrawal-score-${candidate.rank}-${Array.from(item.itemCode, (c) => c.codePointAt(0)!.toString(16)).join("-")}`}
                        className="withdrawal-score"
                        key={item.itemCode}
                        title={item.basis}
                      >
                        <span>
                          {scoreLabels[item.itemCode] ?? item.itemCode}
                        </span>
                        <meter
                          min={0}
                          max={Number(item.weight) || 1}
                          value={Number(item.score)}
                          aria-label={item.itemCode}
                        />
                        <span>
                          {item.score}/{item.weight}
                        </span>
                      </div>
                    ))}
                    <p>
                      총점 {candidate.totalScore} · {candidate.reason}
                    </p>
                    <div className="withdrawal-chips">
                      {position.reasonChips.map((chip) => (
                        <StatusBadge key={chip} tone="neutral">
                          {chip}
                        </StatusBadge>
                      ))}
                    </div>
                    <p>
                      {position.waitDays
                        ? `대기 예상 ${position.waitDays}일`
                        : "바로 이어서 투입"}
                    </p>
                    <button
                      data-testid={`withdrawal-position-select-${candidate.rank}`}
                      disabled={selected || pending || !candidate.needSn}
                      onClick={() => void select(candidate, [])}
                    >
                      {selected ? "가배정됨" : "가배정"}
                    </button>
                  </article>
                );
              })}
              {data.shortageMonths.map((month) => (
                <p
                  data-testid={`withdrawal-shortage-${Array.from(`${month.month}:${month.jobCode}`, (c) => c.codePointAt(0)!.toString(16)).join("-")}`}
                  className="sicost-banner sicost-tone-warn"
                  key={`${month.month}-${month.jobCode}`}
                >
                  부족 안내: {month.month} · {month.jobCode} ·{" "}
                  {month.shortagePeople}명
                </p>
              ))}
            </>
          )
        )}
        <Dialog
          open={Boolean(confirmation)}
          title={`다음 자리 가배정 확인: ${row.developer.name}`}
          confirmLabel="확인 후 가배정"
          pending={pending}
          onCancel={() => setConfirmation(undefined)}
          onConfirm={() =>
            confirmation &&
            select(
              confirmation.candidate,
              confirmation.warnings.map((w) => w.code),
            )
          }
        >
          <div
            data-testid="withdrawal-position-confirmation"
            data-screen-id="SCR-UI-05-M1"
          >
            <p>
              선택 기회 {confirmation?.candidate.nextPosition?.opportunityName}{" "}
              · 자리 {confirmation?.candidate.needSn}
            </p>
            {confirmation?.warnings.map((w) => (
              <p
                key={w.code}
                data-testid={`withdrawal-warning-${Array.from(w.code, (c) => c.codePointAt(0)!.toString(16)).join("-")}`}
              >
                {w.message}
              </p>
            ))}
            <WithdrawalError error={error} />
          </div>
        </Dialog>
      </div>
    </DetailDrawer>
  );
}
