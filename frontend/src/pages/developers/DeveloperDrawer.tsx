import { useCallback, useEffect, useState } from "react";
import { DetailDrawer } from "../../shared/DetailDrawer";
import { DataTable } from "../../shared/DataTable";
import { ErrorBanner, StatusBadge } from "../../shared/Feedback";
import { PeopleChangeHistoryTable } from "../apiops/PeopleChangeHistoryTable";
import { ApiError } from "../../shared/api";
import { ConfirmDialog } from "../../shared/Dialog";
import { DeveloperStatesPanel } from "../states/DeveloperStatesPanel";
import { getPeriodContext } from "../shared/sharedApi";
import { priceHelp } from "./DeveloperForm";
import * as api from "./peopleClient";
import { getSalesPartOptions, type PartOption } from "../salespartsops/partStandardsApi";

const won = (value: string | null | undefined) =>
  value == null ? value : value.replace(/\B(?=(\d{3})+(?!\d))/g, ",");

export function DeveloperDrawer({
  id,
  refreshKey = 0,
  label = (_group: string, value: string) => value,
  onClose,
  onEdit,
  onChanged,
  onDirtyChange,
}: {
  id: string;
  refreshKey?: number;
  /** 공통코드 표시명 찾기(목록과 같은 코드 응답을 쓴다). */
  label?(group: string, value: string): string;
  onClose(): void;
  onEdit(developer: api.DeveloperDetail): void;
  onChanged?(): void | Promise<void>;
  onDirtyChange?(dirty: boolean): void;
}) {
  const [data, setData] = useState<api.DeveloperDetail>(),
    [error, setError] = useState<unknown>(),
    [loading, setLoading] = useState(true);
  const [tab, setTab] = useState("기본 정보"),
    [prices, setPrices] = useState(false);
  const [dirty, setDirty] = useState(false);
  const [parts, setParts] = useState<PartOption[]>([]);
  useEffect(() => {
    let current = true;
    getSalesPartOptions()
      .then((r) => current && setParts(r.items))
      .catch(() => undefined);
    return () => {
      current = false;
    };
  }, []);
  const [leave, setLeave] = useState<() => void>();
  useEffect(() => {
    onDirtyChange?.(dirty);
    return () => onDirtyChange?.(false);
  }, [dirty, onDirtyChange]);
  function navigate(action: () => void) {
    if (dirty) setLeave(() => action);
    else action();
  }
  const load = useCallback(async () => {
    setLoading(true);
    setError(undefined);
    try {
      setData(await api.getDeveloper(id));
    } catch (e) {
      setError(e);
    } finally {
      setLoading(false);
    }
  }, [id]);
  useEffect(() => {
    void load();
  }, [load, refreshKey]);
  return (
    <DetailDrawer
      title={data?.name ?? "개발자 상세"}
      subtitle={data?.managementNo}
      testId="developer-drawer"
      onClose={() => navigate(onClose)}
      actions={
        data && (
          <button
            type="button"
            data-testid="developer-edit-button"
            onClick={() => navigate(() => onEdit(data))}
          >
            수정
          </button>
        )
      }
    >
      {loading && <p role="status">불러오는 중입니다.</p>}
      {error != null && (
        <ErrorBanner
          message={
            error instanceof Error
              ? error.message
              : "상세를 조회하지 못했습니다."
          }
          requestId={
            error instanceof ApiError ? error.body.requestId : undefined
          }
          onRetry={() => void load()}
        />
      )}
      {data && (
        <>
          <nav className="developer-tabs" role="tablist">
            {["기본 정보", "투입 이력", "근무 상태 이력", "변경 이력"].map(
              (name, i) => (
                <button
                  key={name}
                  type="button"
                  role="tab"
                  aria-selected={tab === name}
                  data-testid={`developer-tab-${i}`}
                  onClick={() => navigate(() => setTab(name))}
                >
                  {name}
                </button>
              ),
            )}
          </nav>
          <div role="tabpanel" data-testid="developer-detail-panel">
            {tab === "기본 정보" && (
              <>
                <dl className="developer-facts">
                  {Object.entries({
                    관리번호: data.managementNo,
                    직원구분: label("EMP_TYPE", data.employeeType),
                    소속: data.companyName ?? "자사",
                    "주 직무": data.job,
                    등급: data.grade,
                    직위: data.positionCode ? label("JBPS", data.positionCode) : null,
                    "직접/간접": label("DRCT_CLS", data.directClass),
                    "영업 파트": parts.find((p) => p.id === data.partId)?.name ?? data.partId,
                    입사일: data.hireDate,
                    "근무 상태": label("WORK_STAT", data.status),
                    "사용 여부": data.useYn === "Y" ? "사용" : "중지",
                    "현재 프로젝트": data.currentProjectLabel,
                    "철수 예정일": data.withdrawalDate,
                    "이번 달 MM": data.monthlyMm,
                    "월 단가": won(data.monthlyRate),
                    "월 급여·지급액": won(data.monthlyPay),
                    "보유 스킬": data.skills.join(", "),
                    거주지: data.homeAddress,
                  }).map(([key, value]) => (
                    <div
                      data-testid={`developer-fact-${Array.from(key, (c) => c.codePointAt(0)!.toString(16)).join("-")}`}
                      key={key}
                    >
                      <dt>{key}</dt>
                      <dd>{value ?? "–"}</dd>
                    </div>
                  ))}
                </dl>
                {data.retirementScheduled && (
                  <StatusBadge tone="warn">퇴사 예정</StatusBadge>
                )}
                <p className="sicost-banner sicost-tone-info">{priceHelp}</p>
                <button
                  type="button"
                  data-testid="developer-price-toggle"
                  aria-expanded={prices}
                  onClick={() => setPrices((v) => !v)}
                >
                  가격 이력 {prices ? "접기" : "펼치기"}
                </button>
                {prices && (
                  <DataTable
                    rows={data.priceHistory}
                    rowKey={(p) => p.effectiveMonth}
                    empty="가격 이력이 없습니다."
                    columns={[
                      {
                        id: "effectiveMonth",
                        label: "시작월",
                        render: (p) => p.effectiveMonth,
                      },
                      {
                        id: "endMonth",
                        label: "종료월",
                        render: (p) => p.endMonth ?? "–",
                      },
                      {
                        id: "rate",
                        label: "월 단가",
                        render: (p) => won(p.monthlyRate) ?? "–",
                      },
                      {
                        id: "pay",
                        label: "월 급여·지급액",
                        render: (p) => won(p.monthlyPay) ?? "–",
                      },
                    ]}
                  />
                )}
              </>
            )}
            {tab === "투입 이력" && <DeveloperAssignments id={id} />}
            {tab === "근무 상태 이력" && (
              <DeveloperStates
                id={id}
                onDirtyChange={setDirty}
                onChanged={async () => {
                  await load();
                  await onChanged?.();
                }}
              />
            )}
            {tab === "변경 이력" && (
              <PeopleChangeHistoryTable
                targetType="DEVELOPER"
                targetId={id}
                refreshKey={refreshKey}
              />
            )}
          </div>
        </>
      )}
      <ConfirmDialog
        open={Boolean(leave)}
        title="작성 중인 내용을 나갈까요?"
        confirmLabel="나가기"
        cancelLabel="계속 작성"
        onCancel={() => setLeave(undefined)}
        onConfirm={() => {
          setDirty(false);
          leave?.();
          setLeave(undefined);
        }}
      >
        저장하지 않은 내용은 사라집니다.
      </ConfirmDialog>
    </DetailDrawer>
  );
}
function DeveloperAssignments({ id }: { id: string }) {
  const [data, setData] =
      useState<Awaited<ReturnType<typeof api.getDeveloperAssignments>>>(),
    [page, setPage] = useState(1),
    [error, setError] = useState<unknown>(),
    [loading, setLoading] = useState(true),
    [retry, setRetry] = useState(0);
  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(undefined);
    void api
      .getDeveloperAssignments(id, page)
      .then((r) => {
        if (active) setData(r);
      })
      .catch((e) => {
        if (active) setError(e);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [id, page, retry]);
  return (
    <DataTable
      rows={data?.items ?? []}
      rowKey={(r) => r.id}
      loading={loading}
      error={error instanceof Error ? error.message : undefined}
      onRetry={() => setRetry((n) => n + 1)}
      page={page}
      size={50}
      total={data?.total ?? 0}
      onPageChange={setPage}
      columns={[
        { id: "project", label: "프로젝트", render: (r) => r.projectName },
        { id: "job", label: "직무", render: (r) => r.jobName },
        { id: "start", label: "시작일", render: (r) => r.startDate },
        { id: "end", label: "종료일", render: (r) => r.endDate },
        { id: "mm", label: "계약 MM", render: (r) => r.contractMm },
        { id: "status", label: "투입 상태", render: (r) => r.assignmentStatus },
      ]}
    />
  );
}
function DeveloperStates({
  id,
  onChanged,
  onDirtyChange,
}: {
  id: string;
  onChanged(): Promise<void>;
  onDirtyChange(dirty: boolean): void;
}) {
  const [currentDate, setCurrentDate] = useState<string>(),
    [error, setError] = useState<unknown>(),
    [retry, setRetry] = useState(0);
  useEffect(() => {
    let active = true;
    setError(undefined);
    void getPeriodContext()
      .then((r) => {
        if (active) setCurrentDate(r.currentDate);
      })
      .catch((e) => {
        if (active) setError(e);
      });
    return () => {
      active = false;
    };
  }, [id, retry]);
  return (
    <>
      {error != null && (
        <ErrorBanner
          message={
            error instanceof Error
              ? error.message
              : "기준일을 조회하지 못했습니다."
          }
          onRetry={() => setRetry((n) => n + 1)}
        />
      )}
      {!currentDate && !error && <p role="status">불러오는 중입니다.</p>}
      {currentDate && (
        <DeveloperStatesPanel
          developerId={id}
          currentDate={currentDate}
          onChanged={onChanged}
          onDirtyChange={onDirtyChange}
        />
      )}
    </>
  );
}
