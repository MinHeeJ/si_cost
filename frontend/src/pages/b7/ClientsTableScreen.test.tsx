import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { ClientsTableScreen, type B7ClientRow } from "./ClientsTableScreen";
import { request } from "../../shared/api";
import * as api from "./b7Api";

vi.mock("../../shared/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../shared/api")>()),
  request: vi.fn(),
}));
vi.mock("./b7Api");
const client: B7ClientRow = {
  id: "C-721",
  type: "CUST",
  name: "조회 거래처",
  businessNo: "123-45-67890",
  ceo: null,
  industry: null,
  contactName: null,
  phone: "010-0010-0020",
  taxEmail: "finance@example.test",
  account: null,
  projectCount: 2,
  personCount: 3,
  useYn: "Y",
};
const download = { blob: new Blob(["workbook"]), filename: "거래처.xlsx" };
beforeEach(() => {
  vi.resetAllMocks();
  vi.stubGlobal(
    "matchMedia",
    vi.fn().mockReturnValue({
      matches: false,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    }),
  );
  vi.mocked(request).mockResolvedValue({ items: [client], total: 1 });
  vi.mocked(api.getB7Template).mockResolvedValue(download);
  vi.mocked(api.downloadB7File).mockResolvedValue(download);
});
afterEach(() => vi.unstubAllGlobals());
function draw() {
  const onRegister = vi.fn(),
    onClientOpen = vi.fn();
  render(
    <ClientsTableScreen onRegister={onRegister} onClientOpen={onClientOpen} />,
  );
  return { onRegister, onClientOpen };
}
function lastQuery() {
  return new URL(
    vi.mocked(request).mock.calls.at(-1)![0],
    "https://test.invalid",
  ).searchParams;
}

