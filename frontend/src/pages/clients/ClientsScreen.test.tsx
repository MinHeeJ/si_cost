import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { ClientsScreen } from "./ClientsScreen";
import { clientFixture, pageFixture } from "./clientsFixtures";
import * as api from "./clientsApi";
vi.mock("./clientsApi");
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api.getClients).mockResolvedValue(pageFixture);
  vi.mocked(api.getClient).mockResolvedValue(clientFixture);
  vi.mocked(api.getClientCodes).mockImplementation(async (group) => ({
    items:
      group === "CLNT_TYPE"
        ? [
            { code: "CUST", name: "고객사" },
            { code: "VNDR", name: "협력사" },
            { code: "BOTH", name: "고객·협력" },
          ]
        : [],
  }));
  vi.mocked(api.getClientAssignments).mockResolvedValue({
    items: [],
    total: 0,
    page: 1,
    size: 50,
    basisDate: clientFixture.basisDate,
  });
});
it("기본 사용Y와 전체 생략·구분검색·페이지 유지 계약을 호출한다", async () => {
  render(<ClientsScreen />);
  await screen.findByText(clientFixture.name);
  expect(api.getClients).toHaveBeenCalledWith(
    expect.objectContaining({ useYn: "Y", page: 1, size: 50 }),
    expect.any(AbortSignal),
  );
  await userEvent.selectOptions(screen.getByLabelText("사용 여부"), "");
  await userEvent.selectOptions(screen.getByLabelText("구분"), "CUST");
  await userEvent.type(
    screen.getByPlaceholderText("상호 또는 사업자번호"),
    "123-45",
  );
  await userEvent.click(screen.getByTestId("clients-search-submit"));
  await waitFor(() =>
    expect(api.getClients).toHaveBeenLastCalledWith(
      expect.objectContaining({
        type: "CUST",
        useYn: undefined,
        q: "123-45",
        page: 1,
      }),
      expect.any(AbortSignal),
    ),
  );
});
it("빈 목록은 조건 초기화, 실패는 재조회, 로딩은 상태로 구분한다", async () => {
  vi.mocked(api.getClients).mockResolvedValue({
    ...pageFixture,
    items: [],
    total: 0,
  });
  render(<ClientsScreen />);
  expect(
    await screen.findByText("조회된 거래처가 없습니다."),
  ).toBeInTheDocument();
  expect(screen.getByTestId("clients-empty-reset")).toBeInTheDocument();
  vi.mocked(api.getClients).mockRejectedValueOnce(new Error("조회실패"));
  await userEvent.click(screen.getByTestId("clients-search-submit"));
  expect(await screen.findByRole("alert")).toHaveTextContent("조회실패");
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  expect(
    await screen.findByText("조회된 거래처가 없습니다."),
  ).toBeInTheDocument();
});
it("행 상세는 같은 화면의 오른쪽3탭이며 건수 클릭은 투입조회다", async () => {
  render(<ClientsScreen />);
  await screen.findByText(clientFixture.name);
  await userEvent.click(
    screen.getByTestId(`client-${clientFixture.id}-projects`),
  );
  expect(
    await screen.findByRole("dialog", { name: "거래처 상세" }),
  ).toHaveClass("clients-drawer");
  expect(screen.getByTestId("client-tab-assignments")).toHaveAttribute(
    "aria-selected",
    "true",
  );
  await waitFor(() =>
    expect(api.getClientAssignments).toHaveBeenCalledWith(
      clientFixture.id,
      1,
      expect.any(AbortSignal),
    ),
  );
  expect(
    await screen.findByText("오늘 기준 투입 내역이 없습니다."),
  ).toBeInTheDocument();
  await userEvent.click(screen.getByTestId("client-tab-basic"));
  expect(
    within(screen.getByTestId("client-drawer")).getByText(
      clientFixture.account!,
    ),
  ).toBeInTheDocument();
  expect(screen.getByTestId("client-tab-history")).toHaveTextContent(
    "변경 이력 (1)",
  );
});
it("상세 닫기는 필터·페이지를 바꾸지 않는다", async () => {
  render(<ClientsScreen />);
  await screen.findByText(clientFixture.name);
  await userEvent.click(screen.getByTestId(`client-${clientFixture.id}-open`));
  await screen.findByTestId("client-edit");
  const count = vi.mocked(api.getClients).mock.calls.length;
  await userEvent.click(screen.getByTestId("client-drawer-close"));
  expect(screen.queryByTestId("client-drawer")).not.toBeInTheDocument();
  expect(api.getClients).toHaveBeenCalledTimes(count);
  expect(screen.getByLabelText("사용 여부")).toHaveValue("Y");
});
it("서버basisDate가 달라지면 목록을 다시 읽는다", async () => {
  vi.mocked(api.getClient).mockResolvedValue({
    ...clientFixture,
    basisDate: "2026-06-16",
  });
  render(<ClientsScreen />);
  await screen.findByText(clientFixture.name);
  const before = vi.mocked(api.getClients).mock.calls.length;
  await userEvent.click(screen.getByTestId(`client-${clientFixture.id}-open`));
  await waitFor(() =>
    expect(vi.mocked(api.getClients).mock.calls.length).toBeGreaterThan(before),
  );
});
it("신규등록은 별도경로가 아닌 전체폼이며 필터 밖 등록도 페이지를 강제이동하지 않는다", async () => {
  vi.mocked(api.getClients).mockResolvedValue({ ...pageFixture, total: 80 });
  vi.mocked(api.createClient).mockResolvedValue({
    id: "C-999",
    changed: true,
    message: "등록했습니다.",
    warnings: [],
    client: { ...clientFixture, id: "C-999" },
  });
  render(<ClientsScreen />);
  await screen.findByText(clientFixture.name);
  await userEvent.click(screen.getByRole("button", { name: "다음 페이지" }));
  await waitFor(() =>
    expect(api.getClients).toHaveBeenLastCalledWith(
      expect.objectContaining({ page: 2 }),
      expect.any(AbortSignal),
    ),
  );
  await userEvent.click(screen.getByTestId("clients-register"));
  expect(screen.getByTestId("client-form")).toHaveClass("clients-form-page");
  await screen.findByRole("option", { name: "고객사" });
  const form = within(screen.getByTestId("client-form"));
  await userEvent.selectOptions(form.getByLabelText("구분"), "CUST");
  await userEvent.type(form.getByLabelText(/상호/), "새거래처");
  await userEvent.type(form.getByLabelText(/사업자번호/), "1234567890");
  await userEvent.type(form.getByLabelText(/세금계산서 이메일/), "a@b.test");
  await userEvent.click(screen.getByTestId("client-form-save"));
  expect(await screen.findByTestId("client-created-view")).toBeInTheDocument();
  expect(api.getClients).toHaveBeenLastCalledWith(
    expect.objectContaining({ page: 2, useYn: "Y" }),
    undefined,
  );
  await userEvent.click(screen.getByTestId("client-created-view"));
  await waitFor(() => expect(api.getClient).toHaveBeenCalledWith("C-999"));
});
it("계좌전체보기 후 수정에서도 KEEP빈칸이고 원문은 버린다", async () => {
  vi.mocked(api.getClientAccount).mockResolvedValue({
    accountNumber: "001-2345678",
  });
  render(<ClientsScreen />);
  await screen.findByText(clientFixture.name);
  await userEvent.click(screen.getByTestId(`client-${clientFixture.id}-open`));
  await screen.findByTestId("client-edit");
  await userEvent.click(screen.getByTestId("client-account-show"));
  expect(await screen.findByTestId("client-account-number")).toHaveTextContent(
    "001-2345678",
  );
  await userEvent.click(screen.getByTestId("client-edit"));
  expect(screen.queryByTestId("client-account-number")).not.toBeInTheDocument();
  expect(screen.getByTestId("client-account-keep-input")).toHaveValue("");
  expect(screen.getByLabelText("계좌 작업")).toHaveValue("KEEP");
});
it("지연된 원문 응답은 다른탭으로 이동한 뒤 표시되지 않는다", async () => {
  let resolve!: (value: { accountNumber: string }) => void;
  vi.mocked(api.getClientAccount).mockReturnValue(
    new Promise((done) => {
      resolve = done;
    }),
  );
  render(<ClientsScreen />);
  await screen.findByText(clientFixture.name);
  await userEvent.click(screen.getByTestId(`client-${clientFixture.id}-open`));
  await screen.findByTestId("client-account-show");
  await userEvent.click(screen.getByTestId("client-account-show"));
  await userEvent.click(screen.getByTestId("client-tab-assignments"));
  await act(async () => resolve({ accountNumber: "001-2345678" }));
  await userEvent.click(screen.getByTestId("client-tab-basic"));
  expect(screen.queryByTestId("client-account-number")).not.toBeInTheDocument();
});
it("단건 중지는 bulk-status 한항목만 보내고 취소는 요청하지 않는다", async () => {
  vi.mocked(api.setClientStatus).mockResolvedValue({
    items: [{ id: clientFixture.id, result: "applied" }],
  });
  render(<ClientsScreen />);
  await screen.findByText(clientFixture.name);
  await userEvent.click(screen.getByTestId(`client-${clientFixture.id}-open`));
  await screen.findByTestId("client-status");
  fireEvent.click(screen.getByTestId("client-status"));
  let dialog = screen.getByRole("dialog", { name: "거래처 중지" });
  await userEvent.click(within(dialog).getByRole("button", { name: "취소" }));
  expect(api.setClientStatus).not.toHaveBeenCalled();
  fireEvent.click(screen.getByTestId("client-status"));
  dialog = screen.getByRole("dialog", { name: "거래처 중지" });
  await userEvent.click(within(dialog).getByRole("button", { name: "확인" }));
  await waitFor(() => expect(api.setClientStatus).toHaveBeenCalledOnce());
});
it("선택 열설정을 반영하지만 필수상호는 숨기지 않는다", async () => {
  render(
    <ClientsScreen
      columnView={{
        order: ["name", "type"],
        hidden: ["name", "phone", "account"],
      }}
    />,
  );
  await screen.findByText(clientFixture.name);
  expect(
    screen.getByRole("columnheader", { name: "상호" }),
  ).toBeInTheDocument();
  expect(
    screen.queryByRole("columnheader", { name: "연락처" }),
  ).not.toBeInTheDocument();
});
it("정렬변경은1페이지·같은 workspace내 파트탭은 목록상태를 보존한다", async () => {
  render(<ClientsScreen renderParts={<p>파트관리</p>} />);
  await screen.findByText(clientFixture.name);
  fireEvent.change(screen.getByTestId("clients-sort"), {
    target: { value: "personCount" },
  });
  await waitFor(() =>
    expect(api.getClients).toHaveBeenLastCalledWith(
      expect.objectContaining({ sort: "personCount", page: 1 }),
      expect.any(AbortSignal),
    ),
  );
  await userEvent.click(screen.getByTestId("parts-workspace-tab"));
  expect(screen.getByText("파트관리")).toBeVisible();
  await userEvent.click(screen.getByTestId("clients-workspace-tab"));
  expect(screen.getByTestId("clients-sort")).toHaveValue("personCount");
});
