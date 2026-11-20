import { expect, it } from "vitest";
import { ApiError } from "./api";
import { errorOutsideFields } from "./FieldErrors";

function apiError(field?: string, violations: string[] = []) {
  return new ApiError(400, {
    success: false,
    meta: {},
    code: "VALIDATION_ERROR",
    message: "검사 문구",
    field,
    requestId: "req-1",
    details: violations.length
      ? { violations: violations.map((f) => ({ field: f, message: "줄 문구" })) }
      : undefined,
  } as ConstructorParameters<typeof ApiError>[1]);
}
const shown = (key: string) => key === "projectId" || key === "endDate";

// 위쪽 띠를 숨기는 경우는 「모든 칸 오류가 화면 칸 아래에 보일 때」뿐이다(205 완료 점검 ②).
it.each([
  ["화면 칸 하나", apiError("projectId"), false],
  ["화면 칸 여러 개", apiError("projectId", ["endDate"]), false],
  ["화면에 없는 칸", apiError("jobCode"), true],
  ["화면 칸 + 없는 칸", apiError("projectId", ["jobCode"]), true],
  ["칸 없는 서버 오류", apiError(undefined), true],
  ["일반 오류", new Error("네트워크"), true],
])("%s → 띠 표시 %s", (_name, error, banner) => {
  expect(errorOutsideFields(error, shown) === error).toBe(banner);
});
it("오류가 없으면 그대로 없음", () => {
  expect(errorOutsideFields(undefined, shown)).toBeUndefined();
});
