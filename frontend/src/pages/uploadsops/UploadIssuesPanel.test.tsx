import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { UploadIssuesPanel } from "./UploadIssuesPanel";
import * as api from "./uploadIssuesApi";
vi.mock("./uploadIssuesApi");
const initialPage: api.UploadIssuesPage = {
  items: [
    {
      sequence: 1,
      severity: "ERR",
      rowNumber: 2,
      columnName: "계좌번호",
      inputValue: "[비공개]",
      reason: "형식 오류",
    },
    {
      sequence: 2,
      severity: "WARN",
      rowNumber: 3,
      columnName: "상호",
      inputValue: "저장된 발췌…",
      reason: "같은 상호",
    },
  ],
  total: 103,
  page: 1,
  size: 50,
};
const warnPage = { ...initialPage, items: [initialPage.items[1]], total: 101 };
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api.getUploadIssues).mockImplementation(
    async (_id, severity, page, size) => ({
      ...(severity === "WARN" ? warnPage : initialPage),
      page,
      size,
    }),
  );
  vi.mocked(api.getUploadIssuesXlsx).mockResolvedValue({
    blob: new Blob(),
    filename: "검사.xlsx",
  });
});
function draw(disabled = false) {
  return render(
    <UploadIssuesPanel
      uploadId={17}
      initialPage={initialPage}
      disabled={disabled}
    />,
  );
}
it("기본 전체와 별도 오류·경고 토큰 및 저장된 비공개/발췌를 표시한다", () => {
  draw();
  expect(screen.getByLabelText("문제 유형")).toHaveValue("");
  expect(screen.getByText("오류")).toHaveClass("upload-issue-error");
  expect(screen.getByText("경고")).toHaveClass("upload-issue-warning");
  expect(screen.getByText("[비공개]")).toBeInTheDocument();
  expect(screen.getByText("저장된 발췌…")).toBeInTheDocument();
  expect(api.getUploadIssues).not.toHaveBeenCalled();
});
it("WARN 필터 2페이지에서도 페이지 인자 없이 현재 severity 전체를 한 번 다운로드한다", async () => {
  draw();
  await userEvent.selectOptions(screen.getByLabelText("문제 유형"), "WARN");
  await waitFor(() =>
    expect(api.getUploadIssues).toHaveBeenCalledWith(17, "WARN", 1, 50),
  );
  await userEvent.click(screen.getByLabelText("다음 페이지"));
  await waitFor(() =>
    expect(api.getUploadIssues).toHaveBeenCalledWith(17, "WARN", 2, 50),
  );
  expect(screen.queryByText("형식 오류")).not.toBeInTheDocument();
  await userEvent.click(
    screen.getByRole("button", { name: "현재 severity 전체 문제 다운로드" }),
  );
  await screen.findByText("현재 유형의 전체 문제를 다운로드했습니다.");
  expect(api.getUploadIssuesXlsx).toHaveBeenCalledWith(17, "WARN");
  expect(api.getUploadIssuesXlsx).toHaveBeenCalledTimes(1);
  expect(api.saveDownload).toHaveBeenCalledTimes(1);
});
it("전체 선택은 빈 severity로 전환하며 필터와 크기 변경은 첫 페이지로 이동한다", async () => {
  draw();
  await userEvent.selectOptions(screen.getByLabelText("문제 유형"), "WARN");
  await userEvent.click(screen.getByLabelText("다음 페이지"));
  await userEvent.selectOptions(screen.getByLabelText("문제 유형"), "");
  await waitFor(() =>
    expect(api.getUploadIssues).toHaveBeenLastCalledWith(17, "", 1, 50),
  );
  await userEvent.selectOptions(screen.getByLabelText("한 화면 건수"), "20");
  await waitFor(() =>
    expect(api.getUploadIssues).toHaveBeenLastCalledWith(17, "", 1, 20),
  );
});
it("실패한 조회는 오류를 표시하며 명시적 다시 시도만 요청한다", async () => {
  vi.mocked(api.getUploadIssues).mockRejectedValueOnce(new Error("조회 실패"));
  draw();
  await userEvent.selectOptions(screen.getByLabelText("문제 유형"), "WARN");
  await screen.findByText("조회 실패");
  expect(api.getUploadIssues).toHaveBeenCalledTimes(1);
  expect(
    screen.getByRole("button", { name: "현재 severity 전체 문제 다운로드" }),
  ).toBeDisabled();
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  await waitFor(() =>
    expect(screen.getByLabelText("문제 유형")).toHaveValue("WARN"),
  );
  expect(api.getUploadIssues).toHaveBeenCalledTimes(2);
});
it("다운로드 JSON 오류는 성공으로 저장하지 않고 필터와 페이지를 유지한다", async () => {
  vi.mocked(api.getUploadIssuesXlsx).mockRejectedValueOnce(
    new Error("접근기록 저장 실패"),
  );
  draw();
  await userEvent.click(
    screen.getByRole("button", { name: "현재 severity 전체 문제 다운로드" }),
  );
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "접근기록 저장 실패",
  );
  expect(api.getUploadIssuesXlsx).toHaveBeenCalledTimes(1);
  expect(api.saveDownload).not.toHaveBeenCalled();
  expect(screen.getByText("형식 오류")).toBeInTheDocument();
});
it("빈 문제 목록은 별도 안내와 전체 조건 초기화를 제공한다", async () => {
  vi.mocked(api.getUploadIssues).mockResolvedValueOnce({
    ...initialPage,
    items: [],
    total: 0,
  });
  draw();
  await userEvent.selectOptions(screen.getByLabelText("문제 유형"), "ERR");
  await screen.findByText("검사 문제가 없습니다.");
  await userEvent.click(screen.getByRole("button", { name: "조건 초기화" }));
  await waitFor(() =>
    expect(api.getUploadIssues).toHaveBeenLastCalledWith(17, "", 1, 50),
  );
});
it("상위 검사·반영 진행 중에는 유형과 다운로드를 비활성화한다", () => {
  draw(true);
  expect(screen.getByLabelText("문제 유형")).toBeDisabled();
  expect(
    screen.getByRole("button", { name: "현재 severity 전체 문제 다운로드" }),
  ).toBeDisabled();
});
it("파일 생성 중 중복 클릭을 막고 성공 후 제어를 복구한다", async () => {
  let resolve!: (
    value: Awaited<ReturnType<typeof api.getUploadIssuesXlsx>>,
  ) => void;
  vi.mocked(api.getUploadIssuesXlsx).mockReturnValue(
    new Promise((done) => {
      resolve = done;
    }),
  );
  draw();
  await userEvent.dblClick(
    screen.getByRole("button", { name: "현재 severity 전체 문제 다운로드" }),
  );
  expect(screen.getByRole("button", { name: "파일 생성 중…" })).toBeDisabled();
  expect(api.getUploadIssuesXlsx).toHaveBeenCalledTimes(1);
  resolve({ blob: new Blob(), filename: "검사.xlsx" });
  await screen.findByText("현재 유형의 전체 문제를 다운로드했습니다.");
});
