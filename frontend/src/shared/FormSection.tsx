import { useId, type ReactNode } from "react";

export function FormSection({
  title,
  description,
  children,
  className = "",
  disabled = false,
}: {
  title: string;
  description?: ReactNode;
  children: ReactNode;
  className?: string;
  disabled?: boolean;
}) {
  const id = useId();
  return (
    <fieldset
      disabled={disabled}
      className={`sicost-form-section ${className}`}
      aria-labelledby={id}
    >
      <legend>
        <h2 id={id}>{title}</h2>
      </legend>
      {description && <p>{description}</p>}
      <div className="sicost-form-grid">{children}</div>
    </fieldset>
  );
}
export function FormFullWidth({ children }: { children: ReactNode }) {
  return <div className="sicost-form-full-width">{children}</div>;
}
