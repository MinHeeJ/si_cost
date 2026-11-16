import { describe, it, expect, vi } from "vitest";
import { act, renderHook } from "@testing-library/react";
import { useState } from "react";
import { mergeResume, useResumeExtraction } from "./useResumeExtraction";
import * as api from "./resumeClient";
vi.mock("./resumeClient", async (original) => ({
  ...(await original<typeof import("./resumeClient")>()),
  extractResume: vi.fn(),
}));
const result = (values: api.ResumeValues): api.ResumeExtraction => ({
  targetType: "CANDIDATE",
  values,
  fields: Object.keys(values).map((field) => ({
    field: field as keyof api.ResumeValues,
    valid: true,
    confidence: ["employeeType", "availableDate", "jobCode"].includes(field)
      ? "INFERRED"
      : "EXPLICIT",
    notice: "확인",
    fragment: "",
    matchedWords: [],
    candidates: [],
  })),
  warnings: [],
  careerReference: null,
  notice: "",
});
const initial = () => ({
  name: "",
  employeeType: "FL",
  companyId: null as string | null,
  jobCode: "",
  subJobCode: null as string | null,
  skills: [] as string[],
  homeAreaCode: null as string | null,
  availableDate: "2026-12-16",
  desiredRate: "",
  consent: false,
  gradeCode: "MD",
});
describe("resume draft-only merging", () => {
  it("only untouched defaults and blanks can be replaced; consent and grade are never accepted", () => {
    const form = initial();
    const extraction = result({
      name: "새 이름",
      employeeType: "OS",
      companyId: "V1",
      availableDate: "2026-11-16",
      skills: ["Java"],
    });
    (extraction.values as Record<string, unknown>).consent = true;
    (extraction.values as Record<string, unknown>).gradeCode = "EX";
    const merged = mergeResume(form, extraction, new Set(), new Set());
    expect(merged.form).toMatchObject({
      name: "새 이름",
      employeeType: "OS",
      companyId: "V1",
      availableDate: "2026-11-16",
      skills: ["Java"],
      consent: false,
      gradeCode: "MD",
    });
    expect(merged.applied).toHaveLength(5);
  });
  it("manual clear, same-default decision, connected address, nonempty skills and prior import survive", () => {
    const form = {
      ...initial(),
      name: "이전 추출",
      skills: ["SQL"],
      homeAreaCode: "11",
    };
    const merged = mergeResume(
      form,
      result({
        name: "다른 이름",
        employeeType: "OS",
        companyId: "V2",
        homeAreaCode: "41",
        skills: ["Java"],
        desiredRate: "6500000",
      }),
      new Set(["employeeType", "desiredRate"]),
      new Set(["name"]),
    );
    expect(merged.form).toEqual(form);
    expect(merged.applied).toHaveLength(0);
  });
  it("keeps a previously imported employment decision and rejects a conflicting company on reupload", () => {
    for (const protectedBy of ["filled", "touched"] as const) {
      const form = initial();
      const merged = mergeResume(
        form,
        result({ employeeType: "OS", companyId: "V2", name: "새 이름" }),
        new Set(protectedBy === "touched" ? ["employeeType"] : []),
        new Set(protectedBy === "filled" ? ["employeeType"] : []),
      );
      expect(merged.form.employeeType).toBe("FL");
      expect(merged.form.companyId).toBeNull();
      expect(merged.form.name).toBe("새 이름");
      expect(merged.applied.map((f) => f.field)).toEqual(["name"]);
    }
  });
  it("protects edits made during an outstanding request and counts only actually applied fields", async () => {
    let resolve!: (value: api.ResumeExtraction) => void;
    vi.mocked(api.extractResume).mockReturnValue(
      new Promise((r) => (resolve = r)),
    );
    const { result: hook } = renderHook(() => {
      const [form, setForm] = useState(initial);
      return {
        form,
        setForm,
        resume: useResumeExtraction("CANDIDATE", form, setForm),
      };
    });
    let task!: Promise<void>;
    act(() => {
      task = hook.current.resume.upload(new File(["resume"], "r.txt"));
    });
    act(() => {
      hook.current.resume.touch("name");
      hook.current.setForm((f) => ({ ...f, name: "대기중 수동 입력" }));
    });
    await act(async () => {
      resolve(
        result({
          name: "파일 이름",
          skills: ["Java"],
          availableDate: "2026-11-16",
        }),
      );
      await task;
    });
    expect(hook.current.form.name).toBe("대기중 수동 입력");
    expect(hook.current.form.skills).toEqual(["Java"]);
    expect(hook.current.resume.notice).toContain("2칸");
    expect(hook.current.resume.notice).toContain("✓ 1 · ⚠ 1");
    expect(hook.current.form.consent).toBe(false);
  });
  it("reopens an existing form unlocked and isolates old responses/finally from the next upload", async () => {
    const resolvers: Array<(value: api.ResumeExtraction) => void> = [];
    vi.mocked(api.extractResume).mockImplementation(
      () => new Promise((resolve) => resolvers.push(resolve)),
    );
    const { result: hook, rerender } = renderHook(
      ({ open }) => {
        const [form, setForm] = useState(initial);
        return {
          form,
          resume: useResumeExtraction(
            "CANDIDATE",
            form,
            setForm,
            undefined,
            open,
          ),
        };
      },
      { initialProps: { open: true } },
    );
    let first!: Promise<void>, second!: Promise<void>;
    act(() => {
      first = hook.current.resume.upload(new File(["one"], "one.txt"));
    });
    expect(hook.current.resume.pending).toBe(true);
    rerender({ open: false });
    rerender({ open: true });
    expect(hook.current.resume.pending).toBe(false);
    expect(hook.current.resume.isBusy()).toBe(false);
    act(() => {
      second = hook.current.resume.upload(new File(["two"], "two.txt"));
    });
    await act(async () => {
      resolvers[0](result({ name: "취소된 응답" }));
      await first;
    });
    expect(hook.current.form.name).toBe("");
    expect(hook.current.resume.pending).toBe(true);
    expect(hook.current.resume.isBusy()).toBe(true);
    await act(async () => {
      resolvers[1](result({ name: "새 요청의 이름" }));
      await second;
    });
    expect(hook.current.form.name).toBe("새 요청의 이름");
    expect(hook.current.resume.pending).toBe(false);
    expect(hook.current.resume.isBusy()).toBe(false);
  });
  it("discards a late response after closing the form", async () => {
    let resolve!: (value: api.ResumeExtraction) => void;
    vi.mocked(api.extractResume).mockReturnValue(
      new Promise((r) => (resolve = r)),
    );
    const { result: hook, rerender } = renderHook(
      ({ open }) => {
        const [form, setForm] = useState(initial);
        return {
          form,
          resume: useResumeExtraction(
            "CANDIDATE",
            form,
            setForm,
            undefined,
            open,
          ),
        };
      },
      { initialProps: { open: true } },
    );
    let task!: Promise<void>;
    act(() => {
      task = hook.current.resume.upload(new File(["resume"], "r.txt"));
    });
    rerender({ open: false });
    await act(async () => {
      resolve(result({ name: "늦은 이름" }));
      await task;
    });
    expect(hook.current.form.name).toBe("");
    expect(hook.current.resume.fields).toEqual([]);
  });
});
