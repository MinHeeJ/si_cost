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
  ProjectPartDialog,
  type ProjectPartDialogProps,
} from "./ProjectPartDialog";
import { ProjectPartsPanel } from "./ProjectPartsPanel";
import { ProjectPartTimeline } from "./ProjectPartTimeline";
import { ApiError } from "../../shared/api";
import * as api from "./projectPartsApi";
import * as choices from "../salespartsops/partStandardsApi";
vi.mock("./projectPartsApi");
vi.mock("../salespartsops/partStandardsApi");
const first: api.ProjectPart = {
  effectiveMonth: "2026-01",
  partId: "A",
  partName: "최초 파트",
  reason: null,
  actor: "admin",
  scheduled: false,
};
const base: api.ProjectPart = {
  ...first,
  effectiveMonth: "2026-03",
  partId: "B",
  partName: "보존 파트",
};
const target: api.ProjectPart = {
  ...first,
  effectiveMonth: "2026-08",
  partId: "C",
  partName: "예약 파트",
  scheduled: true,
};
const original: api.ProjectPartsData = {
  items: [first, base, target],
  replacementTargets: [target],
  retainedBasePart: base,
  minimumMonth: "2026-04",
  maximumMonth: "2027-12",
  revision: "before",
};
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api.getProjectParts).mockResolvedValue(original);
  vi.mocked(choices.getSalesPartOptions).mockResolvedValue({
    items: [
      { id: "B", name: "보존 파트", hqYn: "N", firstEffectiveMonth: "2026-01" },
      { id: "C", name: "예약 파트", hqYn: "N", firstEffectiveMonth: "2026-01" },
      { id: "HQ", name: "직접 영업", hqYn: "Y", firstEffectiveMonth: null },
    ],
  });
  vi.mocked(api.saveProjectPart).mockImplementation(async (_id, body) => ({
    value: {
      ...original,
      replacementTargets: [],
      revision: "after",
      items:
        body.partId === "B"
          ? [first, base]
          : [
              first,
              base,
              {
                ...target,
                effectiveMonth: body.effectiveMonth,
                partId: body.partId,
              },
            ],
    },
    revision: "after",
    changed: true,
  }));
});
function props(
  overrides: Partial<ProjectPartDialogProps> = {},
): ProjectPartDialogProps {
  return {
    projectId: "PARTS-1",
    projectName: "파트 시험",
    year: 2026,
    currentYear: 2026,
    effectiveStatus: "ING",
    contractType: "TK",
    onClose: vi.fn(),
    onSaved: vi.fn().mockResolvedValue(undefined),
    onDirtyChange: vi.fn(),
    ...overrides,
  };
}
function draw(overrides: Partial<ProjectPartDialogProps> = {}) {
  const p = props(overrides);
  render(<ProjectPartDialog {...p} />);
  return p;
}
async function ready() {
  await waitFor(() =>
    expect(screen.getByRole("button", { name: "저장" })).not.toBeDisabled(),
  );
}
async function enter(part = "HQ", month = "2026-07", reason = "  변경 사유  ") {
  await ready();
  fireEvent.change(screen.getByRole("combobox"), { target: { value: part } });
  fireEvent.change(screen.getByLabelText("적용 시작월 *"), {
    target: { value: month },
  });
  fireEvent.change(screen.getByRole("textbox", { name: /사유/ }), {
    target: { value: reason },
  });
}
it("보존 마지막 기준파트·교체대상 전체·허용월·보존 안내를 공통 창으로 표시한다", async () => {
  draw();
  await ready();
  expect(screen.getByRole("dialog", { name: "영업 파트 변경" })).toHaveClass(
    "sicost-dialog",
  );
  expect(screen.getByText("2026-08 · 예약 파트")).toBeInTheDocument();
  expect(screen.getByText(/허용월 2026-04 ~ 2027-12/)).toBeInTheDocument();
  expect(screen.getByText(/최초 행과 마감·이관된 시작행은 보존/)).toHaveClass(
    "sicost-tone-info",
  );
  expect(screen.getByRole("combobox")).toHaveValue("B");
  expect(
    screen.getByRole("option", { name: "직접 영업 (본사)" }),
  ).toBeInTheDocument();
});
it("HQ와 선택연도·조회revision·사유원문을 보내고 최종값을 통지한다", async () => {
  const p = draw();
  await enter();
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  await screen.findByText("영업 파트를 저장했습니다.");
  expect(api.saveProjectPart).toHaveBeenCalledWith("PARTS-1", {
    year: 2026,
    expectedRevision: "before",
    partId: "HQ",
    effectiveMonth: "2026-07",
    reason: "  변경 사유  ",
  });
  expect(p.onSaved).toHaveBeenCalledWith(
    expect.objectContaining({ changed: true, revision: "after" }),
  );
  expect(p.onDirtyChange).toHaveBeenLastCalledWith(false);
});
it("기준과 같은 선택은 예약 취소 안내와 서버 저장을 사용한다", async () => {
  draw();
  await enter("B");
  expect(
    screen.getByText(/기존 예약을 취소하고 새 행은 추가하지 않습니다/),
  ).toHaveClass("sicost-tone-warn");
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  await screen.findByText("영업 파트를 저장했습니다.");
  expect(screen.getByText("교체 대상 없음")).toBeInTheDocument();
});
it("예약없고 기준같음의 무변경을 표시한다", async () => {
  vi.mocked(api.getProjectParts).mockResolvedValue({
    ...original,
    items: [first, base],
    replacementTargets: [],
  });
  vi.mocked(api.saveProjectPart).mockResolvedValue({
    value: { ...original, items: [first, base], replacementTargets: [] },
    revision: "before",
    changed: false,
  });
  draw();
  await enter("B");
  expect(screen.getByText(/바뀐 내용 없이 저장/)).toBeInTheDocument();
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  await screen.findByText("바뀐 내용이 없습니다.");
});
it.each(["2026-01", "2026-03", "2028-01"])(
  "허용범위 또는 보존시작월을 벗어난 %s은 거부한다",
  async (month) => {
    draw();
    await enter("HQ", month);
    await userEvent.click(screen.getByRole("button", { name: "저장" }));
    expect(screen.getByLabelText("적용 시작월 *")).toHaveAttribute(
      "aria-invalid",
      "true",
    );
    expect(api.saveProjectPart).not.toHaveBeenCalled();
  },
);
it("500자 초과 사유는 잘라 보내지 않고 오류와 입력을 유지한다", async () => {
  draw();
  await enter("HQ", "2026-07", "가".repeat(501));
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  expect(screen.getByRole("textbox", { name: /사유/ })).toHaveValue(
    "가".repeat(501),
  );
  expect(
    screen.getByText("사유는 500자 이하로 입력해 주세요."),
  ).toBeInTheDocument();
  expect(api.saveProjectPart).not.toHaveBeenCalled();
});
it.each([
  { year: 2025 },
  { effectiveStatus: "END" },
  { effectiveStatus: "LOSE" },
  { contractType: "IN" },
  { readOnly: true },
])("읽기전용 %j에는 편집/저장을 표시하지 않는다", async (state) => {
  draw(state);
  await screen.findByText(/조회만 할 수 있습니다/);
  expect(screen.queryByRole("combobox")).not.toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: "저장" }),
  ).not.toBeInTheDocument();
  expect(api.saveProjectPart).not.toHaveBeenCalled();
});
it("초기이력 없음은 가상행 없이 표시하고 저장을 막는다", async () => {
  vi.mocked(api.getProjectParts).mockResolvedValue({
    ...original,
    items: [],
    replacementTargets: [],
    retainedBasePart: null,
  });
  draw();
  await screen.findByText(/최초 이력을 확인/);
  expect(screen.getByRole("button", { name: "저장" })).toBeDisabled();
});
it("로딩/조회실패에서 저장차단·재조회만 수행한다", async () => {
  vi.mocked(api.getProjectParts).mockRejectedValueOnce(
    new Error("파트 조회 실패"),
  );
  draw();
  expect(screen.getByRole("button", { name: "저장" })).toBeDisabled();
  await screen.findByText("파트 조회 실패");
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  await ready();
  expect(api.getProjectParts).toHaveBeenCalledTimes(2);
  expect(api.saveProjectPart).not.toHaveBeenCalled();
});
it("옵션 실패도 입력을 막고 다시 조회할 수 있다", async () => {
  vi.mocked(choices.getSalesPartOptions).mockRejectedValueOnce(
    new Error("선택 항목 실패"),
  );
  draw();
  await screen.findByText("선택 항목 실패");
  expect(screen.getByRole("button", { name: "저장" })).toBeDisabled();
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  await ready();
});
it("필드오류·requestId를 표시하며 입력을 유지한다", async () => {
  vi.mocked(api.saveProjectPart).mockRejectedValue(
    new ApiError(400, {
      success: false,
      meta: {},
      code: "INVALID_INPUT",
      message: "사유 확인",
      field: "reason",
      requestId: "parts-field",
    }),
  );
  draw();
  await enter();
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  await waitFor(() =>
    expect(screen.getByRole("textbox", { name: /사유/ })).toHaveAttribute(
      "aria-invalid",
      "true",
    ),
  );
  expect(screen.getByText(/parts-field/)).toBeInTheDocument();
  expect(screen.getByRole("textbox", { name: /사유/ })).toHaveValue(
    "  변경 사유  ",
  );
});
it("STALE_WRITE는 자동 재전송 없이 입력을 유지한다", async () => {
  vi.mocked(api.saveProjectPart).mockRejectedValue(
    new ApiError(409, {
      success: false,
      meta: {},
      code: "STALE_WRITE",
      message: "다른 변경",
      requestId: "parts-stale",
    }),
  );
  draw();
  await enter();
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  await screen.findByText(/자동으로 재저장하지 않습니다/);
  expect(api.saveProjectPart).toHaveBeenCalledTimes(1);
  expect(screen.getByRole("combobox")).toHaveValue("HQ");
});
it("저장후 재조회 실패는 저장하지 않고 콜백만 재시도한다", async () => {
  const onSaved = vi
    .fn()
    .mockRejectedValueOnce(new Error("재조회 실패"))
    .mockResolvedValue(undefined);
  draw({ onSaved });
  await enter();
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  await screen.findByText(COMMITTED());
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  await waitFor(() => expect(onSaved).toHaveBeenCalledTimes(2));
  expect(api.saveProjectPart).toHaveBeenCalledTimes(1);
});
function COMMITTED() {
  return "저장은 완료됐지만 목록을 다시 불러오지 못했습니다. 다시 조회해 주세요.";
}
it("dirty를 shell에 전달하고 계속 작성/나가기를 구별한다", async () => {
  const p = draw();
  await enter();
  expect(p.onDirtyChange).toHaveBeenLastCalledWith(true);
  await userEvent.click(screen.getByRole("button", { name: "취소" }));
  const guard = screen.getByRole("dialog", {
    name: "저장하지 않은 내용이 있습니다.",
  });
  await userEvent.click(
    within(guard).getByRole("button", { name: "계속 작성" }),
  );
  expect(p.onClose).not.toHaveBeenCalled();
  expect(screen.getByRole("combobox")).toHaveValue("HQ");
  await userEvent.click(screen.getByRole("button", { name: "취소" }));
  await userEvent.click(screen.getByRole("button", { name: "나가기" }));
  expect(p.onClose).toHaveBeenCalledTimes(1);
});
it("저장중 중복제출과 닫기를 차단한다", async () => {
  let finish!: (value: api.SaveProjectPartResult) => void;
  vi.mocked(api.saveProjectPart).mockReturnValue(
    new Promise((resolve) => {
      finish = resolve;
    }),
  );
  draw();
  await enter();
  fireEvent.submit(screen.getByRole("dialog"));
  fireEvent.submit(screen.getByRole("dialog"));
  await waitFor(() => expect(api.saveProjectPart).toHaveBeenCalledTimes(1));
  expect(screen.getByRole("button", { name: "취소" })).toBeDisabled();
  finish({ value: original, revision: "after", changed: true });
  await screen.findByText("영업 파트를 저장했습니다.");
});
it("월시간선 종료월과 마지막무기한·현재/과거/예정의 다른표식을 렌더한다", () => {
  render(<ProjectPartTimeline items={original.items} currentMonth="2026-07" />);
  expect(screen.getByText("2026-02")).toBeInTheDocument();
  expect(screen.getByText("2026-07")).toBeInTheDocument();
  expect(screen.getByText("종료 없음")).toBeInTheDocument();
  expect(screen.getByText("현재")).toHaveClass("sicost-tone-info");
  expect(screen.getByText("과거")).toHaveClass("sicost-tone-lock");
  expect(screen.getByText("예정")).toHaveClass("sicost-tone-warn");
});
it("상세 영역은 실제조회·변경 버튼으로 창을 열고 동일 shell dirty 콜백을 전달한다", async () => {
  const p = props();
  render(<ProjectPartsPanel {...p} currentMonth="2026-07" />);
  await screen.findByText("최초 파트");
  await userEvent.click(screen.getByRole("button", { name: "변경" }));
  await enter();
  expect(p.onDirtyChange).toHaveBeenLastCalledWith(true);
});
it("읽기전용 상세 영역은 변경버튼을 숨기고 실제시간선을 유지한다", async () => {
  const p = props({ year: 2025 });
  render(<ProjectPartsPanel {...p} currentMonth="2026-07" />);
  await screen.findByText("최초 파트");
  expect(
    screen.queryByRole("button", { name: "변경" }),
  ).not.toBeInTheDocument();
});
it("빈 상세목록은 공통 empty와 비활성 변경을 표시한다", async () => {
  vi.mocked(api.getProjectParts).mockResolvedValue({
    ...original,
    items: [],
    replacementTargets: [],
    retainedBasePart: null,
  });
  render(<ProjectPartsPanel {...props()} currentMonth="2026-07" />);
  await screen.findByText("등록된 영업 파트 이력이 없습니다.");
  expect(screen.getByRole("button", { name: "변경" })).toBeDisabled();
});
