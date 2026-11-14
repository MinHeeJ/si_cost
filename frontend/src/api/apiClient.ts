import { request } from "../shared/api";

export { request, ApiError } from "../shared/api";
export type { ApiEnvelope, ApiErrorBody } from "../shared/api";
export {
  getCurrentUser,
  getMenus,
  getPeriodContext,
  getMonthClose,
  getCommonCodeHistory,
  getSharedCodes as getCodes,
  getSharedParameter as getParameter,
} from "../pages/shared/sharedApi";
export {
  getPreferences,
  putPreference,
  deletePreference,
} from "../pages/me/preferencesApi";
export {
  getCodeGroups,
  createCodeGroup,
  getGroupCodes,
  saveGroupCodes,
  deleteGroupCode,
} from "../pages/codegroups/codeGroupsApi";
export {
  getCodeMappings,
  createCodeMapping,
  deleteCodeMappings,
} from "../pages/codemappings/codeMappingsApi";
export {
  getOperatingStandards,
  saveOperatingStandards,
  getOperatingStandardHistory,
} from "../pages/operatingstandards/operatingStandardsApi";

export const getHealth = () => request<void>("/api/health");

export {
  getProjectCosts,
  getProjectStatusBoard,
} from "../pages/apiops/projectAnalysisApi";
export {
  getProjectCostAnalysis,
  getProjectCostMonths,
  getProjectCostAssignments,
  getProjectCostCauses,
  createProjectCostSimulation,
} from "../pages/costanalysis/projectCostApi";
export {
  getProjectCostSummary,
  getProjectCostExport,
} from "../pages/projectcostsops/projectCostExportApi";
export {
  getProjectStatusBoardSummary,
  getProjectAnalysisDownload,
} from "../pages/projectstatusboardops/projectStatusApi";
export {
  getProjectProgress,
  getProjectProgressHistory,
  createProjectProgressSteps,
  createProjectStepDatesPreview,
  saveProjectStepDates,
  saveProjectStepCompletion,
  createProjectWeekMemo,
  putProjectWeekMemo,
} from "../pages/progress/projectProgressApi";
export {
  getProjectIssues,
  getProjectStatusIssues,
  createProjectIssue,
  putProjectIssue,
} from "../pages/issues/projectIssuesApi";

