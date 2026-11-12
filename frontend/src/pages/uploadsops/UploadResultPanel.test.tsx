import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { ApiError } from "../../shared/api";
import { UploadResultPanel } from "./UploadResultPanel";
import * as api from "../b9/b9Api";
vi.mock("../b9/b9Api");

const applied: api.ProjectUploadResult = {
  uploadId: 17,
  formType: "B9",
  status: "APPLIED",
  projectId: null,
  step: null,
  summary: {
    totalRows: 2,
    successRows: 1,
    warningRows: 1,
    errorRows: 0,
    fileWarningCount: 3,
    tableWarningCount: 4,
  },
  results: [
    { rowNumber: 3, id: "P-original", name: "최초 이름" },
    { rowNumber: 9, id: "P-other", name: "다른 이름" },
  ],
  revision: "immutable-revision",
  changed: true,
};
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api.getUploadResult).mockResolvedValue(applied);
});
it("반영 유실 확인은 명시적 결과 재조회만 호출하며 원행과 최초 이름 전체를 표시한다", async () => {
  render(<UploadResultPanel uploadId={17} />);
  expect(api.getUploadResult).not.toHaveBeenCalled();
  await userEvent.click(screen.getByRole("button", { name: "결과 재조회" }));
  await screen.findByText("최초 이름");
  expect(api.getUploadResult).toHaveBeenCalledTimes(1);
  expect(api.getUploadResult).toHaveBeenCalledWith(17);
  expect(
    screen.getByRole("columnheader", { name: "원래 행" }),
  ).toBeInTheDocument();
  expect(
    screen.getByRole("columnheader", { name: "최종 프로젝트ID" }),
  ).toBeInTheDocument();
  expect(screen.getByText("P-original")).toBeInTheDocument();
  expect(screen.getByText("다른 이름")).toBeInTheDocument();
  expect(screen.getByText("APPLIED 반영 완료")).toHaveClass("sicost-tone-ok");
  expect(screen.getByText("경고 1건")).toHaveClass("b7-warn");
  expect(screen.getByText("오류 0건")).toHaveClass("b7-bad");
});
it("서버 요약의 행 경고와 파일·표 경고를 별도로 표시한다", () => {
  render(<UploadResultPanel uploadId={17} initialResult={applied} />);
  for (const text of [
    "전체 2건",
    "성공 1건",
    "경고 1건",
    "오류 0건",
    "파일 경고 3건",
    "표 경고 4건",
  ])
    expect(screen.getByText(text)).toBeInTheDocument();
  expect(api.getUploadResult).not.toHaveBeenCalled();
  expect(
    screen.queryByRole("button", { name: "전량 반영" }),
  ).not.toBeInTheDocument();
});
it("CHECKED는 읽기 전용 안내와 빈 목록을 보이고 반영 완료로 꾸미지 않는다", () => {
  render(
    <UploadResultPanel
      uploadId={17}
      initialResult={{
        ...applied,
        status: "CHECKED",
        results: [],
        changed: false,
        revision: null,
      }}
    />,
  );
  expect(screen.getByText("CHECKED 검사 완료")).toHaveClass("sicost-tone-info");
  expect(screen.getByText("반영된 프로젝트가 없습니다.")).toBeInTheDocument();
  expect(screen.queryByText("최초 이름")).not.toBeInTheDocument();
  expect(screen.getByText(/아직 반영되지 않았습니다/)).toBeInTheDocument();
});
it("B6는 대상과 당시 단계를 읽기 전용으로 표시하고 B9 표를 표시하지 않는다", () => {
  render(
    <UploadResultPanel
      uploadId={17}
      initialResult={{
        ...applied,
        formType: "B6",
        projectId: "P-selected",
        step: "CTRT",
        results: [],
      }}
    />,
  );
  expect(screen.getByText("P-selected")).toBeInTheDocument();
  expect(screen.getByText("계약")).toBeInTheDocument();
  expect(screen.getByText("당시 단계")).toBeInTheDocument();
  expect(screen.queryByRole("table")).not.toBeInTheDocument();
  expect(screen.queryByRole("combobox")).not.toBeInTheDocument();
});
it("빈 APPLIED B9 결과는 가상 행이나 페이지 제한을 만들지 않는다", () => {
  render(
    <UploadResultPanel
      uploadId={17}
      initialResult={{ ...applied, results: [] }}
    />,
  );
  expect(screen.getByText("반영된 프로젝트가 없습니다.")).toBeInTheDocument();
  expect(screen.queryByLabelText("페이지")).not.toBeInTheDocument();
});
it("실패 후 요청 번호와 기존 결과를 유지하고 자동 재시도하지 않는다", async () => {
  vi.mocked(api.getUploadResult).mockRejectedValueOnce(
    new ApiError(404, {
      success: false,
      meta: {},
      code: "NOT_FOUND",
      message: "업로드 결과가 없습니다.",
      requestId: "result-request",
    }),
  );
  render(<UploadResultPanel uploadId={17} initialResult={applied} />);
  await userEvent.click(screen.getByRole("button", { name: "결과 재조회" }));
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "업로드 결과가 없습니다.",
  );
  expect(screen.getByRole("alert")).toHaveTextContent("result-request");
  expect(screen.getByText("최초 이름")).toBeInTheDocument();
  expect(api.getUploadResult).toHaveBeenCalledTimes(1);
  await userEvent.click(screen.getByRole("button", { name: "결과 재조회" }));
  await screen.findByText("최초 이름");
  expect(api.getUploadResult).toHaveBeenCalledTimes(2);
});
it("상위 작업 중에는 조회 버튼만 잠그며 역할 기반 제한을 추가하지 않는다", () => {
  render(<UploadResultPanel uploadId={17} initialResult={applied} disabled />);
  expect(screen.getByRole("button", { name: "결과 재조회" })).toBeDisabled();
  expect(screen.getByText("최초 이름")).toBeInTheDocument();
  expect(api.getUploadResult).not.toHaveBeenCalled();
});
it("조회 중 중복 제출을 막고 완료 시 제어를 복구한다", async () => {
  let resolve!: (value: api.ProjectUploadResult) => void;
  vi.mocked(api.getUploadResult).mockReturnValue(
    new Promise((done) => {
      resolve = done;
    }),
  );
  render(<UploadResultPanel uploadId={17} />);
  await userEvent.dblClick(screen.getByRole("button", { name: "결과 재조회" }));
  expect(screen.getByRole("button", { name: "불러오는 중…" })).toBeDisabled();
  expect(screen.getByTestId("upload-result-panel")).toHaveAttribute(
    "aria-busy",
    "true",
  );
  expect(api.getUploadResult).toHaveBeenCalledTimes(1);
  await act(async () => resolve(applied));
  expect(screen.getByRole("button", { name: "결과 재조회" })).toBeEnabled();
});
it("대상 변경 시 이전 결과와 늦게 도착한 이전 요청을 새 대상에 표시하지 않는다", async () => {
  let resolve!: (value: api.ProjectUploadResult) => void;
  vi.mocked(api.getUploadResult).mockReturnValueOnce(
    new Promise((done) => {
      resolve = done;
    }),
  );
  const view = render(
    <UploadResultPanel uploadId={17} initialResult={applied} />,
  );
  await userEvent.click(screen.getByRole("button", { name: "결과 재조회" }));
  view.rerender(<UploadResultPanel uploadId={18} initialResult={applied} />);
  await act(async () => resolve(applied));
  expect(screen.queryByText("최초 이름")).not.toBeInTheDocument();
  expect(screen.getByRole("button", { name: "결과 재조회" })).toBeEnabled();
  expect(api.getUploadResult).toHaveBeenCalledTimes(1);
});
