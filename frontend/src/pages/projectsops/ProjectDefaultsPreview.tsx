import { useEffect, useState } from "react";
import { ErrorBanner } from "../../shared/Feedback";
import { ApiError } from "../../shared/api";
import { formatDecimal } from "../../shared/format";
import * as api from "./projectStatusApi";

export function ProjectDefaultsPreview({
  contractAmount,
  contractType,
  onDefaults,
}: {
  contractAmount: string;
  contractType: api.ProjectValue["contractType"];
  onDefaults?(value: api.ProjectDefaults): void;
}) {
  const [data, setData] = useState<api.ProjectDefaults>();
  const [error, setError] = useState<unknown>();
  const [loading, setLoading] = useState(false);
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    let current = true;
    setLoading(true);
    setError(undefined);
    setData(undefined);
    void api
      .getProjectDefaults(contractAmount, contractType)
      .then((value) => {
        if (current) {
          setData(value);
          onDefaults?.(value);
        }
      })
      .catch((e) => {
        if (current) setError(e);
      })
      .finally(() => {
        if (current) setLoading(false);
      });
    return () => {
      current = false;
    };
  }, [contractAmount, contractType, retry, onDefaults]);
  return (
    <section
      data-testid="project-defaults-preview"
      data-screen-id="SCR-UI-01"
      aria-label="서버 기본금액 미리보기"
    >
      <h3>기본금액 미리보기</h3>
      {loading && <p role="status">불러오는 중입니다.</p>}
      {Boolean(error) && (
        <ErrorBanner
          message={
            error instanceof Error
              ? error.message
              : "기본금액을 조회하지 못했습니다."
          }
          requestId={
            error instanceof ApiError ? error.body.requestId : undefined
          }
          onRetry={() => setRetry((n) => n + 1)}
        />
      )}
      {data && (
        <>
          <p>
            기준월 {data.basisMonth} · 최종 저장 시 서버 기준으로 계산합니다.
          </p>
          <dl>
            <dt>자동 예산 (원)</dt>
            <dd>
              {data.budgetAmount === null
                ? "수기 입력 필요"
                : formatDecimal(data.budgetAmount)}
            </dd>
            <dt>목표이익률 (%)</dt>
            <dd>{data.targetMarginRate ?? "–"}</dd>
            <dt>팀비 기본금액 (원)</dt>
            <dd>
              {data.teamExpenseAmount === null
                ? "수기 입력 필요"
                : formatDecimal(data.teamExpenseAmount)}
            </dd>
            <dt>경비 기본금액 (원)</dt>
            <dd>
              {data.projectExpenseAmount === null
                ? "수기 입력 필요"
                : formatDecimal(data.projectExpenseAmount)}
            </dd>
          </dl>
          {data.warnings.map((warning) => (
            <p key={warning} className="sicost-banner sicost-tone-warn">
              {warning}
            </p>
          ))}
        </>
      )}
    </section>
  );
}

export function ProjectOptionSelect({
  purpose,
  value,
  onChange,
  disabled = false,
}: {
  purpose: "ASSIGNMENT" | "BILLING";
  value: string;
  onChange(id: string): void;
  disabled?: boolean;
}) {
  const [q, setQ] = useState("");
  const [items, setItems] = useState<api.ProjectOption[]>([]);
  const [error, setError] = useState<unknown>();
  const [loading, setLoading] = useState(true);
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    let current = true;
    setLoading(true);
    setError(undefined);
    void api
      .getProjectOptions(purpose, q)
      .then((result) => {
        if (current) setItems(result.items);
      })
      .catch((e) => {
        if (current) {
          setError(e);
          setItems([]);
        }
      })
      .finally(() => {
        if (current) setLoading(false);
      });
    return () => {
      current = false;
    };
  }, [purpose, q, retry]);
  return (
    <section
      data-testid="project-options"
      data-screen-id="SCR-UI-01"
      aria-label="프로젝트 선택"
    >
      <label>
        프로젝트 검색
        <input
          data-testid="project-options-search"
          value={q}
          maxLength={100}
          onChange={(e) => setQ(e.target.value)}
          disabled={disabled}
        />
      </label>
      {loading && <p role="status">불러오는 중입니다.</p>}
      {Boolean(error) && (
        <ErrorBanner
          message={
            error instanceof Error
              ? error.message
              : "프로젝트를 조회하지 못했습니다."
          }
          requestId={
            error instanceof ApiError ? error.body.requestId : undefined
          }
          onRetry={() => setRetry((n) => n + 1)}
        />
      )}
      {!loading && !error && !items.length && (
        <p className="sicost-empty">
          선택 가능한 진행·수주예정 프로젝트가 없습니다.
        </p>
      )}
      <label>
        프로젝트
        <select
          data-testid="project-options-select"
          value={value}
          disabled={disabled || loading || Boolean(error)}
          onChange={(e) => onChange(e.target.value)}
        >
          <option value="">선택</option>
          {items.map((item) => (
            <option key={item.id} value={item.id}>
              {item.id} · {item.name} ·{" "}
              {item.status === "PRE" ? "수주예정" : "진행"}
            </option>
          ))}
        </select>
      </label>
    </section>
  );
}
