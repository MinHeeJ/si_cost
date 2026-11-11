import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { StandardValueDialog } from "./StandardValueDialog";
import { ApiError } from "../../shared/api";
import * as api from "./operatingStandardsApi";
vi.mock("./operatingStandardsApi");
const keys = [
  "AI_SCORE_JOB",
  "AI_SCORE_SKILL",
  "AI_SCORE_CUST",
  "AI_SCORE_GRADE",
  "AI_SCORE_PERF",
  "AI_SCORE_COST",
  "AI_SCORE_AVAIL",
  "AI_SCORE_LOC",
];
const rows: api.OperatingStandard[] = keys.map((key) => ({
  key,
  name: `배점 ${key}`,
  value: "10",
  unitNm: "점",
  effectiveMonth: "2026-01",
  description: "읽기 전용 설명",
  keyRevision: "list",
}));
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api.getOperatingStandardHistory).mockImplementation(
    async (key) => ({
      keyRevision: `token:${key}`,
      items: [
        {
          key,
          value: "20",
          unitNm: "점",
          effectiveMonth: "2026-08",
          status: "예정",
          revision: "future",
        },
        {
          key,
          value: "10",
          unitNm: "점",
          effectiveMonth: "2026-01",
          status: "현재",
          revision: "current",
        },
      ],
    }),
  );
});
function draw(onSave = vi.fn().mockResolvedValue(undefined)) {
  render(
    <StandardValueDialog
      rows={rows}
      minimumMonth="2026-02"
      defaultMonth="2026-06"
      onClose={vi.fn()}
      onSave={onSave}
    />,
  );
  return onSave;
}
it("AI 8개를 네열 묶음에 배치하고 메타는 읽기 전용이다", async () => {
  draw();
  await screen.findByLabelText(/AI_SCORE_JOB 값/);
  expect(screen.getAllByRole("textbox")).toHaveLength(8);
  expect(
    screen
      .getByLabelText(/AI_SCORE_JOB 값/)
      .closest(".operatingstandards-group"),
  ).toBeInTheDocument();
  expect(screen.queryByLabelText("이름")).not.toBeInTheDocument();
  expect(screen.queryByLabelText("단위")).not.toBeInTheDocument();
});
it("변경된 칸만 선택월과 전체 키 이력 토큰으로 보낸다", async () => {
  const save = draw();
  fireEvent.change(await screen.findByLabelText(/AI_SCORE_JOB 값/), {
    target: { value: "15" },
  });
  await userEvent.click(screen.getByRole("button", { name: "변경 확인" }));
  expect(
    screen.getByText(/덮어쓸 월 \/ 변경 적용월: 2026-06/),
  ).toBeInTheDocument();
  await userEvent.click(screen.getByRole("button", { name: "확인 후 저장" }));
  expect(save).toHaveBeenCalledWith({
    effectiveMonth: "2026-06",
    changes: [
      {
        key: "AI_SCORE_JOB",
        value: "15",
        expectedRevision: "token:AI_SCORE_JOB",
      },
    ],
  });
});
it("같은 월의 덮어쓰기 전후값을 확인한다", async () => {
  draw();
  fireEvent.change(await screen.findByLabelText(/AI_SCORE_JOB 값/), {
    target: { value: "25" },
  });
  fireEvent.change(screen.getByLabelText(/적용 시작월/), {
    target: { value: "2026-08" },
  });
  expect(screen.getByLabelText(/AI_SCORE_SKILL 값/)).toHaveValue("20");
  await userEvent.click(screen.getByRole("button", { name: "변경 확인" }));
  expect(
    screen.getByText(/AI_SCORE_JOB: 20 → 25 \(기존 월 덮어쓰기\)/),
  ).toBeInTheDocument();
});
it("최소월 이전은 저장하지 않고 필드 오류로 표시한다", async () => {
  const save = draw();
  await screen.findByLabelText(/AI_SCORE_JOB 값/);
  fireEvent.change(screen.getByLabelText(/적용 시작월/), {
    target: { value: "2026-01" },
  });
  fireEvent.submit(screen.getByRole("dialog"));
  await waitFor(() =>
    expect(screen.getByLabelText(/적용 시작월/)).toHaveAttribute(
      "aria-invalid",
      "true",
    ),
  );
  expect(save).not.toHaveBeenCalled();
});
it("합 99 거부를 필드에 표시하고 모든 입력을 보존한다", async () => {
  const save = vi.fn().mockRejectedValue(
    new ApiError(400, {
      success: false,
      meta: {},
      code: "VALIDATION_ERROR",
      message: "배점 합은 100이어야 합니다.",
      field: "changes[0].value",
      requestId: "sum99",
    }),
  );
  draw(save);
  fireEvent.change(await screen.findByLabelText(/AI_SCORE_JOB 값/), {
    target: { value: "9" },
  });
  await userEvent.click(screen.getByRole("button", { name: "변경 확인" }));
  await userEvent.click(screen.getByRole("button", { name: "확인 후 저장" }));
  await waitFor(() =>
    expect(screen.getByLabelText(/AI_SCORE_JOB 값/)).toHaveAttribute(
      "aria-invalid",
      "true",
    ),
  );
  expect(screen.getByLabelText(/AI_SCORE_JOB 값/)).toHaveValue("9");
  expect(save).toHaveBeenCalledTimes(1);
});
it("STALE_WRITE 실패시 자동 재저장하지 않고 편집을 보존한다", async () => {
  const save = vi.fn().mockRejectedValue(
    new ApiError(409, {
      success: false,
      meta: {},
      code: "STALE_WRITE",
      message: "다른 사용자가 변경했습니다.",
      requestId: "stale",
    }),
  );
  draw(save);
  fireEvent.change(await screen.findByLabelText(/AI_SCORE_JOB 값/), {
    target: { value: "15" },
  });
  await userEvent.click(screen.getByRole("button", { name: "변경 확인" }));
  await userEvent.click(screen.getByRole("button", { name: "확인 후 저장" }));
  expect(
    await screen.findByText(/다른 사용자가 변경했습니다/),
  ).toBeInTheDocument();
  expect(save).toHaveBeenCalledTimes(1);
  expect(screen.getByLabelText(/AI_SCORE_JOB 값/)).toHaveValue("15");
});
it("묶음의 미변경칸은 다른 선택월에 값이 달라도 요청하지 않는다", async () => {
  const save = draw();
  await screen.findByLabelText(/AI_SCORE_JOB 값/);
  fireEvent.change(screen.getByLabelText(/적용 시작월/), {
    target: { value: "2026-08" },
  });
  fireEvent.change(screen.getByLabelText(/AI_SCORE_JOB 값/), {
    target: { value: "25" },
  });
  await userEvent.click(screen.getByRole("button", { name: "변경 확인" }));
  await userEvent.click(screen.getByRole("button", { name: "확인 후 저장" }));
  expect(save).toHaveBeenCalledWith({
    effectiveMonth: "2026-08",
    changes: [
      {
        key: "AI_SCORE_JOB",
        value: "25",
        expectedRevision: "token:AI_SCORE_JOB",
      },
    ],
  });
});
it("이력 조회 실패는 저장하지 않고 조회만 재시도한다", async () => {
  vi.mocked(api.getOperatingStandardHistory).mockRejectedValue(
    new Error("이력 연결 실패"),
  );
  const save = draw();
  expect(await screen.findByRole("alert")).toHaveTextContent("이력 연결 실패");
  await userEvent.click(
    within(screen.getByRole("dialog")).getByRole("button", {
      name: "변경 확인",
    }),
  );
  expect(save).not.toHaveBeenCalled();
});
