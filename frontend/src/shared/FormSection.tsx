import type { ReactNode } from "react";
import "./presentation.css";
export function FormSection({
  title,
  children,
  disabled,
  testId,
}: {
  title: string;
  children: ReactNode;
  disabled?: boolean;
  testId: string;
}) {
  return (
    <fieldset
      className="sicost-form-section"
      disabled={disabled}
      data-testid={testId}
    >
      <legend>{title}</legend>
      <div className="sicost-form-section-grid">{children}</div>
    </fieldset>
  );
}
export function FormActions({ children }: { children: ReactNode }) {
  return <footer className="sicost-form-actions">{children}</footer>;
}
