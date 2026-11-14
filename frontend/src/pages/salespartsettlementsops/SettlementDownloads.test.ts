import { afterEach, expect, it, vi } from "vitest";
import {downloadSettlement} from "./settlementCommandsApi";
import {downloadB7File, saveDownload} from "../b7/b7Api";
vi.mock("../b7/b7Api", () => ({downloadB7File: vi.fn(), saveDownload: vi.fn()}));
afterEach(() => vi.clearAllMocks());
it.each(["MONTH", "TREND"] as const)("exports all %s results without page or size and keeps the requested unit", async mode => {
  const file = {blob: new Blob(["xlsx"]), filename: "정산.xlsx"};
  vi.mocked(downloadB7File).mockResolvedValue(file);
  await downloadSettlement("PART-11", {month: "2026-11", page: 2, size: 20, sort: "profitAmount", direction: "DESC"}, mode, "백만원");
  expect(downloadB7File).toHaveBeenCalledWith("/api/exports/master-data", expect.objectContaining({method: "POST"}));
  const body = JSON.parse(vi.mocked(downloadB7File).mock.calls[0][1]!.body as string);
  expect(body).toEqual({view: "SALES_PART_SETTLEMENTS", filters: {partId: "PART-11", month: "2026-11", mode}, sort: mode === "TREND" ? "name" : "profitAmount", direction: mode === "TREND" ? "ASC" : "DESC", amountUnit: "백만원"});
  expect(saveDownload).toHaveBeenCalledWith(file);
});
it("failed generation does not save a file", async () => {
  vi.mocked(downloadB7File).mockRejectedValue(new Error("기록 실패"));
  await expect(downloadSettlement("PART-11", {month:"2026-11",page:1,size:50,sort:"name",direction:"ASC"}, "MONTH", "천원")).rejects.toThrow("기록 실패");
  expect(saveDownload).not.toHaveBeenCalled();
});
