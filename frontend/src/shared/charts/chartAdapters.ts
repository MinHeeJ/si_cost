import type { EChartsCoreOption } from "echarts/core";
import type { CustomSeriesRenderItem } from "echarts";
import { chartColors, chartTheme } from "./chartTheme";

export type ChartSeries = {
  name: string;
  values: (string | null)[];
  type?: "line" | "bar";
  dashed?: boolean;
  dashPattern?: [number, number];
  stack?: string;
  color?: string;
};
// Only the coordinate is rounded. Original API strings always feed tooltips and numeric tables.
export function decimalCoordinate(
  raw: string | null,
  power = 0,
): number | null {
  if (raw == null || !/^-?\d+(\.\d+)?$/.test(raw)) return null;
  const negative = raw.startsWith("-"),
    [whole, decimal = ""] = raw.replace(/^-/, "").split(".");
  const digits = (whole + decimal).replace(/^0+/, "");
  if (!digits) return 0;
  return Number(
    `${negative ? "-" : ""}${digits.slice(0, 15)}e${digits.length - Math.min(15, digits.length) - decimal.length - power}`,
  );
}
export function coordinatePower(series: ChartSeries[]) {
  return Math.max(
    0,
    ...series.flatMap((s) =>
      s.values.map((value) =>
        value == null
          ? 0
          : value.replace(/^-/, "").split(".")[0].replace(/^0+/, "").length -
            12,
      ),
    ),
  );
}
export function seriesChartOptions({
  categories,
  series,
  formatValue,
  axisUnit = "",
  guide,
}: {
  categories: string[];
  series: ChartSeries[];
  formatValue(value: string | null): string;
  axisUnit?: string;
  guide?: { name: string; value: string };
}): EChartsCoreOption {
  const unitPower = axisUnit === "백만원" ? 6 : axisUnit === "천원" ? 3 : 0;
  const power = Math.max(
      unitPower,
      coordinatePower(
        guide
          ? [...series, { name: guide.name, values: [guide.value] }]
          : series,
      ),
    ),
    scaleLabel = power > unitPower ? ` ×10^${power - unitPower}` : "";
  // Keep the year when a forecast crosses a year boundary.
  const spansYears = new Set(categories.filter(label => /^\d{4}-\d{2}$/.test(label)).map(label => label.slice(0, 4))).size > 1;
  return {
    ...chartTheme,
    legend: { ...chartTheme.legend, show: series.length > 1, data: series.map(s => ({ name:s.name, icon:s.dashed ? "emptyRect" : "roundRect" })) },
    xAxis: {
      type: "category",
      data: categories,
      axisLabel: { formatter:(label:string) => !spansYears && /^\d{4}-\d{2}$/.test(label) ? `${Number(label.slice(5))}월` : label },
      boundaryGap: series.some((s) => s.type === "bar"),
      axisLine: { lineStyle: { color: "#D5DAE1" } },
      axisTick: { show: false },
    },
    yAxis: {
      type: "value",
      name: `${axisUnit}${scaleLabel}`,
      splitLine: { lineStyle: { color: "#E7EAEE", type: "dashed" } },
    },
    tooltip: {
      ...chartTheme.tooltip,
      axisPointer: { type: series.some(s => s.type === "bar") ? "shadow" : "line", lineStyle:{type:"dashed",color:"#CBD2DC"}, shadowStyle:{color:"rgba(29,78,216,.06)"} },
      formatter: (parameters: unknown) => {
        const items = (
          Array.isArray(parameters) ? parameters : [parameters]
        ) as { dataIndex?: number; seriesIndex?: number }[];
        const index = items[0]?.dataIndex ?? 0,
          box = document.createElement("div");
        const heading = document.createElement("strong");
        heading.textContent = categories[index] ?? "";
        box.append(heading);
        series.forEach((s, i) => {
          const row = document.createElement("div");
          row.style.cssText =
            "display:flex;gap:16px;justify-content:space-between;margin-top:6px";
          const label = document.createElement("span");
          label.textContent = s.name;
          label.style.color = s.color ?? chartColors[i % chartColors.length];
          const value = document.createElement("b");
          value.textContent = formatValue(s.values[index] ?? null);
          row.append(label, value);
          box.append(row);
        });
        const stacked = series.filter(s => s.stack);
        if (stacked.length) {
          const values = stacked.map(s => s.values[index] ?? null);
          const total = document.createElement("div"); total.style.cssText="border-top:1px solid #E7EAEE;margin-top:8px;padding-top:8px;font-weight:700";
          total.textContent = `합계  ${formatValue(exactSum(values))}`; box.append(total);
        }
        return box;
      },
    },
    series: series.map((s, i) => ({
      name: s.name,
      type: s.type ?? "line",
      data: s.values.map((value) => decimalCoordinate(value, power)),
      stack: s.stack,
      connectNulls: false,
      symbol: "circle",
      symbolSize: s.dashed ? 7 : 5,
      showSymbol: true,
      smooth: 0.25,
      smoothMonotone: "x",
      barMaxWidth: 44,
      itemStyle: { color: s.color ?? (s.dashed ? "#94A3B8" : chartColors[i % chartColors.length]), borderRadius:s.stack && series.slice(i+1).some(next=>next.stack===s.stack) ? [0,0,0,0] : [4,4,0,0], borderColor:s.stack ? "#fff" : undefined,borderWidth:s.stack ? 1 : 0 },
      lineStyle: { width: 2, color:s.dashed ? "#94A3B8" : undefined, type: s.dashed ? (s.dashPattern ?? [6,4]) : "solid" },
      areaStyle: i===0 && s.type!=="bar" ? {opacity:.06} : undefined,
      endLabel: {show:s.type!=="bar" && !s.dashed,formatter:(p:{dataIndex:number})=>formatValue(s.values[p.dataIndex]??null)},
      emphasis: { focus: "series" },
      ...(i === 0 && guide
        ? {
            markLine: {
              symbol: "none",
              label: { formatter: guide.name },
              lineStyle: { type: "dashed", color: "#94A3B8" },
              data: [
                {
                  yAxis: decimalCoordinate(guide.value, power),
                  name: guide.name,
                },
              ],
            },
          }
        : {}),
    })),
  };
}

