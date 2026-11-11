import { beforeEach, expect, it, vi } from "vitest";
import { request } from "../../shared/api";
import * as api from "./peopleClient";
vi.mock("../../shared/api", async (original) => ({
  ...(await original<typeof import("../../shared/api")>()),
  request: vi.fn(),
}));
beforeEach(() => {
  vi.clearAllMocks();
});
it("uses relative approved candidate paths and selected identifiers", async () => {
  const controller = new AbortController();
  await api.getCandidates(
    {
      page: 1,
      size: 20,
      availability: "NEXT_MONTH",
      gradeCode: "MD",
      sort: "availableDate",
      direction: "ASC",
    },
    controller.signal,
  );
  expect(request).toHaveBeenLastCalledWith(
    "/api/candidates?page=1&size=20&availability=NEXT_MONTH&gradeCode=MD&sort=availableDate&direction=ASC",
    { signal: controller.signal },
  );
  await api.getCandidate("CAND-900001", controller.signal);
  expect(request).toHaveBeenLastCalledWith("/api/candidates/CAND-900001", {
    signal: controller.signal,
  });
});
it("create and update keep boolean consent money strings skill arrays and opaque revision", async () => {
  const fields: api.CandidateFields = {
    name: "후보",
    employeeType: "FL",
    companyId: null,
    phone: null,
    email: null,
    consent: false,
    jobCode: "DEV",
    subJobCode: null,
    gradeCode: "MD",
    homeAreaCode: null,
    desiredRate: "1",
    availableDate: "2026-07-16",
    sourceCode: "SITE",
    memo: null,
    skills: ["Java"],
  };
  await api.createCandidate(fields);
  expect(request).toHaveBeenLastCalledWith("/api/candidates", {
    method: "POST",
    body: JSON.stringify(fields),
  });
  await api.putCandidate("CAND-900001", {
    ...fields,
    expectedRevision: "snapshot",
  });
  expect(request).toHaveBeenLastCalledWith("/api/candidates/CAND-900001", {
    method: "PUT",
    body: JSON.stringify({ ...fields, expectedRevision: "snapshot" }),
  });
});
it("reads only existing code and server-date endpoints for defaults", async () => {
  await api.getCandidateCodes("GRADE");
  expect(request).toHaveBeenLastCalledWith("/api/codes/GRADE");
  await api.getCandidateDate();
  expect(request).toHaveBeenLastCalledWith("/api/period-context");
});
