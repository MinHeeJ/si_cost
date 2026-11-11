import { afterEach, expect, it, vi } from "vitest";
import { ApiError } from "../../shared/api";
import {
  createMasterDataExport,
  saveMasterDataDownload,
  type MasterDataExportRequest,
} from "./masterDataExportApi";

const input: MasterDataExportRequest = {
  view: "CLIENTS",
  filters: { useYn: "Y", q: " 검색 " },
  columns: ["name", "phone"],
  amountUnit: "천원",
};
const mime =
  "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
afterEach(() => {
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});
it("posts applied filters and visible columns without page/selection or auth and returns binary", async () => {
  const fetcher = vi.fn().mockResolvedValue(
    new Response(new Uint8Array([80, 75, 3, 4]), {
      headers: {
        "Content-Type": mime,
        "Content-Disposition": `attachment; filename*=UTF-8''${encodeURIComponent("거래처_20260615_090000.xlsx")}`,
      },
    }),
  );
  vi.stubGlobal("fetch", fetcher);
  const controller = new AbortController();
  const result = await createMasterDataExport(
    {
      ...input,
      ...{ page: 50, selectedIds: ["excluded"], regUserId: "excluded" },
    },
    controller.signal,
  );
  expect(result.filename).toBe("거래처_20260615_090000.xlsx");
  expect(result.blob.size).toBe(4);
  expect(fetcher.mock.calls[0][0]).toBe("/api/exports/master-data");
  const options = fetcher.mock.calls[0][1];
  expect(options).toMatchObject({
    method: "POST",
    redirect: "error",
    signal: controller.signal,
  });
  expect(options.headers.Authorization).toBeUndefined();
  expect(JSON.parse(options.body)).toEqual({
    view: "CLIENTS",
    filters: { useYn: "Y", q: "검색" },
    columns: ["name", "phone"],
    amountUnit: "천원",
  });
});
it("전체 omits status predicate instead of sending Y or 전체", async () => {
  const fetcher = vi
    .fn()
    .mockResolvedValue(new Response("failure", { status: 500 }));
  vi.stubGlobal("fetch", fetcher);
  await expect(
    createMasterDataExport({ ...input, filters: {} }),
  ).rejects.toThrow();
  expect(JSON.parse(fetcher.mock.calls[0][1].body).filters).toEqual({});
});
it.each([400, 500])(
  "parses root JSON errors before binary and never retries status %s",
  async (status) => {
    const fetcher = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          success: false,
          meta: {},
          code: "INTERNAL_ERROR",
          message: "기록 저장 실패",
          field: "columns",
          requestId: "trace",
        }),
        {
          status,
          headers: { "Content-Type": "application/json; charset=UTF-8" },
        },
      ),
    );
    vi.stubGlobal("fetch", fetcher);
    await expect(createMasterDataExport(input)).rejects.toBeInstanceOf(
      ApiError,
    );
    expect(fetcher).toHaveBeenCalledTimes(1);
  },
);
it("rejects HTML and malformed download names", async () => {
  const fetcher = vi
    .fn()
    .mockResolvedValueOnce(
      new Response("<html>login</html>", {
        headers: { "Content-Type": "text/html" },
      }),
    )
    .mockResolvedValueOnce(
      new Response("bytes", {
        headers: {
          "Content-Type": mime,
          "Content-Disposition": "attachment; filename*=UTF-8''..%2Fbad.xlsx",
        },
      }),
    );
  vi.stubGlobal("fetch", fetcher);
  await expect(createMasterDataExport(input)).rejects.toThrow(
    "파일을 내려받지 못했습니다.",
  );
  await expect(createMasterDataExport(input)).rejects.toThrow(
    "파일명을 확인할 수 없습니다.",
  );
});
it("revokes object URLs and removes temporary link even if browser download fails", () => {
  const createObjectURL = vi.fn().mockReturnValue("blob:owned-export"),
    revokeObjectURL = vi.fn();
  vi.stubGlobal("URL", { createObjectURL, revokeObjectURL });
  vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {
    throw new Error("cancelled");
  });
  expect(() =>
    saveMasterDataDownload({
      blob: new Blob(["xlsx"]),
      filename: "거래처_20260615_090000.xlsx",
    }),
  ).toThrow("cancelled");
  expect(revokeObjectURL).toHaveBeenCalledWith("blob:owned-export");
  expect(document.querySelector("a[download]")).toBeNull();
});
