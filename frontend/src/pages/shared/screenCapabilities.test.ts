import { describe, expect, it } from "vitest";
import { implementedBusinessScreenIds } from "./screenCapabilities";
import { screenRegistry } from "./screenRegistry";

describe("implementedBusinessScreenIds", () => {
  it.each(["UI-01", "UI-15", "UI-23", "UI-24", "UI-25", "UI-16"])("%s 는 구현된 화면으로 선택할 수 있다", (id) => {
    expect(implementedBusinessScreenIds.has(id)).toBe(true);
  });

  it.each(["UI-20", "UI-22", "UI-99"])("%s 는 준비되지 않은 화면이라 선택할 수 없다", (id) => {
    expect(implementedBusinessScreenIds.has(id)).toBe(false);
  });

  it("공유 등록부에 있는 화면의 구현 여부는 이 목록과 같은 값이다", () => {
    for (const [id, entry] of Object.entries(screenRegistry as Record<string, { implemented?: boolean }>)) {
      if (entry && typeof entry === "object" && "implemented" in entry) {
        expect(entry.implemented).toBe(implementedBusinessScreenIds.has(id));
      }
    }
  });
});
