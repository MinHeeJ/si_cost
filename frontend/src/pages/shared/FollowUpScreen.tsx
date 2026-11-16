import type { ReactNode } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { ScreenPeriodBar } from "./ScreenPeriodBar";
import { useSharedScreen } from "./SharedScreenContext";
import { Icon } from "../../shared/Icon";
import type { AnalysisScope } from "./analysisClient";
export function SharedScreenFrame({
  screenId,
  children,
  periodBar,
  initialMonth,
}: {
  screenId: string;
  children: ReactNode;
  periodBar?: ReactNode;
  initialMonth?: string;
}) {
  const location = useLocation();
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
    <section
      data-screen-id={`SCR-${screenId}`}
      className={`sicost-screen${screenId === "UI-27" ? " sicost-workspace-screen" : ""}`}
    >
      <div className="sicost-titlebar" data-screen-id={`SCR-${screenId}-ENTRY`}>
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
                favorites.includes(screenId) ? "즐겨찾기 해제" : "즐겨찾기 추가"
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
        {periodBar === undefined ? (
          <ScreenPeriodBar
            screenId={screenId}
            initialMonth={
              analysisEntry?.month ?? initialMonth ?? dashboardMonth
            }
          />
        ) : (
          periodBar
        )}
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
        {children}
      </div>
    </section>
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
