import { afterEach, expect, it, vi } from "vitest";
import { ApiError } from "../../shared/api";
import {
  getProjectDistribution,
  getProjectDistributionComparison,
  getProjectDistributionUploads,
  getDistributionUploadResult,
  downloadDistributionTemplate,
  downloadDistribution,
} from "./distributionApi";
import * as downloads from "../b7/b7Api";
vi.mock("../b7/b7Api", () => ({
  downloadB7File: vi
    .fn()
    .mockResolvedValue({ blob: new Blob(), filename: "배분표.xlsx" }),
  saveDownload: vi.fn(),
}));
afterEach(() => {
  vi.clearAllMocks();
  vi.unstubAllGlobals();
});
it("uses selected project identity, required viewed step and no extra year or auth", async () => {
  const fetcher = vi.fn().mockResolvedValue({
    ok: true,
    status: 200,
    text: async () =>
      JSON.stringify({ success: true, meta: {}, data: { items: [] } }),
  });
  vi.stubGlobal("fetch", fetcher);
  await getProjectDistribution("P-selected", "CTRT");
  await getProjectDistributionComparison("P-selected");
  await getProjectDistributionUploads("P-selected");
  await getDistributionUploadResult(91);
  expect(fetcher.mock.calls.map((call) => call[0])).toEqual([
    "/api/projects/P-selected/distribution?step=CTRT",
    "/api/projects/P-selected/distribution/comparison",
    "/api/projects/P-selected/distribution/uploads",
    "/api/uploads/91/result",
  ]);
  for (const call of fetcher.mock.calls)
    expect(call[1].headers.has("Authorization")).toBe(false);
});
it("template sends only projectId while export dispatch uses approved view and exact viewed step", async () => {
  await downloadDistributionTemplate("P-selected");
  await downloadDistribution("P-selected", "PLAN", "백만원");
  expect(downloads.downloadB7File).toHaveBeenNthCalledWith(
    1,
    "/api/excel/B6/template?projectId=P-selected",
  );
  expect(downloads.downloadB7File).toHaveBeenNthCalledWith(
    2,
    "/api/exports/master-data",
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        view: "PROJECT_DISTRIBUTION",
        projectId: "P-selected",
        step: "PLAN",
        amountUnit: "백만원",
      }),
    },
  );
  expect(downloads.saveDownload).toHaveBeenCalledTimes(2);
});
it("surfaces root ApiError with no automatic retries", async () => {
  const fetcher = vi.fn().mockResolvedValue({
    ok: false,
    status: 400,
    text: async () =>
      JSON.stringify({
        success: false,
        meta: {},
        code: "INVALID_INPUT",
        field: "step",
        message: "단계 오류",
        requestId: "distribution-api",
      }),
  });
  vi.stubGlobal("fetch", fetcher);
  await expect(
    getProjectDistribution("P-selected", "EST"),
  ).rejects.toBeInstanceOf(ApiError);
  expect(fetcher).toHaveBeenCalledOnce();
});
