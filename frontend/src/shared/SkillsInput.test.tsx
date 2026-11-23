import { useState } from "react";
import {
  fireEvent,
  render,
  screen,
  waitFor,
} from "./testSupport/businessFixtures";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { request } from "./api";
import { SkillsInput } from "./SkillsInput";
vi.mock("./api", async (original) => ({
  ...(await original<typeof import("./api")>()),
  request: vi.fn(),
}));
beforeEach(() => {
  vi.mocked(request).mockResolvedValue({
    items: [
      { code: "JAVA", name: "Java" },
      { code: "REACT", name: "React" },
    ],
  });
});
function Form({ label = "보유 스킬" }: { label?: string }) {
  const [value, setValue] = useState<string[]>([]);
  return (
    <>
      <SkillsInput value={value} onChange={setValue} label={label} />
      <output data-testid="skills-result">{JSON.stringify(value)}</output>
    </>
  );
}
it.each(["보유 스킬", "기술"])(
  "%s: 마지막 조각만 제안으로 교체하고 미등록 스킬도 보존·정규화·중복 제거",
  async (label) => {
    render(<Form label={label} />);
    const input = screen.getByLabelText(label);
    await waitFor(() =>
      expect(request).toHaveBeenCalledWith(
        "/api/codes/SKILL",
        expect.anything(),
      ),
    );
    await userEvent.type(input, "React, Ja");
    await userEvent.click(await screen.findByRole("option", { name: "Java" }));
    expect(input).toHaveValue("React, Java");
    expect(screen.getByTestId("skills-result")).toHaveTextContent(
      '["React","Java"]',
    );
    fireEvent.change(input, {
      target: { value: " React, java , JAVA, 사내도구X" },
    });
    await userEvent.tab();
    expect(input).toHaveValue("React, Java, 사내도구X");
    expect(screen.getByTestId("skills-result")).toHaveTextContent(
      '["React","Java","사내도구X"]',
    );
    expect(
      screen.getByText(
        "‘사내도구X’은(는) 등록된 스킬이 아니라 AI 추천 점수에 쓰이지 않습니다.",
      ),
    ).toBeVisible();
    expect(screen.queryByText(/‘java’은/)).not.toBeInTheDocument();
  },
);
it("키보드로 두 번째 스킬을 선택한다", async () => {
  render(<Form />);
  const input = screen.getByLabelText("보유 스킬");
  await userEvent.type(input, "React, Ja");
  await screen.findByRole("option", { name: "Java" });
  await userEvent.keyboard("{ArrowDown}{Enter}");
  expect(input).toHaveValue("React, Java");
});
