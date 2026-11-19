import { useContext } from "react";
import { ScreenPermissionContext } from "./permission/PermissionBoundary";
import { HelpTooltip } from "./HelpTooltip";
import { helpContent } from "./helpContent";
const aliases: Record<string,string> = {"초기 상태":"상태","견적 금액":"예상 금액","월급여·지급액":"월 급여·지급액","시도":"거주지(시·도)","시군구":"거주지(시·군·구)","읍면동":"거주지(동)","관리 번호":"관리번호","월MM":"월 MM"};
const normalize = (v:string) => v.replace(/\s/g, "").replace(/[·ㆍ]/g,"·");
export function FieldHelp({ label, screenId, kind = "A" }: {label:string; screenId?:string; kind?:"A"|"B"}) {
 const current = useContext(ScreenPermissionContext),screen=screenId ?? current;
 const cleanLabel=label.replace(/\((?:원(?:\/월)?|%|천원|백만원)\)/g, "").replace(/[*ⓘ]/g, "").trim();
 const additional: Record<string,string>={"예상 최종 마진율":"예상 최종·월간 마진율","월간 마진율":"예상 최종·월간 마진율"};
 const target = aliases[cleanLabel] ?? additional[cleanLabel] ?? cleanLabel;
 const entry = helpContent.find(e=> e.screen===screen && e.kind===kind && normalize(e.target.replace(/^'|' 체크$/g,""))===normalize(target));
 if (!entry) return null;
 return <HelpTooltip label={cleanLabel} iconOnly>{entry.text}</HelpTooltip>;
}
