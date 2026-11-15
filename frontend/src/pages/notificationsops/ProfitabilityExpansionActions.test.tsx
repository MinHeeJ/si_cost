import { useState, type Key } from "react";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { ProfitabilityExpansionActions } from "./ProfitabilityExpansionActions";

describe("현재 페이지 프로젝트 펼침·접기", () => {
  it("현재 페이지 personId만 펼치고 모두 접어도 조건과 합계를 유지한다", async () => {
    const changed = vi.fn();
    function Host() {
      const [keys, setKeys] = useState<Key[]>([]);
      return (
        <>
          <p>2026년 · 2페이지 · 전체 합계</p>
          <ProfitabilityExpansionActions
            personIds={["page-person-a", "page-person-b"]}
            expandedKeys={keys}
            loading={false}
            onExpandedChange={(value) => {
              changed(value);
              setKeys(value);
            }}
          />
          <output>{keys.join(",")}</output>
        </>
      );
    }
    render(<Host />);
    expect(screen.getByRole("button", { name: "모두 접기" })).toBeDisabled();
    await userEvent.click(
      screen.getByRole("button", { name: "현재 페이지 모두 펼치기" }),
    );
    expect(changed).toHaveBeenLastCalledWith([
      "page-person-a",
      "page-person-b",
    ]);
    expect(screen.getByRole("status")).toHaveTextContent(
      "page-person-a,page-person-b",
    );
    await userEvent.click(screen.getByRole("button", { name: "모두 접기" }));
    expect(changed).toHaveBeenLastCalledWith([]);
    expect(
      screen.getByText("2026년 · 2페이지 · 전체 합계"),
    ).toBeInTheDocument();
  });
  it("빈 목록에서는 펼침을 막는다", () => {
    render(
      <ProfitabilityExpansionActions
        personIds={[]}
        expandedKeys={[]}
        loading={false}
        onExpandedChange={vi.fn()}
      />,
    );
    expect(
      screen.getByRole("button", { name: "현재 페이지 모두 펼치기" }),
    ).toBeDisabled();
  });
  it("조회 중에는 새 펼침 요청을 막되 접기로 현재 펼침을 닫을 수 있다", async () => {
    const changed = vi.fn();
    render(
      <ProfitabilityExpansionActions
        personIds={["owned"]}
        expandedKeys={["owned"]}
        loading
        onExpandedChange={changed}
      />,
    );
    expect(
      screen.getByRole("button", { name: "현재 페이지 모두 펼치기" }),
    ).toBeDisabled();
    await userEvent.click(screen.getByRole("button", { name: "모두 접기" }));
    expect(changed).toHaveBeenCalledWith([]);
  });
});
