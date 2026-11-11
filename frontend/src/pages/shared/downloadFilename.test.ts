import { describe, expect, it } from "vitest";
import { downloadFilename } from "./downloadFilename";

const response = (disposition?: string) =>
  new Response("x", disposition ? { headers: { "Content-Disposition": disposition } } : {});

describe("downloadFilename", () => {
  it("서버가 정한 시각 붙은 파일명을 쓴다", () => {
    expect(
      downloadFilename(
        response(`attachment; filename*=UTF-8''${encodeURIComponent("개발자목록_20261112_021500.xlsx")}`),
        "개발자목록.xlsx",
      ),
    ).toBe("개발자목록_20261112_021500.xlsx");
  });
  it("헤더가 없거나 깨졌거나 xlsx가 아니거나 경로가 섞였으면 기본 이름을 쓴다", () => {
    expect(downloadFilename(response(), "a.xlsx")).toBe("a.xlsx");
    expect(downloadFilename(response("attachment; filename*=UTF-8''%E0%A4%A"), "a.xlsx")).toBe("a.xlsx");
    expect(downloadFilename(response("attachment; filename*=UTF-8''x.txt"), "a.xlsx")).toBe("a.xlsx");
    expect(downloadFilename(response("attachment; filename*=UTF-8''..%2Fx.xlsx"), "a.xlsx")).toBe("a.xlsx");
  });
});
