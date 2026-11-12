import { useEffect, useRef, useState } from "react";
import { DetailDrawer } from "../../shared/DetailDrawer";
import { DataTable, type Column } from "../../shared/DataTable";
import { ErrorBanner, StatusBadge } from "../../shared/Feedback";
import { Dialog } from "../../shared/Dialog";
import { ChangeHistoryTable } from "../apiops/ChangeHistoryTable";
import { ClientForm } from "./ClientForm";
import * as api from "./clientsApi";
import type {
  Client,
  ClientAssignment,
  ClientAssignmentsPage,
  ClientSaveResult,
} from "./clientsApi";

type Props = {
  active?: boolean;
  id: string;
  initialTab?: "basic" | "assignments";
  onClose(): void;
  onSaved(result?: ClientSaveResult): void | Promise<void>;
  onBasisChanged(date: string): void;
  onDirtyChange?(dirty: boolean): void;
};
const assignmentColumns: Column<ClientAssignment>[] = [
  { id: "projectName", label: "프로젝트", render: (row) => row.projectName },
  { id: "managementNo", label: "관리번호", render: (row) => row.managementNo },
  { id: "developerName", label: "개발자", render: (row) => row.developerName },
  { id: "jobName", label: "직무", render: (row) => row.jobName },
  {
    id: "inputBeginDate",
    label: "투입 시작일",
    render: (row) => row.inputBeginDate,
  },
  {
    id: "inputEndDate",
    label: "투입 종료일",
    render: (row) => row.inputEndDate,
  },
];
const clientTypeLabel: Record<string, string> = {
  CUST: "고객사",
  VNDR: "협력사",
  BOTH: "고객·협력",
};
export function ClientDrawer(props: Props) {
  return <ClientDrawerContent key={props.id} {...props} />;
}
function ClientDrawerContent({
  active = true,
  id,
  initialTab = "basic",
  onClose,
  onSaved,
  onBasisChanged,
  onDirtyChange,
}: Props) {
  const [client, setClient] = useState<Client>(),
    [error, setError] = useState<unknown>(),
    [loading, setLoading] = useState(true);
  const [tab, setTab] = useState<"basic" | "assignments" | "history">(
      initialTab,
    ),
    [editing, setEditing] = useState(false);
  const [assignments, setAssignments] = useState<ClientAssignmentsPage>(),
    [page, setPage] = useState(1),
    [assignLoading, setAssignLoading] = useState(false),
    [assignError, setAssignError] = useState<unknown>();
  const [number, setNumber] = useState<string | null>(),
    [statusConfirm, setStatusConfirm] = useState(false),
    [pending, setPending] = useState(false);
  const [leaving, setLeaving] = useState(false);
  const dirty = useRef(false),
    busy = useRef(false),
    accountGeneration = useRef(0);
  const closeButton = useRef<HTMLButtonElement>(null);
  async function load() {
    setLoading(true);
    setError(undefined);
    try {
      const result = await api.getClient(id);
      setClient(result);
      onBasisChanged(result.basisDate);
    } catch (e) {
      setError(e);
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => {
    void load();
    closeButton.current?.focus();
    return () => {
      accountGeneration.current++;
    };
  }, [id]);
  useEffect(() => {
    setNumber(undefined);
    accountGeneration.current++;
  }, [tab, editing, active]);
  async function loadAssignments(signal?: AbortSignal) {
    setAssignLoading(true);
    setAssignError(undefined);
    try {
      const result = await api.getClientAssignments(id, page, signal);
      if (!signal?.aborted) {
        setAssignments(result);
        onBasisChanged(result.basisDate);
      }
    } catch (e) {
      if (!signal?.aborted) setAssignError(e);
    } finally {
      if (!signal?.aborted) setAssignLoading(false);
    }
  }
  useEffect(() => {
    if (tab !== "assignments") return;
    const controller = new AbortController();
    void loadAssignments(controller.signal);
    return () => controller.abort();
  }, [tab, page, id]);
  function close() {
    if (pending) return;
    if (dirty.current) setLeaving(true);
    else onClose();
  }
  async function reveal() {
    if (busy.current) return;
    busy.current = true;
    setPending(true);
    setError(undefined);
    const generation = accountGeneration.current;
    try {
      const result = await api.getClientAccount(id);
      if (generation === accountGeneration.current)
        setNumber(result.accountNumber);
    } catch (e) {
      setError(e);
    } finally {
      busy.current = false;
      setPending(false);
    }
  }
  async function changeStatus() {
    if (!client || busy.current) return;
    busy.current = true;
    setPending(true);
    setError(undefined);
    try {
      const result = await api.setClientStatus(client);
      const item = result.items.find((row) => row.id === client.id);
      if (!item || item.result === "rejected")
        throw new Error(
          item?.error?.message ?? "상태 변경 결과를 확인하지 못했습니다.",
        );
      setStatusConfirm(false);
      api.notifyClientsCommitted(id);
      await onSaved();
      await load();
    } catch (e) {
      setError(e);
      setStatusConfirm(false);
    } finally {
      busy.current = false;
      setPending(false);
    }
  }
  if (!active) return null;
  return (
    <DetailDrawer
      label="거래처 상세"
      title={client?.name ?? "거래처 상세"}
      subtitle={
        client
          ? `${client.id} · ${clientTypeLabel[client.type] ?? client.type}`
          : undefined
      }
      testId="client-drawer"
      closeTestId="client-drawer-close"
      pending={pending}
      onClose={close}
      actions={
        client && !editing ? (
          <>
            <button
              type="button"
              data-testid="client-edit"
              onClick={() => {
                setNumber(undefined);
                accountGeneration.current++;
                setEditing(true);
              }}
            >
              수정
            </button>
            <button
              type="button"
              className={client.useYn === "Y" ? "clients-stop-button" : ""}
              data-testid="client-status"
              onClick={() => setStatusConfirm(true)}
            >
              {client.useYn === "Y" ? "중지" : "재개"}
            </button>
          </>
        ) : undefined
      }
    >
      {error != null && (
        <ErrorBanner
          message={
            error instanceof Error
              ? error.message
              : "거래처를 불러오지 못했습니다."
          }
          onRetry={() => void load()}
        />
      )}
      {loading && <p role="status">불러오는 중입니다.</p>}
      {client &&
        (editing ? (
          <ClientForm
            client={client}
            onClose={() => {
              setEditing(false);
              dirty.current = false;
              onDirtyChange?.(false);
            }}
            onDirtyChange={(value) => {
              dirty.current = value;
              onDirtyChange?.(value);
            }}
            onSaved={async (result) => {
              dirty.current = false;
              onDirtyChange?.(false);
              setEditing(false);
              setClient(result.client);
              await onSaved(result);
            }}
          />
        ) : (
          <>
            <nav
              className="clients-tabs"
              aria-label="거래처 상세 탭"
              role="tablist"
            >
              {(
                [
                  ["basic", "기본 정보"],
                  ["assignments", "투입 내역"],
                  ["history", `변경 이력 (${client.historyCount})`],
                ] as const
              ).map(([key, label]) => (
                <button
                  type="button"
                  role="tab"
                  aria-selected={tab === key}
                  key={key}
                  data-testid={`client-tab-${key}`}
                  onClick={() => {
                    setNumber(undefined);
                    accountGeneration.current++;
                    setTab(key);
                  }}
                >
                  {label}
                </button>
              ))}
            </nav>
            <section role="tabpanel" data-testid={`client-panel-${tab}`}>
              {tab === "basic" && (
                <>
                  <p className="clients-status-line">
                    <StatusBadge tone={client.useYn === "Y" ? "ok" : "lock"}>
                      {client.useYn === "Y" ? "사용" : "중지"}
                    </StatusBadge>
                  </p>
                  <dl className="clients-details">
                    {[
                      ["사업자번호", client.businessNo],
                      ["대표자", client.ceo],
                      [
                        "업종",
                        `${client.industry ?? "–"}${client.industryActive === false ? " (중지)" : ""}`,
                      ],
                      ["담당자", client.contactName],
                      ["연락처", client.phone],
                      ["세금계산서 이메일", client.taxEmail],
                      [
                        "지급 계좌",
                        `${client.account ?? "–"}${client.bankActive === false ? " (중지)" : ""}`,
                      ],
                      ["예금주", client.depositor],
                    ].map(([label, value]) => (
                      <div
                        data-testid={`client-detail-field-${["사업자번호", "대표자", "업종", "담당자", "연락처", "세금계산서 이메일", "지급 계좌", "예금주"].indexOf(label ?? "")}`}
                        key={label}
                      >
                        <dt>{label}</dt>
                        <dd>
                          {label === "지급 계좌" && client.account ? (
                            <span className="clients-account-row">
                              {number === undefined ? (
                                <span>{value}</span>
                              ) : (
                                <output data-testid="client-account-number">
                                  {`${(client.account ?? "").split(" ")[0]} ${number}`}
                                </output>
                              )}
                              {number === undefined ? (
                                <button
                                  type="button"
                                  className="clients-account-toggle"
                                  data-testid="client-account-show"
                                  disabled={pending}
                                  onClick={() => void reveal()}
                                >
                                  계좌 전체보기
                                </button>
                              ) : (
                                <button
                                  type="button"
                                  className="clients-account-toggle"
                                  data-testid="client-account-hide"
                                  onClick={() => {
                                    accountGeneration.current++;
                                    setNumber(undefined);
                                  }}
                                >
                                  숨기기
                                </button>
                              )}
                            </span>
                          ) : (
                            (value ?? "–")
                          )}
                        </dd>
                      </div>
                    ))}
                  </dl>
                  <p>집계 기준일 {client.basisDate}</p>
                </>
              )}
              {tab === "assignments" && (
                <>
                  {assignError != null ? (
                    <ErrorBanner
                      message={
                        assignError instanceof Error
                          ? assignError.message
                          : "투입 내역을 불러오지 못했습니다."
                      }
                      onRetry={() => void loadAssignments()}
                    />
                  ) : (
                    <>
                      <p>
                        집계 기준일 {assignments?.basisDate ?? client.basisDate}
                      </p>
                      <DataTable
                        rows={assignments?.items ?? []}
                        columns={assignmentColumns}
                        rowKey={(row) => row.assignmentId}
                        loading={assignLoading}
                        empty="오늘 기준 투입 내역이 없습니다."
                        page={page}
                        size={50}
                        total={assignments?.total ?? 0}
                        onPageChange={setPage}
                      />
                    </>
                  )}
                </>
              )}
              {tab === "history" && (
                <ChangeHistoryTable
                  targetType="CLIENT"
                  targetId={id}
                  refreshKey={client.revision}
                />
              )}
            </section>
          </>
        ))}
      <Dialog
        open={statusConfirm}
        title={client?.useYn === "Y" ? "거래처 중지" : "거래처 재개"}
        pending={pending}
        onCancel={() => setStatusConfirm(false)}
        onConfirm={changeStatus}
      >
        <p>
          {client?.name} 거래처를 {client?.useYn === "Y" ? "중지" : "재개"}
          하시겠습니까?
        </p>
      </Dialog>
      <Dialog
        open={leaving}
        title="저장하지 않은 내용이 있습니다."
        cancelLabel="계속 작성"
        confirmLabel="나가기"
        onCancel={() => setLeaving(false)}
        onConfirm={onClose}
      >
        <p>저장하지 않은 내용이 있습니다.</p>
      </Dialog>
    </DetailDrawer>
  );
}
