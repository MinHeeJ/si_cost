import { DashboardScreen as Dashboard } from "../dashboardops/DashboardScreen";
import { usePreferences } from "../me/usePreferences";
export function DashboardScreen() {
  const preferences = usePreferences();
  const unit = preferences.get("AMT_UNIT")?.value;
  return (
    <Dashboard
      amountUnit={unit === "원" || unit === "백만원" ? unit : "천원"}
    />
  );
}
