import type { ReactNode } from "react";
import { StatusBadge } from "./Feedback";
import { HelpTooltip } from "./HelpTooltip";
import { PeriodStatus } from "./PeriodStatus";
export function getFinancialWarnings(
  notices: string[],
  { excludeCompleteCoverage = false } = {},
) {
  return [...new Set(notices.map((notice) => notice.trim()).filter(Boolean))]
    .filter((notice) => !/(^잠정[ :：]|미마감 월은|미마감 월이 포함|마감 전이라|^예정 포함)/.test(notice))
    .filter((notice) => {
      if (!excludeCompleteCoverage) return true;
      const coverage = notice.match(/^(?:전년 )?자료\s+(\d+)\/(\d+)개월$/);
      return !coverage || coverage[1] !== coverage[2];
    });
}
/** Preserve every actionable notice, while showing period state exactly once in its shared slot. */
export function FinancialOverview({context,months,provisional,notices=[],unitControl,caption,periodDetail,comparison="전년 같은 기간"}:{context:{year:number;month:string;nowMonth:string};months:{month:string;sourceKind:string;completeness?:{estimated:string[]}}[];provisional:boolean;notices?:string[];unitControl?:ReactNode;caption?:ReactNode;periodDetail?:ReactNode;comparison?:ReactNode}) {
 const closed=months.filter(m=>m.sourceKind==="CLOSED_SNAPSHOT").map(m=>m.month).sort().at(-1);
 const relevant=months.filter(m=>m.month<=context.month);
 const complete=relevant.length>0 && relevant.every(m=>["CLOSED_SNAPSHOT","MIGRATED"].includes(m.sourceKind));
 const warnings=getFinancialWarnings(notices);
 const estimated=[...new Set(months.filter(m=>m.month<=context.month).flatMap(m=>m.completeness?.estimated??[]))];
 const groups=new Map<string,number>();warnings.forEach(n=>{const text=n.replace(/^\d{4}-\d{2}\s*/,'');const key=text.includes('：')||text.includes(':')?text.split(/[:：]/)[0]:text.includes('사업계획')?'사업계획':text.startsWith('자료 ')?'자료 범위':'기타 안내';groups.set(key,(groups.get(key)??0)+1)});
 return <>
  <PeriodStatus><span>마감 완료 <b>{closed?`${Number(closed.slice(5))}월까지`:"없음"}</b></span><span><StatusBadge tone={provisional?"warn":complete?"lock":"neutral"}>{provisional?"잠정":complete?"확정":"미확인"}</StatusBadge> <b>{Number(context.month.slice(5))}월</b>{provisional?"은 마감 전이라 요약 칸과 누계가 바뀔 수 있습니다.":complete?"까지 확정된 자료입니다.":" 자료 상태를 확인해 주세요."}</span>{estimated.length>0 && <HelpTooltip label="예정 포함" trigger={<StatusBadge tone="info">예정 포함</StatusBadge>}>{estimated.join(" · ")}</HelpTooltip>}{periodDetail}</PeriodStatus>
  {(caption !== null || unitControl) && <div className="financial-caption"><span>{caption === undefined ? <>{context.year}년 · {context.month}까지 실적 · {comparison}(1~{Number(context.month.slice(5))}월)과 비교 · 부가세 제외</> : caption}</span>{unitControl}</div>}
  {warnings.length>0 && <details className="financial-notice-summary"><summary><span aria-hidden="true">⚠</span> 확인 필요 <b>{warnings.length}건</b> <span>({[...groups].map(([name,count])=>`${name} ${count}`).join(" · ")})</span> <span className="sicost-text-link">자세히</span></summary><ul>{warnings.map(n=><li key={n}>{n}</li>)}</ul></details>}
 </>;
}
