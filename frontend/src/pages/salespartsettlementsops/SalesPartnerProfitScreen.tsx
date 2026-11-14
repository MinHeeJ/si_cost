import { useRef, useState } from "react";
import {
  SalesPartnerProfitScreen as SettlementReadScreen,
  type SalesPartnerProfitScreenProps,
} from "../salespartsettlements/SalesPartnerProfitScreen";
import { SettlementCardInput } from "./SettlementCardInput";
import { SettlementTrendPanel } from "./SettlementTrendPanel";
export function SalesPartnerProfitScreen(
  props: SalesPartnerProfitScreenProps = {},
) {
  const [trend, setTrend] = useState(false);
  const guard = useRef<(proceed: () => void) => void>((proceed) => proceed());
  return (
    <SettlementReadScreen
      {...props}
      showMonthTable={!trend}
      guardNavigation={(proceed) => guard.current(proceed)}
      renderCard={(data, refresh) => (
        <SettlementCardInput
          data={data}
          refresh={refresh}
          onGuard={(next) => {
            guard.current = next;
          }}
          trend={trend}
          onView={setTrend}
        />
      )}
      renderViews={(data) => (
        <>
          {trend && (
            <SettlementTrendPanel partId={data.partId} month={data.month} />
          )}
        </>
      )}
    />
  );
}
