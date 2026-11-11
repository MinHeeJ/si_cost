import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AddressSelector } from "../../shared/AddressSelector";
import { ApiError } from "../../shared/api";
import * as areas from "../../shared/areaClient";
import type { PeopleArea } from "../../shared/areaClient";

vi.mock("../../shared/areaClient", () => ({
  getAreaSidos: vi.fn(),
  getAreaChildren: vi.fn(),
  getAreaPath: vi.fn(),
}));
const sido: PeopleArea = {
  code: "0100000000",
  level: "SIDO",
  parentCode: null,
  name: "경기",
  useYn: "Y",
};
const sgg: PeopleArea = {
  code: "0101000000",
  level: "SGG",
  parentCode: sido.code,
  name: "성남시 분당구",
  useYn: "Y",
};
const emd: PeopleArea = {
  code: "0101010000",
  level: "EMD",
  parentCode: sgg.code,
  name: "정자동",
  useYn: "Y",
};
const sejong: PeopleArea = {
  code: "3600000000",
  level: "SIDO",
  parentCode: null,
  name: "세종",
  useYn: "Y",
};
const town: PeopleArea = {
  code: "3611025000",
  level: "EMD",
  parentCode: sejong.code,
  name: "조치원읍",
  useYn: "Y",
};
const roots = [sido, sejong];
function Form({
  initial = null,
  maxLevel = "EMD",
}: {
  initial?: string | null;
  maxLevel?: "EMD" | "SGG";
}) {
  const [value, setValue] = useState(initial);
  return (
    <>
      <AddressSelector value={value} onChange={setValue} maxLevel={maxLevel} />
      <output aria-label="저장할 주소 코드">{value ?? "NULL"}</output>
    </>
  );
}
async function ready() {
  await waitFor(() =>
    expect(screen.getByLabelText("거주지(시·도)")).not.toBeDisabled(),
  );
}
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(areas.getAreaSidos).mockResolvedValue({ items: roots });
  vi.mocked(areas.getAreaChildren).mockImplementation(
    async (parent, level) => ({
      items:
        parent === sido.code && level === "SGG"
          ? [sgg]
          : parent === sgg.code && level === "EMD"
            ? [emd]
            : parent === sejong.code && level === "EMD"
              ? [town]
              : [],
    }),
  );
  vi.mocked(areas.getAreaPath).mockImplementation(async (code) => ({
    items:
      code === emd.code
        ? [sido, sgg, emd]
        : code === sgg.code
          ? [sido, sgg]
          : code === town.code
            ? [sejong, town]
            : roots.filter((a) => a.code === code),
    homeAddress: null,
  }));
});
describe("공유 거주지 선택", () => {
  it("개발자는 시도·시군구·법정동을 선택하고 코드 하나만 전달한다", async () => {
    render(<Form />);
    await ready();
    await userEvent.selectOptions(
      screen.getByLabelText("거주지(시·도)"),
      sido.code,
    );
    await ready();
    await userEvent.selectOptions(
      screen.getByLabelText("거주지(시·군·구)"),
      sgg.code,
    );
    await ready();
    await userEvent.selectOptions(
      screen.getByLabelText("거주지(동)"),
      emd.code,
    );
    await ready();
    expect(screen.getByLabelText("저장할 주소 코드")).toHaveTextContent(
      emd.code,
    );
    expect(screen.getByLabelText("거주지(동)")).toHaveValue(emd.code);
    expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
  });
  it("상위 변경 직후 하위값을 비우고 세종은 시군구를 건너뛴다", async () => {
    render(<Form initial={emd.code} />);
    await ready();
    await userEvent.selectOptions(
      screen.getByLabelText("거주지(시·도)"),
      sejong.code,
    );
    await ready();
    expect(screen.getByLabelText("저장할 주소 코드")).toHaveTextContent(
      sejong.code,
    );
    expect(screen.queryByLabelText("거주지(시·군·구)")).not.toBeInTheDocument();
    expect(screen.getByLabelText("거주지(동)")).toHaveValue("");
    await userEvent.selectOptions(
      screen.getByLabelText("거주지(동)"),
      town.code,
    );
    await ready();
    expect(screen.getByLabelText("거주지(동)")).toHaveValue(town.code);
  });
  it("시군구 해제 시 동도 비우되 시도는 유지한다", async () => {
    render(<Form initial={emd.code} />);
    await ready();
    await userEvent.selectOptions(
      screen.getByLabelText("거주지(시·군·구)"),
      "",
    );
    await ready();
    expect(screen.getByLabelText("거주지(동)")).toHaveValue("");
    expect(screen.getByLabelText("저장할 주소 코드")).toHaveTextContent(
      sido.code,
    );
  });
  it("후보는 시군구까지만 보여주며 세종은 시도까지 저장한다", async () => {
    render(<Form initial={sgg.code} maxLevel="SGG" />);
    await ready();
    expect(screen.queryByLabelText("거주지(동)")).not.toBeInTheDocument();
    await userEvent.selectOptions(
      screen.getByLabelText("거주지(시·도)"),
      sejong.code,
    );
    await ready();
    expect(screen.getByLabelText("저장할 주소 코드")).toHaveTextContent(
      sejong.code,
    );
    expect(screen.queryByLabelText("거주지(시·군·구)")).not.toBeInTheDocument();
    expect(areas.getAreaChildren).not.toHaveBeenCalledWith(
      expect.anything(),
      "EMD",
      expect.anything(),
    );
  });
  it("전체 비우기는 null을 전달하고 모든 단계를 초기화한다", async () => {
    render(<Form initial={emd.code} />);
    await ready();
    await userEvent.click(screen.getByRole("button", { name: "비우기" }));
    await ready();
    expect(screen.getByLabelText("저장할 주소 코드")).toHaveTextContent("NULL");
    expect(screen.getByLabelText("거주지(시·도)")).toHaveValue("");
    expect(screen.getByLabelText("거주지(동)")).toBeDisabled();
  });
  it("코드 경로로 기존 주소를 복원하며 onChange를 호출하지 않는다", async () => {
    const change = vi.fn();
    render(
      <AddressSelector value={emd.code} onChange={change} maxLevel="EMD" />,
    );
    await ready();
    expect(areas.getAreaPath).toHaveBeenCalledWith(
      emd.code,
      expect.any(AbortSignal),
    );
    expect(screen.getByLabelText("거주지(시·도)")).toHaveValue(sido.code);
    expect(screen.getByLabelText("거주지(시·군·구)")).toHaveValue(sgg.code);
    expect(screen.getByLabelText("거주지(동)")).toHaveValue(emd.code);
    expect(change).not.toHaveBeenCalled();
  });
  it("중지된 기존 경로는 표시하되 새 선택으로 제공하지 않는다", async () => {
    const stopped = { ...sgg, useYn: "N" as const };
    vi.mocked(areas.getAreaChildren).mockResolvedValue({ items: [] });
    vi.mocked(areas.getAreaPath).mockResolvedValue({
      items: [sido, stopped, emd],
      homeAddress: "경기 성남시 분당구 정자동",
    });
    render(<Form initial={emd.code} />);
    await ready();
    const option = screen.getByRole("option", {
      name: "성남시 분당구 (사용 중지)",
    });
    expect(option).toBeDisabled();
    expect(screen.getByText("사용 중지")).toHaveClass("sicost-tone-lock");
    expect(screen.getByLabelText("거주지(동)")).toHaveValue(emd.code);
    expect(screen.getByRole("option", { name: "정자동" })).toBeDisabled();
    expect(screen.getByLabelText("저장할 주소 코드")).toHaveTextContent(
      emd.code,
    );
  });
  it("불러오는 중 입력을 잠그고 빈 목록은 명시한다", async () => {
    let finish!: (value: areas.PeopleAreas) => void;
    vi.mocked(areas.getAreaSidos).mockReturnValue(
      new Promise((resolve) => {
        finish = resolve;
      }),
    );
    render(<Form />);
    expect(screen.getByText("행정구역을 불러오는 중…")).toHaveAttribute(
      "role",
      "status",
    );
    expect(screen.getByLabelText("거주지(시·도)")).toBeDisabled();
    await act(async () => finish({ items: [] }));
    expect(screen.getByText("선택할 행정구역이 없습니다.")).toBeInTheDocument();
  });
  it("실패 시 코드·입력을 보존하고 요청번호와 조회 재시도만 제공한다", async () => {
    const change = vi.fn();
    vi.mocked(areas.getAreaSidos).mockRejectedValueOnce(
      new ApiError(500, {
        success: false,
        meta: {},
        code: "INTERNAL_ERROR",
        message: "행정구역 조회 실패",
        requestId: "address-read",
      }),
    );
    render(
      <AddressSelector value={emd.code} onChange={change} maxLevel="EMD" />,
    );
    expect(await screen.findByRole("alert")).toHaveTextContent("address-read");
    expect(change).not.toHaveBeenCalled();
    await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
    await ready();
    expect(screen.getByLabelText("거주지(동)")).toHaveValue(emd.code);
    expect(change).not.toHaveBeenCalled();
  });
  it("오래된 응답이 새로운 사람의 주소를 덮지 않는다", async () => {
    let finish!: (value: areas.PeopleAreaPath) => void;
    vi.mocked(areas.getAreaPath).mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          finish = resolve;
        }),
    );
    const { rerender } = render(
      <AddressSelector value={emd.code} onChange={vi.fn()} maxLevel="EMD" />,
    );
    rerender(
      <AddressSelector value={town.code} onChange={vi.fn()} maxLevel="EMD" />,
    );
    await ready();
    await act(async () =>
      finish({ items: [sido, sgg, emd], homeAddress: "이전 주소" }),
    );
    expect(screen.getByLabelText("거주지(시·도)")).toHaveValue(sejong.code);
    expect(screen.getByLabelText("거주지(동)")).toHaveValue(town.code);
  });
  it("읽기 전용에는 변경·비우기 작업이 없다", async () => {
    render(
      <AddressSelector
        value={sgg.code}
        onChange={vi.fn()}
        maxLevel="SGG"
        readOnly
      />,
    );
    await waitFor(() =>
      expect(screen.getByLabelText("거주지(시·군·구)")).toHaveValue(sgg.code),
    );
    expect(screen.getByLabelText("거주지(시·도)")).toBeDisabled();
    expect(
      screen.queryByRole("button", { name: "비우기" }),
    ).not.toBeInTheDocument();
  });
  it("필드 한국어 오류를 보여주고 첫 주소 필드로 포커스한다", async () => {
    const { rerender } = render(
      <AddressSelector value={null} onChange={vi.fn()} maxLevel="EMD" />,
    );
    await ready();
    rerender(
      <AddressSelector
        value={null}
        onChange={vi.fn()}
        maxLevel="EMD"
        error="거주지를 확인해 주세요."
      />,
    );
    expect(screen.getByRole("alert")).toHaveTextContent(
      "거주지를 확인해 주세요.",
    );
    expect(screen.getByLabelText("거주지(시·도)")).toHaveFocus();
  });
  it("비활성 컨트롤은 입력을 보내지 않는다", async () => {
    const change = vi.fn();
    render(
      <AddressSelector
        value={sgg.code}
        onChange={change}
        maxLevel="SGG"
        disabled
      />,
    );
    await waitFor(() =>
      expect(screen.getByLabelText("거주지(시·군·구)")).toHaveValue(sgg.code),
    );
    expect(screen.getByLabelText("거주지(시·군·구)")).toBeDisabled();
    fireEvent.click(screen.getByRole("button", { name: "비우기" }));
    expect(change).not.toHaveBeenCalled();
  });
});
