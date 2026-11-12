import { request } from "../../shared/api";
import { downloadB7File, saveDownload } from "../b7/b7Api";

export type DistributionStep = "EST" | "CTRT" | "PLAN";
export type AmountUnit = "원" | "천원" | "백만원";
export type DistributionProject = {
  id: string;
  name: string;
  customerId: string | null;
  contractType: "TK" | "MO" | "DP" | "IN";
  status: "ING" | "PRE" | "END" | "LOSE";
  startDate: string;
  endDate: string;
  workplace: string | null;
  feature: string | null;
  estimateAmount: string | null;
  originalContractAmount: string | null;
  contractAmount: string | null;
  budgetAmount: string | null;
  teamExpenseAmount: string | null;
  projectExpenseAmount: string | null;
  budgetManual: "Y" | "N";
  lossDate: string | null;
  lossReason: string | null;
  lossMemo: string | null;
  opportunityId: string | null;
};
export type DistributionCell = { month: string; amount: string };
export type DistributionRow = {
  rowSn: number;
  rowType: "DEV" | "ITEM";
  developerId: string | null;
  managementNo: string | null;
  name: string | null;
  itemName: string | null;
  jobNames: string[];
  useYn: "Y" | "N" | null;
  cells: DistributionCell[];
  totalAmount: string;
};
export type ProjectDistributionData = {
  project: DistributionProject;
  viewedStep: DistributionStep;
  selectedStep: DistributionStep | null;
  months: string[];
  rows: DistributionRow[];
  monthlyTotals: DistributionCell[];
  totalAmount: string;
  revision: string;
};
export type ProjectMismatch = {
  developerId: string;
  managementNo: string;
  name: string;
  month: string;
  type: "NO_ASSIGNMENT" | "NO_DISTRIBUTION";
  amount: string;
  actualMm: string;
};
export type ProjectDistributionComparison = {
  selectedStep: DistributionStep | null;
  contractAmount: string;
  totalAmount: string;
  differenceAmount: string;
  mismatchCount: number;
  items: ProjectMismatch[];
  warnings: string[];
};
export type ProjectUploadRecord = {
  uploadId: number;
  step: DistributionStep;
  fileName: string;
  actor: string;
  registeredAt: string;
  status: "CHECKED" | "APPLIED";
  appliedAt: string | null;
};
export type DistributionUploadResult = {
  uploadId: number;
  formType: "B6" | "B9";
  status: "CHECKED" | "APPLIED";
  projectId: string | null;
  step: DistributionStep | null;
  summary: {
    totalRows: number;
    successRows: number;
    warningRows: number;
    errorRows: number;
    fileWarningCount: number;
    tableWarningCount: number;
  };
  results: { rowNumber: number; id: string; name: string }[];
  revision: string | null;
  changed: boolean;
};
const base = (id: string) =>
  `/api/projects/${encodeURIComponent(id)}/distribution`;
export const getProjectDistribution = (id: string, step: DistributionStep) =>
  request<ProjectDistributionData>(
    `${base(id)}?${new URLSearchParams({ step })}`,
  );
export const getProjectDistributionComparison = (id: string) =>
  request<ProjectDistributionComparison>(`${base(id)}/comparison`);
export const getProjectDistributionUploads = (id: string) =>
  request<{ items: ProjectUploadRecord[] }>(`${base(id)}/uploads`);
export const getDistributionUploadResult = (uploadId: number) =>
  request<DistributionUploadResult>(`/api/uploads/${uploadId}/result`);
export const downloadDistributionTemplate = async (projectId: string) =>
  saveDownload(
    await downloadB7File(
      `/api/excel/B6/template?${new URLSearchParams({ projectId })}`,
    ),
  );
export const downloadDistribution = async (
  projectId: string,
  step: DistributionStep,
  amountUnit: AmountUnit,
) =>
  saveDownload(
    await downloadB7File("/api/exports/master-data", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        view: "PROJECT_DISTRIBUTION",
        projectId,
        step,
        amountUnit,
      }),
    }),
  );
