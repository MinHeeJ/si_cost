import { useEffect, useRef, useState } from "react";
import { ApiError } from "../../shared/api";
import { OPERATING_STANDARDS_COMMITTED } from "../operatingstandards/operatingStandardsApi";
import { ErrorBanner, StatusBadge } from "../../shared/Feedback";
import { PeriodSelector } from "./PeriodSelector";
import { clampPeriod, screenPeriods } from "./periods";
import {
  getMonthClose,
  getPeriodContext,
  getSharedPreferences,
  putSharedPreference,
  type PeriodContext,
  type PeriodValue,
  type SharedPreference,
} from "./sharedApi";
export function ScreenPeriodBar({
  screenId,
  controlled,
}: {
  screenId: string;
  controlled?: {
    month: string;
    currentMonth: string;
    onChange(month: string): void;
    disabled?: boolean;
  };
}) {
  const type = screenPeriods[screenId] ?? "NONE";
  const [context, setContext] = useState<PeriodContext | null>(null),
    [value, setValue] = useState<PeriodValue | null>(null);
  const [preference, setPreference] = useState<SharedPreference | undefined>(),
    [error, setError] = useState(""),
    [status, setStatus] = useState("");
  const [pending, setPending] = useState(false),
    [loading, setLoading] = useState(true),
    [reload, setReload] = useState(0);
  const saving = useRef(false),
    closeRead = useRef(0);
  useEffect(() => {
    if (type === "NONE" || controlled) return;
    const refresh = (event: Event) => {
      if ((event as CustomEvent<{ cash: boolean }>).detail?.cash)
        setReload((n) => n + 1);
    };
    window.addEventListener(OPERATING_STANDARDS_COMMITTED, refresh);
    return () =>
      window.removeEventListener(OPERATING_STANDARDS_COMMITTED, refresh);
  }, [type]);
  const ranges = (ctx: PeriodContext) => {
    const currentYear = ctx.currentMonth.slice(0, 4),
      startYear = ctx.cashBaseMonth.slice(0, 4);
    return type === "YEAR_MONTH"
      ? { min: ctx.cashBaseMonth, max: ctx.currentMonth }
      : {
          min:
            type === "YEAR_CUM" &&
            ["UI-07", "UI-08", "UI-09"].includes(screenId)
              ? String(Number(startYear) - 1)
              : startYear,
          max: type === "YEAR" ? String(Number(currentYear) + 1) : currentYear,
        };
  };
  const cumulative = (
    year: string,
    month: string,
    ctx: PeriodContext,
  ): PeriodValue => ({
    type: "YEAR_CUM",
    year,
    month: clampPeriod(
      `${year}-${month.slice(5)}`,
      `${year}-01`,
      year === ctx.currentMonth.slice(0, 4) ? ctx.currentMonth : `${year}-12`,
    ),
  });
  const validStored = (
    stored: unknown,
    ctx: PeriodContext,
  ): stored is PeriodValue => {
    if (!stored || typeof stored !== "object") return false;
    const item = stored as PeriodValue,
      range = ranges(ctx);
    if (item.type !== type) return false;
    if (item.type === "YEAR_CUM")
      return (
        typeof item.year === "string" &&
        typeof item.month === "string" &&
        /^\d{4}-(0[1-9]|1[0-2])$/.test(item.month) &&
        item.year >= range.min &&
        item.year <= range.max &&
        item.month.slice(0, 4) === item.year &&
        item.month >= `${item.year}-01` &&
        item.month <=
          (item.year === ctx.currentMonth.slice(0, 4)
            ? ctx.currentMonth
            : `${item.year}-12`)
      );
    return (
      typeof item.value === "string" &&
      (type === "YEAR" ? /^\d{4}$/ : /^\d{4}-(0[1-9]|1[0-2])$/).test(
        item.value,
      ) &&
      item.value >= range.min &&
      item.value <= range.max
    );
  };
  useEffect(() => {
    if (type === "NONE" || controlled) return;
    let live = true;
    setLoading(true);
    setError("");
    void Promise.all([getPeriodContext(), getSharedPreferences()])
      .then(([ctx, prefs]) => {
        if (!live) return;
        setContext(ctx);
        const pref = prefs.items.find(
          (item) => item.type === "PERIOD" && item.key === screenId,
        );
        setPreference(pref);
        const range = ranges(ctx),
          initial = clampPeriod(
            type === "YEAR_MONTH"
              ? ctx.currentMonth
              : ctx.currentMonth.slice(0, 4),
            range.min,
            range.max,
          );
        const fallback: PeriodValue =
          type === "YEAR_CUM"
            ? cumulative(initial, ctx.currentMonth, ctx)
            : { type: type as "YEAR" | "YEAR_MONTH", value: initial };
        const valid = validStored(pref?.value, ctx);
        setValue(valid ? (pref!.value as PeriodValue) : fallback);
        if (pref?.error) setError(pref.error.message);
        else if (pref?.stored && !valid)
          setError(
            "저장된 기간이 현재 허용 범위를 벗어나 기본 기간을 표시합니다. 저장된 설정은 변경하지 않았습니다.",
          );
      })
      .catch((e: unknown) => {
        if (live)
          setError(
            e instanceof Error ? e.message : "기간을 불러오지 못했습니다.",
          );
      })
      .finally(() => {
        if (live) setLoading(false);
      });
    return () => {
      live = false;
    };
  }, [screenId, type, reload]);
  useEffect(() => {
    if (controlled || !context || !value || type === "NONE") return;
    window.dispatchEvent(
      new CustomEvent("sicost:screen-period-changed", {
        detail: { screenId, value, context },
      }),
    );
    const read = ++closeRead.current;
    if (value.type === "YEAR") {
      setStatus("");
      return;
    }
    const month = value.type === "YEAR_CUM" ? value.month : value.value;
    setStatus("");
    void getMonthClose(month, month)
      .then((result) => {
        if (read === closeRead.current)
          setStatus(result.items[0]?.status ?? "");
      })
      .catch((e: unknown) => {
        if (read === closeRead.current)
          setError(e instanceof Error ? e.message : "마감 상태 조회 실패");
      });
    return () => {
      closeRead.current++;
    };
  }, [context, value, type]);
  if (controlled)
    return (
      <section
        className="sicost-period-bar"
        aria-label="화면 기간"
        data-testid="financial-controlled-period"
      >
        <label>
          선택월{" "}
          <input
            data-testid="financial-period-input"
            aria-label="선택월"
            type="month"
            min="0001-01"
            max={controlled.currentMonth}
            value={controlled.month}
            disabled={controlled.disabled}
            onChange={(event) => {
              if (
                /^(?!0000)\d{4}-(0[1-9]|1[0-2])$/.test(event.target.value) &&
                event.target.value <= controlled.currentMonth
              )
                controlled.onChange(event.target.value);
            }}
          />
        </label>
      </section>
    );
  if (type === "NONE") return null;
  if (loading) return <p role="status">기간을 불러오는 중입니다.</p>;
  if (!context || !value)
    return (
      <ErrorBanner message={error} onRetry={() => setReload((n) => n + 1)} />
    );
  const range = ranges(context),
    unavailable =
      range.min > range.max || context.cashBaseMonth > context.currentMonth;
  const year = value.type === "YEAR_CUM" ? value.year : value.value.slice(0, 4),
    thisYear = context.currentMonth.slice(0, 4);
  const save = async (next: PeriodValue, confirmed = false) => {
    if (saving.current || unavailable || !validStored(next, context)) return;
    if (
      !confirmed &&
      !window.dispatchEvent(
        new CustomEvent("sicost:screen-period-changing", {
          cancelable: true,
          detail: {
            screenId,
            value: next,
            proceed: () => void save(next, true),
          },
        }),
      )
    )
      return;
    saving.current = true;
    setPending(true);
    setError("");
    try {
      const result = await putSharedPreference(
        "PERIOD",
        screenId,
        next,
        preference?.revision ?? null,
      );
      setPreference(result.preference);
      setValue(next);
    } catch (e) {
      setError(
        e instanceof ApiError
          ? `${e.message} 문의 번호: ${e.body.requestId}`
          : e instanceof Error
            ? e.message
            : "기간 저장 실패",
      );
    } finally {
      saving.current = false;
      setPending(false);
    }
  };
  return (
    <section
      data-screen-id={`SCR-${screenId}-PERIOD-LOAD`}
      className="sicost-period-bar"
      aria-label="화면 기간"
    >
      <div
        data-screen-id={`SCR-${screenId}-PERIOD-CHANGE`}
        className="sicost-period-controls"
      >
        {value.type === "YEAR_CUM" ? (
          <>
            <label>
              누계 연도{" "}
              <select
                aria-label="누계 연도"
                value={value.year}
                disabled={pending || unavailable}
                onChange={(event) =>
                  void save(
                    cumulative(event.target.value, value.month, context),
                  )
                }
              >
                {Array.from(
                  {
                    length: Math.max(
                      0,
                      Number(range.max) - Number(range.min) + 1,
                    ),
                  },
                  (_, index) => String(Number(range.min) + index),
                ).map((option) => (
                  <option key={option}>{option}</option>
                ))}
              </select>
            </label>
            <PeriodSelector
              type="YEAR_MONTH"
              label="누계 월"
              value={value.month}
              min={`${value.year}-01`}
              max={
                value.year === thisYear
                  ? context.currentMonth
                  : `${value.year}-12`
              }
              disabled={pending || unavailable}
              onChange={(month) => void save({ ...value, month })}
            />
            <span>1~{Number(value.month.slice(5))}월 누계</span>
          </>
        ) : (
          <PeriodSelector
            type={value.type}
            value={value.value}
            min={range.min}
            max={range.max}
            disabled={pending || unavailable}
            onChange={(next) => void save({ ...value, value: next })}
          />
        )}
        {unavailable ? (
          <StatusBadge tone="warn">운영 시작 전</StatusBadge>
        ) : value.type === "YEAR" ? (
          <StatusBadge
            tone={year < thisYear ? "lock" : year > thisYear ? "info" : "ok"}
          >
            {year < thisYear
              ? "지난 해·읽기 전용"
              : year > thisYear
                ? "내년·계획"
                : "올해"}
          </StatusBadge>
        ) : (
          status && (
            <StatusBadge tone={status === "작업중" ? "info" : "lock"}>
              {status}
            </StatusBadge>
          )
        )}
      </div>
      {error && (
        <ErrorBanner message={error} onRetry={() => setReload((n) => n + 1)} />
      )}
    </section>
  );
}
