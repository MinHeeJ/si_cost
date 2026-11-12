import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import {
  ProjectPlansPanel,
  type ProjectPlansPanelProps,
} from "./ProjectPlansPanel";
import { ApiError } from "../../shared/api";
import * as api from "./projectPlansApi";
vi.mock("./projectPlansApi", async (original) => ({
  ...(await original<typeof import("./projectPlansApi")>()),
  getProjectPlans: vi.fn(),
  saveProjectPlans: vi.fn(),
  getPlanJobOptions: vi.fn(),
}));
const original: api.ProjectPlansData = {
  items: [
    {
      jobCode: "DEV",
      jobName: "개발",
      useYn: "N",
      planMm: "5.00",
      actualMm: "3.25",
      differenceMm: "-1.75",
      source: "OPP",
    },
    {
      jobCode: "QA",
      jobName: "시험",
      useYn: "Y",
      planMm: null,
      actualMm: "1.00",
      differenceMm: "1.00",
      source: null,
    },
    {
      jobCode: "PM",
      jobName: "관리",
      useYn: "Y",
      planMm: "2.00",
      actualMm: "2.00",
      differenceMm: "0.00",
      source: "MANUAL",
    },
  ],
  planMmTotal: "7.00",
  actualMmTotal: "6.25",
  differenceMmTotal: "-0.75",
  revision: "before",
};
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api.getProjectPlans).mockResolvedValue(original);
  vi.mocked(api.getPlanJobOptions).mockResolvedValue({
    items: [
      { code: "QA", name: "시험" },
      { code: "PM", name: "관리" },
      { code: "BA", name: "분석" },
    ],
    order: ["BA", "DEV", "QA", "PM"],
  });
  vi.mocked(api.saveProjectPlans).mockImplementation(async (_id, body) => {
    const items = original.items.map((row) => {
      const item = body.items.find((value) => value.jobCode === row.jobCode);
      if (!item) return row;
      const units = api.planMmUnits(item.planMm);
      return {
        ...row,
        planMm: api.planMmDisplay(units),
        differenceMm: api
          .planMmDisplay(api.planMmUnits(row.actualMm) - units)
          .replace("−", "-"),
        source: row.source ?? ("MANUAL" as const),
      };
    });
    return {
      value: { ...original, items, revision: "after" },
      revision: "after",
      changed: body.items.length > 0,
    };
  });
});
function draw(overrides: Partial<ProjectPlansPanelProps> = {}) {
  const props: ProjectPlansPanelProps = {
    projectId: "PLAN-1",
    year: 2026,
    currentYear: 2026,
    effectiveStatus: "ING",
    onSaved: vi.fn().mockResolvedValue(undefined),
    onDirtyChange: vi.fn(),
    ...overrides,
  };
  const view = render(<ProjectPlansPanel {...props} />);
  return { ...props, view };
}
async function input(name: string, value: string) {
  const field = await screen.findByRole("textbox", { name: `${name} 계획 MM` });
  fireEvent.change(field, { target: { value } });
  return field;
}
it("4열과 미충원 안내·전체기간 설명·부족/초과/맞음의 개별 색상을 표시한다", async () => {
  draw();
  await screen.findByRole("textbox", { name: "개발 계획 MM" });
  expect(
    screen.getAllByRole("columnheader").map((cell) => cell.textContent),
  ).toEqual(["직무", "계획 MM", "실제 MM", "차이"]);
  expect(screen.getByText("중지")).toHaveClass("sicost-tone-lock");
  const rows = screen.getAllByRole("row");
  expect(within(rows[1]).getByText("부족")).toHaveClass("sicost-tone-bad");
  expect(within(rows[2]).getByText("초과")).toHaveClass("sicost-tone-warn");
  expect(within(rows[3]).getByText("맞음")).toHaveClass("sicost-tone-ok");
  expect(screen.getByText(/계획보다 모자라는 MM/)).toHaveTextContent(
    "더 투입하지 않으면 계획 MM을 실제에 맞게 줄여 주세요.",
  );
  expect(screen.getByText(/프로젝트 전체 기간 총 MM/)).toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: /삭제/ }),
  ).not.toBeInTheDocument();
  expect(screen.getAllByRole("textbox")).toHaveLength(3);
});
it("미저장 계획은 빈칸이고 실제·차이는 입력이 아니다", async () => {
  draw();
  expect(
    await screen.findByRole("textbox", { name: "시험 계획 MM" }),
  ).toHaveValue("");
  expect(
    screen.queryByRole("textbox", { name: /실제|차이/ }),
  ).not.toBeInTheDocument();
});
it("중지된 OPP 행도 수정하며 실제/차이/출처 없이 연도·revision·변경직무만 전송한다", async () => {
  const props = draw();
  await input("개발", "0");
  await input("시험", "1");
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  await screen.findByText("인원 계획을 저장했습니다.");
  expect(api.saveProjectPlans).toHaveBeenCalledWith("PLAN-1", {
    year: 2026,
    expectedRevision: "before",
    items: [
      { jobCode: "DEV", planMm: "0" },
      { jobCode: "QA", planMm: "1" },
    ],
  });
  expect(props.onSaved).toHaveBeenCalledWith(
    expect.objectContaining({ changed: true, revision: "after" }),
  );
  expect(props.onDirtyChange).toHaveBeenLastCalledWith(false);
});
it("빈값 저장은 0으로 전송하며 합계를 0으로 계산한다", async () => {
  draw();
  await input("개발", "");
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  await screen.findByText("인원 계획을 저장했습니다.");
  expect(api.saveProjectPlans).toHaveBeenCalledWith(
    "PLAN-1",
    expect.objectContaining({
      items: [
        { jobCode: "DEV", planMm: "0" },
        { jobCode: "QA", planMm: "0" },
      ],
    }),
  );
});
it.each(["-1", "+1", "1e3", "1.001", "10000", "문자"])(
  "잘못된 MM %s은 입력 유지와 필드 오류를 표시하고 저장하지 않는다",
  async (value) => {
    draw();
    const field = await input("개발", value);
    await userEvent.click(screen.getByRole("button", { name: "저장" }));
    expect(field).toHaveAttribute("aria-invalid", "true");
    expect(field).toHaveValue(value);
    expect(api.saveProjectPlans).not.toHaveBeenCalled();
  },
);
it("각 9999.99는 합계가 상한을 넘어도 축소하지 않고 실시간 합계는 정확하다", async () => {
  draw();
  await input("개발", "9999.99");
  await input("시험", "9999.99");
  expect(screen.getByLabelText("MM 합계")).toHaveTextContent("계획 20001.98");
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  await screen.findByText("인원 계획을 저장했습니다.");
  expect(api.saveProjectPlans).toHaveBeenCalledWith(
    "PLAN-1",
    expect.objectContaining({
      items: [
        { jobCode: "DEV", planMm: "9999.99" },
        { jobCode: "QA", planMm: "9999.99" },
      ],
    }),
  );
});
it("직무 추가 창은 활성 미존재만 제공하고 로컬 추가·JOB순서이며 저장 전 DB요청은 없다", async () => {
  const props = draw();
  await screen.findByRole("textbox", { name: "개발 계획 MM" });
  await userEvent.click(screen.getByRole("button", { name: "직무 추가" }));
  const dialog = screen.getByRole("dialog", { name: "직무 추가" });
  await within(dialog).findByRole("option", { name: "분석 (BA)" });
  expect(
    within(dialog).queryByRole("option", { name: "시험 (QA)" }),
  ).not.toBeInTheDocument();
  expect(
    within(dialog).queryByRole("option", { name: /개발/ }),
  ).not.toBeInTheDocument();
  await userEvent.selectOptions(within(dialog).getByRole("combobox"), "BA");
  await userEvent.click(within(dialog).getByRole("button", { name: "추가" }));
  expect(screen.getAllByRole("textbox")[0]).toHaveAccessibleName(
    "분석 계획 MM",
  );
  expect(props.onDirtyChange).toHaveBeenLastCalledWith(true);
  expect(api.saveProjectPlans).not.toHaveBeenCalled();
});
it("추가 가능한 직무가 없으면 설명과 비활성 CTA를 보인다", async () => {
  vi.mocked(api.getPlanJobOptions).mockResolvedValue({
    items: [{ code: "QA", name: "시험" }],
    order: ["QA"],
  });
  draw();
  await screen.findByRole("textbox", { name: "개발 계획 MM" });
  await userEvent.click(screen.getByRole("button", { name: "직무 추가" }));
  await screen.findByText("추가 가능한 직무가 없습니다.");
  expect(screen.getByRole("button", { name: "추가" })).toBeDisabled();
});
it("직무 옵션 오류는 창에서 재조회만 하고 입력행을 보존한다", async () => {
  vi.mocked(api.getPlanJobOptions).mockRejectedValueOnce(
    new Error("직무 조회 실패"),
  );
  draw();
  await input("개발", "4");
  await userEvent.click(screen.getByRole("button", { name: "직무 추가" }));
  const dialog = screen.getByRole("dialog", { name: "직무 추가" });
  await within(dialog).findByText("직무 조회 실패");
  await userEvent.click(
    within(dialog).getByRole("button", { name: "다시 조회" }),
  );
  await within(dialog).findByRole("combobox");
  expect(screen.getByRole("textbox", { name: "개발 계획 MM" })).toHaveValue(
    "4",
  );
  expect(api.saveProjectPlans).not.toHaveBeenCalled();
});
it.each([
  { year: 2025 },
  { effectiveStatus: "LOSE" },
  { effectiveStatus: "END" },
  { readOnly: true },
])(
  "기간/상태 경계 %j에서는 수정·직무 추가·저장을 숨기고 조회를 유지한다",
  async (state) => {
    draw(state);
    await screen.findByText(/조회만 할 수 있습니다/);
    await screen.findByText("개발", { exact: false });
    expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "직무 추가" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "저장" }),
    ).not.toBeInTheDocument();
    expect(api.saveProjectPlans).not.toHaveBeenCalled();
  },
);
it("PM 여부로 편집권한을 제한하지 않고 내부 프로젝트 계획도 동일하게 제공한다", async () => {
  draw();
  const field = await screen.findByRole("textbox", { name: "개발 계획 MM" });
  expect(field).toBeEnabled();
});
it("빈 목록은 가상행 대신 공통 빈 상태를 보인다", async () => {
  vi.mocked(api.getProjectPlans).mockResolvedValue({
    items: [],
    planMmTotal: "0.00",
    actualMmTotal: "0.00",
    differenceMmTotal: "0.00",
    revision: "empty",
  });
  draw();
  await screen.findByText("등록된 인원 계획과 실제 투입 직무가 없습니다.");
  expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
});
it("로딩과 조회 실패에는 저장을 막고 조회 재시도만 실행한다", async () => {
  vi.mocked(api.getProjectPlans).mockRejectedValueOnce(
    new Error("계획 조회 실패"),
  );
  draw();
  expect(screen.getByRole("button", { name: "저장" })).toBeDisabled();
  await screen.findByText("계획 조회 실패");
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  await screen.findByRole("textbox", { name: "개발 계획 MM" });
  expect(api.getProjectPlans).toHaveBeenCalledTimes(2);
  expect(api.saveProjectPlans).not.toHaveBeenCalled();
});
it("서버 항목 오류와 requestId를 표시하고 사용자의 입력을 유지한다", async () => {
  vi.mocked(api.saveProjectPlans).mockRejectedValue(
    new ApiError(400, {
      success: false,
      meta: {},
      code: "INVALID_INPUT",
      field: "items[0].planMm",
      message: "계획 확인",
      requestId: "plan-error",
    }),
  );
  draw();
  const field = await input("개발", "4");
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  await waitFor(() => expect(field).toHaveAttribute("aria-invalid", "true"));
  expect(field).toHaveValue("4");
  expect(screen.getByText(/plan-error/)).toBeInTheDocument();
});
it("stale는 재저장 차단·dirty 확인으로 입력 유지 또는 명시 최신 재조회한다", async () => {
  vi.mocked(api.saveProjectPlans).mockRejectedValue(
    new ApiError(409, {
      success: false,
      meta: {},
      code: "STALE_WRITE",
      message: "변경 충돌",
      requestId: "plan-stale",
    }),
  );
  draw();
  await input("개발", "4");
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  await screen.findByText(/자동으로 재저장하지 않습니다/);
  expect(screen.getByRole("button", { name: "저장" })).toBeDisabled();
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  await userEvent.click(screen.getByRole("button", { name: "계속 작성" }));
  expect(screen.getByRole("textbox", { name: "개발 계획 MM" })).toHaveValue(
    "4",
  );
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  await userEvent.click(screen.getByRole("button", { name: "나가기" }));
  await waitFor(() => expect(api.getProjectPlans).toHaveBeenCalledTimes(2));
  expect(api.saveProjectPlans).toHaveBeenCalledTimes(1);
});
it("저장후 refresh 실패는 재저장하지 않고 refresh 콜백만 재실행한다", async () => {
  const onSaved = vi
    .fn()
    .mockRejectedValueOnce(new Error("목록 실패"))
    .mockResolvedValue(undefined);
  draw({ onSaved });
  await input("개발", "4");
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  await screen.findByText(COMMITTED_MESSAGE);
  expect(screen.getByRole("button", { name: "저장" })).toBeDisabled();
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  await waitFor(() => expect(onSaved).toHaveBeenCalledTimes(2));
  expect(api.saveProjectPlans).toHaveBeenCalledTimes(1);
});
const COMMITTED_MESSAGE =
  "저장은 완료됐지만 목록을 다시 불러오지 못했습니다. 다시 조회해 주세요.";
