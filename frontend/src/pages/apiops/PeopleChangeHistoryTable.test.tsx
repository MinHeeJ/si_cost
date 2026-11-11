import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { PeopleChangeHistoryTable } from "./PeopleChangeHistoryTable";
import type { PeopleHistoryEntry, PeopleHistoryPage } from "./peopleClient";

const entry: PeopleHistoryEntry = {
  historyId: 9,
  targetTable: "TB_HR_DEV",
  targetKey: "selected-person",
  changeType: "UPD",
  itemName: "이름",
  beforeValue: "이전",
  afterValue: "저장 당시 값",
  reason: null,
  actorId: "admin",
  actorName: "임시 관리자",
  createdAt: "2026-01-02 09:00",
};
const page = (
  items: PeopleHistoryEntry[] = [],
  total = items.length,
): PeopleHistoryPage => ({ items, total, page: 1, size: 50 });
const response = (data: PeopleHistoryPage) =>
  new Response(JSON.stringify({ success: true, meta: {}, data }));
const fetcher = vi.fn<typeof fetch>();
beforeEach(() => {
  vi.resetAllMocks();
  vi.stubGlobal("fetch", fetcher);
});
afterEach(() => vi.unstubAllGlobals());

it.each(["DEVELOPER", "CANDIDATE"] as const)(
  "%s 상세의 선택한 사람과 페이지를 사람 API로 조회한다",
  async (targetType) => {
    fetcher.mockImplementation(async () => response(page([entry])));
    render(
      <PeopleChangeHistoryTable
        targetType={targetType}
        targetId="selected-person"
      />,
    );
    await screen.findByText("저장 당시 값");
    const query = new URLSearchParams(
      String(fetcher.mock.calls[0][0]).split("?")[1],
    );
    expect(String(fetcher.mock.calls[0][0])).toMatch(
      /^\/api\/people-history\?/,
    );
    expect(query.get("targetType")).toBe(targetType);
    expect(query.get("targetId")).toBe("selected-person");
    expect(screen.getByTestId("people-history-panel")).toHaveAttribute(
      "data-screen-id",
      targetType === "DEVELOPER" ? "UI-02" : "UI-33",
    );
    expect(screen.getByText("TB_HR_DEV / selected-person")).toBeInTheDocument();
  },
);
it("로딩 중에는 빈 목록을 잘못 표시하지 않고 완료 뒤 빈 이력을 표시한다", async () => {
  let resolve!: (value: Response) => void;
  fetcher.mockReturnValue(
    new Promise((done) => {
      resolve = done;
    }),
  );
  render(
    <PeopleChangeHistoryTable
      targetType="DEVELOPER"
      targetId="selected-person"
    />,
  );
  expect(screen.getByRole("status")).toHaveTextContent("불러오는 중");
  expect(
    screen.queryByText("조회된 변경 이력이 없습니다."),
  ).not.toBeInTheDocument();
  resolve(response(page()));
  expect(
    await screen.findByText("조회된 변경 이력이 없습니다."),
  ).toBeInTheDocument();
});
it("실패하면 한국어 오류와 requestId를 표시하고 명시적 조회만 재시도한다", async () => {
  fetcher
    .mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          success: false,
          meta: {},
          code: "INTERNAL_ERROR",
          message: "이력을 불러오지 못했습니다.",
          requestId: "people-trace",
        }),
        { status: 500 },
      ),
    )
    .mockImplementation(async () => response(page()));
  render(
    <PeopleChangeHistoryTable
      targetType="CANDIDATE"
      targetId="selected-person"
    />,
  );
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "이력을 불러오지 못했습니다.",
  );
  expect(screen.getByRole("alert")).toHaveTextContent("people-trace");
  expect(fetcher).toHaveBeenCalledTimes(1);
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  await screen.findByText("조회된 변경 이력이 없습니다.");
  expect(fetcher).toHaveBeenCalledTimes(2);
  expect(
    fetcher.mock.calls.every(
      ([path, options]) =>
        String(path).startsWith("/api/people-history?") &&
        !options?.method &&
        !options?.body,
    ),
  ).toBe(true);
});
it("기본·가격·스킬·상태의 서버 정렬과 유형별 공통 색을 보존한다", async () => {
  const items: PeopleHistoryEntry[] = [
    {
      ...entry,
      historyId: 4,
      targetTable: "TB_HR_DEV_STAT",
      targetKey: "selected-person:1",
      changeType: "NEW",
    },
    {
      ...entry,
      historyId: 3,
      targetTable: "TB_HR_DEV_SKILL",
      itemName: "보유 스킬",
    },
    {
      ...entry,
      historyId: 2,
      targetTable: "TB_HR_DEV_PRC",
      targetKey: "selected-person:202601",
      changeType: "STOP",
    },
    { ...entry, historyId: 1, changeType: "RESUME" },
  ];
  fetcher.mockImplementation(async () => response(page(items)));
  render(
    <PeopleChangeHistoryTable
      targetType="DEVELOPER"
      targetId="selected-person"
      dense
    />,
  );
  expect(await screen.findByText("등록")).toHaveClass("sicost-tone-ok");
  expect(screen.getByText("변경")).toHaveClass("sicost-tone-info");
  expect(screen.getByText("중지")).toHaveClass("sicost-tone-lock");
  expect(screen.getByText("재개")).toHaveClass("sicost-tone-ok");
  const rows = screen.getAllByRole("row").slice(1);
  expect(
    rows.map((row) => within(row).getAllByRole("cell")[1].textContent),
  ).toEqual(items.map((row) => `${row.targetTable} / ${row.targetKey}`));
  expect(screen.getByRole("table")).toHaveClass("sicost-dense");
});
it("후보 연락처/이메일은 UI에서도 마스킹하며 금액과 일반 감사값은 유지한다", async () => {
  const items = [
    {
      ...entry,
      historyId: 3,
      itemName: "TELNO",
      beforeValue: "private-phone",
      afterValue: "private-next",
      reason: "private-reason",
    },
    {
      ...entry,
      historyId: 2,
      itemName: "이메일",
      beforeValue: null,
      afterValue: "private-email",
    },
    {
      ...entry,
      historyId: 1,
      itemName: "월 단가",
      beforeValue: "100",
      afterValue: "200",
    },
  ];
  fetcher.mockImplementation(async () => response(page(items)));
  const { container } = render(
    <PeopleChangeHistoryTable
      targetType="CANDIDATE"
      targetId="selected-person"
    />,
  );
  await screen.findByText("200");
  expect(screen.getAllByText("[비공개]")).toHaveLength(3);
  expect(container.textContent).not.toContain("private-");
  expect(items[0].beforeValue).toBe("private-phone");
});
it("사람 이력은 읽기 전용이며 일괄·엑셀·권한/로그인 제어를 발명하지 않는다", async () => {
  fetcher.mockImplementation(async () => response(page([entry])));
  render(
    <PeopleChangeHistoryTable
      targetType="CANDIDATE"
      targetId="selected-person"
    />,
  );
  await screen.findByText("저장 당시 값");
  for (const name of [
    "수정",
    "삭제",
    "저장",
    "엑셀 업로드",
    "목록 다운로드",
    "로그인",
  ])
    expect(screen.queryByRole("button", { name })).not.toBeInTheDocument();
  expect(screen.queryByRole("checkbox")).not.toBeInTheDocument();
  expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
});
it("페이지/크기 변경은 서버 조회하며 크기 변경은 첫 페이지로 돌아간다", async () => {
  fetcher.mockImplementation(async () => response(page([entry], 101)));
  render(
    <PeopleChangeHistoryTable
      targetType="DEVELOPER"
      targetId="selected-person"
    />,
  );
  await screen.findByText("저장 당시 값");
  await userEvent.click(screen.getByRole("button", { name: "다음 페이지" }));
  await waitFor(() =>
    expect(String(fetcher.mock.lastCall?.[0])).toContain("page=2&size=50"),
  );
  await waitFor(() =>
    expect(screen.getByLabelText("한 화면 건수")).toBeEnabled(),
  );
  await userEvent.selectOptions(screen.getByLabelText("한 화면 건수"), "20");
  await waitFor(() =>
    expect(String(fetcher.mock.lastCall?.[0])).toContain("page=1&size=20"),
  );
});
it("대상 변경/닫기는 요청을 중단하고 늦은 응답이 새 대상 이력을 덮지 않는다", async () => {
  let resolve!: (value: Response) => void;
  fetcher
    .mockReturnValueOnce(
      new Promise((done) => {
        resolve = done;
      }),
    )
    .mockImplementation(async () => response(page()));
  const { rerender, unmount } = render(
    <PeopleChangeHistoryTable targetType="DEVELOPER" targetId="first-person" />,
  );
  const oldSignal = fetcher.mock.calls[0][1]?.signal;
  rerender(
    <PeopleChangeHistoryTable
      targetType="CANDIDATE"
      targetId="second-person"
    />,
  );
  expect(oldSignal?.aborted).toBe(true);
  await screen.findByText("조회된 변경 이력이 없습니다.");
  await act(async () => resolve(response(page([entry]))));
  expect(screen.queryByText("저장 당시 값")).not.toBeInTheDocument();
  const currentSignal = fetcher.mock.lastCall?.[1]?.signal;
  unmount();
  expect(currentSignal?.aborted).toBe(true);
});
it("저장 후 refreshKey 변경은 즉시 재조회하며 조회 실패 후에는 저장 없이 재조회만 한다", async () => {
  fetcher
    .mockImplementationOnce(async () => response(page()))
    .mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          success: false,
          meta: {},
          code: "INTERNAL_ERROR",
          message: "저장된 이력을 다시 조회해 주세요.",
          requestId: "after-save",
        }),
        { status: 500 },
      ),
    )
    .mockImplementation(async () => response(page([entry])));
  const { rerender } = render(
    <PeopleChangeHistoryTable
      targetType="DEVELOPER"
      targetId="selected-person"
      refreshKey={1}
    />,
  );
  await screen.findByText("조회된 변경 이력이 없습니다.");
  rerender(
    <PeopleChangeHistoryTable
      targetType="DEVELOPER"
      targetId="selected-person"
      refreshKey={2}
    />,
  );
  expect(await screen.findByRole("alert")).toHaveTextContent("after-save");
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  await screen.findByText("저장 당시 값");
  expect(fetcher).toHaveBeenCalledTimes(3);
  expect(
    fetcher.mock.calls.every(
      ([, options]) => !options?.method && !options?.body,
    ),
  ).toBe(true);
});
it("긴 값은 안전한 텍스트로 펼치며 개인정보가 아닌 원문을 절단하지 않는다", async () => {
  const value = "<script>스킬 목록</script>".repeat(6);
  fetcher.mockImplementation(async () =>
    response(page([{ ...entry, afterValue: value }])),
  );
  render(
    <PeopleChangeHistoryTable
      targetType="DEVELOPER"
      targetId="selected-person"
    />,
  );
  await userEvent.click(await screen.findByText("긴 값 펼침"));
  expect(screen.getByText(value).closest("details")).toHaveAttribute("open");
});
