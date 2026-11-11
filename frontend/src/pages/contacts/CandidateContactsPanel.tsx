import { useEffect, useId, useRef, useState } from "react";
import { DataTable, type Column } from "../../shared/DataTable";
import { Dialog, ConfirmDialog } from "../../shared/Dialog";
import {
  ErrorBanner,
  StatusBadge,
  Toast,
  type Tone,
} from "../../shared/Feedback";
import { ApiError } from "../../shared/api";
import * as api from "./peopleClient";
import "./contacts.css";

const statuses: Record<
  api.CandidateContactStatus,
  { label: string; tone: Tone }
> = {
  BEFORE: { label: "연락 전", tone: "neutral" },
  DONE: { label: "연락함", tone: "info" },
  ITVW: { label: "면접 예정", tone: "warn" },
  HOLD: { label: "보류", tone: "lock" },
  CTRT: { label: "계약 완료", tone: "ok" },
};
export function CandidateContactStatusBadge({
  status,
}: {
  status: api.CandidateContactStatus;
}) {
  return (
    <StatusBadge tone={statuses[status].tone}>
      {statuses[status].label}
    </StatusBadge>
  );
}
const columns: Column<api.CandidateContact>[] = [
  { id: "contactDate", label: "연락일", render: (row) => row.contactDate },
  {
    id: "status",
    label: "상태",
    render: (row) => <CandidateContactStatusBadge status={row.status} />,
  },
  {
    id: "memo",
    label: "메모",
    render: (row) => <span className="candidate-contact-memo">{row.memo}</span>,
  },
  {
    id: "actor",
    label: "처리자",
    render: (row) => `${row.actorName} (${row.actorId})`,
  },
];
export type CandidateContactsPanelProps = {
  candidate: api.CandidateContactContext;
  active?: boolean;
  onCommitted?(): void | Promise<void>;
  onDirtyChange?(dirty: boolean): void;
};
// SCR-UI-33 / SLICE-BASIC132-CANDIDATES: mount in the retained candidate drawer's 연락 기록 tab.
export function CandidateContactsPanel(props: CandidateContactsPanelProps) {
  return <Contacts key={props.candidate.id} {...props} />;
}
function Contacts({
  candidate,
  active = true,
  onCommitted,
  onDirtyChange,
}: CandidateContactsPanelProps) {
  const [mobile, setMobile] = useState(
    () => window.matchMedia?.("(max-width: 767px)").matches ?? false,
  );
  const [page, setPage] = useState(1);
  const [size, setSize] = useState<20 | 50 | 100>(mobile ? 20 : 50);
  const [data, setData] = useState<api.CandidateContactsPage>();
  const [mobileRows, setMobileRows] = useState<api.CandidateContact[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<unknown>();
  const [version, setVersion] = useState(0);
  const [open, setOpen] = useState(false);
  const [discard, setDiscard] = useState(false);
  const [status, setStatus] = useState(candidate.contactStatus);
  const [memo, setMemo] = useState("");
  const [draftRevision, setDraftRevision] = useState(candidate.revision);
  const [context, setContext] = useState(candidate);
  const [dirty, setDirty] = useState(false);
  const [pending, setPending] = useState(false);
  const [fields, setFields] = useState<Record<string, string>>({});
  const [saveError, setSaveError] = useState<unknown>();
  const [committed, setCommitted] = useState(false);
  const [refreshed, setRefreshed] = useState(false);
  const [success, setSuccess] = useState(false);
  const inFlight = useRef(false);
  const callbacks = useRef({ onCommitted, onDirtyChange });
  callbacks.current = { onCommitted, onDirtyChange };
  const statusId = useId(),
    memoId = useId();
  useEffect(() => {
    const media = window.matchMedia?.("(max-width: 767px)");
    if (!media) return;
    const change = () => {
      setMobile(media.matches);
      setSize(media.matches ? 20 : 50);
      setPage(1);
      setMobileRows([]);
    };
    media.addEventListener("change", change);
    return () => media.removeEventListener("change", change);
  }, []);
  useEffect(() => {
    if (!dirty && (!committed || refreshed)) {
      setContext(candidate);
      setStatus(candidate.contactStatus);
      setDraftRevision(candidate.revision);
    }
  }, [candidate, dirty, committed]);
  useEffect(() => {
    callbacks.current.onDirtyChange?.(dirty);
  }, [dirty]);
  useEffect(() => () => callbacks.current.onDirtyChange?.(false), []);
  useEffect(() => {
    if (!active) return;
    const controller = new AbortController();
    setLoading(true);
    setLoadError(undefined);
    const needsCommitRefresh = committed && !refreshed;
    void Promise.all([
      api.getCandidateContacts(candidate.id, page, size, controller.signal),
      needsCommitRefresh
        ? api.getCandidateContactContext(candidate.id, controller.signal)
        : Promise.resolve(undefined),
      needsCommitRefresh
        ? Promise.resolve().then(() => callbacks.current.onCommitted?.())
        : Promise.resolve(),
    ])
      .then(([result, current]) => {
        if (controller.signal.aborted) return;
        setData(result);
        setMobileRows((previous) =>
          mobile && page > 1
            ? [
                ...previous.filter(
                  (row) => !result.items.some((next) => next.sn === row.sn),
                ),
                ...result.items,
              ]
            : result.items,
        );
        if (current) {
          setContext(current);
          setDraftRevision(current.revision);
          setStatus(current.contactStatus);
        }
        if (needsCommitRefresh) setRefreshed(true);
      })
      .catch((error: unknown) => {
        if (!controller.signal.aborted) setLoadError(error);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
    // refreshed is deliberately not a trigger: a completed refresh must not issue another request.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [candidate.id, active, page, size, version, committed, mobile]);
  useEffect(() => {
    if (!success) return;
    const timer = window.setTimeout(() => setSuccess(false), 2000);
    return () => window.clearTimeout(timer);
  }, [success]);
  const begin = () => {
    if (committed && !refreshed) return;
    if (!dirty) {
      setStatus(context.contactStatus);
      setDraftRevision(context.revision);
    }
    setSaveError(undefined);
    setFields({});
    setOpen(true);
  };
  const save = async () => {
    if (inFlight.current) return;
    const text = memo.trim();
    if (!text || [...text].length > 200) {
      setFields({ memo: "연락 메모는 1~200자로 입력해 주세요." });
      return;
    }
    inFlight.current = true;
    setPending(true);
    setFields({});
    setSaveError(undefined);
    try {
      await api.createCandidateContact(candidate.id, {
        contactStatus: status,
        memo: text,
        expectedRevision: draftRevision,
      });
      setOpen(false);
      setDirty(false);
      setMemo("");
      setSuccess(true);
      setRefreshed(false);
      setPage(1);
      setMobileRows([]);
      setCommitted(true);
      setVersion((value) => value + 1);
    } catch (error) {
      setSaveError(error);
      if (error instanceof ApiError) setFields(error.fieldMessages);
    } finally {
      inFlight.current = false;
      setPending(false);
    }
  };
  const requestId =
    loadError instanceof ApiError ? loadError.body.requestId : undefined;
  return (
    <section
      role="tabpanel"
      aria-label="연락 기록"
      hidden={!active}
      data-screen-id="UI-33"
      data-testid="candidate-contacts-panel"
      className="candidate-contacts-panel"
    >
      <header className="candidate-contacts-heading">
        <h2>연락 기록 {data ? `(${data.total})` : ""}</h2>
        <button
          type="button"
          data-testid="candidate-contact-create"
          className="sicost-primary"
          disabled={pending || (committed && !refreshed)}
          onClick={begin}
        >
          연락 등록
        </button>
      </header>
      {success && <Toast message="연락을 저장했습니다." />}
      {loadError ? (
        <ErrorBanner
          message={
            committed && !refreshed
              ? "연락은 저장되었습니다. 다시 저장하지 말고 조회를 다시 시도해 주세요."
              : loadError instanceof Error
                ? loadError.message
                : "연락 기록을 불러오지 못했습니다."
          }
          requestId={requestId}
          onRetry={() => setVersion((value) => value + 1)}
        />
      ) : mobile ? (
        <div className="candidate-contact-cards" aria-busy={loading}>
          {loading && <p role="status">불러오는 중입니다.</p>}
          {!loading && !mobileRows.length && (
            <p className="sicost-empty">연락 기록이 없습니다.</p>
          )}
          {mobileRows.map((row) => (
            <article
              data-testid={`candidate-contact-${row.sn}`}
              key={row.sn}
              className="candidate-contact-card"
            >
              <div>
                <time>{row.contactDate}</time>{" "}
                <CandidateContactStatusBadge status={row.status} />
              </div>
              <p className="candidate-contact-memo">{row.memo}</p>
              <small>
                {row.actorName} ({row.actorId})
              </small>
            </article>
          ))}
          {data && mobileRows.length < data.total && (
            <button
              type="button"
              data-testid="candidate-contacts-more"
              disabled={loading}
              onClick={() => setPage((value) => value + 1)}
            >
              20개 더보기
            </button>
          )}
        </div>
      ) : (
        <DataTable
          rows={data?.items ?? []}
          columns={columns}
          rowKey={(row) => row.sn}
          loading={loading}
          empty="연락 기록이 없습니다."
          page={page}
          size={size}
          total={data?.total ?? 0}
          onPageChange={setPage}
          onSizeChange={(value) => {
            setPage(1);
            setSize(value);
          }}
        />
      )}
      <Dialog
        open={active && open}
        title="연락 등록"
        pending={pending}
        onConfirm={save}
        onCancel={() => setOpen(false)}
        confirmLabel="연락 저장"
      >
        <div
          data-testid="candidate-contact-form"
          className="candidate-contact-form"
        >
          <p>
            대상 {candidate.name} ({candidate.id})
          </p>
          <div className="sicost-field">
            <label htmlFor={statusId}>연락 상태 *</label>
            <select
              id={statusId}
              data-testid="candidate-contact-status"
              value={status}
              disabled={pending}
              aria-invalid={Boolean(fields.contactStatus)}
              onChange={(event) => {
                setStatus(event.target.value as api.CandidateContactStatus);
                setDirty(true);
              }}
            >
              {Object.entries(statuses).map(([code, value]) => (
                <option
                  data-testid={`candidate-contact-status-option-${code.toLowerCase()}`}
                  key={code}
                  value={code}
                >
                  {value.label}
                </option>
              ))}
            </select>
            {fields.contactStatus && (
              <p className="sicost-field-error">{fields.contactStatus}</p>
            )}
          </div>
          <div className="sicost-field">
            <label htmlFor={memoId}>연락 메모 *</label>
            <textarea
              id={memoId}
              data-testid="candidate-contact-memo"
              value={memo}
              disabled={pending}
              aria-invalid={Boolean(fields.memo)}
              aria-describedby={`${memoId}-help`}
              rows={5}
              onChange={(event) => {
                setMemo(event.target.value);
                setDirty(true);
              }}
            />
            <small id={`${memoId}-help`}>
              1~200자 · {[...memo.trim()].length}/200
            </small>
            {fields.memo && (
              <p role="alert" className="sicost-field-error">
                {fields.memo}
              </p>
            )}
          </div>
          <p>
            연락일은 서버 오늘로 저장됩니다. 같은 상태도 새 연락으로 기록됩니다.
          </p>
          {Boolean(saveError) && (
            <ErrorBanner
              message={
                saveError instanceof Error
                  ? saveError.message
                  : "저장하지 못했습니다. 입력한 내용은 유지됩니다."
              }
              requestId={
                saveError instanceof ApiError
                  ? saveError.body.requestId
                  : undefined
              }
            />
          )}
          {dirty && (
            <button
              type="button"
              data-testid="candidate-contact-leave"
              disabled={pending}
              onClick={() => setDiscard(true)}
            >
              입력을 버리고 나가기
            </button>
          )}
        </div>
      </Dialog>
      <ConfirmDialog
        open={active && discard}
        title="입력 내용 버리기"
        confirmLabel="버리고 나가기"
        onCancel={() => setDiscard(false)}
        onConfirm={() => {
          setDiscard(false);
          setOpen(false);
          setMemo("");
          setDirty(false);
          setFields({});
          setSaveError(undefined);
        }}
      >
        <p>작성한 연락 메모를 버리시겠습니까?</p>
      </ConfirmDialog>
    </section>
  );
}
