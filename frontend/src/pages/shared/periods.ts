export type PeriodType = "YEAR" | "YEAR_MONTH" | "YEAR_CUM" | "NONE";
export const screenPeriods: Record<string, PeriodType> = {
  "UI-16": "YEAR_MONTH",
  "UI-15": "NONE",
  "UI-02": "NONE",
  "UI-01": "YEAR",
  "UI-31": "NONE",
  "UI-12": "YEAR",
  "UI-05": "YEAR",
  "UI-11": "YEAR",
  "UI-28": "YEAR",
  "UI-17": "YEAR",
  "UI-29": "NONE",
  "UI-33": "NONE",
  "UI-13": "YEAR",
  "UI-03": "YEAR",
  "UI-04": "YEAR_MONTH",
  "UI-19": "YEAR_MONTH",
  "UI-32": "NONE",
  "UI-10": "YEAR_MONTH",
  "UI-07": "YEAR_CUM",
  "UI-08": "YEAR_CUM",
  "UI-09": "YEAR_CUM",
  "UI-18": "YEAR_CUM",
  "UI-21": "YEAR",
  "UI-14": "YEAR_MONTH",
  "UI-26": "NONE",
  "UI-30": "NONE",
  "UI-23": "NONE",
  "UI-24": "NONE",
  "UI-25": "NONE",
  "UI-27": "NONE",
};
export function periodOptions(
  type: "YEAR" | "YEAR_MONTH",
  min: string,
  max: string,
): string[] {
  if (min > max) return [];
  const result: string[] = [];
  if (type === "YEAR")
    for (let year = Number(min); year <= Number(max); year++)
      result.push(String(year).padStart(4, "0"));
  else {
    let [year, month] = min.split("-").map(Number);
    while (
      `${String(year).padStart(4, "0")}-${String(month).padStart(2, "0")}` <=
      max
    ) {
      result.push(
        `${String(year).padStart(4, "0")}-${String(month).padStart(2, "0")}`,
      );
      if (++month === 13) {
        month = 1;
        year++;
      }
    }
  }
  return result;
}
export const clampPeriod = (value: string, min: string, max: string) =>
  value < min ? min : value > max ? max : value;
