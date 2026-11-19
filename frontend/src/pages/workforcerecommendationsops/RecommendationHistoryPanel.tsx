import { FieldHelp } from "../../shared/FieldHelp";
import { useEffect, useRef, useState } from "react";
import { FilterBar } from "../../shared/FilterBar";
import { DetailDrawer } from "../../shared/DetailDrawer";
import { ApiError } from "../../shared/api";
import * as api from "./recommendationBrowseApi";
import {
  RecommendationBrowseError,
  RecommendationSavedValues,
} from "./RecommendationSavedValues";
import "./recommendationBrowse.css";

export function RecommendationHistoryPanel({ onClose }: { onClose(): void }) {
  const [draft, setDraft] = useState({
    targetType: "" as "" | api.TargetType,
    targetId: "",
    job: "",
    recommendationType: "" as "" | "AI" | "NEXT",
  });
  const [query, setQuery] = useState<api.RecommendationHistoryQuery>({
    page: 1,
    size: 50,
  });
  const [data, setData] = useState<api.RecommendationHistory>();
  const [loading, setLoading] = useState(true),
    [error, setError] = useState<unknown>();
  const [fields, setFields] = useState<Record<string, string>>({});
  const [selected, setSelected] = useState<string>(),
    [detail, setDetail] = useState<api.RecommendationSnapshot>();
  const [detailError, setDetailError] = useState<unknown>(),
    [detailLoading, setDetailLoading] = useState(false);
  const [retry, setRetry] = useState(0),
    [detailRetry, setDetailRetry] = useState(0);
  const listGeneration = useRef(0),
    detailGeneration = useRef(0);
  useEffect(() => {
    const current = ++listGeneration.current,
      controller = new AbortController();
    setLoading(true);
    setError(undefined);
    setData(undefined);
    void api
      .getWorkforceRecommendationHistory(query, controller.signal)
      .then((value) => {
        if (current === listGeneration.current && !controller.signal.aborted)
          setData(value);
      })
      .catch((e) => {
        if (current === listGeneration.current && !controller.signal.aborted) {
          setError(e);
          if (e instanceof ApiError) setFields(e.fieldMessages);
        }
      })
      .finally(() => {
        if (current === listGeneration.current && !controller.signal.aborted)
          setLoading(false);
      });
    return () => controller.abort();
  }, [query, retry]);
  useEffect(() => {
    if (!selected) return;
    const current = ++detailGeneration.current,
      controller = new AbortController();
    setDetail(undefined);
    setDetailError(undefined);
    setDetailLoading(true);
    void api
      .getRecommendationHistorySnapshot(selected, controller.signal)
      .then((value) => {
        if (current === detailGeneration.current && !controller.signal.aborted)
          setDetail(value);
      })
      .catch((e) => {
        if (current === detailGeneration.current && !controller.signal.aborted)
          setDetailError(e);
      })
      .finally(() => {
        if (current === detailGeneration.current && !controller.signal.aborted)
          setDetailLoading(false);
      });
    return () => controller.abort();
  }, [selected, detailRetry]);
  const apply = () => {
    const next: Record<string, string> = {};
    if (Boolean(draft.targetType) !== Boolean(draft.targetId.trim()))
      next.targetId = "대상 유형과 대상 ID를 함께 지정해 주세요.";
    if (draft.targetId.trim().length > 20)
      next.targetId = "대상 ID는 20자 이내로 입력해 주세요.";
    if (draft.job.trim().length > 10)
      next.job = "직무는 10자 이내로 입력해 주세요.";
    setFields(next);
    if (Object.keys(next).length) return;
    setSelected(undefined);
    setDetail(undefined);
    setQuery({
      ...(query.sort ? { sort: query.sort, direction: query.direction } : {}),
      page: 1,
      size: query.size,
      ...(draft.targetType
        ? { targetType: draft.targetType, targetId: draft.targetId.trim() }
        : {}),
      ...(draft.job.trim() ? { job: draft.job.trim() } : {}),
      ...(draft.recommendationType
        ? { recommendationType: draft.recommendationType }
        : {}),
    });
  };
  return (
    <DetailDrawer
      title="추천 기록"
      onClose={onClose}
      testId="recommendation-history-drawer"
      closeTestId="recommendation-history-close"
    >
      <section
        className="recommendation-history"
        data-testid="recommendation-history-panel"
        data-screen-id="SCR-UI-01-B138-HISTORY SCR-UI-19-B140-HISTORY-OPEN SCR-UI-19-B140-HISTORY-PAGE SCR-UI-19-B140-HISTORY-CLOSE"
        aria-busy={loading}
      >
        <FilterBar
          testId="history-filters"
          searchTestId="history-search"
          resetTestId="history-reset"
          disabled={loading}
          onSubmit={apply}
          onReset={() => {
            setDraft({ targetType: "", targetId: "", job: "", recommendationType: "" });
            setFields({});
            setSelected(undefined);
            setDetail(undefined);
            setQuery({ page: 1, size: query.size });
          }}
        >
          <label className="sicost-field">
            대상 유형
            <select
              data-testid="history-target-type"
              value={draft.targetType}
              onChange={(e) =>
                setDraft({
                  ...draft,
                  targetType: e.target.value as typeof draft.targetType,
                })
              }
            >
              <option value="">전체</option>
              <option value="OPPORTUNITY">기회</option>
              <option value="PROJECT">프로젝트</option>
            </select>
          </label>
          <label className="sicost-field">
            대상 ID
            <input
              data-testid="history-target-id"
              value={draft.targetId}
              aria-invalid={Boolean(fields.targetId)}
              onChange={(e) => setDraft({ ...draft, targetId: e.target.value })}
            />
            {fields.targetId && (
              <span role="alert" className="sicost-field-error">
                {fields.targetId}
              </span>
            )}
          </label>
          <label className="sicost-field">
            직무
            <FieldHelp label="직무" /><input
              data-testid="history-job"
              value={draft.job}
              aria-invalid={Boolean(fields.job)}
              onChange={(e) => setDraft({ ...draft, job: e.target.value })}
            />
            {fields.job && (
              <span role="alert" className="sicost-field-error">
                {fields.job}
              </span>
            )}
          </label>
          <label className="sicost-field">
            추천 유형
            <select
              data-testid="history-kind"
              value={draft.recommendationType}
              onChange={(e) =>
                setDraft({
                  ...draft,
                  recommendationType: e.target
                    .value as typeof draft.recommendationType,
                })
              }
            >
              <option value="">전체</option>
              <option value="AI">AI</option>
              <option value="NEXT">NEXT</option>
            </select>
          </label>
        </FilterBar>
        <RecommendationBrowseError
          error={error}
          retry={() => setRetry((n) => n + 1)}
        />
        {loading && <p role="status">기록 조회 중</p>}
        {!loading && !error && data && (
          <>
            {data.items.length === 0 ? (
              <div className="recommendation-empty">추천 기록이 없습니다.
                <button type="button" onClick={() => {
                  setDraft({ targetType: "", targetId: "", job: "", recommendationType: "" });
                  setFields({});
                  setSelected(undefined);
                  setDetail(undefined);
                  setQuery({ page: 1, size: query.size });
                }}>조건 초기화</button>
              </div>
            ) : (
              <div className="sicost-table-viewport">
                <table className="sicost-table">
                  <thead>
                    <tr>
                      {([
                        ["rcmdId", "회차"], ["recommendationType", "유형"], ["targetId", "대상"],
                        ["jobCode", "직무"], ["startMonth", "시작월"], ["requestedGradeCode", "요청등급"],
                        ["executedAt", "실행시각"], ["executedBy", "사용자"], ["candidateCount", "후보 수"],
                      ] as const).map(([id, label]) => (
                        <th key={id} aria-sort={query.sort === id ? query.direction === "DESC" ? "descending" : "ascending" : "none"}>
                          <button type="button" onClick={() => setQuery((old) => ({ ...old, sort: id, direction: old.sort === id && old.direction !== "DESC" ? "DESC" : "ASC", page: 1 }))}>
                            {label}{query.sort === id ? query.direction === "DESC" ? " ▼" : " ▲" : ""}
                          </button>
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {data.items.map((row) => (
                      <tr
                        key={row.rcmdId}
                        data-testid={`history-round-${row.rcmdId}`}
                      >
                        <td>
                          <button
                            type="button"
                            data-testid={`history-open-${row.rcmdId}`}
                            aria-pressed={selected === row.rcmdId}
                            onClick={() => setSelected(row.rcmdId)}
                          >
                            {row.rcmdId}
                          </button>
                        </td>
                        <td>{row.recommendationType}</td>
                        <td>{row.targetId}</td>
                        <td>{row.jobCode}</td>
                        <td>{row.startMonth}</td>
                        <td>{row.requestedGradeCode}</td>
                        <td>{row.executedAt}</td>
                        <td>{row.executedBy}</td>
                        <td className="sicost-decimal">{row.candidateCount}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
            <nav
              className="recommendation-pagination"
              aria-label="추천 기록 페이지"
            >
              <span>전체 {data.totalCount}건</span>
              <button
                type="button"
                data-testid="history-previous"
                disabled={query.page <= 1}
                onClick={() => setQuery({ ...query, page: query.page - 1 })}
              >
                이전
              </button>
              <span>{data.page}</span>
              <button
                type="button"
                data-testid="history-next"
                disabled={query.page * query.size >= data.totalCount}
                onClick={() => setQuery({ ...query, page: query.page + 1 })}
              >
                다음
              </button>
              <label>
                페이지 크기
                <select
                  data-testid="history-size"
                  value={query.size}
                  onChange={(e) =>
                    setQuery({
                      ...query,
                      page: 1,
                      size: Number(e.target.value),
                    })
                  }
                >
                  {[20, 50, 100].map((size) => (
                    <option key={size} value={size}>
                      {size}
                    </option>
                  ))}
                </select>
              </label>
            </nav>
          </>
        )}
        {selected && (
          <section data-testid="history-detail" aria-busy={detailLoading}>
            {detailLoading && <p role="status">저장된 추천을 조회 중입니다.</p>}
            <RecommendationBrowseError
              error={detailError}
              retry={() => setDetailRetry((n) => n + 1)}
            />
            {!detailLoading && !detailError && detail && (
              <RecommendationSavedValues value={detail} />
            )}
          </section>
        )}
      </section>
    </DetailDrawer>
  );
}
