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
let csrf: { headerName: string; token: string } | undefined;
let csrfLoading: Promise<void> | undefined;
let csrfGeneration = 0;
export function resetCsrf() {
  csrfGeneration++;
  csrf = undefined;
  csrfLoading = undefined;
}
async function prepareCsrf() {
  while (!csrf) {
    if (!csrfLoading) {
      const generation = csrfGeneration;
      const pending: Promise<void> = (async () => {
        const response = await fetch("/api/auth/csrf", {
          credentials: "same-origin",
          redirect: "error",
          cache: "no-store",
        });
        if (!response.ok) throw new Error("보안 토큰을 받지 못했습니다.");
        const body = (await response.json()) as ApiEnvelope<{
          headerName: string;
          token: string;
        }>;
        if (!body.success || !body.data?.headerName || !body.data?.token)
          throw new Error("보안 토큰 응답을 확인해 주세요.");
        if (generation === csrfGeneration) csrf = body.data;
      })().finally(() => {
        if (csrfLoading === pending) csrfLoading = undefined;
      });
      csrfLoading = pending;
    }
    await csrfLoading;
  }
  return csrf;
}
/** Cookies and CSRF for JSON, multipart, downloads and streams; never retry a write. */
export async function authenticatedFetch(
  path: string,
  options: RequestInit = {},
): Promise<Response> {
  if (!path.startsWith("/api/") || path.startsWith("//") || /[\r\n]/.test(path))
    throw new Error("API 상대 경로가 필요합니다.");
  const method = (options.method ?? "GET").toUpperCase();
  const headers = new Headers(options.headers);
  if (!["GET", "HEAD", "OPTIONS"].includes(method)) {
    const token = await prepareCsrf();
    headers.set(token.headerName, token.token);
  }
  if (options.body instanceof FormData) headers.delete("Content-Type");
  const response = await fetch(path, {
    ...options,
    headers,
    credentials: "same-origin",
    redirect: "error",
    cache: "no-store",
  });
  const serverTime = response.headers?.get("X-Session-Server-Time"),
    expiresAt = response.headers?.get("X-Session-Expires-At");
  if (
    response.ok &&
    serverTime &&
    expiresAt &&
    Number.isFinite(Date.parse(serverTime)) &&
    Number.isFinite(Date.parse(expiresAt))
  )
    window.dispatchEvent(
      new CustomEvent("sicost:session-timing", {
        detail: { serverTime, expiresAt },
      }),
    );
  if (path.startsWith("/api/auth/") && method === "POST" && response.ok)
    resetCsrf();
  if (response.status === 400) {
    const error = (await response
      .clone()
      .json()
      .catch(() => undefined)) as ApiErrorBody | undefined;
    if (error?.code === "FIELD_NOT_EDITABLE")
      window.dispatchEvent(
        new CustomEvent("sicost:permission-changed", { detail: { path } }),
      );
  }
  if (response.status === 401 || response.status === 403) {
    let error: ApiErrorBody;
    try {
      error = (await response.clone().json()) as ApiErrorBody;
    } catch {
      throw new Error("인증 응답을 확인할 수 없습니다.");
    }
    if (error.code === "CSRF_INVALID" || response.status === 401) resetCsrf();
    if (
      response.status === 401 &&
      path !== "/api/auth/login" &&
      path !== "/api/auth/session" &&
      !(
        path === "/api/auth/mfa/verify" &&
        ["MFA_INVALID", "MFA_EXPIRED"].includes(error.code)
      )
    )
      window.dispatchEvent(
        new CustomEvent("sicost:authentication-required", { detail: error }),
      );
    if (
      response.status === 403 &&
      !["CSRF_INVALID", "ORIGIN_FORBIDDEN"].includes(error.code) &&
      path !== "/api/current-user/capabilities"
    )
      window.dispatchEvent(
        new CustomEvent("sicost:permission-changed", { detail: { path } }),
      );
    throw new ApiError(response.status, error);
  }
  return response;
}
export async function request<T>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  if (!path.startsWith("/api/") || path.startsWith("//") || /[\r\n]/.test(path))
    throw new Error("API 상대 경로가 필요합니다.");
  const headers = new Headers(options.headers);
  if (options.body != null && !(options.body instanceof FormData))
    headers.set("Content-Type", "application/json");
  headers.set("Accept", "application/json");
  const response = await authenticatedFetch(path, {
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
