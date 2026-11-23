import { stubBusinessFetch } from "../../shared/testSupport/businessFixtures";
import { afterEach, expect, it, vi } from "vitest";
import { ApiError } from "../../shared/api";
import {
  getProjectDistribution,
  getProjectDistributionComparison,
  getProjectDistributionUploads,
  getDistributionUploadResult,
  downloadDistributionTemplate,
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
  stubBusinessFetch(fetcher);
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
it("template sends only projectId and saves the returned file without a master-data export", async () => {
  await downloadDistributionTemplate("P-selected");
  expect(downloads.downloadB7File).toHaveBeenNthCalledWith(
    1,
    "/api/excel/B6/template?projectId=P-selected",
  );
  expect(downloads.downloadB7File).toHaveBeenCalledTimes(1);
  expect(downloads.saveDownload).toHaveBeenCalledTimes(1);
});
it("surfaces root ApiError with no automatic retries", async () => {
  const fetcher = vi.fn().mockImplementation(
    async () =>
      new Response(
        JSON.stringify({
          success: false,
          meta: {},
          code: "INVALID_INPUT",
          field: "step",
          message: "단계 오류",
          requestId: "distribution-api",
        }),
        { status: 400, headers: { "Content-Type": "application/json" } },
      ),
  );
  stubBusinessFetch(fetcher);
  const error = await getProjectDistribution("P-selected", "EST").catch(
    (error: unknown) => error,
  );
  expect(error).toBeInstanceOf(ApiError);
  expect(error).toMatchObject({
    status: 400,
    body: {
      code: "INVALID_INPUT",
      field: "step",
      message: "단계 오류",
      requestId: "distribution-api",
    },
  });
  expect(fetcher).toHaveBeenCalledOnce();
});
