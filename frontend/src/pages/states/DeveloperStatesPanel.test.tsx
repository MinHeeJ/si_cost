import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { DeveloperStatesPanel } from "./DeveloperStatesPanel";
import { ApiError } from "../../shared/api";
import * as api from "./developerStatesApi";
vi.mock("./developerStatesApi");
const row: api.DeveloperState = {
  sn: 7,
  state: "LEAVE",
  startDate: "2026-06-01",
  endDate: null,
  reason: "학업",
  actorId: "admin",
  actorName: "임시 관리자",
  allowedActions: ["EDIT_END"],
};
const result: api.DeveloperStates = {
  items: [row],
  hireDate: "2026-01-01",
  revision: "opaque-revision",
};
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api.getDeveloperStates).mockResolvedValue(result);
  vi.mocked(api.createDeveloperState).mockResolvedValue({
    sn: 8,
    warnings: [],
  });
  vi.mocked(api.putDeveloperStateEnd).mockResolvedValue({ changed: true });
  vi.mocked(api.saveDeveloperStateCancel).mockResolvedValue({ changed: true });
});
function draw(onChanged = vi.fn().mockResolvedValue(undefined)) {
  render(
    <DeveloperStatesPanel
      developerId="DEV-000901"
      currentDate="2026-06-15"
      onChanged={onChanged}
    />,
  );
  return onChanged;
}
async function openCreate() {
  await screen.findByText("학업");
  await userEvent.click(screen.getByRole("button", { name: "상태 등록" }));
}
function setDate(label: RegExp, value: string) {
  fireEvent.change(screen.getByLabelText(label, { selector: "input" }), {
    target: { value },
  });
}
it("로딩·빈 목록·표시전용 입사행이며 WORK를 만들지 않는다", async () => {
  let resolve!: (data: api.DeveloperStates) => void;
  vi.mocked(api.getDeveloperStates).mockReturnValue(
    new Promise((r) => {
      resolve = r;
    }),
  );
  draw();
  expect(screen.getByText("불러오는 중입니다.")).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "상태 등록" })).toBeDisabled();
  resolve({ ...result, items: [] });
  expect(
    await screen.findByText("등록된 근무 상태 이력이 없습니다."),
  ).toBeInTheDocument();
  expect(screen.getByText(/입사·재직/)).toHaveTextContent("2026-01-01");
  expect(api.createDeveloperState).not.toHaveBeenCalled();
});
it("실패는 requestId와 조회 재시도로 복구한다", async () => {
  vi.mocked(api.getDeveloperStates).mockRejectedValueOnce(
    new ApiError(500, {
      success: false,
      meta: {},
      code: "INTERNAL_ERROR",
      message: "조회 실패",
      requestId: "request-read",
    }),
  );
  draw();
  expect(await screen.findByRole("alert")).toHaveTextContent("request-read");
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  await screen.findByText("학업");
  expect(api.getDeveloperStates).toHaveBeenCalledTimes(2);
});
it("허용 작업이 없는 종료휴직은 수정·삭제·취소를 표시하지 않는다", async () => {
  vi.mocked(api.getDeveloperStates).mockResolvedValue({
    ...result,
    items: [{ ...row, endDate: "2026-06-14", allowedActions: [] }],
  });
  draw();
  await screen.findByText("학업");
  expect(
    screen.queryByRole("button", { name: "종료일 수정" }),
  ).not.toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: /삭제|취소/ }),
  ).not.toBeInTheDocument();
});
it("진행휴직·퇴사예정의 서로 다른 배지와 등록자·사유를 보여준다", async () => {
  vi.mocked(api.getDeveloperStates).mockResolvedValue({
    ...result,
    items: [
      {
        ...row,
        sn: 8,
        state: "RETIRE",
        startDate: "2026-07-01",
        reason: null,
        allowedActions: ["CANCEL"],
      },
      row,
    ],
  });
  draw();
  await screen.findByText("학업");
  expect(screen.getByText("진행 중")).toBeInTheDocument();
  expect(screen.getByText("휴직")).toHaveClass("sicost-tone-lock");
  expect(screen.getByText("퇴사 예정")).toHaveClass("sicost-tone-warn");
  expect(screen.getAllByText("임시 관리자")).toHaveLength(2);
});
it("LEAVE 저장은 종료일NULL·revision을 보내고 ACT 경고와 즉시 갱신한다", async () => {
  const changed = draw();
  await openCreate();
  setDate(/시작일/, "2026-06-16");
  vi.mocked(api.createDeveloperState).mockResolvedValue({
    sn: 8,
    warnings: [
      {
        field: "state",
        message:
          "겹치는 ACT 투입 1건: 2026-06-01 ~ 2026-06-30. 휴직은 저장했습니다.",
      },
    ],
  });
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  await screen.findByText(/겹치는 ACT/);
  expect(api.createDeveloperState).toHaveBeenCalledWith("DEV-000901", {
    state: "LEAVE",
    startDate: "2026-06-16",
    endDate: null,
    reason: null,
    expectedRevision: "opaque-revision",
  });
  await waitFor(() => expect(changed).toHaveBeenCalledOnce());
  expect(api.getDeveloperStates).toHaveBeenCalledTimes(2);
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
});
it("종료일 수정은 선택된 sn으로 날짜와 NULL을 전달한다", async () => {
  draw();
  await screen.findByText("학업");
  await userEvent.click(screen.getByRole("button", { name: "종료일 수정" }));
  setDate(/종료일/, "2026-06-30");
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  await waitFor(() =>
    expect(api.putDeveloperStateEnd).toHaveBeenCalledWith("DEV-000901", 7, {
      endDate: "2026-06-30",
      expectedRevision: "opaque-revision",
    }),
  );
});
it("빈 종료일 수정도 명시NULL이며 changed=false를 구별한다", async () => {
  vi.mocked(api.putDeveloperStateEnd).mockResolvedValue({ changed: false });
  draw();
  await screen.findByText("학업");
  await userEvent.click(screen.getByRole("button", { name: "종료일 수정" }));
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  await screen.findByText("바뀐 내용이 없습니다.");
  expect(api.putDeveloperStateEnd).toHaveBeenCalledWith("DEV-000901", 7, {
    endDate: null,
    expectedRevision: "opaque-revision",
  });
});
it("시작일 첫오류를 한국어로 표시하고 포커스한다", async () => {
  draw();
  await openCreate();
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  expect(
    screen.getByText("시작일을 YYYY-MM-DD로 입력해 주세요."),
  ).toBeInTheDocument();
  expect(screen.getByLabelText(/시작일/)).toHaveFocus();
  expect(api.createDeveloperState).not.toHaveBeenCalled();
});
it("dirty 취소는 계속작성으로 보존하고 명시적 나가기만 폐기한다", async () => {
  draw();
  await openCreate();
  await userEvent.type(screen.getByLabelText(/사유/), "보존할 사유");
  await userEvent.click(screen.getByRole("button", { name: "취소" }));
  await userEvent.click(screen.getByRole("button", { name: "계속 작성" }));
  expect(screen.getByLabelText(/사유/)).toHaveValue("보존할 사유");
  await userEvent.click(screen.getByRole("button", { name: "취소" }));
  await userEvent.click(screen.getByRole("button", { name: "나가기" }));
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  expect(api.createDeveloperState).not.toHaveBeenCalled();
});
it("마감은 경고성공이 아닌 차단으로 표시하고 입력·requestId·차단월을 보존한다", async () => {
  vi.mocked(api.createDeveloperState).mockRejectedValue(
    new ApiError(409, {
      success: false,
      meta: {},
      code: "CLOSED_MONTH",
      message: "변경 구간에 마감된 달이 있어 저장할 수 없습니다.",
      requestId: "closed-request",
      details: { blockingMonths: ["2026-06"] } as never,
    }),
  );
  draw();
  await openCreate();
  setDate(/시작일/, "2026-06-16");
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("closed-request");
  expect(screen.getByText("차단월: 2026-06")).toBeInTheDocument();
  expect(screen.getByLabelText(/시작일/)).toHaveValue("2026-06-16");
  expect(api.getDeveloperStates).toHaveBeenCalledOnce();
});
it("commit후 조회실패는 저장됨 안내·재조회만 하고 저장을 반복하지 않는다", async () => {
  vi.mocked(api.getDeveloperStates)
    .mockResolvedValueOnce(result)
    .mockRejectedValueOnce(new Error("read failed"))
    .mockResolvedValue(result);
  draw();
  await openCreate();
  setDate(/시작일/, "2026-06-16");
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  await screen.findByText(/저장은 완료됐지만/);
  expect(screen.getByRole("button", { name: "상태 등록" })).toBeDisabled();
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  await screen.findByText("저장한 내용을 다시 조회했습니다.");
  expect(api.createDeveloperState).toHaveBeenCalledOnce();
  expect(api.getDeveloperStates).toHaveBeenCalledTimes(3);
});
it("WORK는 종료일 입력과 payload를 제외하고 경계안내를 보인다", async () => {
  draw();
  await openCreate();
  await userEvent.selectOptions(screen.getByLabelText("상태 *"), "WORK");
  expect(screen.queryByLabelText(/종료일/)).not.toBeInTheDocument();
  expect(screen.getByText(/휴직 중인 사람만 재직/)).toBeInTheDocument();
  setDate(/복귀일/, "2026-06-16");
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  await waitFor(() =>
    expect(api.createDeveloperState).toHaveBeenCalledWith("DEV-000901", {
      state: "WORK",
      startDate: "2026-06-16",
      reason: null,
      expectedRevision: "opaque-revision",
    }),
  );
});
it("WORK 취소는 확인 후 해당 행만 취소한다", async () => {
  vi.mocked(api.getDeveloperStates).mockResolvedValue({
    ...result,
    items: [{ ...row, state: "WORK", allowedActions: ["CANCEL"] }],
  });
  draw();
  await screen.findByText("학업");
  await userEvent.click(screen.getByRole("button", { name: "재직 취소" }));
  expect(screen.getByText(/전체 이력을 검사합니다/)).toBeInTheDocument();
  expect(api.saveDeveloperStateCancel).not.toHaveBeenCalled();
  await userEvent.click(screen.getByRole("button", { name: "재직 취소 확인" }));
  await waitFor(() =>
    expect(api.saveDeveloperStateCancel).toHaveBeenCalledWith("DEV-000901", 7, {
      reason: null,
      expectedRevision: "opaque-revision",
    }),
  );
});
it("RETIRE는 마지막근무일·조기철수 안내와 종료일 없는 등록", async () => {
  draw();
  await openCreate();
  await userEvent.selectOptions(screen.getByLabelText("상태 *"), "RETIRE");
  expect(screen.getByText(/먼저 조기 철수/)).toBeInTheDocument();
  setDate(/마지막 근무일/, "2026-06-30");
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  await waitFor(() =>
    expect(api.createDeveloperState).toHaveBeenCalledWith("DEV-000901", {
      state: "RETIRE",
      startDate: "2026-06-30",
      reason: null,
      expectedRevision: "opaque-revision",
    }),
  );
});
it("실제퇴사자 취소도 허용작업만 보고 사유를 필수로 검증한다", async () => {
  vi.mocked(api.getDeveloperStates).mockResolvedValue({
    ...result,
    items: [
      {
        ...row,
        state: "RETIRE",
        startDate: "2026-06-14",
        allowedActions: ["CANCEL"],
      },
    ],
  });
  draw();
  await screen.findByText("학업");
  await userEvent.click(screen.getByRole("button", { name: "퇴사 취소" }));
  await userEvent.click(screen.getByRole("button", { name: "퇴사 취소 확인" }));
  expect(screen.getByLabelText("사유 *")).toHaveFocus();
  expect(api.saveDeveloperStateCancel).not.toHaveBeenCalled();
  await userEvent.type(screen.getByLabelText("사유 *"), "잘못된 등록");
  await userEvent.click(screen.getByRole("button", { name: "퇴사 취소 확인" }));
  await waitFor(() =>
    expect(api.saveDeveloperStateCancel).toHaveBeenCalledWith("DEV-000901", 7, {
      reason: "잘못된 등록",
      expectedRevision: "opaque-revision",
    }),
  );
});
it("서버 필드오류는 첫 필드 포커스·입력보존하고 자동재전송하지 않는다", async () => {
  vi.mocked(api.createDeveloperState).mockRejectedValue(
    new ApiError(400, {
      success: false,
      meta: {},
      code: "VALIDATION_ERROR",
      message: "입사일을 확인해 주세요.",
      field: "startDate",
      requestId: "request-field",
    }),
  );
  draw();
  await openCreate();
  setDate(/시작일/, "2026-06-16");
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  await screen.findByText(/request-field/);
  expect(screen.getByLabelText(/시작일/)).toHaveFocus();
  expect(api.createDeveloperState).toHaveBeenCalledOnce();
});
it("중복 저장은 pending 동안 금지한다", async () => {
  let resolve!: (value: api.DeveloperStateCreated) => void;
  vi.mocked(api.createDeveloperState).mockReturnValue(
    new Promise((r) => {
      resolve = r;
    }),
  );
  draw();
  await openCreate();
  setDate(/시작일/, "2026-06-16");
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  expect(screen.getByRole("button", { name: "처리 중…" })).toBeDisabled();
  expect(screen.getByLabelText(/시작일/)).toBeDisabled();
  resolve({ sn: 8, warnings: [] });
  await waitFor(() =>
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument(),
  );
  expect(api.createDeveloperState).toHaveBeenCalledOnce();
});
it.each([767, 768])(
  "%ipx 모두 단건 상태 입력은 있고 일괄·엑셀·삭제가 없다",
  async (width) => {
    window.innerWidth = width;
    draw();
    await screen.findByText("학업");
    const panel = within(
      screen.getByRole("region", { name: "근무 상태 이력" }),
    );
    expect(panel.getByRole("button", { name: "상태 등록" })).toBeEnabled();
    expect(
      panel.queryByRole("button", { name: /일괄|엑셀|삭제/ }),
    ).not.toBeInTheDocument();
  },
);
