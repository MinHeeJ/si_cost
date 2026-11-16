import { ApiError, type ApiErrorBody } from "./api";
export type ResumeValues = Partial<{
  name: string;
  employeeType: string;
  companyId: string | null;
  jobCode: string;
  subJobCode: string | null;
  skills: string[];
  homeAreaCode: string;
  phone: string;
  email: string;
  desiredRate: string;
  availableDate: string;
}>;
export type ResumeField = {
  field: keyof ResumeValues;
  valid: boolean;
  confidence: "EXPLICIT" | "INFERRED";
  notice: string;
  fragment: string;
  matchedWords: string[];
  candidates: unknown[];
};
export type ResumeExtraction = {
  targetType: "DEVELOPER" | "CANDIDATE";
  values: ResumeValues;
  fields: ResumeField[];
  warnings: string[];
  careerReference: string | null;
  notice: string;
};
export const resumeAccept = ".doc,.docx,.rtf,.pdf,.ppt,.pptx,.hwp,.hwpx,.txt";
export async function extractResume(
  file: File,
  targetType: ResumeExtraction["targetType"],
  signal?: AbortSignal,
): Promise<ResumeExtraction> {
  if (!file.size || file.size > 10485760)
    throw new Error("파일은 1바이트~10MiB로 선택해 주세요.");
  if (!/\.(docx?|rtf|pdf|pptx?|hwpx?|txt)$/i.test(file.name))
    throw new Error(
      "지원하는 문서 파일을 선택해 주세요. 이미지 OCR은 제공하지 않습니다.",
    );
  const body = new FormData();
  body.append("file", file);
  body.append("targetType", targetType);
  const response = await fetch("/api/resume-extractions", {
    method: "POST",
    body,
    signal,
    redirect: "error",
    headers: { Accept: "application/json" },
  });
  const envelope = await response.json();
  if (!response.ok)
    throw new ApiError(response.status, envelope as ApiErrorBody);
  if (envelope.success !== true)
    throw new Error("추출 응답을 확인하지 못했습니다. 입력값은 유지됩니다.");
  return envelope.data;
}
