import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { DeveloperPoolScreen } from "./DeveloperPoolScreen";
import { DeveloperForm, developerBanner, priceHelp } from "./DeveloperForm";
import { DeveloperDrawer } from "./DeveloperDrawer";
import { ApiError } from "../../shared/api";
import * as api from "./peopleClient";
import * as companies from "../clientsops/clientActionsApi";
import * as parts from "../salespartsops/partStandardsApi";
import * as areas from "../../shared/areaClient";
import * as stateApi from "../states/developerStatesApi";
import * as sharedApi from "../shared/sharedApi";
vi.mock("./peopleClient");
vi.mock("../clientsops/clientActionsApi");
vi.mock("../salespartsops/partStandardsApi");
vi.mock("../../shared/areaClient");
vi.mock("../states/developerStatesApi");
vi.mock("../shared/sharedApi");
const developer: api.DeveloperDetail = {
  id: "DEV-000901",
  managementNo: "K-0901",
  name: "검증개발자",
  companyId: null,
  companyName: null,
  employeeType: "FT",
  grade: "중급",
  job: "개발",
  status: "WTHDR",
  useYn: "Y",
  retireDate: null,
  retirementScheduled: false,
  currentProjects: [{ id: "P-901", name: "검증프로젝트" }],
  currentProjectLabel: "검증프로젝트",
  withdrawalDate: "2026-08-31",
  monthlyMm: "1.10",
  mmOver: "1.00",
  warnings: [{ field: "monthlyMm", message: "기준값 1.00 초과" }],
  monthlyRate: "5000000",
  revision: "revision",
  gradeCode: "MD",
  jobCode: "DEV",
  subJobCode: null,
  positionCode: "MEMBER",
  directClass: "DRCT",
  partId: "HQ",
  hireDate: "2020-01-01",
  homeAreaCode: null,
  homeAddress: null,
  monthlyPay: "3000000",
  skills: ["Java"],
  addressPath: { items: [], homeAddress: null },
  priceHistory: [
    {
      effectiveMonth: "2026-06",
      endMonth: null,
      monthlyRate: "5000000",
      monthlyPay: "3000000",
    },
  ],
  editPrice: {
    effectiveMonth: "2026-06",
    endMonth: null,
    monthlyRate: "5000000",
    monthlyPay: "3000000",
  },
};
const codes: Record<string, { code: string; name: string }[]> = {
  EMP_TYPE: [
    { code: "FT", name: "정규직" },
    { code: "OS", name: "외주" },
  ],
  JOB: [{ code: "DEV", name: "개발" }],
  GRADE: [{ code: "MD", name: "중급" }],
  JBPS: [{ code: "MEMBER", name: "팀원" }],
  DRCT_CLS: [
    { code: "DRCT", name: "직접" },
    { code: "INDRCT", name: "간접" },
  ],
  WORK_STAT: [
    { code: "WTHDR", name: "철수예정" },
    { code: "IDLE", name: "대기" },
    { code: "WORK", name: "재직" },
  ],
};
beforeEach(() => {
  vi.resetAllMocks();
  Object.defineProperty(window, "matchMedia", {
    configurable: true,
    value: vi.fn().mockReturnValue({
      matches: false,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    }),
  });
  window.scrollTo = vi.fn();
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      text: async () =>
        JSON.stringify({ success: true, meta: {}, data: { items: [] } }),
    }),
  );
  vi.mocked(api.getPeopleCodes).mockImplementation(async (group) => ({
    items: codes[group] ?? [],
  }));
  vi.mocked(api.getDevelopers).mockResolvedValue({
    items: [developer],
    total: 1,
    page: 1,
    size: 50,
  });
  vi.mocked(api.getDeveloper).mockResolvedValue(developer);
  vi.mocked(api.createDeveloper).mockResolvedValue({
    id: developer.id,
    managementNo: developer.managementNo,
    warnings: [],
  });
  vi.mocked(api.putDeveloper).mockResolvedValue({ changed: true });
  vi.mocked(companies.getClientOptions).mockResolvedValue({
    items: [{ id: "C-901", name: "검증협력사", type: "VNDR" }],
  });
  vi.mocked(parts.getSalesPartOptions).mockResolvedValue({
    items: [{ id: "HQ", name: "본사", hqYn: "Y", firstEffectiveMonth: null }],
  });
  vi.mocked(areas.getAreaSidos).mockResolvedValue({ items: [] });
  vi.mocked(api.getDeveloperAssignments).mockResolvedValue({
    items: [],
    total: 0,
    page: 1,
    size: 50,
  });
  vi.mocked(sharedApi.getPeriodContext).mockResolvedValue({
    currentDate: "2026-06-15",
    currentMonth: "2026-06",
    timezone: "Asia/Seoul",
    cashBaseMonth: "2026-01",
    monthClose: { month: "2026-06", status: "작업중" },
  });
  vi.mocked(stateApi.getDeveloperStates).mockResolvedValue({
    items: [],
    hireDate: developer.hireDate,
    revision: "revision",
  });
});
async function ready() {
  await screen.findByRole("option", { name: "팀원" });
  await waitFor(() =>
    expect(screen.getByTestId("developer-form-save")).toBeEnabled(),
  );
}
async function fill() {
  await ready();
  fireEvent.change(screen.getByLabelText(/이름/), {
    target: { value: "등록이름" },
  });
  await userEvent.selectOptions(screen.getByLabelText("소속"), "__own__");
  await userEvent.selectOptions(screen.getByLabelText("주 직무"), "DEV");
  await userEvent.selectOptions(screen.getByLabelText("영업 파트"), "HQ");
}
it("신규 기본 FT/MEMBER/DRCT와 미선택 소속·파트 및 지정 배너", async () => {
  render(<DeveloperForm onClose={vi.fn()} onSaved={vi.fn()} />);
  await ready();
  expect(screen.getByLabelText("직원구분")).toHaveValue("FT");
  expect(screen.getByLabelText("직위")).toHaveValue("MEMBER");
  expect(screen.getByLabelText("직접/간접")).toHaveValue("DRCT");
  expect(screen.getByLabelText("소속")).toHaveValue("");
  expect(screen.getByLabelText("영업 파트")).toHaveValue("");
  expect(screen.getByText(developerBanner)).toBeInTheDocument();
  expect(screen.queryByLabelText("연락처")).not.toBeInTheDocument();
});
it("한국어 첫 오류로 포커스하고 저장0회", async () => {
  render(<DeveloperForm onClose={vi.fn()} onSaved={vi.fn()} />);
  await ready();
  await userEvent.click(screen.getByTestId("developer-form-save"));
  expect(
    screen.getByText("이름을 1~50자로 입력해 주세요."),
  ).toBeInTheDocument();
  expect(screen.getByLabelText(/이름/)).toHaveFocus();
  expect(api.createDeveloper).not.toHaveBeenCalled();
});
it("외주는 자사 선택을 거부한다", async () => {
  render(<DeveloperForm onClose={vi.fn()} onSaved={vi.fn()} />);
  await fill();
  await userEvent.selectOptions(screen.getByLabelText("직원구분"), "OS");
  await userEvent.click(screen.getByTestId("developer-form-save"));
  expect(screen.getByText("소속 협력사를 선택해 주세요.")).toBeInTheDocument();
  expect(api.createDeveloper).not.toHaveBeenCalled();
});
it("수정 관리번호 readonly, 파생정보/미편집 금액 생략, revision 전송", async () => {
  render(
    <DeveloperForm developer={developer} onClose={vi.fn()} onSaved={vi.fn()} />,
  );
  await ready();
  expect(screen.getByLabelText("관리번호")).toHaveAttribute("readonly");
  fireEvent.change(screen.getByLabelText(/이름/), {
    target: { value: "이름변경" },
  });
  await userEvent.click(screen.getByTestId("developer-form-save"));
  await waitFor(() => expect(api.putDeveloper).toHaveBeenCalledOnce());
  const [id, body] = vi.mocked(api.putDeveloper).mock.calls[0];
  expect(id).toBe(developer.id);
  expect(body).toMatchObject({
    expectedRevision: developer.revision,
    name: "이름변경",
    companyId: null,
  });
  for (const field of [
    "managementNo",
    "monthlyRate",
    "monthlyPay",
    "retireDate",
    "status",
    "useYn",
    "phone",
    "effectiveMonth",
  ])
    expect(body).not.toHaveProperty(field);
});
it("수정 금액 하나만 편집하면 그 금액만 전송", async () => {
  render(
    <DeveloperForm developer={developer} onClose={vi.fn()} onSaved={vi.fn()} />,
  );
  await ready();
  fireEvent.change(screen.getByTestId("developer-rate-input"), {
    target: { value: "6000000" },
  });
  await userEvent.click(screen.getByTestId("developer-form-save"));
  await waitFor(() => expect(api.putDeveloper).toHaveBeenCalled());
  expect(vi.mocked(api.putDeveloper).mock.calls[0][1]).toMatchObject({
    monthlyRate: "6000000",
  });
  expect(vi.mocked(api.putDeveloper).mock.calls[0][1]).not.toHaveProperty(
    "monthlyPay",
  );
});
it("예정가격 편집값과 시작월 도움말", async () => {
  render(
    <DeveloperForm
      developer={{
        ...developer,
        monthlyRate: null,
        editPrice: { ...developer.editPrice!, effectiveMonth: "2027-01" },
      }}
      onClose={vi.fn()}
      onSaved={vi.fn()}
    />,
  );
  await ready();
  expect(screen.getByTestId("developer-rate-input")).toHaveValue("5000000");
  expect(screen.getByText(/2027-01/)).toBeInTheDocument();
  expect(screen.getByText(priceHelp)).toBeInTheDocument();
  expect(screen.queryByLabelText("적용월")).not.toBeInTheDocument();
});
it("dirty 취소 계속 작성은 입력 보존과 서버 요청0", async () => {
  const close = vi.fn();
  render(<DeveloperForm onClose={close} onSaved={vi.fn()} />);
  await fill();
  await userEvent.click(screen.getByTestId("developer-form-cancel"));
  await userEvent.click(screen.getByRole("button", { name: "계속 작성" }));
  expect(screen.getByLabelText(/이름/)).toHaveValue("등록이름");
  expect(close).not.toHaveBeenCalled();
  expect(api.createDeveloper).not.toHaveBeenCalled();
});
it("dirty 명시적 나가기만 폐기", async () => {
  const close = vi.fn();
  render(<DeveloperForm onClose={close} onSaved={vi.fn()} />);
  await fill();
  await userEvent.click(screen.getByTestId("developer-form-cancel"));
  await userEvent.click(screen.getByRole("button", { name: "나가기" }));
  expect(close).toHaveBeenCalledOnce();
});
it("마감 충돌은 필드와 문의번호를 표시하고 입력 보존", async () => {
  vi.mocked(api.putDeveloper).mockRejectedValue(
    new ApiError(409, {
      success: false,
      meta: {},
      code: "CLOSED_MONTH",
      message: "현재월이 마감되었습니다.",
      field: "monthlyRate",
      requestId: "dev-request",
    }),
  );
  render(
    <DeveloperForm developer={developer} onClose={vi.fn()} onSaved={vi.fn()} />,
  );
  await ready();
  fireEvent.change(screen.getByTestId("developer-rate-input"), {
    target: { value: "6000000" },
  });
  await userEvent.click(screen.getByTestId("developer-form-save"));
  expect(await screen.findByText(/문의 번호: dev-request/)).toBeInTheDocument();
  expect(screen.getByTestId("developer-rate-input")).toHaveValue("6000000");
  expect(screen.getByTestId("developer-rate-input")).toHaveFocus();
});
it("loading, empty, error 재조회 구분", async () => {
  vi.mocked(api.getDevelopers).mockImplementationOnce(
    () => new Promise(() => {}),
  );
  const { unmount } = render(<DeveloperPoolScreen />);
  expect(screen.getByText("불러오는 중입니다.")).toBeInTheDocument();
  unmount();
  vi.mocked(api.getDevelopers).mockResolvedValue({
    items: [],
    total: 0,
    page: 1,
    size: 50,
  });
  render(<DeveloperPoolScreen />);
  expect(
    await screen.findByText("조회된 자료가 없습니다."),
  ).toBeInTheDocument();
  vi.mocked(api.getDevelopers).mockRejectedValueOnce(new Error("목록 실패"));
  await userEvent.click(screen.getByTestId("developer-search-button"));
  expect(await screen.findByText("목록 실패")).toBeInTheDocument();
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  expect(
    await screen.findByText("조회된 자료가 없습니다."),
  ).toBeInTheDocument();
});
it("조회 Enter만 필터 적용하고 초기화는 기본값", async () => {
  render(<DeveloperPoolScreen />);
  await screen.findByText(developer.name);
  await userEvent.type(
    screen.getByPlaceholderText("이름 또는 관리번호"),
    " 이름 ",
  );
  expect(api.getDevelopers).toHaveBeenCalledOnce();
  fireEvent.submit(screen.getByTestId("developer-filter-form"));
  await waitFor(() =>
    expect(api.getDevelopers).toHaveBeenLastCalledWith(
      expect.objectContaining({ q: "이름", page: 1, includeStopped: false }),
    ),
  );
  await userEvent.click(screen.getByTestId("developer-reset-button"));
  await waitFor(() =>
    expect(api.getDevelopers).toHaveBeenLastCalledWith(
      expect.objectContaining({ q: undefined, page: 1 }),
    ),
  );
  expect(
    screen.queryByRole("option", { name: "재직" }),
  ).not.toBeInTheDocument();
});
it("목록 상태 색·MM경고·관리번호와 소속 표시", async () => {
  render(<DeveloperPoolScreen />);
  await screen.findByText(developer.name);
  expect(await screen.findByText("철수예정", { selector: "span" })).toHaveClass(
    "sicost-tone-warn",
  );
  expect(screen.getByText(/K-0901 · 자사/)).toBeInTheDocument();
  expect(screen.getByText("이번 달 1.10 MM")).toBeInTheDocument();
  expect(screen.getByText("기준값 1.00 초과")).toBeInTheDocument();
});
it("같은 주소 전체화면 등록과 201 관리번호 및 2초 이내 재조회", async () => {
  render(<DeveloperPoolScreen />);
  await screen.findByText(developer.name);
  await userEvent.click(screen.getByTestId("developer-create-button"));
  await fill();
  const start = performance.now();
  await userEvent.click(screen.getByTestId("developer-form-save"));
  expect(
    await screen.findByText(/등록했습니다. 관리번호 K-0901/),
  ).toBeInTheDocument();
  expect(performance.now() - start).toBeLessThan(2000);
  expect(api.createDeveloper).toHaveBeenCalledOnce();
  expect(api.getDeveloper).toHaveBeenCalledWith(developer.id);
});
it("commit후조회실패는 재저장 없이 조회만 재시도", async () => {
  render(<DeveloperPoolScreen />);
  await screen.findByText(developer.name);
  await userEvent.click(screen.getByTestId("developer-create-button"));
  await fill();
  vi.mocked(api.getDeveloper).mockRejectedValueOnce(new Error("조회 실패"));
  await userEvent.click(screen.getByTestId("developer-form-save"));
  await screen.findByText(/저장은 완료됐지만/);
  await userEvent.click(screen.getByTestId("developer-committed-retry"));
  await waitFor(() =>
    expect(screen.getByTestId("developer-drawer")).toBeInTheDocument(),
  );
  expect(api.createDeveloper).toHaveBeenCalledOnce();
});
it("상세 가격이력 토글과 스킬 주소 조회전용", async () => {
  render(
    <DeveloperDrawer id={developer.id} onClose={vi.fn()} onEdit={vi.fn()} />,
  );
  await screen.findByText("검증개발자");
  expect(screen.getByText(priceHelp)).toBeInTheDocument();
  expect(screen.getByText("Java")).toBeInTheDocument();
  expect(screen.queryByLabelText("보유 스킬")).not.toBeInTheDocument();
  await userEvent.click(screen.getByTestId("developer-price-toggle"));
  expect(screen.getByText("2026-06")).toBeInTheDocument();
  await userEvent.click(screen.getByTestId("developer-price-toggle"));
  expect(screen.queryByText("2026-06")).not.toBeInTheDocument();
});
it("상세 투입·상태는 선택한 id API로 읽고 빈 이력을 구분", async () => {
  render(
    <DeveloperDrawer id={developer.id} onClose={vi.fn()} onEdit={vi.fn()} />,
  );
  await screen.findByText("검증개발자");
  await userEvent.click(screen.getByRole("tab", { name: "투입 이력" }));
  await screen.findByText("조회된 자료가 없습니다.");
  expect(api.getDeveloperAssignments).toHaveBeenCalledWith(developer.id, 1);
  await userEvent.click(screen.getByRole("tab", { name: "근무 상태 이력" }));
  await screen.findByText(/2020-01-01 · 입사·재직/);
  expect(stateApi.getDeveloperStates).toHaveBeenCalledWith(developer.id);
  expect(screen.getByTestId("developer-state-create")).toBeEnabled();
});
it("상세 상태 탭은 기존 등록 패널에 revision과 저장 후 갱신을 연결", async () => {
  const changed = vi.fn();
  vi.mocked(stateApi.createDeveloperState).mockResolvedValue({
    sn: 1,
    warnings: [],
  });
  render(
    <DeveloperDrawer
      id={developer.id}
      onClose={vi.fn()}
      onEdit={vi.fn()}
      onChanged={changed}
    />,
  );
  await screen.findByText(developer.name);
  await userEvent.click(screen.getByRole("tab", { name: "근무 상태 이력" }));
  await userEvent.click(await screen.findByTestId("developer-state-create"));
  fireEvent.change(screen.getByTestId("developer-state-start"), {
    target: { value: "2026-07-01" },
  });
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  await waitFor(() => expect(changed).toHaveBeenCalledOnce());
  expect(stateApi.createDeveloperState).toHaveBeenCalledWith(
    developer.id,
    expect.objectContaining({
      expectedRevision: "revision",
      state: "LEAVE",
      startDate: "2026-07-01",
    }),
  );
});
it("모바일 단건 목록20건·더보기·엑셀 비노출", async () => {
  vi.mocked(window.matchMedia).mockReturnValue({
    matches: true,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
  } as unknown as MediaQueryList);
  vi.mocked(api.getDevelopers).mockResolvedValue({
    items: [developer],
    total: 21,
    page: 1,
    size: 20,
  });
  render(<DeveloperPoolScreen />);
  await screen.findByText(/검증개발자 · K-0901/);
  expect(api.getDevelopers).toHaveBeenCalledWith({ page: 1, size: 20 });
  expect(
    screen.queryByTestId("developer-export-button"),
  ).not.toBeInTheDocument();
  expect(screen.queryByTestId("b8-upload-button")).not.toBeInTheDocument();
  await userEvent.click(screen.getByTestId("developer-more-button"));
  await waitFor(() =>
    expect(api.getDevelopers).toHaveBeenLastCalledWith({ page: 2, size: 20 }),
  );
});
it("상세 닫기는 목록 검색조건을 유지", async () => {
  render(<DeveloperPoolScreen />);
  await screen.findByText(developer.name);
  fireEvent.change(screen.getByTestId("developer-search-input"), {
    target: { value: "검증" },
  });
  await userEvent.click(screen.getByTestId("developer-search-button"));
  await waitFor(() =>
    expect(api.getDevelopers).toHaveBeenLastCalledWith(
      expect.objectContaining({ q: "검증" }),
    ),
  );
  await userEvent.click(screen.getByTestId("developer-name-dev-000901"));
  await screen.findByTestId("developer-drawer");
  await userEvent.click(
    within(screen.getByTestId("developer-drawer")).getByRole("button", {
      name: "닫기",
    }),
  );
  expect(screen.getByTestId("developer-search-input")).toHaveValue("검증");
  expect(window.scrollTo).toHaveBeenCalled();
});

