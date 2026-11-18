import { describe, expect, it } from "vitest";
import {
  chartCoordinates,
  ratioOption,
  quantityOption,
  seriesOption,
  waterfallOption,
} from "./chartAdapters";

describe("chart adapters", () => {
  it("preserves supplied ratios including zero, negative and unknown without normalizing to 100", () => {
    const option = ratioOption([
      { label: "영", share: "0" },
      { label: "음수", share: "-12.5" },
      { label: "미확인", share: null },
    ]) as any;
    expect(
      option.series[0].data.map(
        (point: { value: number | null }) => point.value,
      ),
    ).toEqual([0, -12.5, null]);
    expect(option.tooltip.formatter({ dataIndex: 1 })).toContain("−12.5%");
    expect(option.tooltip.formatter({ dataIndex: 2 })).toContain("–");
  });
  it("bounds coordinates without rounding the tooltip source and distinguishes null/zero/negative", () => {
    const raw = "-9007199254740993123456789";
    const scale = chartCoordinates([raw, "0", null]);
    expect(Math.abs(scale.point(raw)!)).toBeLessThan(1_000_000_000);
    expect(scale.point(null)).toBeNull();
    expect(scale.point("0")).toBe(0);
    expect(scale.point(raw)).toBeLessThan(0);
    const option = seriesOption(
      ["당월", "다음월", "미확인"],
      [{ name: "원가", values: [raw, "0", null] }],
      "원",
    ) as any;
    expect(option.series[0].data[2]).toBeNull();
    expect(option.series[0].connectNulls).toBe(false);
    expect(option.tooltip.formatter([{ dataIndex: 0 }])).toContain(
      "−9,007,199,254,740,993,123,456,789",
    );
    expect(option.tooltip.formatter([{ dataIndex: 1 }])).toContain("0 원");
    expect(option.tooltip.formatter([{ dataIndex: 2 }])).toContain("– 원");
  });
  it("keeps stacked values, forecast dashes and minimum cash from the caller rather than calculating totals", () => {
    const option = seriesOption(
      ["당월"],
      [
        { name: "실적", values: ["0"], stack: "cost" },
        { name: "예정", values: [null], dashed: true },
      ],
      "백만원",
      "1000000",
    ) as any;
    expect(option.series[0].stack).toBe("cost");
    expect(option.series[1].lineStyle.type).toBe("dashed");
    expect(option.series[0].markLine.data[0].yAxis).toBe(1);
    const quantity = quantityOption(
      ["당월"],
      [{ name: "가용", values: ["0.5"] }],
      "명",
    ) as any;
    expect(quantity.series[0].data).toEqual([0.5]);
    expect(quantity.tooltip.formatter([{ dataIndex: 0 }])).toContain("0.5명");
  });
  it("uses supplied waterfall endpoints, leaves unknown steps absent and colors a negative subtotal as a total", () => {
    const option = waterfallOption(
      [
        {
          label: "순이익",
          amount: "-1000000",
          from: 0n,
          to: -1000000n,
          subtotal: true,
        },
        {
          label: "미확인",
          amount: null,
          from: null,
          to: null,
          subtotal: false,
        },
      ],
      "원",
    ) as any;
    expect(option.series[0].data).toEqual([
      [0, 0, -1],
      [1, null, null],
    ]);
    expect(option.series[0].renderItem({}, { value: () => 1 })).toBeUndefined();
  });
});