export function waterfallChartOptions(
  steps: {
    label: string;
    value: string | null;
    from: string | null;
    to: string | null;
    subtotal: boolean;
  }[],
  formatValue: (value: string | null) => string,
  formatLabel: (value: string | null) => string = formatValue,
): EChartsCoreOption {
  const power = Math.max(
    6,
    coordinatePower([
      { name: "range", values: steps.flatMap((step) => [step.from, step.to]) },
    ]),
  );
  const kind = (step:typeof steps[number]) => step.subtotal ? "합계 단계" : step.value?.startsWith("-") ? "빼는 항목" : "더하는 항목";
  const render = (group:string):CustomSeriesRenderItem => (params, api) => {
    const step=steps[params.dataIndex];
    if (kind(step)!==group) return;
    if (step.from==null || step.to==null) {
      const anchor=api.coord([params.dataIndex,0]);
      return {type:"text",style:{x:anchor[0],y:120,text:"미확인",align:"center",fill:"#6B7280",fontSize:12}};
    }
    const from=api.coord([params.dataIndex,decimalCoordinate(step.from,power)!]),to=api.coord([params.dataIndex,decimalCoordinate(step.to,power)!]);
    const size=api.size?.([1,0]) as number[]|undefined,width=Math.min(44,(size?.[0]??48)*.5),top=Math.min(from[1],to[1]);
    const negative=step.value?.startsWith("-");
    const fill=step.subtotal ? negative ? "#C81E1E" : {type:"linear",x:0,y:0,x2:0,y2:1,colorStops:[{offset:0,color:"#3D6CF2"},{offset:1,color:"#1D4ED8"}]} : negative ? "#EF4444" : "#0EA5E9";
    const formatted=formatLabel(step.value).replace(/^-/,"−");
    const text=step.subtotal || negative || step.value==null ? formatted : `+${formatted}`;
    const children:any[]=[{type:"rect",shape:{x:from[0]-width/2,y:top,width,height:Math.max(1,Math.abs(to[1]-from[1])),r:4},style:{fill}},
      {type:"text",style:{x:from[0],y:top-10,text,align:"center",verticalAlign:"bottom",fill:step.subtotal?"#0F172A":"#6B7280",fontSize:step.subtotal?12.5:11.5,fontWeight:step.subtotal?700:400}}];
    if(params.dataIndex<steps.length-1 && steps[params.dataIndex+1].from!=null && steps[params.dataIndex+1].to!=null) {const next=api.coord([params.dataIndex+1,decimalCoordinate(step.to,power)!]);children.push({type:"line",shape:{x1:from[0]+width/2,y1:to[1],x2:next[0]-width/2,y2:to[1]},style:{stroke:"#CBD2DC",lineDash:[2,3],lineWidth:1}});}
    return {type:"group",children};
  };
  return {
    ...chartTheme,
    legend: { ...chartTheme.legend, show:true, selectedMode:false, data:["합계 단계","더하는 항목","빼는 항목"] },
    xAxis: {
      type: "category",
      data: steps.map((step) => step.label),
      axisLabel: { interval: 0, rotate: 15 },
      axisTick: { show: false },
    },
    yAxis: {
      type: "value",
      name: power === 6 ? "백만원" : `원 ×10^${power}`,
      splitLine: { lineStyle: { color: "#E7EAEE", type: "dashed" } },
    },
    tooltip: {
      ...chartTheme.tooltip,
      trigger: "item",
      formatter: (params: unknown) => {
        const index = (params as { dataIndex: number }).dataIndex,
          box = document.createElement("div");
        box.textContent = `${steps[index].label}: ${formatValue(steps[index].value)}`;
        return box;
      },
    },
    series: ["합계 단계","더하는 항목","빼는 항목"].map(name=>({
      name,type:"custom",renderItem:render(name),encode:{x:0,y:[1,2]},
      itemStyle:{color:name==="합계 단계"?"#1D4ED8":name==="더하는 항목"?"#0EA5E9":"#EF4444"},
      // Missing steps use an axis anchor only to draw their "미확인" text; render never draws a zero bar.
      data:steps.map((step,index)=>[index,decimalCoordinate(step.from,power)??0,decimalCoordinate(step.to,power)??0])
    })),
  };
}

/** Tooltip totals retain arbitrary precision; coordinates alone may use floating point. */
function exactSum(values:(string|null)[]):string|null {
 if (values.some(v=>v==null || !/^-?\d+(\.\d+)?$/.test(v))) return null;
 const scale=Math.max(0,...values.map(v=>v!.split(".")[1]?.length??0));
 const sum=values.reduce((total,value)=>{ const v=value!,negative=v.startsWith("-"),[whole,decimal=""]=v.replace(/^-/,"").split(".");return total+(negative?-1n:1n)*BigInt(whole+decimal.padEnd(scale,"0")); },0n);
 const digits=(sum<0n?-sum:sum).toString().padStart(scale+1,"0");return `${sum<0n?"-":""}${scale?digits.slice(0,-scale)+"."+digits.slice(-scale):digits}`;
}
