import { usePreferences } from "../me/usePreferences";
import type { AmountUnit } from "../../shared/format";
import { ErrorBanner } from "../../shared/Feedback";
import { SettlementActions } from "./SettlementActions";
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
  const preferences = usePreferences();
  const [error, setError] = useState("");
  const storedUnit = preferences.get("AMT_UNIT")?.value;
  const unit: AmountUnit = storedUnit === "원" || storedUnit === "백만원" ? storedUnit : "천원";
  const changeUnit = (value: AmountUnit) => { setError(""); void preferences.put("AMT_UNIT", "*", value).catch((failure: unknown) => setError(failure instanceof Error ? failure.message : "단위 설정을 저장하지 못했습니다.")); };
  const guard = useRef<(proceed: () => void) => void>((proceed) => proceed());
  return (
    <>
    {error && <ErrorBanner message={error} />}
    <SettlementReadScreen
      {...props}
      amountUnit={unit}
      onAmountUnitChange={changeUnit}
      showMonthTable={!trend}
      renderActions={(data, _refresh, query) => <SettlementActions data={data} query={query} trend={trend} unit={unit} />}
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
            <SettlementTrendPanel partId={data.partId} month={data.month} amountUnit={unit} onAmountUnitChange={changeUnit} />
          )}
        </>
      )}
    />
    </>
  );
}
