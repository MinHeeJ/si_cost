import { DeveloperDrawer } from "../developers/DeveloperDrawer";

/** Reuse the developer detail and its peopleClient. No profitability-specific editor or detail copy. */
export function ProfitabilityDetails({
  personId,
  onClose,
}: {
  personId: string;
  onClose(): void;
}) {
  return <DeveloperDrawer key={personId} id={personId} onClose={onClose} />;
}
