import { HelpTooltip } from "../../shared/HelpTooltip";
import { helpContent } from "../../shared/helpContent";
import { useState } from "react";
import { PeriodStatusTarget } from "../../shared/PeriodStatus";
import { useScreenMotion } from "../../shared/useScreenMotion";
import type { ReactNode } from "react";
import { ScreenTitleActionTarget } from "../../shared/ScreenTitleActions";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { ScreenPeriodBar } from "./ScreenPeriodBar";
import { useSharedScreen } from "./SharedScreenContext";
import { Icon } from "../../shared/Icon";
import type { AnalysisScope } from "./analysisClient";
import { usePermission } from "../../shared/permission/PermissionBoundary";
export function SharedScreenFrame({
  screenId,
  children,
  periodBar,
  initialMonth,
  headerActions,
}: {
  screenId: string;
  children: ReactNode;
  periodBar?: ReactNode;
  initialMonth?: string;
  headerActions?: ReactNode;
}) {
  const motionRoot = useScreenMotion(screenId);
  const [statusTarget,setStatusTarget]=useState<HTMLDivElement|null>(null);
  const location = useLocation();
  const canUpdate = usePermission(screenId, "UPD");
  const incoming = location.state as {
    fromDashboard?: boolean;
    fromAnalysis?: boolean;
    month?: string;
    scope?: AnalysisScope;
    guidance?: string;
  } | null;
  const analysisEntry =
    location.pathname === `/${screenId}` && incoming?.fromAnalysis
      ? incoming
      : undefined;
  const dashboardMonth =
    location.pathname === `/${screenId}` &&
    incoming?.fromDashboard &&
    ["UI-01", "UI-05", "UI-14", "UI-18", "UI-21", "UI-28"].includes(screenId)
      ? incoming.month
      : undefined;
  const { menu, menus, favorites, star, pending } = useSharedScreen(screenId),
    navigate = useNavigate();
  const parent = menus.find((node) => node.menuId === menu?.parentId);
  if (!menu) return <div role="alert">사용 가능한 메뉴가 없습니다.</div>;
  return (
    <ScreenTitleActionTarget.Provider value><PeriodStatusTarget.Provider value={statusTarget}>
      <section
        ref={motionRoot}
        data-screen-id={`SCR-${screenId}`}
        className={`sicost-screen${screenId === "UI-27" ? " sicost-workspace-screen" : ""}`}
      >
        <div
          className="sicost-titlebar"
          data-screen-id={`SCR-${screenId}-ENTRY`}
        >
          <div>
            <p className="sicost-breadcrumb">
              {parent?.name}
              {parent && " › "}
              {menu.name}
            </p>
            <div className="sicost-title-row">
              <h1>{menu.name}</h1>
              <button
                data-screen-id={`SCR-${screenId}-STAR`}
                className={
                  favorites.includes(screenId)
                    ? "sicost-star selected"
                    : "sicost-star"
                }
                type="button"
                disabled={pending}
                aria-label={
                  favorites.includes(screenId)
                    ? "즐겨찾기 해제"
                    : "즐겨찾기 추가"
                }
                aria-pressed={favorites.includes(screenId)}
                onClick={() => star(screenId)}
              >
                <Icon
                  name="star"
                  size={18}
                  fill={favorites.includes(screenId) ? "currentColor" : "none"}
                />
              </button>
              <small>{screenId}</small>
            </div>
          </div>
          {headerActions}
        </div>
        {menu.pcOnly && (
          <div className="sicost-pc-notice" role="note">
            <h2>PC에서 이용해 주세요.</h2>
            <p>이 화면은 PC에서 사용할 수 있습니다.</p>
            <button
              data-screen-id={`SCR-${screenId}-PC-BACK`}
              onClick={() => navigate(-1)}
            >
              뒤로
            </button>
            <Link data-screen-id={`SCR-${screenId}-PC-HOME`} to="/UI-16">
              대시보드
            </Link>
          </div>
        )}
        <div
          className={`sicost-screen-content${menu.pcOnly ? " sicost-pc-body" : ""}`}
        >
          <div className="sicost-period-row">{periodBar === undefined ? (
            <ScreenPeriodBar
              screenId={screenId}
              initialMonth={
                analysisEntry?.month ?? initialMonth ?? dashboardMonth
              }
            />
          ) : (
            periodBar
          )}
          <div className="sicost-period-status-slot" ref={setStatusTarget} /></div>
          {["UI-05","UI-11"].includes(screenId) && <HelpTooltip label="안내">{helpContent.filter(e=>e.screen===screenId && e.kind==="C").map((e,i)=><p key={i}>{e.text}</p>)}</HelpTooltip>}
          {analysisEntry?.scope && (
            <p className="sicost-banner sicost-tone-info" role="note">
              분석에서 연 원천 화면 · {analysisEntry.scope.fromMonth} ~{" "}
              {analysisEntry.scope.toMonth} ·{" "}
              {analysisEntry.scope.targets
                .filter((t) => t.targetType !== "FUNCTION")
                .map((t) => t.displayName)
                .join(" · ")}{" "}
              {analysisEntry.scope.filters
                .map((f) => `${f.key} ${f.operator} ${f.values.join(", ")}`)
                .join(" · ")}
              <br />
              {analysisEntry.guidance} · 원래 화면의 조회 단위가 다르면 월별
              원장과 위 분석 범위를 대조해 주세요.
            </p>
          )}
          {!canUpdate &&
            [
              "UI-01",
              "UI-02",
              "UI-10",
              "UI-12",
              "UI-13",
              "UI-15",
              "UI-18",
              "UI-23",
              "UI-24",
              "UI-25",
              "UI-27",
              "UI-29",
              "UI-31",
              "UI-33",
            ].includes(screenId) && (
              <p role="note">
                수정 권한이 없어 보기만 할 수 있습니다(권한 › 메뉴×동작 표의
                수정).
              </p>
            )}
          {children}
        </div>
      </section>
    </PeriodStatusTarget.Provider></ScreenTitleActionTarget.Provider>
  );
}
export function FollowUpScreen({ screenId }: { screenId: string }) {
  return (
    <SharedScreenFrame screenId={screenId}>
      <div className="sicost-followup">
        <span className="sicost-followup-icon">
          <Icon name="folder" size={28} />
        </span>
        <h2>이 화면은 다음 단계에서 만듭니다.</h2>
        <p>현재 단계에서는 메뉴 이동과 화면 설정을 이용할 수 있습니다.</p>
      </div>
    </SharedScreenFrame>
  );
}
