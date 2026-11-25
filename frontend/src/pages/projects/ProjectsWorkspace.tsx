import { ScreenLoading } from "../../shared/ScreenLoading";
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

export const projectScreenGuide = (
  <dl>
    <div>
      <dt>프로젝트 조회</dt>
      <dd>
        연도를 선택하고 계약유형, 상태, 영업 파트, 프로젝트명 또는 고객사로
        검색할 수 있습니다. [영업파트 미지정] 버튼을 누르면 영업 파트가
        지정되지 않은 프로젝트를 조회합니다.
      </dd>
    </div>
    <div>
      <dt>등록과 상세 관리</dt>
      <dd>
        [등록]에서 프로젝트를 한 건씩 추가할 수 있습니다. 목록에서 프로젝트명을
        누르면 기본 정보, 진행 현황, 이슈, 인원 계획, 투입 인력, 계약 변경
        이력을 확인할 수 있습니다.
      </dd>
    </div>
    <div>
      <dt>엑셀 신규 등록</dt>
      <dd>
        [엑셀 업로드]를 열어 [양식 내려받기]를 누르세요. 첫 시트의 열 제목을
        유지하고, 프로젝트명·계약유형·시작일·종료일을 입력하세요. 외부
        프로젝트에는 고객사·영업 파트·계약금액도 필요합니다.
        엑셀은 새 프로젝트를 등록할 때 사용하며 기존 프로젝트는 상세에서 수정합니다.
      </dd>
    </div>
    <div>
      <dt>엑셀 입력 형식</dt>
      <dd>
        날짜는 2026-12-01 형식으로, 금액은 원 단위 정수로 입력하세요.
        계약유형은 ‘턴키’처럼 화면에 표시되는 이름을 쓰세요. 프로젝트ID를
        비우면 등록할 때 자동 발급됩니다. 검사를 마친 뒤 [전량 반영]을 누르세요.
      </dd>
    </div>
  </dl>
);

export function ProjectsWorkspace({
  active = true,
  onDirtyChange,
  registration,
}: {
  active?: boolean;
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
  const size = preferences.get("PAGE_SIZE")?.value;
  // UI-01은 목록·상세·배분표 모두 원 단위 콤마(SFR-006-01 「그 외 화면은 원」, 사용자 결정 2026-11-19).
  const amountUnit = "원" as const;
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
        <ScreenLoading message="프로젝트 기간과 설정을 불러오는 중입니다." />
      ) : (
        <ProjectsScreen
          active={active}
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
