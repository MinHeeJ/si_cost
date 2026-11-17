import { useCallback, useEffect, useState } from "react";
import { Dialog } from "../../shared/Dialog";
import { useCommitRefresh } from "../../shared/useCommitRefresh";
import { AssignmentCreateForm } from "../assignmentsops/AssignmentCreateForm";
import { AssignmentError } from "../assignmentsops/AssignmentDialogs";
import type { Page } from "../assignmentsops/assignmentActionsApi";
import * as api from "./assignmentCandidatesApi";

export function AssignmentCandidatesPanel({
  year,
  today,
  readOnly = false,
  onConfirmed,
  onChanged,
  onDirtyChange,
}: {
  year: number;
  today: string;
  readOnly?: boolean;
  onConfirmed(id: string): Promise<void>;
  onChanged(): Promise<void>;
  onDirtyChange?(dirty: boolean): void;
}) {
  const [data, setData] = useState<Page<api.AssignmentCandidate>>();
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>();
  const [confirming, setConfirming] = useState<api.AssignmentCandidate>();
  const [dropping, setDropping] = useState<api.AssignmentCandidate>();
  const [notice, setNotice] = useState("");
  const { commit, pending, message } = useCommitRefresh(
    "저장됐지만 다시 불러오지 못했습니다",
  );
  const load = useCallback(async () => {
    setLoading(true);
    try {
      const result = await api.getAssignmentCandidates(page);
      setData(result);
      setError(undefined);
    } catch (e) {
      setError(e);
      throw e;
    } finally {
      setLoading(false);
    }
  }, [page]);
  useEffect(() => {
    if (readOnly) return;
    void load().catch(() => undefined);
    const refresh = () => {
      void load().catch(() => undefined);
    };
    window.addEventListener(api.SALES_COMMITTED, refresh);
    return () => window.removeEventListener(api.SALES_COMMITTED, refresh);
  }, [load, readOnly]);
  if (readOnly) return null;
  const refresh = async () => {
    await Promise.all([load(), onChanged()]);
  };
  return (
    <section
      data-testid="assignment-candidates-panel"
      className="assignment-form"
      aria-busy={loading}
    >
      <h2>배정 후보{data ? ` (${data.total})` : ""}</h2>
      <p className="sicost-banner sicost-tone-info">
        수주한 기회의 배정 후보입니다. 연도 필터와 별개로 전체 후보를
        표시합니다.
      </p>
      <AssignmentError
        error={error}
        retry={() => {
          void load().catch(() => undefined);
        }}
      />
      {(notice || message) && <p role="status">{notice || message}</p>}
      {loading ? (
        <p role="status">배정 후보를 불러오는 중입니다.</p>
      ) : (
        !error &&
        data && (
          <>
            {data.items.length === 0 ? (
              <p>배정 후보가 없습니다.</p>
            ) : (
              <div className="sicost-table-viewport">
                <table className="sicost-table">
                  <thead>
                    <tr>
                      {[
                        "프로젝트",
                        "사람",
                        "직무",
                        "예정기간",
                        "상태",
                        "행동",
                      ].map((label) => (
                        <th
                          key={label}
                          data-testid={`assignment-candidate-header-${Array.from(label, (c) => c.codePointAt(0)!.toString(16)).join("-")}`}
                        >
                          {label}
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {data.items.map((row) => (
                      <tr
                        key={row.id}
                        data-testid={`assignment-candidate-${row.id}`}
                      >
                        <td>{row.projectName}</td>
                        <td>{row.developerName}</td>
                        <td>{row.jobCode}</td>
                        <td>
                          {row.startDate} ~ {row.endDate}
                        </td>
                        <td>
                          <span className="sicost-badge sicost-tone-info">
                            CNVT
                          </span>
                        </td>
                        <td>
                          <button
                            type="button"
                            data-testid={`candidate-confirm-${row.id}`}
                            disabled={pending}
                            onClick={() => {
                              setNotice("");
                              setConfirming(row);
                            }}
                          >
                            확정
                          </button>
                          <button
                            type="button"
                            data-testid={`candidate-drop-${row.id}`}
                            disabled={pending}
                            onClick={() => {
                              setNotice("");
                              setDropping(row);
                            }}
                          >
                            빼기
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
            <nav aria-label="배정 후보 페이지">
              <button
                type="button"
                data-testid="candidate-previous"
                disabled={page <= 1 || loading}
                onClick={() => setPage((p) => p - 1)}
              >
                이전
              </button>
              <span>{page}</span>
              <button
                type="button"
                data-testid="candidate-next"
                disabled={page * data.size >= data.total || loading}
                onClick={() => setPage((p) => p + 1)}
              >
                다음
              </button>
            </nav>
          </>
        )
      )}
      {confirming && (
        <div
          data-screen-id="SCR-UI-19-B140-CONFIRM-OPEN SCR-UI-19-B140-CONFIRM-SUBMIT SCR-UI-19-B140-CONFIRM-CANCEL"
          data-testid="candidate-confirmation-panel"
        >
          <AssignmentCreateForm
            year={year}
            today={today}
            onDirtyChange={onDirtyChange}
            initialValues={{
              year,
              projectId: confirming.projectId,
              developerId: confirming.developerId,
              jobCode: confirming.jobCode,
              startDate: confirming.startDate,
              endDate: confirming.endDate,
              contractMm: confirming.contractMm,
              inputType: "NEW",
              contractDeveloperId: null,
            }}
            submitAssignment={(values) =>
              api.confirmAssignmentCandidate(confirming, values)
            }
            onClose={() => setConfirming(undefined)}
            onSaved={async () => undefined}
            onCandidateSaved={async (id) => {
              try {
                await refresh();
                await onConfirmed(id);
                setNotice("배정 후보를 확정했습니다.");
              } catch {
                setNotice("저장됐지만 다시 불러오지 못했습니다");
              }
            }}
          />
        </div>
      )}
      <Dialog
        open={Boolean(dropping)}
        title="배정 후보 빼기"
        confirmLabel="빼기"
        pending={pending}
        onCancel={() => setDropping(undefined)}
        onConfirm={async () => {
          if (!dropping) return;
          try {
            await commit(async () => {
              await api.dropAssignmentCandidate(dropping);
              setDropping(undefined);
            }, refresh);
          } catch (e) {
            setError(e);
          }
        }}
      >
        <div data-testid="candidate-drop-dialog">
          <p>
            {dropping?.developerName} 후보를 제외하시겠습니까? 실제 배정은
            생성하지 않습니다.
          </p>
          <AssignmentError error={error} />
        </div>
      </Dialog>
    </section>
  );
}
