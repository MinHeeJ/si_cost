export const chartColors = [
  "#1d4ed8",
  "#1e9e5a",
  "#7c5ce0",
  "#0ea5e9",
  "#db4f8c",
  "#94a3b8",
];
export const chartTheme = {
  color: chartColors,
  backgroundColor: "transparent",
  textStyle: {
    fontFamily:
      '"Pretendard Variable", "Pretendard", "Noto Sans KR", sans-serif',
    color: "#4b5563",
    fontSize: 12,
  },
  animationDuration: 700,
  animationDurationUpdate: 400,
  animationEasing: "cubicOut",
  grid: { left: 16, right: 24, top: 48, bottom: 24, containLabel: true },
  legend: {
    top: 4,
    type: "scroll",
    textStyle: { color: "#4b5563", fontSize: 12 },
    itemWidth: 16,
    itemHeight: 8,
  },
  tooltip: {
    trigger: "axis",
    renderMode: "richText",
    backgroundColor: "#fff",
    borderColor: "#d5dae1",
    textStyle: { color: "#111827", fontSize: 12 },
    confine: true,
  },
  xAxis: {
    type: "category",
    axisLine: { lineStyle: { color: "#d5dae1" } },
    axisTick: { show: false },
    axisLabel: { color: "#4b5563" },
  },
  yAxis: {
    type: "value",
    axisLine: { show: false },
    axisTick: { show: false },
    splitLine: { lineStyle: { color: "#e7eaee", type: "dashed" } },
  },
};
