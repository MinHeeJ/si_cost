import { useId, useState } from "react";
import { Link } from "react-router-dom";
import { ApiError } from "../../shared/api";
import { BusinessName } from "../../shared/BusinessName";
import { useCodeNames } from "../../shared/codeNames";
import { DetailDrawer } from "../../shared/DetailDrawer";
import { ErrorBanner, StatusBadge } from "../../shared/Feedback";
import {
  formatAmount,
  formatFixedDecimal,
  formatRate,
  formatShortDateTime,
} from "../../shared/format";
import { ScreenLoading } from "../../shared/ScreenLoading";
import { SegmentedTabList } from "../../shared/SegmentedTabs";
import { DetailDescription } from "../../shared/Surface";
import { TentativeAssignmentsPanel } from "../tentativeassignmentsops/TentativeAssignmentsPanel";
import {
  OpportunityRejectionNotice,
  SalesOpportunityActions,
  stageNames,
} from "./SalesOpportunityActions";
import { SalesQuotePanel } from "./SalesQuotePanel";
import type { Opportunity } from "./salesCommandsApi";

const tabs = [
  ["base", "기본 정보"],
  ["needs", "필요 인력"],
  ["quote", "견적 점검/승인 이력"],
  ["manage", "기회 관리"],
] as const;