export {
  getBusinessUsers,
  getBusinessUser,
  createBusinessUser,
  putBusinessUser,
} from "../pages/businessusers/businessUsersApi";
export { getBusinessUserOptions } from "../pages/businessusersops/businessUserOptionsApi";
export {
  getClients,
  getClient,
  createClient,
  putClient,
} from "../pages/clients/clientsApi";
export {
  getClientOptions,
  getClientSelection,
  getClientAccount,
  getClientAssignments,
  saveClientBulkStatus,
} from "../pages/clientsops/clientActionsApi";
export {
  getSalesParts,
  getSalesPart,
  createSalesPart,
  putSalesPart,
} from "../pages/salesparts/salesPartsApi";
export {
  getSalesPartDefaults,
  getSalesPartOptions,
  createSalesPartStandard,
} from "../pages/salespartsops/partStandardsApi";
export { getMasterHistory } from "../pages/apiops/masterHistoryApi";
export {
  getB7Template,
  createB7Validation,
  saveB7Apply,
} from "../pages/b7/b7Api";
export {
  getUploadIssues,
  getUploadIssuesXlsx,
} from "../pages/uploadsops/uploadIssuesApi";
export { createMasterDataExport } from "../pages/exportsops/masterDataExportApi";
export {
  getAreaSidos,
  getAreaChildren,
  getAreaPath,
} from "../shared/areaClient";
export {
  getDevelopers,
  getDeveloper,
  createDeveloper,
  putDeveloper,
} from "../pages/developers/peopleClient";
export {
  getDeveloperSelection,
  getDeveloperAssignments,
  saveDeveloperBulkStatus,
} from "../pages/developersops/peopleClient";
export {
  getDeveloperStates,
  createDeveloperState,
  putDeveloperStateEnd,
  saveDeveloperStateCancel,
} from "../pages/states/developerStatesApi";
export {
  getCandidates,
  getCandidate,
  createCandidate,
  putCandidate,
} from "../pages/candidates/peopleClient";
export { getCandidateSummary } from "../pages/candidatesops/candidateSummaryApi";
export type { CandidateSummary } from "../pages/candidatesops/candidateSummaryApi";
export {
  getCandidateContacts,
  createCandidateContact,
} from "../pages/contacts/peopleClient";
export { getPeopleHistory } from "../pages/apiops/peopleClient";
export {
  getB8Template,
  createB8Validation,
  saveB8Apply,
} from "../pages/b8/b8Api";
export {
  getProjects,
  getProject,
  createProject,
  putProject,
  getProjectDefaults,
} from "../pages/projects/projectsApi";
export {
  getProjectOptions,
  getProjectLossPreview,
  saveProjectProgress,
  saveProjectLoss,
} from "../pages/projectsops/projectStatusApi";
export {
  getProjectBudget,
  saveProjectBudget,
} from "../pages/budget/projectBudgetApi";
export {
  getProjectParts,
  saveProjectPart,
} from "../pages/parts/projectPartsApi";
export {
  getProjectPlans,
  saveProjectPlans,
} from "../pages/plans/projectPlansApi";
export {
  getProjectExpenses,
  createProjectExpense,
  deleteProjectExpense,
} from "../pages/expenses/projectExpensesApi";
export {
  getBusinessUserProjects,
  getUserProjectPm as getProjectPm,
  assignUserProject as saveProjectPm,
  detachUserProject as deleteProjectPm,
} from "../pages/businessusersops/userProjectsApi";
export {
  getProjectDistribution,
  getProjectDistributionComparison,
  getProjectDistributionUploads,
} from "../pages/distribution/distributionApi";
export {
  createB9Validation,
  saveB9Apply,
  getB9Template,
  getUploadResult,
} from "../pages/b9/b9Api";
export {
  createB6Validation,
  saveB6Apply,
  getB6Template,
} from "../pages/b6/b6Api";
export {
  getAssignments,
  getAssignment,
  getMonths as getAssignmentMonths,
  preview as createAssignmentPreview,
  createAssignment,
  changeAssignment as createAssignmentChange,
  selectAssignments as createAssignmentSelection,
  saveBulk as saveAssignmentBulk,
  candidates as getAssignmentSubstitutionCandidates,
  substitutionPreview as createAssignmentSubstitutionPreview,
  substitute as createAssignmentSubstitution,
} from "../pages/assignmentsops/assignmentActionsApi";
export {
  getAssignmentHistory,
  createAssignmentHistoryCorrection,
} from "../pages/history/assignmentHistoryApi";
export {
  getPayroll,
  getPayrollUploads,
  getPayrollResult,
  getPayrollTemplate,
  createB1Validation,
  createB1Apply,
  exportPayroll,
} from "../pages/apiops/payrollApi";
export {
  getB5Template,
  createB5Validation,
  createB5Apply,
  getB5Result,
} from "../pages/b5/b5Api";
export {
  downloadAssignments,
  assignmentColumns,
} from "../pages/assignments/assignmentDownloadsApi";
export {
  getBills,
  getBill,
  createBill,
  putBill,
} from "../pages/bills/billsApi";
export {
  getBillEstimate,
  getBillsSummary,
  getBillSelection,
  getBillCopyDraft,
  saveBillCancellation,
  createNextMonthBill,
  createCopiedBill,
} from "../pages/billsops/billOperationsApi";
export {
  getBillReceipts,
  createBillReceipt,
  saveBillReceiptCancellation,
} from "../pages/receipts/receiptsApi";
export {
  loadReceiptBatchSelection,
  createBatchReceipts,
} from "../pages/receiptsops/receiptBatchApi";
export {
  getBillingChecks,
  getReceivables,
} from "../pages/apiops/billingReviewApi";
export { downloadBillingReview } from "../pages/apiops/billingReviewExportApi";
export {
  getFinancialItems,
  saveFinancialItems,
} from "../pages/financialitems/financialApi";
export {
  getFinancialYearStatus,
  createFinancialPreview,
} from "../pages/financialitemsops/financialPreviewApi";
export {
  createCorporateTaxDefaults,
  getCorporateTaxAllocation,
  saveCorporateTaxPaymentConfirmation,
} from "../pages/corporatetaxesops/corporateTaxApi";
export {
  getFinancialContributions,
  getFinancialHistory,
} from "../pages/apiops/financialReadApi";
export {
  getB2Template,
  createB2Validation,
  saveB2Apply,
  getFinancialUploadResult,
} from "../pages/b2/financialImportApi";
