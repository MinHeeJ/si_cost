import { createContext, useContext, type ReactNode } from "react";
import { createPortal } from "react-dom";
export const PeriodStatusTarget = createContext<HTMLElement|null|undefined>(undefined);
export function PeriodStatus({children}:{children:ReactNode}) {
 const target=useContext(PeriodStatusTarget);
 if(target===undefined)return <div className="sicost-period-status" role="group" aria-label="조회 기간 상태">{children}</div>;
 return target ? createPortal(<div className="sicost-period-status" role="group" aria-label="조회 기간 상태">{children}</div>,target) : null;
}
