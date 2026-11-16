import {
  useEffect,
  useRef,
  useState,
  type Dispatch,
  type SetStateAction,
} from "react";
import {
  extractResume,
  type ResumeExtraction,
  type ResumeField,
  type ResumeValues,
} from "./resumeClient";
const common = new Set([
  "name",
  "employeeType",
  "companyId",
  "jobCode",
  "subJobCode",
  "skills",
  "homeAreaCode",
]);
const candidate = new Set([
  ...common,
  "phone",
  "email",
  "desiredRate",
  "availableDate",
]);
const empty = (value: unknown) =>
  value == null ||
  (typeof value === "string" && !value.trim()) ||
  (Array.isArray(value) && !value.length);
export function mergeResume<T extends object>(
  form: T,
  extraction: ResumeExtraction,
  touched: ReadonlySet<string>,
  filled: ReadonlySet<string>,
) {
  const next = { ...form } as Record<string, unknown>;
  const applied: ResumeField[] = [];
  const protectedFields: string[] = [];
  const allowed = extraction.targetType === "DEVELOPER" ? common : candidate;
  const employmentConflict =
    (touched.has("employeeType") || filled.has("employeeType")) &&
    extraction.values.employeeType !== undefined &&
    extraction.values.employeeType !== next.employeeType;
  for (const field of extraction.fields) {
    const key = field.field;
    const value = extraction.values[key];
    if (!allowed.has(key) || !field.valid || value == null) continue;
    const defaultValue =
      key === "employeeType" ||
      (extraction.targetType === "CANDIDATE" && key === "availableDate");
    if (
      touched.has(key) ||
      filled.has(key) ||
      (!empty(next[key]) && !defaultValue) ||
      (employmentConflict && (key === "employeeType" || key === "companyId")) ||
      (key === "companyId" && touched.has("companyId"))
    ) {
      protectedFields.push(key);
      continue;
    }
    if (
      (key === "subJobCode" && value === next.jobCode) ||
      (key === "jobCode" && value === next.subJobCode)
    ) {
      protectedFields.push(key);
      continue;
    }
    next[key] = value;
    applied.push(field);
  }
  return { form: next as T, applied, protectedFields };
}
export function useResumeExtraction<T extends object>(
  target: ResumeExtraction["targetType"],
  form: T,
  setForm: Dispatch<SetStateAction<T>>,
  onApplied?: () => void,
  open = true,
) {
  const touched = useRef(new Set<string>()),
    filled = useRef(new Set<string>()),
    current = useRef(form),
    live = useRef(true),
    busy = useRef(false),
    controller = useRef<AbortController>(),
    generation = useRef(0);
  current.current = form;
  const [pending, setPending] = useState(false),
    [error, setError] = useState(""),
    [notice, setNotice] = useState(""),
    [fields, setFields] = useState<ResumeField[]>([]),
    [warnings, setWarnings] = useState<string[]>([]),
    [career, setCareer] = useState<string | null>(null);
  useEffect(() => {
    live.current = open;
    // Candidate forms may remain mounted while closed. Invalidate work before aborting,
    // including transports which deliver a response despite cancellation.
    generation.current++;
    busy.current = false;
    setPending(false);
    if (!open) {
      controller.current?.abort();
      setError("");
      setFields([]);
      setNotice("");
      setWarnings([]);
      setCareer(null);
    }
    return () => {
      live.current = false;
      generation.current++;
      busy.current = false;
      controller.current?.abort();
    };
  }, [open]);
  function touch(key: string) {
    touched.current.add(key);
    setFields((old) => old.filter((f) => f.field !== key));
  }
  async function upload(file: File) {
    if (busy.current || !live.current) return;
    const request = ++generation.current;
    busy.current = true;
    setPending(true);
    setError("");
    controller.current = new AbortController();
    try {
      const result = await extractResume(
        file,
        target,
        controller.current.signal,
      );
      if (!live.current || request !== generation.current) return;
      const merged = mergeResume(
        current.current,
        result,
        touched.current,
        filled.current,
      );
      current.current = merged.form;
      setForm(merged.form);
      merged.applied.forEach((f) => filled.current.add(f.field));
      setFields((old) => [
        ...old.filter((f) => !merged.applied.some((a) => a.field === f.field)),
        ...merged.applied,
      ]);
      const a = merged.applied.filter(
          (f) => f.confidence === "EXPLICIT",
        ).length,
        b = merged.applied.length - a;
      setNotice(
        merged.applied.length
          ? `${file.name}에서 ${merged.applied.length}칸을 채웠습니다(✓ ${a} · ⚠ ${b}). 확인 후 [등록]을 누르세요. 파일은 저장하지 않습니다.`
          : "가져올 칸을 찾지 못했습니다. 직접 적어 주세요.",
      );
      setWarnings([
        ...result.warnings,
        ...result.fields
          .filter((f) => !f.valid)
          .map((f) => `${f.field}: ${f.notice}`),
        ...merged.protectedFields.map(
          (key) => `${key}: 직접 입력했거나 이미 채운 값은 유지했습니다.`,
        ),
      ]);
      setCareer(result.careerReference);
      if (merged.applied.length) onApplied?.();
    } catch (e) {
      if (
        live.current &&
        request === generation.current &&
        !(e instanceof DOMException && e.name === "AbortError")
      )
        setError(
          e instanceof Error
            ? e.message
            : "추출하지 못했습니다. 기존 입력은 유지됩니다.",
        );
    } finally {
      if (request === generation.current) {
        busy.current = false;
        if (live.current) setPending(false);
      }
    }
  }
  return {
    pending,
    error,
    notice,
    fields,
    warnings,
    career,
    touch,
    upload,
    isTouched: (key: keyof T) => touched.current.has(String(key)),
    isBusy: () => busy.current,
  };
}
export type ResumeAssistState = Pick<
  ReturnType<typeof useResumeExtraction>,
  "pending" | "error" | "notice" | "fields" | "warnings" | "career" | "upload"
>;
