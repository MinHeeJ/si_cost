import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { FinancialUploadIssuesPanel } from "./FinancialUploadIssuesPanel";
import * as api from "../uploadsops/uploadIssuesApi";
vi.mock("../uploadsops/uploadIssuesApi");
const page: api.UploadIssuesPage = {
  items: [
    {
      sequence: 1,
      severity: "ERR",
      rowNumber: 2,
      columnName: "금액",
      inputValue: "잘못된 값",
      reason: "숫자 형식 오류",
    },
  ],
  total: 101,
  page: 1,
  size: 50,
};
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api.getUploadIssues).mockImplementation(
    async (_id, _severity, pageNo, size) => ({ ...page, page: pageNo, size }),
  );
  vi.mocked(api.getUploadIssuesXlsx).mockResolvedValue({
    blob: new Blob(),
    filename: "검사문제.xlsx",
  });
});
it("UI-10 문제 조회는 기존 저장 문제 API와 유형/페이지를 사용한다", async () => {
  render(<FinancialUploadIssuesPanel uploadId={19} initialPage={page} />);
  expect(screen.getByTestId("financial-upload-issues")).toHaveAttribute(
    "data-screen-id",
    "SCR-UI-10-B2-ISSUES",
  );
  await userEvent.selectOptions(screen.getByLabelText("문제 유형"), "WARN");
  await waitFor(() =>
    expect(api.getUploadIssues).toHaveBeenCalledWith(19, "WARN", 1, 50),
  );
  await userEvent.click(screen.getByLabelText("다음 페이지"));
  await waitFor(() =>
    expect(api.getUploadIssues).toHaveBeenLastCalledWith(19, "WARN", 2, 50),
  );
  await userEvent.click(screen.getByTestId("upload-issues-download"));
  await waitFor(() =>
    expect(api.getUploadIssuesXlsx).toHaveBeenCalledWith(19, "WARN"),
  );
  expect(api.saveDownload).toHaveBeenCalledTimes(1);
});
it("다운로드 실패는 파일을 저장하지 않는다", async () => {
  vi.mocked(api.getUploadIssuesXlsx).mockRejectedValueOnce(
    new Error("기록 저장 실패"),
  );
  render(<FinancialUploadIssuesPanel uploadId={19} initialPage={page} />);
  await userEvent.click(screen.getByTestId("upload-issues-download"));
  await screen.findByText(/기록 저장 실패/);
  expect(api.saveDownload).not.toHaveBeenCalled();
});
