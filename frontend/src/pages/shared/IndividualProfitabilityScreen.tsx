import type { ReactNode } from "react";
import { useInRouterContext, useLocation } from "react-router-dom";
import { SharedScreenFrame } from "./FollowUpScreen";
import {
  IndividualProfitabilityScreen as ProfitabilityBody,
  type IndividualProfitabilityScreenProps,
} from "../profitability/IndividualProfitabilityScreen";
import { ProfitabilityExportButton } from "../profitability/ProfitabilityExportButton";
import { ProfitabilityCalculationGuide } from "../profitability/ProfitabilityCalculationGuide";
/** Keep this registered entry and its mounted work-tab identity during body assembly. */
type EntryProps = IndividualProfitabilityScreenProps & { children?: ReactNode };
export function IndividualProfitabilityScreen(props: EntryProps = {}) {
  const routed = useInRouterContext();
  return routed ? (
    <RoutedProfitabilityEntry {...props} />
  ) : (
    <ProfitabilityEntry {...props} />
  );
}
function RoutedProfitabilityEntry(props: EntryProps) {
  const location = useLocation();
  const incoming = location.state as {
    fromAnalysis?: boolean;
    month?: string;
    developerId?: string;
  } | null;
  const analysisEntry =
    location.pathname === "/UI-03" &&
    incoming?.fromAnalysis &&
    incoming.month &&
    /^\d{4}-(0[1-9]|1[0-2])$/.test(incoming.month)
      ? {
          key: location.key,
          year: Number(incoming.month.slice(0, 4)),
          developerId: incoming.developerId,
        }
      : undefined;
  return <ProfitabilityEntry {...props} analysisEntry={analysisEntry} />;
}
function ProfitabilityEntry({ children, ...slots }: EntryProps) {
  return (
    <SharedScreenFrame screenId="UI-03">
      <div data-screen-id="UI-03" data-testid="individual-profitability-screen">
        {children ?? (
          <ProfitabilityBody
            renderExport={(query) => (
              <ProfitabilityExportButton query={query} />
            )}
            renderCalculationGuide={(data, query) => (
              <ProfitabilityCalculationGuide data={data} view={query.view} />
            )}
            {...slots}
          />
        )}
      </div>
    </SharedScreenFrame>
  );
}
