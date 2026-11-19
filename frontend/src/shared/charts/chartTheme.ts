export const chartColors = [
  "#1D4ED8",
  "#1E9E5A",
  "#7C5CE0",
  "#0EA5E9",
  "#DB4F8C",
  "#94A3B8",
];
export const chartTheme = {
  color: chartColors,
  textStyle: {
    fontFamily:
      '"Pretendard Variable", Pretendard, "Malgun Gothic", sans-serif',
    color: "#4B5563",
    fontSize: 12,
  },
  animationDuration: 700,
  animationDurationUpdate: 400,
  grid: { left: 62, right: 24, top: 44, bottom: 38, containLabel: true },
  legend: {
    top: 4,
    itemWidth: 14,
    itemHeight: 8,
    textStyle: { color: "#4B5563", fontSize: 12 },
  },
  tooltip: {
    trigger: "axis",
    backgroundColor: "#fff",
    borderColor: "#E7EAEE",
    textStyle: { color: "#111827" },
    extraCssText: "border-radius:8px;box-shadow:0 6px 20px rgba(17,24,39,.12);",
  },
};
