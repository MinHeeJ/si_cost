import { useEffect, type RefObject } from "react";
import { ApiError } from "./api";

/**
 * 칸 아래에 이미 보이는 서버 검사 오류는 위쪽 오류 띠에 다시 보이지 않는다(SFR-017-01 「틀린 칸 아래 빨간 이유」).
 * 칸에 붙지 않거나 화면에 없는 칸의 오류는 그대로 띠에 남긴다.
 */
export function errorOutsideFields(
  error: unknown,
  shown: (field: string) => boolean,
): unknown {
  if (!(error instanceof ApiError)) return error;
  const keys = Object.keys(error.fieldMessages);
  return keys.length > 0 && keys.every(shown) ? undefined : error;
}

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
