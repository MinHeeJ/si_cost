import type { ReactNode } from "react";
import { SharedScreenFrame } from "./FollowUpScreen";
import {
  IndividualProfitabilityScreen as ProfitabilityBody,
  type IndividualProfitabilityScreenProps,
} from "../profitability/IndividualProfitabilityScreen";
import { ProfitabilityExportButton } from "../profitability/ProfitabilityExportButton";
import { ProfitabilityCalculationGuide } from "../profitability/ProfitabilityCalculationGuide";
/** Keep this registered entry and its mounted work-tab identity during body assembly. */
export function IndividualProfitabilityScreen({
  children,
  ...slots
}: IndividualProfitabilityScreenProps & { children?: ReactNode } = {}) {
  return (
    <SharedScreenFrame screenId="UI-03">
      <div data-screen-id="UI-03" data-testid="individual-profitability-screen">
        {children ?? (
          <ProfitabilityBody
            renderExport={(query) => (
              <ProfitabilityExportButton query={query} />
            )}
            renderCalculationGuide={(data) => (
              <ProfitabilityCalculationGuide data={data} />
            )}
            {...slots}
          />
        )}
      </div>
    </SharedScreenFrame>
  );
}