export function SalesOpportunityDetail({
  detail,
  error,
  initialTab = "base",
  onClose,
  onEdit,
  onRetry,
  refresh,
}: {
  detail?: Opportunity;
  error: unknown;
  initialTab?: "base" | "quote";
  onClose(): void;
  onEdit(detail: Opportunity): void;
  onRetry(): void;
  refresh(): Promise<void>;
}) {
  const [tab, setTab] = useState<(typeof tabs)[number][0]>(initialTab);
  const [approvalTarget, setApprovalTarget] = useState<HTMLDivElement | null>(
    null,
  );
  const id = useId();
  const name = useCodeNames(
    "JOB",
    "GRADE",
    "OPP_STAT",
    "LOSE_RSN",
    "CTRT_TYPE",
    "APRV_ACT",
    "APRV_STAT",
  );
  return (
    <DetailDrawer
      title={detail?.name ?? "영업 기회 상세"}
      subtitle={
        detail && (
          <>
            {detail.id}
            <StatusBadge
              tone={
                detail.status === "WIN"
                  ? "ok"
                  : detail.status === "STOP"
                    ? "lock"
                    : "neutral"
              }
            >
              {detail.status === "ING"
                ? stageNames[detail.stage]
                : name("OPP_STAT", detail.status)}
            </StatusBadge>
            {detail.approvalStatus !== "NONE" && (
              <button
                type="button"
                className={`sales-command-badge sales-command-${detail.approvalStatus.toLowerCase()} sales-opportunity-approval-link`}
                onClick={() => {
                  setTab("quote");
                  document.getElementById(`${id}-tab-quote`)?.focus();
                }}
              >
                {name("APRV_STAT", detail.approvalStatus)}
              </button>
            )}
          </>
        )
      }
      testId="opportunity-detail-drawer"
      onClose={onClose}
      actions={
        detail?.status === "ING" && (
          <button
            type="button"
            data-testid="opportunity-edit-button"
            onClick={() => onEdit(detail)}
          >
            수정
          </button>
        )
      }
      feedback={
        error != null && (
          <ErrorBanner
            title="영업 기회 상세 조회 실패"
            message={
              error instanceof Error
                ? error.message
                : "상세를 불러오지 못했습니다."
            }
            requestId={
              error instanceof ApiError ? error.body.requestId : undefined
            }
            retryTestId="detail-retry-button"
            onRetry={onRetry}
          />
        )
      }
    >
      {!detail && error == null && (
        <ScreenLoading message="상세를 불러오는 중입니다." />
      )}
      {detail && (
        <div className="sales-opportunity-detail">
          <SegmentedTabList
            as="nav"
            role="tablist"
            aria-label="기회 상세 탭"
            className="sales-opportunity-detail-tabs"
          >
            {tabs.map(([key, label]) => (
              <button
                type="button"
                role="tab"
                key={key}
                id={`${id}-tab-${key}`}
                aria-controls={`${id}-panel-${key}`}
                aria-selected={tab === key}
                tabIndex={tab === key ? 0 : -1}
                data-testid={`detail-tab-${key}`}
                onClick={() => setTab(key)}
              >
                {label}
              </button>
            ))}
          </SegmentedTabList>
          <OpportunityRejectionNotice opportunity={detail} />
          {tabs.map(([key]) => (
            <section
              key={key}
              role="tabpanel"
              id={`${id}-panel-${key}`}
              aria-labelledby={`${id}-tab-${key}`}
              hidden={tab !== key}
              className="sales-opportunity-detail-panel"
              data-testid={tab === key ? "opportunity-detail-panel" : undefined}
            >
              {key === "manage" && (
                <SalesOpportunityActions
                  opportunity={detail}
                  refresh={refresh}
                  approvalTarget={approvalTarget}
                  showRejection={false}
                />
              )}
              {key === "base" && tab === key && (
                <>
                  <DetailDescription>
                    <dt>고객사</dt>
                    <dd>
                      <BusinessName kind="client" id={detail.customerId} />
                    </dd>
                    <dt>영업 파트</dt>
                    <dd>
                      <BusinessName kind="part" id={detail.partId} />
                    </dd>
                    <dt>계약 유형</dt>
                    <dd>
                      {detail.contractType
                        ? name("CTRT_TYPE", detail.contractType)
                        : "–"}
                    </dd>
                    <dt>수주 확률</dt>
                    <dd>
                      {formatRate(detail.probability)} ·{" "}
                      {detail.manualProbability ? "수동" : "기본"}
                    </dd>
                    <dt>예상 금액</dt>
                    <dd>{formatAmount(detail.amount, "원")}</dd>
                    <dt>시작 예정월</dt>
                    <dd>{detail.startMonth || "–"}</dd>
                    <dt>예상 기간</dt>
                    <dd>
                      {detail.months == null ? "–" : `${detail.months}개월`}
                    </dd>
                    <dt>다음 할 일</dt>
                    <dd>{detail.nextTask || "–"}</dd>
                    <dt>기한</dt>
                    <dd>{detail.nextDue || "–"}</dd>
                    <dt>근무지</dt>
                    <dd>{detail.workplace || "–"}</dd>
                    <dt>비고</dt>
                    <dd className="sales-opportunity-detail-note">
                      {detail.memo || "–"}
                    </dd>
                    {detail.resultDate && (
                      <>
                        <dt>처리일</dt>
                        <dd>{detail.resultDate}</dd>
                      </>
                    )}
                    {detail.projectId && (
                      <>
                        <dt>수주 프로젝트</dt>
                        <dd>
                          <Link
                            className="sicost-text-link"
                            to={`/UI-01?${new URLSearchParams({ projectId: detail.projectId })}`}
                          >
                            <BusinessName
                              kind="project"
                              id={detail.projectId}
                            />
                          </Link>
                        </dd>
                      </>
                    )}
                    {detail.lossReasonCode && (
                      <>
                        <dt>실주 사유</dt>
                        <dd>
                          {name("LOSE_RSN", detail.lossReasonCode)}
                          {detail.lossMemo && ` · ${detail.lossMemo}`}
                        </dd>
                      </>
                    )}
                  </DetailDescription>
                </>
              )}
              {key === "needs" && tab === key && (
                <>
                  <h3>필요 인력</h3>
                  <div className="sicost-table-viewport">
                    <table
                      className="sicost-table"
                      data-testid="opportunity-needs-table"
                    >
                      <thead>
                        <tr>
                          {[
                            "직무",
                            "등급",
                            "인원",
                            "시작월",
                            "개월",
                            "월 MM",
                            "예상 단가 (원)",
                          ].map((label) => (
                            <th scope="col" key={label}>
                              {label}
                            </th>
                          ))}
                        </tr>
                      </thead>
                      <tbody>
                        {detail.needs.length === 0 && (
                          <tr>
                            <td colSpan={7}>필요 인력이 없습니다.</td>
                          </tr>
                        )}
                        {detail.needs.map((n, i) => (
                          <tr
                            data-testid={`detail-need-${n.needSn ?? i}`}
                            key={n.needSn ?? i}
                          >
                            <td>{name("JOB", n.jobCode)}</td>
                            <td>
                              {n.gradeCode
                                ? name("GRADE", n.gradeCode)
                                : "등급 미지정"}
                            </td>
                            <td>{n.count}</td>
                            <td>{n.startMonth}</td>
                            <td>{n.months}</td>
                            <td>{formatFixedDecimal(n.monthlyMm, 2)}</td>
                            <td>
                              {n.expectedRate == null
                                ? "직무 기본단가"
                                : formatAmount(n.expectedRate, "원")}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                  <TentativeAssignmentsPanel
                    items={detail.tentativeAssignments.filter(
                      (t) => t.status === "ACT",
                    )}
                    needs={detail.needs}
                    revision={detail.revision}
                    opportunityStatus={detail.status}
                    onRefresh={refresh}
                  />
                </>
              )}
              {key === "quote" && (
                <>
                  {tab === key && (
                    <SalesQuotePanel
                      key={detail.revision}
                      needs={detail.needs}
                      initial={detail.quote}
                    />
                  )}
                  <div
                    className="sales-opportunity-approval"
                    ref={setApprovalTarget}
                  />
                  <h3>승인 이력</h3>
                  <div className="sicost-table-viewport">
                    <table
                      className="sicost-table sales-opportunity-history"
                      aria-label="승인 이력"
                    >
                      <thead>
                        <tr>
                          {[
                            "처리 일시",
                            "처리",
                            "이익률",
                            "사유",
                            "처리자",
                          ].map((label) => (
                            <th scope="col" key={label}>
                              {label}
                            </th>
                          ))}
                        </tr>
                      </thead>
                      <tbody>
                        {detail.approvalHistory.length === 0 && (
                          <tr>
                            <td colSpan={5}>승인 이력이 없습니다.</td>
                          </tr>
                        )}
                        {detail.approvalHistory.map((entry) => (
                          <tr
                            data-testid={`approval-history-${entry.histSn}`}
                            key={entry.histSn}
                          >
                            <td>{formatShortDateTime(entry.registeredAt)}</td>
                            <td>{name("APRV_ACT", entry.action)}</td>
                            <td>{formatRate(entry.profitRate)}</td>
                            <td>{entry.reason || "–"}</td>
                            <td>{entry.registeredBy}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </>
              )}
            </section>
          ))}
        </div>
      )}
    </DetailDrawer>
  );
}