it("열 이름을 눌러 정렬한다: 오름차순 → 내림차순, 별도의 정렬 줄은 없다", async () => {
  render(<DeveloperPoolScreen />);
  await screen.findByText(developer.name);
  expect(screen.queryByTestId("developer-sort-select")).not.toBeInTheDocument();
  expect(
    screen.queryByTestId("developer-sort-direction"),
  ).not.toBeInTheDocument();
  expect(screen.getByTestId("developer-sort-name")).toHaveTextContent("이름 ↑");
  await userEvent.click(screen.getByTestId("developer-sort-monthly-rate"));
  await waitFor(() =>
    expect(api.getDevelopers).toHaveBeenLastCalledWith(
      expect.objectContaining({
        sort: "monthlyRate",
        direction: "ASC",
        page: 1,
      }),
    ),
  );
  expect(screen.getByTestId("developer-sort-monthly-rate")).toHaveTextContent(
    "월 단가 ↑",
  );
  await userEvent.click(screen.getByTestId("developer-sort-monthly-rate"));
  await waitFor(() =>
    expect(api.getDevelopers).toHaveBeenLastCalledWith(
      expect.objectContaining({ sort: "monthlyRate", direction: "DESC" }),
    ),
  );
  expect(screen.getByTestId("developer-sort-monthly-rate")).toHaveTextContent(
    "월 단가 ↓",
  );
  expect(
    screen.queryByTestId("developer-sort-current-projects"),
  ).not.toBeInTheDocument();
});
it("이름 열은 기본이 오름차순이라 처음 누르면 내림차순이 된다", async () => {
  render(<DeveloperPoolScreen />);
  await screen.findByText(developer.name);
  await userEvent.click(screen.getByTestId("developer-sort-name"));
  await waitFor(() =>
    expect(api.getDevelopers).toHaveBeenLastCalledWith(
      expect.objectContaining({ sort: "name", direction: "DESC" }),
    ),
  );
  expect(screen.getByTestId("developer-sort-name")).toHaveTextContent("이름 ↓");
});
it("행 체크박스·선택 도구 줄이 있고, 조회된 N건 모두 선택은 page·size 없이 조건만 보낸다", async () => {
  render(<DeveloperPoolScreen />);
  await screen.findByText(developer.name);
  expect(screen.getByTestId("developer-status-toolbar")).toBeInTheDocument();
  await userEvent.click(
    screen.getByRole("checkbox", { name: `${developer.id} 선택` }),
  );
  expect(screen.getByText("선택 1건")).toBeInTheDocument();
  await userEvent.click(screen.getByTestId("developer-select-all"));
  const fetch = vi.mocked(globalThis.fetch);
  await waitFor(() =>
    expect(
      fetch.mock.calls.some(([url]) =>
        String(url).includes("/api/developers/selection"),
      ),
    ).toBe(true),
  );
  const url = String(
    fetch.mock.calls.find(([u]) =>
      String(u).includes("/api/developers/selection"),
    )![0],
  );
  expect(url).not.toMatch(/page=|size=/);
});
it("상세에서 단건 사용 중지·다시 사용을 할 수 있다", async () => {
  render(
    <DeveloperDrawer id={developer.id} onClose={vi.fn()} onEdit={vi.fn()} />,
  );
  await screen.findByText("검증개발자");
  expect(screen.getByRole("button", { name: "사용 중지" })).toBeInTheDocument();
});
