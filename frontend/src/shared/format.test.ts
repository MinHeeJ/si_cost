import { describe, expect, it } from "vitest";
import {
  compareDecimal,
  formatDateTime,
  formatDecimal,
  normalizeDecimal,
  validMonth,
} from "./format";

describe("정밀한 공통 표시와 입력", () => {
  it("0, NULL, 음수를 구분하고 원값을 바꾸지 않는다", () => {
    const row = Object.freeze({
      value: "99999999999.1234",
      zero: "0.0000",
      absent: null,
    });
    expect(formatDecimal(row.value)).toBe("99,999,999,999.1234");
    expect(formatDecimal(row.zero)).toBe("0");
    expect(formatDecimal(row.absent)).toBe("–");
    expect(formatDecimal("-1234.5000")).toBe("−1,234.5");
    expect(row.value).toBe("99999999999.1234");
    expect(row.zero).toBe("0.0000");
    expect(row.absent).toBeNull();
  });
  it("쉼표·공백·끝 단위를 정리하되 반올림하지 않는다", () => {
    expect(normalizeDecimal(" 12,345.6789 원 ")).toBe("12345.6789");
    expect(normalizeDecimal("101%")).toBe("101");
    expect(normalizeDecimal("123.4500")).toBe("123.4500");
    expect(normalizeDecimal("", true)).toBe("0");
    expect(() => normalizeDecimal("")).toThrow();
  });
  it.each(["1.12345", "100000000000", "-1", "1e2", "NaN", "Infinity"])(
    "잘못된 숫자를 거부한다: %s",
    (value) => {
      expect(() => normalizeDecimal(value)).toThrow();
    },
  );
  it.each(["0001-01", "9999-12", "2026-02"])(
    "실제 월 경계를 허용한다: %s",
    (value) => expect(validMonth(value)).toBe(true),
  );
  it.each(["2026-00", "2026-13", "0000-01", "2026-1"])(
    "잘못된 월을 거부한다: %s",
    (value) => expect(validMonth(value)).toBe(false),
  );
  it("큰 숫자와 소수 정렬에도 부동소수점을 쓰지 않는다", () => {
    expect(compareDecimal("999999999999999.0001", "999999999999999.0002")).toBe(
      -1,
    );
    expect(compareDecimal("1.00", "1")).toBe(0);
    expect(compareDecimal("-2", "-1")).toBe(-1);
    expect(compareDecimal(null, "0")).toBe(-1);
  });
  it("서버 시각을 서울 표시로 바꾸되 문자열을 바꾸지 않는다", () => {
    const value = "2026-01-01T00:00:00Z";
    expect(formatDateTime(value)).toBe("2026-01-01 09:00");
    expect(formatDateTime("2026-01-01T00:00:00")).toBe("2026-01-01 00:00");
    expect(value).toBe("2026-01-01T00:00:00Z");
  });
});
