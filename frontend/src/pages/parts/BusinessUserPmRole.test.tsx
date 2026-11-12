import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { BusinessUserForm } from "../businessusers/BusinessUserForm";
import { ApiError } from "../../shared/api";
import * as api from "../businessusers/businessUsersApi";
vi.mock("../businessusers/businessUsersApi");
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api.getBusinessRoleOptions).mockResolvedValue({
    items: [],
    roleOptions: [
      { roleId: "PM", name: "프로젝트 담당" },
      { roleId: "SALES_LDR", name: "영업 파트장" },
    ],
  });
});
it("UI-23 연결잔존409는 해제안내와 다른역할·입력을 유지한다", async () => {
  vi.mocked(api.putBusinessUser).mockRejectedValue(
    new ApiError(409, {
      success: false,
      meta: {},
      code: "USER_IS_PROJECT_PM",
      field: "businessRoleIds",
      message: "담당 프로젝트를 먼저 해제한 뒤 PM 역할을 제거해 주세요.",
      requestId: "pm-connected",
    }),
  );
  const onSaved = vi.fn();
  render(
    <BusinessUserForm
      user={{
        userId: "pm12",
        name: "PM 사용자",
        email: "pm@example.org",
        roles: ["PM", "파트장"],
        businessRoleIds: ["PM", "SALES_LDR"],
        status: "USE",
        lastLoginAt: null,
        revision: "before",
      }}
      onClose={vi.fn()}
      onSaved={onSaved}
    />,
  );
  await screen.findByRole("checkbox", { name: "프로젝트 담당" });
  await userEvent.click(
    screen.getByRole("checkbox", { name: "프로젝트 담당" }),
  );
  await userEvent.click(screen.getByRole("button", { name: "저장" }));
  await waitFor(() =>
    expect(screen.getAllByText(/먼저 해제/).length).toBeGreaterThan(0),
  );
  expect(screen.getByRole("checkbox", { name: "영업 파트장" })).toBeChecked();
  expect(screen.getByRole("textbox", { name: "이름" })).toHaveValue(
    "PM 사용자",
  );
  expect(api.putBusinessUser).toHaveBeenCalledWith("pm12", {
    name: "PM 사용자",
    email: "pm@example.org",
    businessRoleIds: ["SALES_LDR"],
    expectedRevision: "before",
  });
  expect(onSaved).not.toHaveBeenCalled();
});
