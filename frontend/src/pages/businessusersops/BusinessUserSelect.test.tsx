import { useState } from "react";
import { act, fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, useLocation } from "react-router-dom";
import { beforeEach, expect, it, vi } from "vitest";
import { BusinessUserSelect } from "./BusinessUserSelect";
import * as api from "./businessUserOptionsApi";
vi.mock("./businessUserOptionsApi", async (original) => ({
  ...(await original<typeof api>()),
  getBusinessUserOptions: vi.fn(),
}));
const sameNames = [
  { userId: "personone", name: "동명", label: "동명(personone)" },
  { userId: "persontwo", name: "동명", label: "동명(persontwo)" },
];
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api.getBusinessUserOptions).mockResolvedValue({
    items: [],
    roleOptions: [],
  });
});
function Location() {
  const location = useLocation();
  return (
    <output aria-label="현재 위치">
      {JSON.stringify({ path: location.pathname, state: location.state })}
    </output>
  );
}
function Host({ purpose = "PM" }: { purpose?: api.BusinessUserPurpose }) {
  const [value, setValue] = useState("");
  return (
    <>
      <BusinessUserSelect purpose={purpose} value={value} onChange={setValue} />
      <output aria-label="저장할 ID">{value}</output>
      <Location />
    </>
  );
}
function mount(purpose: api.BusinessUserPurpose = "PM") {
  return render(
    <MemoryRouter initialEntries={["/UI-15"]}>
      <Host purpose={purpose} />
    </MemoryRouter>,
  );
}
it("does not query until opened and distinguishes same names while storing only user ID", async () => {
  vi.mocked(api.getBusinessUserOptions).mockResolvedValue({
    items: sameNames,
    roleOptions: [],
  });
  mount();
  expect(api.getBusinessUserOptions).not.toHaveBeenCalled();
  await userEvent.click(screen.getByRole("button", { name: /PM:/ }));
  const select = await screen.findByLabelText("PM");
  expect(screen.getByRole("option", { name: "동명(personone)" })).toHaveValue(
    "personone",
  );
  expect(screen.getByRole("option", { name: "동명(persontwo)" })).toHaveValue(
    "persontwo",
  );
  await userEvent.selectOptions(select, "persontwo");
  expect(screen.getByLabelText("저장할 ID")).toHaveTextContent("persontwo");
  expect(
    screen.getByRole("button", { name: "PM: 동명(persontwo)" }),
  ).toBeInTheDocument();
  expect(api.getBusinessUserOptions).toHaveBeenCalledWith(
    "PM",
    expect.any(AbortSignal),
  );
});
it.each(["PM", "PART_LEADER"] as const)(
  "%s empty options lead to registration inside UI-23 with return context",
  async (purpose) => {
    mount(purpose);
    await userEvent.click(
      screen.getByRole("button", { name: /선택해 주세요/ }),
    );
    const registration = await screen.findByRole("button", {
      name: "사용자 등록",
    });
    expect(screen.queryByRole("combobox")).not.toBeInTheDocument();
    await userEvent.click(registration);
    const target = JSON.parse(screen.getByLabelText("현재 위치").textContent!);
    expect(target).toEqual({
      path: "/UI-23",
      state: {
        businessUserRegistration: true,
        qualificationPurpose: purpose,
        returnTo: "/UI-15",
      },
    });
  },
);
it("empty registration can use host navigation and dirty-state guard instead of replacing its form", async () => {
  const register = vi.fn();
  render(
    <MemoryRouter>
      <BusinessUserSelect
        purpose="PM"
        value=""
        onChange={vi.fn()}
        onRegister={register}
      />
    </MemoryRouter>,
  );
  await userEvent.click(screen.getByRole("button", { name: /PM:/ }));
  await userEvent.click(
    await screen.findByRole("button", { name: "사용자 등록" }),
  );
  expect(register).toHaveBeenCalledTimes(1);
});
it("loading is separate from empty state", async () => {
  let resolve!: (data: api.BusinessUserOptions) => void;
  vi.mocked(api.getBusinessUserOptions).mockReturnValue(
    new Promise((yes) => {
      resolve = yes;
    }),
  );
  mount();
  await userEvent.click(screen.getByRole("button", { name: /PM:/ }));
  expect(screen.getByText("불러오는 중입니다.")).toHaveAttribute(
    "role",
    "status",
  );
  expect(
    screen.queryByRole("button", { name: "사용자 등록" }),
  ).not.toBeInTheDocument();
  await act(async () => resolve({ items: [], roleOptions: [] }));
  expect(
    (await screen.findByText("선택할 수 있는 PM 사용자가 없습니다."))
      .parentElement,
  ).toHaveClass("sicost-empty", "sicost-empty-state");
});
it("failed requests are not empty and retry only on user action", async () => {
  vi.mocked(api.getBusinessUserOptions).mockRejectedValueOnce(
    new Error("옵션 조회 실패"),
  );
  mount();
  await userEvent.click(screen.getByRole("button", { name: /PM:/ }));
  expect(await screen.findByRole("alert")).toHaveTextContent("옵션 조회 실패");
  expect(
    screen.queryByRole("button", { name: "사용자 등록" }),
  ).not.toBeInTheDocument();
  expect(api.getBusinessUserOptions).toHaveBeenCalledTimes(1);
  await userEvent.click(screen.getByRole("button", { name: "다시 조회" }));
  await screen.findByRole("button", { name: "사용자 등록" });
  expect(api.getBusinessUserOptions).toHaveBeenCalledTimes(2);
});
it("every reopen uses newest server values without retaining removed candidates", async () => {
  vi.mocked(api.getBusinessUserOptions).mockResolvedValueOnce({
    items: sameNames,
    roleOptions: [],
  });
  mount();
  await userEvent.click(screen.getByRole("button", { name: /PM:/ }));
  await screen.findByRole("option", { name: "동명(personone)" });
  await userEvent.click(screen.getByRole("button", { name: /PM:/ }));
  await userEvent.click(screen.getByRole("button", { name: /PM:/ }));
  await screen.findByRole("button", { name: "사용자 등록" });
  expect(
    screen.queryByRole("option", { name: "동명(personone)" }),
  ).not.toBeInTheDocument();
  expect(api.getBusinessUserOptions).toHaveBeenCalledTimes(2);
});
it("committed user changes refresh open selectors without a save or a login", async () => {
  vi.mocked(api.getBusinessUserOptions).mockResolvedValueOnce({
    items: sameNames,
    roleOptions: [],
  });
  mount();
  await userEvent.click(screen.getByRole("button", { name: /PM:/ }));
  await screen.findByRole("option", { name: "동명(personone)" });
  act(() => api.notifyBusinessUsersCommitted());
  await screen.findByRole("button", { name: "사용자 등록" });
  expect(api.getBusinessUserOptions).toHaveBeenCalledTimes(2);
});
it("disabled fields cannot open or select and keep stored identity visible", () => {
  render(
    <MemoryRouter>
      <BusinessUserSelect
        purpose="PART_LEADER"
        value="storedperson"
        selectedLabel="기존 담당자"
        disabled
        onChange={vi.fn()}
      />
    </MemoryRouter>,
  );
  expect(
    screen.getByRole("button", { name: "파트장: 기존 담당자" }),
  ).toBeDisabled();
  expect(api.getBusinessUserOptions).not.toHaveBeenCalled();
});
it("changing purpose cancels previous request so late response cannot replace the new options", async () => {
  let resolve!: (data: api.BusinessUserOptions) => void;
  vi.mocked(api.getBusinessUserOptions).mockReturnValueOnce(
    new Promise((yes) => {
      resolve = yes;
    }),
  );
  const view = render(
    <MemoryRouter>
      <BusinessUserSelect purpose="PM" value="" onChange={vi.fn()} />
    </MemoryRouter>,
  );
  await userEvent.click(screen.getByRole("button", { name: /PM:/ }));
  const signal = vi.mocked(api.getBusinessUserOptions).mock.calls[0][1]!;
  view.rerender(
    <MemoryRouter>
      <BusinessUserSelect purpose="PART_LEADER" value="" onChange={vi.fn()} />
    </MemoryRouter>,
  );
  await screen.findByText("선택할 수 있는 파트장 사용자가 없습니다.");
  expect(signal.aborted).toBe(true);
  await act(async () => resolve({ items: sameNames, roleOptions: [] }));
  expect(screen.queryByRole("combobox")).not.toBeInTheDocument();
});
it("an obsolete stored user remains a disabled display value, never a newly selectable candidate", async () => {
  const change = vi.fn();
  vi.mocked(api.getBusinessUserOptions).mockResolvedValue({
    items: sameNames,
    roleOptions: [],
  });
  render(
    <MemoryRouter>
      <BusinessUserSelect
        purpose="PM"
        value="pastperson"
        selectedLabel="이전 담당자"
        onChange={change}
      />
    </MemoryRouter>,
  );
  await userEvent.click(screen.getByRole("button", { name: /PM:/ }));
  expect(
    await screen.findByRole("option", { name: "이전 담당자" }),
  ).toBeDisabled();
  fireEvent.change(screen.getByLabelText("PM"), {
    target: { value: "pastperson" },
  });
  expect(change).not.toHaveBeenCalled();
});
