/** 서버가 Content-Disposition으로 정한 파일명(…_YYYYMMDD_HHmmss.xlsx)을 쓴다. 없거나 깨졌으면 기본 이름으로 저장한다. */
export function downloadFilename(response: Response, fallback: string): string {
  const encoded = /filename\*=UTF-8''([^;]+)/i.exec(
    response.headers.get("Content-Disposition") ?? "",
  )?.[1];
  if (!encoded) return fallback;
  try {
    const name = decodeURIComponent(encoded);
    return name.endsWith(".xlsx") && !/[\\/]/.test(name) ? name : fallback;
  } catch {
    return fallback;
  }
}