it("12열을 계약 순서로 표시하고 동일 상호도 선택한 거래처 ID로 연다", async () => {
  vi.mocked(request).mockResolvedValue({
    items: [client, { ...client, id: "C-722", businessNo: "123-45-67891" }],
    total: 2,
  });
  const { onClientOpen } = draw();
  await screen.findByText("123-45-67891");
  expect(
    screen.getAllByRole("columnheader").map((node) => node.textContent),
  ).toEqual([
    "구분",
    "상호",
    "사업자번호",
    "대표자",
    "업종",
    "담당자",
    "연락처",
    "세금계산서 이메일",
    "지급 계좌",
    "진행 프로젝트",
    "투입 인력",
    "사용 여부",
  ]);
  await userEvent.click(screen.getByText("123-45-67891"));
  expect(onClientOpen).toHaveBeenCalledWith("C-722");
  expect(
    screen
      .getAllByText("고객사")
      .some((node) => node.classList.contains("b7-info")),
  ).toBe(true);
  expect(
    screen
      .getAllByText("사용")
      .some((node) => node.classList.contains("b7-ok")),
  ).toBe(true);
});
it("등록·양식·업로드·내보내기를 실제 작업에 연결한다", async () => {
  const { onRegister } = draw();
  await screen.findByText(client.name);
  await userEvent.click(screen.getByRole("button", { name: "등록" }));
  expect(onRegister).toHaveBeenCalledOnce();
  await userEvent.click(screen.getByRole("button", { name: "양식 내려받기" }));
  await waitFor(() => expect(api.saveDownload).toHaveBeenCalledWith(download));
  expect(api.getB7Template).toHaveBeenCalledOnce();
  await userEvent.click(screen.getByRole("button", { name: "목록 다운로드" }));
  expect(api.downloadB7File).toHaveBeenCalledWith(
    "/api/exports/master-data",
    expect.objectContaining({ method: "POST" }),
  );
  const options = vi.mocked(api.downloadB7File).mock.calls[0][1]!;
  expect(JSON.parse(options.body as string)).toMatchObject({
    view: "CLIENTS",
    filters: { useYn: "Y" },
    columns: [
      "type",
      "name",
      "businessNo",
      "ceo",
      "industry",
      "contactName",
      "phone",
      "taxEmail",
      "account",
      "projectCount",
      "personCount",
      "useYn",
    ],
  });
  await userEvent.click(screen.getByRole("button", { name: "엑셀 업로드" }));
  expect(
    screen.getByRole("dialog", { name: "엑셀 업로드 결과 · 거래처" }),
  ).toBeInTheDocument();
  await userEvent.click(
    within(screen.getByRole("dialog")).getByRole("button", { name: "취소" }),
  );
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  expect(screen.getByText(client.name)).toBeInTheDocument();
});
it("최초 Y와 전체 생략을 구분하고 Enter 검색은 trim·page1로 적용한다", async () => {
  draw();
  await screen.findByText(client.name);
  expect(lastQuery().get("useYn")).toBe("Y");
  await userEvent.selectOptions(screen.getByLabelText("사용 여부"), "");
  await userEvent.type(screen.getByLabelText("상호"), "  검색어  {Enter}");
  await waitFor(() => expect(lastQuery().get("q")).toBe("검색어"));
  expect(lastQuery().has("useYn")).toBe(false);
  expect(lastQuery().get("page")).toBe("1");
  await userEvent.click(screen.getByRole("button", { name: "목록 다운로드" }));
  expect(
    JSON.parse(vi.mocked(api.downloadB7File).mock.calls[0][1]!.body as string)
      .filters,
  ).toEqual({ q: "검색어" });
});
it("빈 목록은 조건 초기화로 기본 조건을 다시 조회한다", async () => {
  vi.mocked(request).mockResolvedValue({ items: [], total: 0 });
  draw();
  expect(
    await screen.findByText("조회된 자료가 없습니다."),
  ).toBeInTheDocument();
  await userEvent.click(screen.getByRole("button", { name: "조건 초기화" }));
  await waitFor(() => expect(request).toHaveBeenCalledTimes(2));
  expect(lastQuery().get("useYn")).toBe("Y");
});
it("로딩·오류를 구분하며 자동 재시도 없이 조회만 재시도한다", async () => {
  let reject!: (error: Error) => void;
  vi.mocked(request).mockReturnValueOnce(
    new Promise((_, no) => {
      reject = no;
    }),
  );
  draw();
  expect(screen.getByRole("status")).toHaveTextContent("불러오는 중");
  await act(async () => reject(new Error("목록 실패")));
  expect(screen.getByRole("alert")).toHaveTextContent("목록 실패");
  expect(request).toHaveBeenCalledTimes(1);
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  expect(await screen.findByText(client.name)).toBeInTheDocument();
});
it("모바일에는 20건 카드·더 보기·단건만 제공하고 업로드와 파일 작업을 숨긴다", async () => {
  vi.mocked(matchMedia).mockReturnValue({
    matches: true,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
  } as unknown as MediaQueryList);
  vi.mocked(request)
    .mockResolvedValueOnce({ items: [client], total: 21 })
    .mockResolvedValueOnce({
      items: [{ ...client, id: "C-722", name: "다음 거래처" }],
      total: 21,
    });
  const { onClientOpen } = draw();
  await screen.findByText(client.name);
  expect(lastQuery().get("size")).toBe("20");
  expect(screen.queryByRole("table")).not.toBeInTheDocument();
  for (const name of ["엑셀 업로드", "양식 내려받기", "목록 다운로드"])
    expect(screen.queryByRole("button", { name })).not.toBeInTheDocument();
  await userEvent.click(screen.getByRole("button", { name: "더 보기" }));
  await screen.findByText("다음 거래처");
  expect(screen.getByText(client.name)).toBeInTheDocument();
  expect(lastQuery().get("page")).toBe("2");
  await userEvent.click(screen.getByRole("button", { name: "다음 거래처" }));
  expect(onClientOpen).toHaveBeenCalledWith("C-722");
});
it("저장된 열 순서와 숨김을 표와 내보내기에 같이 적용한다", async () => {
  const onColumnsChange = vi.fn();
  render(
    <ClientsTableScreen
      onRegister={vi.fn()}
      onColumnsChange={onColumnsChange}
      columnView={{ order: ["name", "type", "businessNo"], hidden: ["type"] }}
    />,
  );
  await screen.findByText(client.name);
  expect(
    screen.getAllByRole("columnheader").map((node) => node.textContent),
  ).toEqual(["상호", "사업자번호"]);
  await userEvent.click(screen.getByRole("button", { name: "열 설정" }));
  expect(onColumnsChange).toHaveBeenCalledOnce();
  await userEvent.click(screen.getByRole("button", { name: "목록 다운로드" }));
  expect(
    JSON.parse(vi.mocked(api.downloadB7File).mock.calls[0][1]!.body as string)
      .columns,
  ).toEqual(["name", "businessNo"]);
});
it("이전 검색 응답이 늦게 와도 최신 검색 결과를 덮어쓰지 않는다", async () => {
  let resolve!: (value: unknown) => void;
  vi.mocked(request).mockReturnValueOnce(
    new Promise((yes) => {
      resolve = yes;
    }),
  );
  draw();
  await userEvent.type(screen.getByLabelText("상호"), "최신{Enter}");
  await screen.findByText(client.name);
  await act(async () =>
    resolve({ items: [{ ...client, name: "이전 결과" }], total: 1 }),
  );
  expect(screen.queryByText("이전 결과")).not.toBeInTheDocument();
  expect(screen.getByText(client.name)).toBeInTheDocument();
});
