import { useSharedScreen } from "../pages/shared/SharedScreenContext";
import { Icon } from "./Icon";
import type { ReactNode } from "react";

export function FormPageHeader({
  title,
  actions,
}: {
  title: string;
  actions: ReactNode;
}) {
  return (
    <header className="sicost-editor-heading">
      <h1>{title}</h1>
      <div className="sicost-editor-heading-actions">{actions}</div>
    </header>
  );
}

export function FormPage({
  title,
  children,
  actions,
  showHeader = true,
  compactHeader = false,
  breadcrumb,
  screenId,
}: {
  title: string;
  children: ReactNode;
  actions: ReactNode;
  showHeader?: boolean;
  compactHeader?: boolean;
  breadcrumb?: string;
  screenId?: string;
}) {
  const shared = useSharedScreen(screenId ?? "");
  return (
    <section className="sicost-form-page" data-testid="people-form-page">
      {showHeader && compactHeader && (
        <FormPageHeader title={title} actions={actions} />
      )}
      {showHeader && !compactHeader && (
        <header className="developer-toolbar sicost-form-heading">
          {actions}
          <div>
            {breadcrumb && <p className="sicost-breadcrumb">{breadcrumb}</p>}
            <div className="sicost-title-row">
              <h1>{title}</h1>
              {screenId && shared.menu && <button type="button" className="sicost-star" aria-label={shared.favorites.includes(screenId) ? "즐겨찾기 해제" : "즐겨찾기 추가"} aria-pressed={shared.favorites.includes(screenId)} disabled={shared.pending} onClick={() => shared.star(screenId)}><Icon name="star" size={18} fill={shared.favorites.includes(screenId) ? "currentColor" : "none"} /></button>}
              {screenId && <small>{screenId}</small>}
            </div>
          </div>
        </header>
      )}
      {children}
    </section>
  );
}
