import { useEffect, useId, useState } from "react";
import { useNavigate } from "react-router-dom";
import { DataTable } from "../../shared/DataTable";
import { ErrorBanner } from "../../shared/Feedback";
import {
  compareDecimal,
  formatAmount,
  formatDateTime,
  formatDecimal,
  formatFixedDecimal,
  type AmountUnit,
} from "../../shared/format";
import { Icon } from "../../shared/Icon";
import { useAnalysisAssistant } from "./AnalysisAssistantContext";
import {
  downloadQuestion,
  type AnalysisTable,
  type AnalysisRow,
} from "./analysisClient";
import "./analysis.css";
import { analysisDestination } from "./analysisNavigation";
const states: Record<string, string> = {
  CLOSED: "마감",
  MIGRATED: "이관",
  PROVISIONAL: "잠정",
  PLANNED: "예정",
  UNKNOWN: "미확인",
  OK: "완료",
  NO_ANS: "답할 수 없음",
  ERR: "오류",
};
const scenarios = { LOW: "낮음", BASE: "기본", HIGH: "높음" };
export function AnalysisConversation({
  compact = false,
}: {
  compact?: boolean;
}) {
  const a = useAnalysisAssistant(),
    navigate = useNavigate(),
    id = useId();
  const [showHistory, setShowHistory] = useState(!compact),
    [unit, setUnit] = useState<AmountUnit>("천원");
  useEffect(() => {
    void a.initialize();
  }, []);
  const text = a.question.trim(),
    valid = [...text].length >= 1 && [...text].length <= 1000;
  const testPrefix = compact ? "analysis-side" : "analysis-main";
  return (
    <div
      className={`analysis-workspace${compact ? " compact" : ""}`}
      data-screen-id={compact ? "SCR-UI-26-P1" : "SCR-UI-26"}
      data-testid={`${testPrefix}-conversation`}
    >
      <button
        type="button"
        className="analysis-history-toggle"
        data-testid={`${testPrefix}-history-toggle`}
        onClick={() => setShowHistory((v) => !v)}
        aria-expanded={showHistory}
      >
        <Icon name="clock" size={16} /> 질문 기록{" "}
        {showHistory ? "닫기" : "열기"}
      </button>
      {showHistory && (
        <aside className="analysis-history" aria-label="질문 기록">
          <header>
            <h2>질문 기록</h2>
            <button
              type="button"
              data-testid={`${testPrefix}-history-refresh`}
              onClick={() => void a.refresh()}
            >
              새로고침
            </button>
          </header>
          {a.historyError && (
            <ErrorBanner
              message={a.historyError}
              onRetry={() => void a.refresh()}
            />
          )}
          {!a.history.length && !a.historyError && <p>아직 질문이 없습니다.</p>}
          {a.history.map((h, index) => (
            <article
              key={h.qnaId}
              data-testid={`${testPrefix}-history-row-${index}`}
            >
              <button
                className="analysis-history-title"
                data-testid={`${testPrefix}-history-ask-${index}`}
                type="button"
                disabled={a.pending}
                onClick={() => void a.askAgain(h.qnaId)}
                title="현재 데이터로 다시 질문"
              >
                {h.question}
              </button>
              <small>
                {formatDateTime(h.questionAt)} · {states[h.status]}{" "}
                {h.provisional && "· 잠정"}
              </small>
              <button
                type="button"
                data-testid={`${testPrefix}-history-view-${index}`}
                onClick={() => void a.view(h.qnaId)}
              >
                답변 보기
              </button>
            </article>
          ))}
          {a.cursor && (
            <button
              type="button"
              data-testid={`${testPrefix}-history-more`}
              onClick={() => void a.refresh(true)}
            >
              더 보기 (50건)
            </button>
          )}
        </aside>
      )}
      <section className="analysis-chat" aria-label="AI 대화">
        <p className="analysis-safety">
          <Icon name="sparkle" size={16} /> 조회·해석 전용 · 업무 변경과 메일
          발송을 실행하지 않습니다.
        </p>
        {a.lastQuestion && (
          <p className="analysis-user-message">{a.lastQuestion}</p>
        )}
        {a.pending && (
          <div className="analysis-progress" role="status" aria-live="polite">
            {["데이터 찾는 중", "계산 중", "정리 중"].map((s, i) => (
              <span
                className={s === a.progress?.message ? "active" : ""}
                key={s}
              >
                {i + 1}. {s}
              </span>
            ))}
            {!a.progress && <small>서버 접수 확인 중</small>}
          </div>
        )}
        {a.error && <ErrorBanner message={a.error} />}
        {a.result ? (
          <article className="analysis-answer">
            <header>
              <h2>{a.result.conclusion}</h2>
              <span>{states[a.result.status]}</span>
            </header>
            {a.snapshot && (
              <p className="sicost-banner sicost-tone-info">{a.snapshot}</p>
            )}
            {a.pending && (
              <p>
                이전 질문 결과입니다. 새 질문은 위 진행 단계에 따라 처리
                중입니다.
              </p>
            )}
            <p>
              적용 범위:{" "}
              {a.result.appliedScope.targets
                .map((t) => t.displayName)
                .join(" · ")}
              <br />
              기간: {a.result.appliedScope.fromMonth} ~{" "}
              {a.result.appliedScope.toMonth} · 기준일{" "}
              {a.result.appliedScope.baseDate}
            </p>
            <small>
              데이터 기준시점 {formatDateTime(a.result.dataAsOf)} · 전송 화면{" "}
              {a.result.context.screenId ?? "–"} · 선택월{" "}
              {a.result.context.baseMonth ?? "없음"}
            </small>
            {a.result.tables.length > 0 && (
              <label className="analysis-unit">
                금액 단위{" "}
                <select
                  aria-label="답변 금액 단위"
                  data-testid={`${testPrefix}-amount-unit`}
                  value={unit}
                  onChange={(e) => setUnit(e.target.value as AmountUnit)}
                >
                  {["원", "천원", "백만원"].map((u) => (
                    <option key={u}>{u}</option>
                  ))}
                </select>
              </label>
            )}
            {a.result.tables.map((table) => (
              <AnalysisResultTable
                key={`${a.qnaId}-${table.tableId}`}
                table={table}
                unit={unit}
              />
            ))}
            <h3>가정 / 미확인 사유</h3>
            {[...a.result.assumptions, ...a.result.unknownReasons].length ? (
              <ul>
                {[...a.result.assumptions, ...a.result.unknownReasons].map(
                  (s, i) => (
                    <li key={i}>{s}</li>
                  ),
                )}
              </ul>
            ) : (
              <p>추가 가정 없음</p>
            )}
            <h3>사용한 데이터</h3>
            {a.result.usedData.length ? (
              <ul>
                {a.result.usedData.map((s, i) => (
                  <li key={i}>
                    <strong>{s.queryName}</strong> · {states[s.sourceState]}
                    <p>{s.calculationBasis}</p>
                    <small>
                      대상:{" "}
                      {s.scope.targets.map((t) => t.displayName).join(" · ")} ·
                      기간 {s.scope.fromMonth} ~ {s.scope.toMonth} · 기준일{" "}
                      {s.scope.baseDate}
                      {s.scope.filters
                        .map(
                          (f) =>
                            ` · ${f.key} ${f.operator} ${f.values.join(", ")}`,
                        )
                        .join("")}
                      <br />
                      자료 식별값: {s.referenceIds.join(" · ")}
                    </small>
                  </li>
                ))}
              </ul>
            ) : (
              <p>사용 가능한 조회 근거 없음</p>
            )}
            <h3>다음 행동</h3>
            <ul>
              {a.result.nextActions.map((s, i) => (
                <li key={i}>{s}</li>
              ))}
            </ul>
            <div className="analysis-actions">
              {a.result.screenLinks.map((link, i) => (
                <button
                  type="button"
                  key={i}
                  data-testid={`${testPrefix}-source-${i}`}
                  onClick={() => {
                    const destination = analysisDestination(link);
                    navigate(destination.to, { state: destination.state });
                  }}
                  title={link.guidance}
                >
                  {link.screenId} 원래 화면 열기
                </button>
              ))}
              {a.result.status === "OK" && a.qnaId && (
                <button
                  type="button"
                  data-testid={`${testPrefix}-download`}
                  onClick={() =>
                    void downloadQuestion(a.qnaId!).catch((e) =>
                      a.setError(
                        e instanceof Error ? e.message : "다운로드 실패",
                      ),
                    )
                  }
                >
                  엑셀 다운로드
                </button>
              )}
            </div>
            <small>{a.result.interpreter.notice}</small>
          </article>
        ) : (
          <div className="analysis-empty">
            <Icon name="sparkle" size={32} />
            <h2>등록 자료에 대해 질문하세요</h2>
            <p>
              손익·원가·인력·자금의 공통 조회로 답변합니다.
              <br />
              질문은 매번 독립적으로 해석하며 이전 대화의 대상을 이어받지
              않습니다.
            </p>
          </div>
        )}
        <div className="analysis-composer">
          <div className="analysis-suggestions">
            {[
              "다음 분기 대기 인력",
              "이번 달 적자 인력",
              "이번 달 매출·영업이익",
            ].map((q, index) => (
              <button
                type="button"
                disabled={a.pending}
                key={q}
                data-testid={`${testPrefix}-suggestion-${index}`}
                onClick={() => void a.send(q)}
              >
                {q}
              </button>
            ))}
          </div>
          <label htmlFor={id}>질문</label>
          <div className="analysis-input">
            <textarea
              id={id}
              data-testid={`${testPrefix}-question`}
              value={a.question}
              placeholder="조회할 대상·기간·지표를 입력하세요"
              aria-invalid={!valid && Boolean(a.question)}
              aria-describedby={`${id}-error`}
              onChange={(e) => a.setQuestion(e.target.value)}
              onKeyDown={(e) => {
                if (
                  e.key === "Enter" &&
                  !e.shiftKey &&
                  !e.nativeEvent.isComposing &&
                  e.keyCode !== 229
                ) {
                  e.preventDefault();
                  if (valid && !a.pending) void a.send();
                }
              }}
            />
            <button
              type="button"
              className="sicost-primary"
              data-testid={`${testPrefix}-send`}
              disabled={!valid || a.pending}
              onClick={() => void a.send()}
            >
              {a.pending ? "처리 중…" : "보내기"}
            </button>
          </div>
          <small
            id={`${id}-error`}
            className={
              !valid && Boolean(a.question) ? "sicost-field-error" : ""
            }
          >
            {!valid
              ? "질문은 앞뒤 공백을 제외하고 1~1000자로 입력해 주세요."
              : "Enter 전송 · Shift+Enter 줄바꿈"}{" "}
            ({[...text].length}/1000)
          </small>
        </div>
      </section>
    </div>
  );
}
function AnalysisResultTable({
  table,
  unit,
}: {
  table: AnalysisTable;
  unit: AmountUnit;
}) {
  const [page, setPage] = useState(1),
    [sort, setSort] = useState({
      id: table.sort[0]?.columnKey ?? table.columns[0].key,
      direction: table.sort[0]?.direction ?? ("ASC" as "ASC" | "DESC"),
    });
  const column = table.columns.find((c) => c.key === sort.id)!;
  const cell = (r: AnalysisRow, key: string) =>
    r.cells.find((c) => c.columnKey === key);
  const rows = [...table.rows].sort((a, b) => {
    const x = cell(a, sort.id)?.value ?? null,
      y = cell(b, sort.id)?.value ?? null;
    return (
      (column.valueType === "DECIMAL"
        ? compareDecimal(x, y)
        : (x ?? "").localeCompare(y ?? "")) *
      (sort.direction === "ASC" ? 1 : -1)
    );
  });
  const columns: import("../../shared/DataTable").Column<AnalysisRow>[] =
    table.columns.map((c) => ({
      id: c.key,
      label: `${c.label}${c.unit ? ` (${c.unit === "원" ? unit : c.unit})` : ""}`,
      align: c.valueType === "DECIMAL" ? "right" : "left",
      sortable: true,
      render: (r: AnalysisRow) => (
        <span
          className={
            c.valueType === "DECIMAL" && cell(r, c.key)?.value?.startsWith("-")
              ? "sicost-number-negative"
              : undefined
          }
          title={cell(r, c.key)?.reason ?? undefined}
        >
          {display(cell(r, c.key)?.value, c.key)}
          {cell(r, c.key)?.reason && <small> · {cell(r, c.key)?.reason}</small>}
          {c.key === table.columns[0].key && r.reason && (
            <small> · {r.reason}</small>
          )}
        </span>
      ),
    }));
  const display = (value: string | null | undefined, key: string) => {
    const c = table.columns.find((c) => c.key === key)!;
    return c.valueType === "DECIMAL"
      ? c.unit === "원"
        ? formatAmount(value, unit)
        : c.unit === "MM"
          ? formatFixedDecimal(value, 2)
          : ["명", "%", "%p"].includes(c.unit)
            ? formatFixedDecimal(value, 1)
            : formatDecimal(value)
      : (states[value ?? ""] ?? value ?? "–");
  };
  return (
    <section className="analysis-result-table">
      <h3>
        {table.title} · {scenarios[table.scenario]}{" "}
        <small>{states[table.sourceState]}</small>
      </h3>
      <p>
        {table.periodLabel} · {table.totalRows}행 · 금액 {unit}, 기타 지표는 열
        단위 · 정렬 {column.label}{" "}
        {sort.direction === "ASC" ? "오름차순" : "내림차순"}
      </p>
      <DataTable<AnalysisRow>
        rows={rows.slice((page - 1) * 50, page * 50)}
        rowKey={(r) => r.rowKey}
        columns={columns}
        page={page}
        size={50}
        total={table.totalRows}
        onPageChange={setPage}
        sort={sort}
        onSortChange={(key) => {
          setSort({
            id: key,
            direction:
              sort.id === key && sort.direction === "ASC" ? "DESC" : "ASC",
          });
          setPage(1);
        }}
        empty={table.emptyReason ?? "정상 조회 0건"}
        footer={{
          label: "전체 합계",
          render: (c) =>
            c.id === table.columns[0].key ? (
              "전체 합계"
            ) : table.totals.some((v) => v.columnKey === c.id) ? (
              <span>
                {display(
                  table.totals.find((v) => v.columnKey === c.id)?.value,
                  c.id,
                )}
                {table.totals.find((v) => v.columnKey === c.id)?.reason && (
                  <small>
                    {" "}
                    · {table.totals.find((v) => v.columnKey === c.id)?.reason}
                  </small>
                )}
              </span>
            ) : (
              ""
            ),
        }}
      />
    </section>
  );
}
