export type ApiEnvelope<T> = {
  success: true;
  meta: Record<string, never>;
  data: T;
};
export type Violation = { field: string; message: string };
export type ApiErrorBody = {
  success: false;
  meta: Record<string, never>;
  code: string;
  message: string;
  requestId: string;
  field?: string;
  details?: { violations?: Violation[]; references?: unknown[] };
};
export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly body: ApiErrorBody,
  ) {
    super(body.message);
  }
  get fieldMessages(): Record<string, string> {
    const messages: Record<string, string> = {};
    if (this.body.field) messages[this.body.field] = this.body.message;
    for (const violation of this.body.details?.violations ?? [])
      messages[violation.field] = violation.message;
    return messages;
  }
}
export async function request<T>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  if (!path.startsWith("/api/") || path.startsWith("//") || /[\r\n]/.test(path))
    throw new Error("API 상대 경로가 필요합니다.");
  const headers = new Headers(options.headers);
  if (options.body != null) headers.set("Content-Type", "application/json");
  headers.set("Accept", "application/json");
  const response = await fetch(path, {
    ...options,
    headers,
    redirect: "error",
  });
  if (
    response.status === 204 ||
    (response.status === 201 && response.headers.get("Content-Length") === "0")
  )
    return undefined as T;
  const text = await response.text();
  if (response.ok && response.status === 201 && !text) return undefined as T;
  let body: unknown;
  try {
    body = JSON.parse(text);
  } catch {
    throw new Error(
      "서버 응답을 확인할 수 없습니다. 입력한 내용은 유지됩니다.",
    );
  }
  if (!response.ok) {
    const error = body as ApiErrorBody;
    if (
      error.success !== false ||
      typeof error.message !== "string" ||
      typeof error.code !== "string"
    )
      throw new Error("서버 요청에 실패했습니다.");
    throw new ApiError(response.status, error);
  }
  const envelope = body as ApiEnvelope<T>;
  if (envelope.success !== true || !envelope.meta)
    throw new Error("올바르지 않은 서버 응답입니다.");
  return envelope.data;
}
