import { useEffect, useState } from "react";
import { ErrorBanner } from "../../shared/Feedback";
import { usePreferences } from "../me/usePreferences";
import { ColumnPreferencesDialog } from "../me/ColumnPreferencesDialog";
import type {
  ScreenColumnPreferences,
  PeriodPreference,
} from "../me/preferencesApi";
import {
  getPeriodContext,
  type PeriodContext,
  type PeriodValue,
} from "../shared/sharedApi";
import { projectColumns } from "./projectFormat";
import { ProjectsScreen, type ProjectsRegistration } from "./ProjectsScreen";

export function ProjectsWorkspace({
  onDirtyChange,
  registration,
}: {
  onDirtyChange?(dirty: boolean): void;
  registration?: ProjectsRegistration;
}) {
  const preferences = usePreferences();
  const [context, setContext] = useState<PeriodContext>(),
    [selectedYear, setSelectedYear] = useState<number>();
  const [columns, setColumns] = useState(false),
    [error, setError] = useState<unknown>(),
    [retry, setRetry] = useState(0);
  useEffect(() => {
    let current = true;
    void getPeriodContext()
      .then((c) => {
        if (current) {
          setContext(c);
          setError(undefined);
        }
      })
      .catch((e) => {
        if (current) setError(e);
      });
    return () => {
      current = false;
    };
  }, [retry]);
  useEffect(() => {
    const changed = (event: Event) => {
      const detail = (
        event as CustomEvent<{
          screenId: string;
          value: PeriodValue;
          context: PeriodContext;
        }>
      ).detail;
      if (detail.screenId === "UI-01" && detail.value.type === "YEAR") {
        setContext(detail.context);
        setSelectedYear(Number(detail.value.value));
      }
    };
    window.addEventListener("sicost:screen-period-changed", changed);
    return () =>
      window.removeEventListener("sicost:screen-period-changed", changed);
  }, []);
  const stored = preferences.get("PERIOD", "UI-01")?.value as
    | PeriodPreference
    | undefined;
  const currentYear = context
    ? Number(context.currentMonth.slice(0, 4))
    : undefined;
  const rawYear =
    selectedYear ??
    (stored?.type === "YEAR" ? Number(stored.value) : currentYear);
  const year =
    context && rawYear !== undefined
      ? Math.max(
          Number(context.cashBaseMonth.slice(0, 4)),
          Math.min(rawYear, Number(context.currentMonth.slice(0, 4)) + 1),
        )
      : undefined;
  const size = preferences.get("PAGE_SIZE")?.value,
    unit = preferences.get("AMT_UNIT")?.value;
  const amountUnit = unit === "원" || unit === "백만원" ? unit : "천원";
  const dense = preferences.get("DENSE")?.value === "좁게";
  const view = (
    preferences.get("COLS", "UI-01")?.value as
      | ScreenColumnPreferences
      | undefined
  )?.projects;
  async function setting(action: () => Promise<void>) {
    try {
      await action();
      setError(undefined);
    } catch (e) {
      setError(e);
    }
  }
  return (
    <>
      {error != null && (
        <ErrorBanner
          message={
            error instanceof Error
              ? error.message
              : "화면 설정을 불러오지 못했습니다."
          }
          onRetry={() => setRetry((n) => n + 1)}
        />
      )}
      {!context ||
      year === undefined ||
      currentYear === undefined ||
      preferences.loading ? (
        <p role="status">프로젝트 기간과 설정을 불러오는 중입니다.</p>
      ) : (
        <ProjectsScreen
          year={year}
          currentYear={currentYear}
          currentDate={context.currentDate}
          registration={registration}
          onDirtyChange={onDirtyChange}
          columnView={view}
          dense={dense}
          pageSize={size === 20 || size === 100 ? size : 50}
          amountUnit={amountUnit}
          onColumnsChange={() => setColumns(true)}
          onDensityChange={() =>
            void setting(() =>
              preferences.put("DENSE", "*", dense ? "넓게" : "좁게"),
            )
          }
          onPageSizeChange={(size) =>
            setting(() => preferences.put("PAGE_SIZE", "*", size))
          }
          onAmountUnitChange={(unit) =>
            setting(() => preferences.put("AMT_UNIT", "*", unit))
          }
        />
      )}
      {columns && (
        <ColumnPreferencesDialog
          open
          screenId="UI-01"
          viewId="projects"
          columns={projectColumns}
          onClose={() => setColumns(false)}
        />
      )}
    </>
  );
}
