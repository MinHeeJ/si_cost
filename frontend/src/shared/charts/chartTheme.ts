export const chartTheme = {
  color: ["#1d4ed8", "#f59e0b", "#10b981", "#8b5cf6", "#ec4899", "#6b7280"],
  textStyle: {
    fontFamily:
      '"Pretendard Variable", Pretendard, "Malgun Gothic", sans-serif',
    color: "#4b5563",
    fontSize: 12,
  },
  animationDuration: 700,
  animationDurationUpdate: 400,
  grid: { top: 42, right: 24, bottom: 40, left: 68, containLabel: true },
  legend: { top: 0, icon: "roundRect", itemWidth: 12, itemHeight: 8 },
  tooltip: {
    trigger: "axis",
    renderMode: "richText",
    backgroundColor: "#fff",
    borderColor: "#e7eaee",
    textStyle: { color: "#111827", fontSize: 12 },
    extraCssText: "box-shadow:0 6px 16px #11182712;border-radius:8px",
  },
  xAxis: {
    type: "category",
    axisLine: { lineStyle: { color: "#d5dae1" } },
    axisTick: { show: false },
  },
  yAxis: {
    type: "value",
    splitLine: { lineStyle: { color: "#e7eaee" } },
    axisLine: { show: false },
  },
};
