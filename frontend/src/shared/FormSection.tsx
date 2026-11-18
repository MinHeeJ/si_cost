import { useId, type ReactNode } from "react";

export function FormSection({
  title,
  description,
  children,
  disabled,
  testId,
  className = "",
}: {
  title: string;
  description?: ReactNode;
  children: ReactNode;
  disabled?: boolean;
  testId: string;
  className?: string;
}) {
  const id = useId();
  return (
    <fieldset
      className={`sicost-form-section ${className}`.trim()}
      disabled={disabled}
      data-testid={testId}
      aria-describedby={description ? id : undefined}
    >
      <legend>{title}</legend>
      {description && (
        <p id={id} className="sicost-form-section-description">
          {description}
        </p>
      )}
      {children}
    </fieldset>
  );
}
