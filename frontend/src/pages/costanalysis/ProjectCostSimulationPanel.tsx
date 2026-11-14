import { useRef, useState } from "react";
import { ApiError } from "../../shared/api";
import { ErrorBanner, StatusBadge } from "../../shared/Feedback";
import * as api from "./projectCostApi";

export function ProjectCostSimulationPanel({
  detail,
  onReload,
  onDirtyChange,
}: {
  detail: api.Detail;
  onReload?(): void;
  onDirtyChange?(dirty: boolean): void;
}) {
  const defaults = detail.simulationDefaults;
  const initial = () => ({
    n: String(defaults.n),
    c: defaults.c ?? "",
    m: String(defaults.m),
    r: defaults.r ?? "",
  });
  const [fields, setFields] = useState(initial);
  const [result, setResult] = useState<api.SimulationResult>();
  const [error, setError] = useState<unknown>();
  const [violations, setViolations] = useState<Record<string, string>>({});
  const [pending, setPending] = useState(false);
  const inFlight = useRef(false);
  const monthly = detail.row.contractType !== "TK";
  const reset = () => {
    setFields(initial());
    setResult(undefined);
    setError(undefined);
    setViolations({});
    onDirtyChange?.(false);
  };
  async function submit() {
    if (inFlight.current || !defaults.enabled) return;
    const next: Record<string, string> = {};
    if (
      !/^-?\d+$/.test(fields.n) ||
      BigInt(fields.n) < -2147483648n ||
      BigInt(fields.n) > 2147483647n
    )
      next.n = "인원은 서버 정수 범위로 입력해 주세요.";
    if (!/^[1-9]\d*$/.test(fields.m) || BigInt(fields.m) > 2147483647n)
      next.m = "개월은 1 이상의 정수로 입력해 주세요.";
    for (const key of monthly ? (["c", "r"] as const) : (["c"] as const))
      if (!/^(0|[1-9]\d{0,14})$/.test(fields[key]))
        next[key] = "원 정수 0~999,999,999,999,999로 입력해 주세요.";
    setResult(undefined);
    setError(undefined);
    setViolations(next);
    if (Object.keys(next).length) return;
    inFlight.current = true;
    setPending(true);
    try {
      const value = await api.createProjectCostSimulation(detail.projectId, {
        month: detail.month,
        n: parseInt(fields.n, 10),
        c: fields.c,
        m: parseInt(fields.m, 10),
        ...(monthly ? { r: fields.r } : {}),
      });
      setResult(value);
      onDirtyChange?.(false);
    } catch (e) {
      setError(e);
      if (e instanceof ApiError) setViolations(e.fieldMessages);
    } finally {
      inFlight.current = false;
      setPending(false);
    }
  }
  return (
    <section data-testid="cost-simulation" className="cost-simulation">
      <h3>
        가상 마진 시뮬레이션 <StatusBadge tone="info">가상</StatusBadge>
      </h3>
      <p>저장되지 않음 · 계약/투입/급여/원가를 변경하지 않습니다.</p>
      {!defaults.enabled && (
        <p className="sicost-banner sicost-tone-lock">{defaults.reason}</p>
      )}
      {monthly && (
        <p className="sicost-banner sicost-tone-info">
          월단위·파견은 한 달 기준 · 적용개월(m)은 결과에 사용하지 않습니다.
        </p>
      )}
      <form
        onSubmit={(e) => {
          e.preventDefault();
          void submit();
        }}
      >
        <div className="cost-form-grid">
          {(
            [
              ["n", "인원 변화 (명, 빼려면 −)"],
              ["c", "1인 월 원가 (원)"],
              ["m", "적용 개월"],
              ...(monthly ? [["r", "1인 월 단가 (원)"]] : []),
            ] as [keyof typeof fields, string][]
          ).map(([key, label]) => (
            <label className="sicost-field" key={key}>
              {label}
              <input
                data-testid={`cost-simulation-${key}`}
                aria-invalid={!!violations[key]}
                disabled={pending || !defaults.enabled}
                value={fields[key]}
                onChange={(e) => {
                  setFields({ ...fields, [key]: e.target.value });
                  setResult(undefined);
                  onDirtyChange?.(true);
                }}
              />
              {violations[key] && (
                <span role="alert" className="sicost-field-error">
                  {violations[key]}
                </span>
              )}
            </label>
          ))}
        </div>
        <div className="cost-actions">
          <button
            className="sicost-primary"
            data-testid="cost-simulation-submit"
            disabled={pending || !defaults.enabled}
          >
            가상계산
          </button>
          <button
            type="button"
            data-testid="cost-simulation-reset"
            disabled={pending}
            onClick={reset}
          >
            초기값으로
          </button>
          <button
            type="button"
            data-testid="cost-simulation-cancel"
            disabled={pending}
            onClick={reset}
          >
            취소
          </button>
        </div>
      </form>
      {pending && <p role="status">가상계산 중입니다.</p>}
      {error != null && (
        <ErrorBanner
          message={
            error instanceof Error ? error.message : "가상계산에 실패했습니다."
          }
          requestId={
            error instanceof ApiError ? error.body.requestId : undefined
          }
          onRetry={onReload}
        />
      )}
      {result && (
        <section data-testid="cost-simulation-result" role="status">
          <dl>
            <dt>변경 전 마진</dt>
            <dd className={result.beforeMargin?.startsWith("-") ? "cost-negative" : undefined}>{result.beforeMargin}%</dd>
            <dt>변경 후 마진</dt>
            <dd className={result.afterMargin?.startsWith("-") ? "cost-negative" : undefined}>{result.afterMargin}%</dd>
            <dt>차이</dt>
            <dd className={result.differencePp?.startsWith("-") ? "cost-negative" : undefined}>{result.differencePp}%p</dd>
            <dt>변경 전 목표 대비</dt>
            <dd className={result.beforeTargetGap?.startsWith("-") ? "cost-negative" : undefined}>{result.beforeTargetGap ?? "–"}</dd>
            <dt>변경 후 목표 대비</dt>
            <dd className={result.afterTargetGap?.startsWith("-") ? "cost-negative" : undefined}>{result.afterTargetGap ?? "–"}</dd>
          </dl>
          <p>{result.message}</p>
        </section>
      )}
    </section>
  );
}
