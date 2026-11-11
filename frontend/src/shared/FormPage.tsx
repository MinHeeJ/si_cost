import type { ReactNode } from "react";

export function FormPage({
  title,
  children,
  actions,
}: {
  title: string;
  children: ReactNode;
  actions: ReactNode;
}) {
  return (
    <section className="sicost-form-page" data-testid="people-form-page">
      <header className="developer-toolbar">
        <h1>{title}</h1>
        {actions}
      </header>
      {children}
    </section>
  );
}
