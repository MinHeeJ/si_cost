import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { ApiError } from "../../shared/api";
import { ClientForm } from "./ClientForm";
import { clientFixture } from "./clientsFixtures";
import * as api from "./clientsApi";
vi.mock("./clientsApi");
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api.getClientCodes).mockImplementation(async (group) => ({
    items:
      group === "CLNT_TYPE"
        ? [
            { code: "CUST", name: "고객사" },
            { code: "VNDR", name: "협력사" },
            { code: "BOTH", name: "고객·협력" },
          ]
        : group === "BANK"
          ? [{ code: "001", name: "시험은행" }]
          : [],
  }));
  vi.mocked(api.putClient).mockResolvedValue({
    id: clientFixture.id,
    client: clientFixture,
    changed: true,
    message: "저장했습니다.",
    warnings: [],
  });
  vi.mocked(api.createClient).mockResolvedValue({
    id: clientFixture.id,
    client: clientFixture,
    changed: true,
    message: "등록했습니다.",
    warnings: [],
  });
});
async function selectType(type: string) {
  await screen.findByRole("option", { name: "고객사" });
  fireEvent.change(screen.getByLabelText("구분"), { target: { value: type } });
}
it("구분 선택 전 안내, 고객 이메일, 협력 계좌, 겸업 두 영역을 표시한다", async () => {
  render(<ClientForm onClose={vi.fn()} onSaved={vi.fn()} />);
  expect(screen.getByText(/구분을 선택하면/)).toBeInTheDocument();
  expect(screen.queryByLabelText(/세금계산서 이메일/)).not.toBeInTheDocument();
  await selectType("CUST");
  expect(screen.getByLabelText(/세금계산서 이메일/)).toBeRequired();
  expect(screen.queryByLabelText("은행")).not.toBeInTheDocument();
  await selectType("VNDR");
  expect(screen.getByLabelText("은행")).toBeRequired();
  expect(screen.queryByLabelText(/세금계산서 이메일/)).not.toBeInTheDocument();
  await selectType("BOTH");
  expect(screen.getByLabelText("은행")).toBeInTheDocument();
  expect(screen.getByLabelText(/세금계산서 이메일/)).toBeInTheDocument();
});
it("KEEP은 빈 번호와 불변 사업자번호를 표시하고 계좌3필드를 생략한다", async () => {
  const saved = vi.fn();
  render(
    <ClientForm client={clientFixture} onClose={vi.fn()} onSaved={saved} />,
  );
  await screen.findByRole("option", { name: "협력사" });
  expect(screen.getByLabelText("계좌번호")).toHaveValue("");
  expect(screen.getByLabelText(/사업자번호/)).toHaveAttribute("readonly");
  await userEvent.clear(screen.getByLabelText(/상호/));
  await userEvent.type(screen.getByLabelText(/상호/), "수정상호");
  await userEvent.click(screen.getByTestId("client-form-save"));
  await waitFor(() => expect(saved).toHaveBeenCalledOnce());
  const [id, body] = vi.mocked(api.putClient).mock.calls[0];
  expect(id).toBe(clientFixture.id);
  expect(body).toMatchObject({
    accountAction: "KEEP",
    expectedRevision: "current",
    name: "수정상호",
  });
  for (const key of [
    "bankCode",
    "accountNumber",
    "depositor",
    "id",
    "businessNo",
  ])
    expect(body).not.toHaveProperty(key);
});
it("교체 확인은 끝4자리만 표시하며 취소0요청, 확인1요청이다", async () => {
  render(
    <ClientForm client={clientFixture} onClose={vi.fn()} onSaved={vi.fn()} />,
  );
  await screen.findByRole("option", { name: "협력사" });
  fireEvent.change(screen.getByLabelText("계좌 작업"), {
    target: { value: "REPLACE" },
  });
  fireEvent.change(screen.getByLabelText("은행"), { target: { value: "001" } });
  await userEvent.type(screen.getByLabelText(/계좌번호/), "001-9999888");
  await userEvent.type(screen.getByLabelText(/예금주/), "다른예금주");
  await userEvent.click(screen.getByTestId("client-form-save"));
  expect(
    await screen.findByText("다음 지급부터 새 계좌로 보냅니다"),
  ).toBeInTheDocument();
  const dialog = screen.getByRole("dialog");
  expect(dialog).toHaveTextContent("****9888");
  expect(dialog).not.toHaveTextContent("001-9999888");
  await userEvent.click(within(dialog).getByRole("button", { name: "취소" }));
  expect(api.putClient).not.toHaveBeenCalled();
  await userEvent.click(screen.getByTestId("client-form-save"));
  await userEvent.click(screen.getByRole("button", { name: "확인 후 저장" }));
  await waitFor(() => expect(api.putClient).toHaveBeenCalledOnce());
});
it("CLEAR 경고 취소시 저장하지 않고 기존 거래처를 유지한다", async () => {
  render(
    <ClientForm client={clientFixture} onClose={vi.fn()} onSaved={vi.fn()} />,
  );
  await selectType("CUST");
  await userEvent.type(screen.getByLabelText(/세금계산서 이메일/), "a@b.test");
  await userEvent.click(screen.getByTestId("client-form-save"));
  expect(
    await screen.findByText("고객사로 변경하면 지급 계좌 정보가 지워집니다"),
  ).toBeInTheDocument();
  await userEvent.click(
    within(screen.getByRole("dialog")).getByRole("button", { name: "취소" }),
  );
  expect(api.putClient).not.toHaveBeenCalled();
});
it("신규등록에는 계좌 확인도 accountAction도 없고 미적용 입력은 전송하지 않는다", async () => {
  render(<ClientForm onClose={vi.fn()} onSaved={vi.fn()} />);
  await selectType("CUST");
  await userEvent.type(screen.getByLabelText(/상호/), "신규");
  await userEvent.type(screen.getByLabelText(/사업자번호/), "1234567890");
  await userEvent.type(screen.getByLabelText(/세금계산서 이메일/), "a@b.test");
  await userEvent.click(screen.getByTestId("client-form-save"));
  await waitFor(() => expect(api.createClient).toHaveBeenCalledOnce());
  expect(api.createClient).toHaveBeenCalledWith(
    expect.objectContaining({ type: "CUST", businessNo: "1234567890" }),
  );
  expect(vi.mocked(api.createClient).mock.calls[0][0]).not.toHaveProperty(
    "accountAction",
  );
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
});
it("서버 필드 오류는 입력을 유지하고 성공콜백을 호출하지 않는다", async () => {
  const saved = vi.fn();
  vi.mocked(api.putClient).mockRejectedValue(
    new ApiError(400, {
      success: false,
      meta: {},
      code: "VALIDATION_ERROR",
      message: "상호 길이를 확인해 주세요.",
      field: "name",
      requestId: "test",
    }),
  );
  render(
    <ClientForm client={clientFixture} onClose={vi.fn()} onSaved={saved} />,
  );
  await screen.findByRole("option", { name: "협력사" });
  await userEvent.click(screen.getByTestId("client-form-save"));
  await waitFor(() =>
    expect(screen.getByLabelText(/상호/)).toHaveAttribute(
      "aria-invalid",
      "true",
    ),
  );
  expect(screen.getByLabelText(/상호/)).toHaveValue(clientFixture.name);
  expect(saved).not.toHaveBeenCalled();
});
it("dirty 취소는 계속 작성시 상태보존하고 나가기때만 닫는다", async () => {
  const close = vi.fn();
  render(<ClientForm onClose={close} onSaved={vi.fn()} />);
  await userEvent.type(screen.getByLabelText(/상호/), "작성중");
  await userEvent.click(screen.getByTestId("client-form-cancel"));
  await userEvent.click(screen.getByRole("button", { name: "계속 작성" }));
  expect(close).not.toHaveBeenCalled();
  expect(screen.getByLabelText(/상호/)).toHaveValue("작성중");
  await userEvent.click(screen.getByTestId("client-form-cancel"));
  await userEvent.click(screen.getByRole("button", { name: "나가기" }));
  expect(close).toHaveBeenCalledOnce();
  expect(api.createClient).not.toHaveBeenCalled();
});
it("선택 코드 조회 실패는 저장을 차단하고 재조회한다", async () => {
  vi.mocked(api.getClientCodes).mockRejectedValue(new Error("실패"));
  render(<ClientForm onClose={vi.fn()} onSaved={vi.fn()} />);
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "선택 항목을 불러오지 못했습니다.",
  );
  expect(screen.getByTestId("client-form-save")).toBeDisabled();
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  expect(api.getClientCodes).toHaveBeenCalledTimes(6);
});