it("같은 저장 응답은 무변경으로 구분한다", async () => {
  vi.mocked(api.saveProjectPlans).mockResolvedValue({
    value: original,
    revision: "before",
    changed: false,
  });
  draw();
  await screen.findByRole("textbox", { name: "개발 계획 MM" });
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  await screen.findByText("바뀐 내용이 없습니다.");
});
it("중복 클릭은 한 번만 저장하고 pending동안 입력과 추가를 막는다", async () => {
  let finish!: (value: api.SaveProjectPlansResult) => void;
  vi.mocked(api.saveProjectPlans).mockReturnValue(
    new Promise((resolve) => {
      finish = resolve;
    }),
  );
  draw();
  await input("개발", "4");
  const button = screen.getByRole("button", { name: "저장" });
  fireEvent.click(button);
  fireEvent.click(button);
  await waitFor(() => expect(api.saveProjectPlans).toHaveBeenCalledTimes(1));
  expect(screen.getByRole("button", { name: "직무 추가" })).toBeDisabled();
  expect(screen.getByRole("textbox", { name: "개발 계획 MM" })).toBeDisabled();
  finish({ value: original, revision: "after", changed: true });
  await screen.findByText("인원 계획을 저장했습니다.");
});
it("dirty는 shell에 전달하고 원값 복구 및 unmount에서 해제한다", async () => {
  const props = draw();
  await input("개발", "4");
  expect(props.onDirtyChange).toHaveBeenLastCalledWith(true);
  await input("개발", "5.00");
  expect(props.onDirtyChange).toHaveBeenLastCalledWith(false);
  await input("개발", "4");
  props.view.unmount();
  expect(props.onDirtyChange).toHaveBeenLastCalledWith(false);
});
it("프로젝트 전환시 이전 프로젝트 입력과 응답을 새 프로젝트에 재사용하지 않는다", async () => {
  const props = draw();
  await input("개발", "4");
  props.view.rerender(
    <ProjectPlansPanel
      projectId="PLAN-2"
      year={2026}
      currentYear={2026}
      effectiveStatus="ING"
      onSaved={vi.fn()}
    />,
  );
  await waitFor(() =>
    expect(api.getProjectPlans).toHaveBeenLastCalledWith("PLAN-2"),
  );
  expect(
    await screen.findByRole("textbox", { name: "개발 계획 MM" }),
  ).toHaveValue("5.00");
});
