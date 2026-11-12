import { useEffect, useRef, useState } from "react";
import { ApiError } from "../../shared/api";
import { DataTable, type Column } from "../../shared/DataTable";
import { Dialog } from "../../shared/Dialog";
import { ErrorBanner, StatusBadge } from "../../shared/Feedback";
import { useCommitRefresh } from "../../shared/useCommitRefresh";
import { getSharedCodes, type SharedCodeLookup } from "../shared/sharedApi";
import {
  createAssignmentHistoryCorrection,
  getAssignmentHistory,
  type AssignmentHistory,
  type AssignmentHistoryPage,
} from "./assignmentHistoryApi";
import "./assignmentHistory.css";

const types: Record<AssignmentHistory["type"], string> = {
  INPUT: "최초 투입",
  EXT: "연장",
  EARLY: "조기 철수",
  UPRC: "단가",
  RATE: "월 MM",
  EXTP: "연장 가능성",
  KIND: "투입구분",
  CRCT: "감사 정정",
  CNCL: "취소",
};
type Props = {
  assignmentId: string;
  year: number;
  currentYear: number;
  revision: string;
  refreshKey?: string | number;
  onChanged(revision: string): Promise<unknown> | void;
  onDirtyChange?(dirty: boolean): void;
};
function message(error: unknown) {
  return error instanceof ApiError
    ? `${error.message} (문의 번호: ${error.body.requestId})`
    : error instanceof Error
      ? error.message
      : "이력을 불러오지 못했습니다.";
}
// SCR-UI-12: StaffingAssignmentsScreen 상세의 변경 이력 탭과 M-CRCT.
export function AssignmentHistoryPanel(props: Props) {
  return (
    <AssignmentHistoryContent
      key={`${props.assignmentId}:${props.year}`}
      {...props}
    />
  );
}
function AssignmentHistoryContent({
  assignmentId,
  year,
  currentYear,
  revision,
  refreshKey,
  onChanged,
  onDirtyChange,
}: Props) {
  const [page, setPage] = useState(1),
    [size, setSize] = useState<20 | 50 | 100>(50),
    [type, setType] = useState("");
  const [data, setData] = useState<AssignmentHistoryPage>(),
    [loading, setLoading] = useState(true),
    [error, setError] = useState<string>();
  const [reload, setReload] = useState(0),
    [selected, setSelected] = useState<AssignmentHistory>();
  const [content, setContent] = useState(""),
    [reason, setReason] = useState(""),
    [codes, setCodes] = useState<SharedCodeLookup[]>([]);
  const [codeError, setCodeError] = useState<string>(),
    [formError, setFormError] = useState<string>(),
    [fields, setFields] = useState<Record<string, string>>({});
  const [discard, setDiscard] = useState(false),
    [latestRevision, setLatestRevision] = useState(revision);
  const commit = useCommitRefresh();
  const requestSerial = useRef(0);
  useEffect(() => setLatestRevision(revision), [revision]);
  useEffect(() => {
    const dirty = Boolean(selected && (content || reason));
    onDirtyChange?.(dirty);
    return () => onDirtyChange?.(false);
  }, [selected, content, reason, onDirtyChange]);
  useEffect(() => {
    const controller = new AbortController(),
      serial = ++requestSerial.current;
    setLoading(true);
    setError(undefined);
    getAssignmentHistory(
      assignmentId,
      { year, page, size, type },
      controller.signal,
    )
      .then((value) => {
        if (serial === requestSerial.current && !controller.signal.aborted)
          setData(value);
      })
      .catch((e) => {
        if (!controller.signal.aborted) setError(message(e));
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [assignmentId, year, page, size, type, reload, refreshKey]);
  async function loadCodes() {
    setCodeError(undefined);
    try {
      setCodes((await getSharedCodes("ASGN_CHG_RSN")).items);
    } catch (e) {
      setCodeError(message(e));
    }
  }
  function open(row: AssignmentHistory) {
    setSelected(row);
    setContent("");
    setReason("");
    setFormError(undefined);
    setFields({});
    void loadCodes();
  }
  function cancel() {
    if (content || reason) setDiscard(true);
    else setSelected(undefined);
  }
  async function save() {
    if (!selected || commit.pending || year < currentYear) return;
    const correctContent = content.trim(),
      reasonCode = reason.trim();
    const invalid: Record<string, string> = {};
    if (!correctContent || [...correctContent].length > 200)
      invalid.correctContent = "바른 내용은 1~200자로 입력해 주세요.";
    if (!reasonCode) invalid.reasonCode = "사유코드를 선택해 주세요.";
    setFields(invalid);
    if (Object.keys(invalid).length) return;
    setFormError(undefined);
    let committedRevision = latestRevision;
    try {
      await commit.commit(
        async () => {
          const saved = await createAssignmentHistoryCorrection(
            assignmentId,
            selected.sequence,
            {
              year,
              expectedRevision: latestRevision,
              correctContent,
              reasonCode,
            },
          );
          setLatestRevision(saved.revision);
          setSelected(undefined);
          setContent("");
          setReason("");
          committedRevision = saved.revision;
          setReload((value) => value + 1);
          return saved;
        },
        () => Promise.resolve(onChanged(committedRevision)),
      );
    } catch (e) {
      setFormError(message(e));
      if (e instanceof ApiError) setFields(e.fieldMessages);
    }
  }
  const columns: Column<AssignmentHistory>[] = [
    { id: "sequence", label: "번호", render: (row) => row.sequence },
    {
      id: "type",
      label: "종류",
      render: (row) => (
        <StatusBadge
          tone={
            row.type === "CRCT" ? "warn" : row.type === "CNCL" ? "lock" : "info"
          }
        >
          {types[row.type]} ({row.type})
        </StatusBadge>
      ),
    },
    { id: "applyDate", label: "적용일", render: (row) => row.applyDate },
    { id: "before", label: "변경 전", render: (row) => row.before ?? "–" },
    { id: "after", label: "변경 후", render: (row) => row.after ?? "–" },
    {
      id: "typed",
      label: "원 단가 / 월 MM",
      align: "right",
      render: (row) =>
        `${row.newUnitPrice ?? "–"} / ${row.newContractMm ?? "–"}`,
    },
    {
      id: "reason",
      label: "사유 / 메모",
      render: (row) => `${row.reasonCode ?? "–"} / ${row.memo ?? "–"}`,
    },
    {
      id: "flags",
      label: "표시",
      render: (row) => (
        <>
          <span>
            소급 {row.retroactive} · 마감 {row.closeCross}
          </span>
          {row.corrected === "Y" && (
            <StatusBadge tone="warn">정정됨</StatusBadge>
          )}
          {row.correctionTargetSequence !== null && (
            <span>원행 #{row.correctionTargetSequence}</span>
          )}
        </>
      ),
    },
    {
      id: "actor",
      label: "처리자 / 처리시각",
      render: (row) => `${row.actor} / ${row.registeredAt}`,
    },
    {
      id: "action",
      label: "작업",
      render: (row) =>
        year >= currentYear &&
        row.type !== "INPUT" &&
        row.type !== "CRCT" &&
        row.corrected === "N" ? (
          <button
            type="button"
            data-testid={`history-correct-${row.sequence}`}
            disabled={loading || commit.pending}
            onClick={() => open(row)}
          >
            이력 정정
          </button>
        ) : (
          "–"
        ),
    },
  ];
  return (
    <section
      className="assignment-history-panel"
      data-testid="assignment-history-panel"
      data-screen-id="SCR-UI-12"
    >
      <div className="assignment-history-toolbar">
        <h2>변경 이력</h2>
        <label>
          종류{" "}
          <select
            data-testid="history-type-filter"
            value={type}
            onChange={(event) => {
              setType(event.target.value);
              setPage(1);
              setSelected(undefined);
            }}
          >
            <option value="">전체</option>
            {Object.entries(types).map(([code, label]) => (
              <option
                data-testid={`history-type-filter-option-${code.toLowerCase()}`}
                key={code}
                value={code}
              >
                {label} ({code})
              </option>
            ))}
          </select>
        </label>
      </div>
      <p className="sicost-banner sicost-tone-info">
        원래 기록은 그대로 남고 정정됨으로 표시됩니다. 지금 값을 바꾸려면 변경
        기록을 따로 넣으세요.
      </p>
      {year < currentYear && (
        <p className="sicost-banner sicost-tone-lock">
          지난 연도는 조회만 할 수 있습니다.
        </p>
      )}
      {commit.message && <p role="status">{commit.message}</p>}
      <DataTable
        rows={data?.items ?? []}
        columns={columns}
        rowKey={(row) => row.sequence}
        loading={loading}
        error={error}
        onRetry={() => setReload((value) => value + 1)}
        empty="등록된 변경 이력이 없습니다."
        page={page}
        size={size}
        total={data?.total ?? 0}
        onPageChange={setPage}
        onSizeChange={(value) => {
          setSize(value);
          setPage(1);
          setSelected(undefined);
        }}
        dense
      />
      <Dialog
        open={Boolean(selected)}
        title={`이력 정정 / ${assignmentId} / #${selected?.sequence ?? ""}`}
        pending={commit.pending}
        onCancel={cancel}
        onConfirm={save}
        confirmLabel="정정 저장"
        confirmDisabled={
          Boolean(codeError) || codes.length === 0 || year < currentYear
        }
      >
        <div
          data-testid="history-correction-dialog"
          className="assignment-history-form"
        >
          <p>
            {selected &&
              `${types[selected.type]} (${selected.type}) / ${selected.applyDate}`}
          </p>
          <p>기존 내용: {selected?.after ?? "–"}</p>
          <p className="sicost-banner sicost-tone-warn">
            정정은 감사 보완입니다. 원본 실제값·현재값·월자료·연장횟수는 바뀌지
            않습니다.
          </p>
          {codeError && (
            <ErrorBanner message={codeError} onRetry={() => void loadCodes()} />
          )}
          <label className="sicost-field">
            바른 내용 *
            <textarea
              data-testid="history-correct-content"
              value={content}
              aria-invalid={Boolean(fields.correctContent)}
              onChange={(event) => setContent(event.target.value)}
              rows={4}
            />
          </label>
          {fields.correctContent && <p role="alert">{fields.correctContent}</p>}
          <label className="sicost-field">
            사유코드 *
            <select
              data-testid="history-reason-code"
              value={reason}
              aria-invalid={Boolean(fields.reasonCode)}
              onChange={(event) => setReason(event.target.value)}
            >
              <option value="">선택해 주세요</option>
              {codes.map((code) => (
                <option
                  data-testid={`history-reason-code-option-${code.code.toLowerCase().replace(/_/g, "-")}`}
                  key={code.code}
                  value={code.code}
                >
                  {code.name}
                </option>
              ))}
            </select>
          </label>
          {fields.reasonCode && <p role="alert">{fields.reasonCode}</p>}
          {formError && <ErrorBanner message={formError} />}
        </div>
      </Dialog>
      <Dialog
        open={discard}
        title="작성 중인 내용을 나가시겠습니까?"
        onCancel={() => setDiscard(false)}
        cancelLabel="계속 작성"
        confirmLabel="나가기"
        onConfirm={() => {
          setDiscard(false);
          setSelected(undefined);
          setContent("");
          setReason("");
        }}
      >
        <p>자동 저장하지 않습니다.</p>
      </Dialog>
    </section>
  );
}
