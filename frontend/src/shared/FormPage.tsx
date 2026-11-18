import type { ReactNode } from "react";

export function FormPage({
  title,
  children,
  actions,
  showHeader = true,
  breadcrumb,
  screenId,
}: {
  title: string;
  children: ReactNode;
  actions: ReactNode;
  showHeader?: boolean;
  breadcrumb?: string;
  screenId?: string;
}) {
  return (
    <section className="sicost-form-page" data-testid="people-form-page">
      {showHeader && (
        <header className="developer-toolbar sicost-form-heading">
          {actions}
          <div>
            {breadcrumb && <p className="sicost-breadcrumb">{breadcrumb}</p>}
            <div className="sicost-title-row">
              <h1>{title}</h1>
              {screenId && <small>{screenId}</small>}
            </div>
          </div>
        </header>
      )}
      {children}
    </section>
  );
}
