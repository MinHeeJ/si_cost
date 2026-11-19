import { useEffect, type RefObject } from "react";

export function focusFirstError(
  root: ParentNode | null,
  fields: Record<string, string> = {},
) {
  const first = Array.from(
    root?.querySelectorAll<HTMLElement>("input, select, textarea, button") ??
      [],
  ).find(
    (node) =>
      node.getAttribute("aria-invalid") === "true" ||
      Boolean(fields[node.getAttribute("name") ?? ""]),
  );
  first?.focus();
  first?.scrollIntoView?.({ block: "center", behavior: "smooth" });
}

export function useFieldErrorFocus(
  ref: RefObject<HTMLElement>,
  fields: Record<string, string>,
) {
  useEffect(() => {
    if (Object.values(fields).some(Boolean))
      focusFirstError(ref.current, fields);
  }, [fields, ref]);
}

export function FieldError({ message, id }: { message?: string; id?: string }) {
  return message ? (
    <span id={id} role="alert" className="sicost-field-error">
      {message}
    </span>
  ) : null;
}
