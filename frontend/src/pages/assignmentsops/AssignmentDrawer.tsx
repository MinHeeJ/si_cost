import { useCallback, useEffect, useState } from "react";
import { DetailDrawer } from "../../shared/DetailDrawer";
import { DataTable } from "../../shared/DataTable";
import { Dialog } from "../../shared/Dialog";
import { formatDecimal } from "../../shared/format";
import { useCommitRefresh } from "../../shared/useCommitRefresh";
import {
  AssignmentError,
  AssignmentWarnings,
  ReasonFields,
} from "./AssignmentDialogs";
import * as api from "./assignmentActionsApi";
export const kindName = (code: string | null) =>
  code === null
    ? "미정"
    : ({
        H: "높음",
        M: "보통",
        L: "낮음",
        NEW: "신규",
        ADD: "추가",
        SUB: "대체",
        FREE: "무상",
      }[code] ?? code);
export function AssignmentDrawer({
  id,
  year,
  readonly,
  onClose,
  onChange,
}: {
  id: string;
  year: number;
  readonly: boolean;
  onClose(): void;
  onChange(row: api.Assignment): void;
}) {
  const [row, setRow] = useState<api.Assignment>(),
    [months, setMonths] = useState<api.Page<api.Month>>(),
    [history, setHistory] = useState<api.Page<api.History>>(),
    [tab, setTab] = useState("info"),
    [historyType, setHistoryType] = useState(""),
    [page, setPage] = useState(1),
    [error, setError] = useState<unknown>(),
    [loading, setLoading] = useState(true),
    [correcting, setCorrecting] = useState<api.History>(),
    [content, setContent] = useState(""),
    [reason, setReason] = useState("");
  const { pending, message, commit } = useCommitRefresh();
  const load = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    try {
      const [a, m, h] = await Promise.all([
        api.getAssignment(id, year),
        api.getMonths(id, year),
        api.getHistory(id, year, historyType || undefined, page),
      ]);
      setRow(a);
      setMonths(m);
      setHistory(h);
    } catch (e) {
      setError(e);
      throw e;
    } finally {
      setLoading(false);
    }
  }, [id, year, historyType, page]);
  useEffect(() => {
    void load().catch(() => {});
  }, [load]);
  const value = (type: string, v: string | null) =>
    type === "EXTP" || type === "KIND" ? kindName(v) : (v ?? "–");
  return (
    <DetailDrawer
      title={`투입 배정 ${id}`}
      onClose={onClose}
      testId="assignment-detail-drawer"
      actions={
        row && !readonly && row.assignmentStatus === "ACT" ? (
          <button
            type="button"
            data-testid="assignment-change-open"
            onClick={() => onChange(row)}
          >
            변경 기록
          </button>
        ) : undefined
      }
    >
      <AssignmentError
        error={error}
        retry={() => void load().catch(() => {})}
      />
      {message && <p role="status">{message}</p>}
      <nav className="assignment-toolbar">
        <button
          type="button"
          data-testid="assignment-info-tab"
          onClick={() => setTab("info")}
        >
          투입 정보
        </button>
        <button
          type="button"
          data-testid="assignment-history-tab"
          onClick={() => setTab("history")}
        >
          변경 이력 ({history?.total ?? "–"})
        </button>
      </nav>
      {row && (
        <>
          <p>
            {row.projectName} / {row.projectId} · {row.developerName} (
            {row.managementNo}) / {row.developerId}
          </p>
          <p>
            실제 투입자 {row.developerId} / 계약상 인력{" "}
            {row.contractDeveloperId ?? "–"} · {row.jobName}
          </p>
          <p>
            {row.startDate} ~ {row.endDate} · {row.assignmentStatus} · 연장{" "}
            {row.extensionCount}회 · 조기철수 {row.earlyWithdrawal}
          </p>
          <p>
            계약 월MM {row.contractMm} / 단가{" "}
            {formatDecimal(row.billingUnitPrice)} 원 · {kindName(row.inputType)}{" "}
            · 연장가능성 {kindName(row.extensionPossibility)}
          </p>
          <AssignmentWarnings
            warnings={row.warnings}
            protectedMonths={row.protectedMonths}
          />
        </>
      )}
      {tab === "info" ? (
        <section data-testid="assignment-months-panel">
          <DataTable
            rows={months?.items ?? []}
            rowKey={(m) => m.month}
            loading={loading}
            empty="저장된 월자료가 없습니다. 전기간 보호 배정은 원천/이력만 등록됩니다."
            columns={[
              { id: "month", label: "월", render: (m) => m.month },
              {
                id: "days",
                label: "일수",
                render: (m) => m.inputDays,
                align: "right",
              },
              {
                id: "contract",
                label: "적용계약MM",
                render: (m) => m.contractMm,
                align: "right",
              },
              {
                id: "mm",
                label: "실제MM",
                render: (m) => m.monthlyMm,
                align: "right",
              },
              {
                id: "price",
                label: "단가 (원)",
                render: (m) => formatDecimal(m.appliedUnitPrice),
                align: "right",
              },
              {
                id: "sales",
                label: "매출 (원)",
                render: (m) => formatDecimal(m.salesAmount),
                align: "right",
              },
              { id: "fixed", label: "고정", render: (m) => m.fixed },
            ]}
          />
        </section>
      ) : (
        <section data-testid="assignment-history-panel">
          <label>
            종류
            <select
              data-testid="assignment-history-type"
              value={historyType}
              onChange={(e) => {
                setHistoryType(e.target.value);
                setPage(1);
              }}
            >
              <option value="">전체</option>
              {[
                "INPUT",
                "EXT",
                "EARLY",
                "UPRC",
                "RATE",
                "EXTP",
                "KIND",
                "CRCT",
                "CNCL",
              ].map((t) => (
                <option
                  data-testid={`assignment-history-type-option-${t.toLowerCase()}`}
                  key={t}
                >
                  {t}
                </option>
              ))}
            </select>
          </label>
          <DataTable
            rows={history?.items ?? []}
            rowKey={(h) => h.sequence}
            loading={loading}
            page={page}
            total={history?.total ?? 0}
            onPageChange={setPage}
            empty="해당 종류 이력이 없습니다."
            columns={[
              { id: "date", label: "적용일", render: (h) => h.applyDate },
              { id: "type", label: "종류", render: (h) => h.type },
              {
                id: "before",
                label: "이전",
                render: (h) => (
                  <span title={h.before ?? undefined}>
                    {value(h.type, h.before)}
                  </span>
                ),
              },
              {
                id: "after",
                label: "이후",
                render: (h) => (
                  <span title={h.after ?? undefined}>
                    {value(h.type, h.after)}
                  </span>
                ),
              },
              {
                id: "reason",
                label: "사유/메모",
                render: (h) => `${h.reasonCode ?? "–"} / ${h.memo ?? "–"}`,
              },
              {
                id: "actor",
                label: "입력자/시각",
                render: (h) => `${h.actor} / ${h.registeredAt}`,
              },
              {
                id: "badge",
                label: "배지",
                render: (h) =>
                  `${h.retroactive === "Y" ? "소급 " : ""}${h.closeCross === "Y" ? "마감걸침 " : ""}${h.corrected === "Y" ? "정정됨" : ""}`,
              },
              {
                id: "correct",
                label: "작업",
                render: (h) =>
                  !readonly &&
                  !["INPUT", "CRCT"].includes(h.type) &&
                  h.corrected !== "Y" ? (
                    <button
                      type="button"
                      data-testid={`assignment-correct-${h.sequence}`}
                      onClick={() => {
                        setCorrecting(h);
                        setContent("");
                        setReason("");
                      }}
                    >
                      정정
                    </button>
                  ) : (
                    "–"
                  ),
              },
            ]}
          />
        </section>
      )}
      <Dialog
        open={Boolean(correcting)}
        title="이력 정정"
        pending={pending}
        confirmLabel="정정 저장"
        onCancel={() => setCorrecting(undefined)}
        onConfirm={async () => {
          if (!row || !correcting) return;
          try {
            await commit(async () => {
              await api.correctHistory(id, correcting.sequence, {
                year,
                expectedRevision: row.revision,
                correctContent: content,
                reasonCode: reason,
              });
              setCorrecting(undefined);
            }, load);
          } catch (e) {
            setError(e);
          }
        }}
      >
        <div data-testid="assignment-correction-dialog">
          <p>
            {correcting?.type} / {correcting?.applyDate} / {correcting?.after}
          </p>
          <p>
            원래 기록은 그대로 남고 정정됨으로 표시됩니다. 지금 값을 바꾸려면
            변경 기록을 따로 넣으세요.
          </p>
          <label className="sicost-field">
            바른 내용 *
            <textarea
              data-testid="assignment-correction-content"
              value={content}
              onChange={(e) => setContent(e.target.value)}
            />
          </label>
          <ReasonFields
            reason={reason}
            memo=""
            onChange={(k, v) => {
              if (k === "reason") setReason(v);
            }}
          />
          <AssignmentError error={error} />
        </div>
      </Dialog>
    </DetailDrawer>
  );
}
