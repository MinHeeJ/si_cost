import type { ComponentType } from "react";
import { screenPeriods, type PeriodType } from "./periods";
import { DashboardScreen } from "./DashboardScreen";
import { BusinessPartnersScreen } from "./BusinessPartnersScreen";
import { DeveloperPoolScreen } from "./DeveloperPoolScreen";
import { ProjectsScreen } from "./ProjectsScreen";
import { ProjectStatusBoardScreen } from "./ProjectStatusBoardScreen";
import { StaffingAssignmentsScreen } from "./StaffingAssignmentsScreen";
import { WithdrawalListScreen } from "./WithdrawalListScreen";
import { StaffingPlanComparisonScreen } from "./StaffingPlanComparisonScreen";
import { WorkforceOperationsScreen } from "./WorkforceOperationsScreen";
import { WorkforceDemandForecastScreen } from "./WorkforceDemandForecastScreen";
import { SalesPipelineScreen } from "./SalesPipelineScreen";
import { ExternalCandidatesScreen } from "./ExternalCandidatesScreen";
import { BillingReceiptsScreen } from "./BillingReceiptsScreen";
import { IndividualProfitabilityScreen } from "./IndividualProfitabilityScreen";
import { ProjectCostAnalysisScreen } from "./ProjectCostAnalysisScreen";
import { SalesPartnerProfitScreen } from "./SalesPartnerProfitScreen";
import { RateRenegotiationsScreen } from "./RateRenegotiationsScreen";
import { FinancialItemsScreen } from "./FinancialItemsScreen";
import { ProfitStructureScreen } from "./ProfitStructureScreen";
import { OperatingProfitScreen } from "./OperatingProfitScreen";
import { NetProfitScreen } from "./NetProfitScreen";
import { BusinessPlanPerformanceScreen } from "./BusinessPlanPerformanceScreen";
import { CashFlowScreen } from "./CashFlowScreen";
import { MonthCloseScreen } from "./MonthCloseScreen";
import { AnalysisAssistantScreen } from "./AnalysisAssistantScreen";
import { WorkforceRecommendationsScreen } from "./WorkforceRecommendationsScreen";
import { UsersScreen } from "./UsersScreen";
import { PermissionsScreen } from "./PermissionsScreen";
import { MenusScreen } from "./MenusScreen";
import { CommonCodesScreen } from "./CommonCodesScreen";
const components: Record<string, ComponentType> = {
  "UI-16": DashboardScreen,
  "UI-15": BusinessPartnersScreen,
  "UI-02": DeveloperPoolScreen,
  "UI-01": ProjectsScreen,
  "UI-31": ProjectStatusBoardScreen,
  "UI-12": StaffingAssignmentsScreen,
  "UI-05": WithdrawalListScreen,
  "UI-11": StaffingPlanComparisonScreen,
  "UI-28": WorkforceOperationsScreen,
  "UI-17": WorkforceDemandForecastScreen,
  "UI-29": SalesPipelineScreen,
  "UI-33": ExternalCandidatesScreen,
  "UI-13": BillingReceiptsScreen,
  "UI-03": IndividualProfitabilityScreen,
  "UI-04": ProjectCostAnalysisScreen,
  "UI-19": SalesPartnerProfitScreen,
  "UI-32": RateRenegotiationsScreen,
  "UI-10": FinancialItemsScreen,
  "UI-07": ProfitStructureScreen,
  "UI-08": OperatingProfitScreen,
  "UI-09": NetProfitScreen,
  "UI-18": BusinessPlanPerformanceScreen,
  "UI-21": CashFlowScreen,
  "UI-14": MonthCloseScreen,
  "UI-26": AnalysisAssistantScreen,
  "UI-30": WorkforceRecommendationsScreen,
  "UI-23": UsersScreen,
  "UI-24": PermissionsScreen,
  "UI-25": MenusScreen,
  "UI-27": CommonCodesScreen,
};
export const screenRegistry: Record<
  string,
  { component: ComponentType; periodType: PeriodType }
> = Object.fromEntries(
  Object.entries(components).map(([id, component]) => [
    id,
    { component, periodType: screenPeriods[id] },
  ]),
);
