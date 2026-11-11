import { act, fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { ApiError } from "../../shared/api";
import { MasterDataExportButton } from "./MasterDataExportButton";
import * as api from "./masterDataExportApi";
vi.mock("./masterDataExportApi", async (original) => ({
  ...(await original<typeof api>()),
  createMasterDataExport: vi.fn(),
  saveMasterDataDownload: vi.fn(),
}));
const result = {
  blob: new Blob(["xlsx"]),
  filename: "거래처_20260615_090000.xlsx",
};
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api.createMasterDataExport).mockResolvedValue(result);
});
afterEach(() => vi.unstubAllGlobals());
it("downloads all applied filter results and ordered visible columns without selection dependency", async () => {
  render(
    <MasterDataExportButton
      view="CLIENTS"
      filters={{ type: "CUST", useYn: "Y", q: "적용값" }}
      sort="phone"
      direction="DESC"
      columnView={{ order: ["phone", "name", "account"], hidden: ["account"] }}
    />,
  );
  await userEvent.click(screen.getByRole("button", { name: "목록 다운로드" }));
  expect(await screen.findByText("파일을 내려받았습니다.")).toHaveAttribute(
    "role",
    "status",
  );
  expect(api.createMasterDataExport).toHaveBeenCalledWith(
    {
      view: "CLIENTS",
      filters: { type: "CUST", useYn: "Y", q: "적용값" },
      sort: "phone",
      direction: "DESC",
      columns: ["phone", "name"],
      amountUnit: "천원",
    },
    expect.any(AbortSignal),
  );
  expect(api.saveMasterDataDownload).toHaveBeenCalledWith(result);
});
it("zero row list can still download title-only file and parts always send nine columns", async () => {
  render(
    <MasterDataExportButton
      view="PARTS"
      filters={{}}
      columnView={{ order: ["name"], hidden: [] }}
      amountUnit="백만원"
    />,
  );
  await userEvent.click(screen.getByRole("button", { name: "목록 다운로드" }));
  await screen.findByText("파일을 내려받았습니다.");
  expect(api.createMasterDataExport).toHaveBeenCalledWith(
    expect.objectContaining({
      view: "PARTS",
      filters: {},
      columns: api.partExportColumns,
      amountUnit: "백만원",
    }),
    expect.any(AbortSignal),
  );
});
it("pending state prevents duplicate clicks without automatic retry", async () => {
  let resolve!: (value: api.MasterDataDownload) => void;
  vi.mocked(api.createMasterDataExport).mockReturnValue(
    new Promise((yes) => {
      resolve = yes;
    }),
  );
  render(<MasterDataExportButton view="CLIENTS" filters={{}} />);
  const button = screen.getByRole("button", { name: "목록 다운로드" });
  fireEvent.click(button);
  fireEvent.click(button);
  expect(screen.getByRole("button", { name: "파일 생성 중…" })).toBeDisabled();
  expect(screen.getByRole("status")).toHaveTextContent("전체 결과");
  expect(api.createMasterDataExport).toHaveBeenCalledTimes(1);
  await act(async () => resolve(result));
});
it("failed download retains field reason and trace, never saves JSON, and retries only manually", async () => {
  vi.mocked(api.createMasterDataExport).mockRejectedValueOnce(
    new ApiError(400, {
      success: false,
      meta: {},
      code: "VALIDATION_ERROR",
      message: "열을 확인해 주세요.",
      field: "columns",
      requestId: "trace-id",
    }),
  );
  render(<MasterDataExportButton view="CLIENTS" filters={{}} />);
  await userEvent.click(screen.getByRole("button", { name: "목록 다운로드" }));
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "열을 확인해 주세요.",
  );
  expect(screen.getByRole("alert")).toHaveTextContent(
    "columns · 문의 번호: trace-id",
  );
  expect(api.saveMasterDataDownload).not.toHaveBeenCalled();
  expect(api.createMasterDataExport).toHaveBeenCalledTimes(1);
  await userEvent.click(screen.getByRole("button", { name: "다시 시도" }));
  await screen.findByText("파일을 내려받았습니다.");
  expect(api.createMasterDataExport).toHaveBeenCalledTimes(2);
});
it("parent loading or failed-list state can disable download", () => {
  render(<MasterDataExportButton view="CLIENTS" filters={{}} disabled />);
  fireEvent.click(screen.getByRole("button", { name: "목록 다운로드" }));
  expect(api.createMasterDataExport).not.toHaveBeenCalled();
});
it("mobile state hides download rather than exposing desktop actions", () => {
  vi.stubGlobal("matchMedia", () => ({
    matches: true,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
  }));
  render(<MasterDataExportButton view="CLIENTS" filters={{}} />);
  expect(screen.queryByRole("button")).not.toBeInTheDocument();
  expect(api.createMasterDataExport).not.toHaveBeenCalled();
});
it("unmount cancels generation and ignores late response", async () => {
  let resolve!: (value: api.MasterDataDownload) => void;
  vi.mocked(api.createMasterDataExport).mockReturnValue(
    new Promise((yes) => {
      resolve = yes;
    }),
  );
  const view = render(<MasterDataExportButton view="PARTS" filters={{}} />);
  await userEvent.click(screen.getByRole("button", { name: "목록 다운로드" }));
  const signal = vi.mocked(api.createMasterDataExport).mock.calls[0][1]!;
  view.unmount();
  expect(signal.aborted).toBe(true);
  await act(async () => resolve(result));
  expect(api.saveMasterDataDownload).not.toHaveBeenCalled();
});
