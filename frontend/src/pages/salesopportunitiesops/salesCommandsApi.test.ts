import { beforeEach, expect, it, vi } from "vitest";
import { request } from "../../shared/api";
import * as api from "./salesCommandsApi";
vi.mock("../../shared/api", async (original) => ({
  ...(await original<typeof import("../../shared/api")>()),
  request: vi.fn(),
}));
beforeEach(() => vi.resetAllMocks());
it("every command uses selected opportunity identity and explicit revision without server fields", async () => {
  const o = {
    id: "O-0101",
    revision: "source-revision",
    registeredBy: "never-send",
  } as unknown as api.Opportunity;
  vi.mocked(request).mockResolvedValue({
    changed: false,
    revision: "same",
    warnings: [],
    opportunityId: o.id,
  });
  const win: api.WinInput = {
    contractAmount: "1",
    contractType: "TK",
    startDate: "2026-07-01",
    endDate: "2026-08-01",
    projectStatus: "ING",
  };
  await api.saveSalesOpportunityStage(o, {
    stage: "PROP",
    manualProbability: false,
  });
  await api.createSalesOpportunityApproval(o, "REJ", "원문 사유");
  await api.createSalesOpportunityLoss(o, "PRICE", "메모");
  await api.saveSalesOpportunityStop(o);
  await api.saveSalesOpportunityResume(o);
  await api.createSalesOpportunityWin(o, win);
  const actions = ["stage", "approval", "lose", "stop", "resume", "win"];
  actions.forEach((action, i) => {
    const [path, options] = vi.mocked(request).mock.calls[i];
    expect(path).toBe(`/api/sales-opportunities/${o.id}/${action}`);
    expect(options?.method).toBe("POST");
    const body = JSON.parse(options?.body as string);
    expect(body.expectedRevision).toBe("source-revision");
    expect(body.registeredBy).toBeUndefined();
  });
});
it("preview sends only draft needs and never dispatches commit", async () => {
  const listener = vi.fn();
  window.addEventListener("sicost:sales-committed", listener);
  try {
    vi.mocked(request).mockResolvedValue({});
    const needs = [{ jobCode: "BE" }];
    await api.createSalesOpportunityQuotePreview(needs);
    expect(request).toHaveBeenCalledWith(
      "/api/sales-opportunities/quote-preview",
      { method: "POST", body: JSON.stringify({ needs }) },
    );
    expect(listener).not.toHaveBeenCalled();
  } finally {
    window.removeEventListener("sicost:sales-committed", listener);
  }
});
it("actual commits broadcast shared invalidation; no-op emits nothing", async () => {
  const listener = vi.fn();
  window.addEventListener("sicost:sales-committed", listener);
  try {
    const o = { id: "O-0101", revision: "revision" } as api.Opportunity;
    vi.mocked(request)
      .mockResolvedValueOnce({ changed: true, opportunityId: o.id })
      .mockResolvedValueOnce({ changed: false, opportunityId: o.id });
    await api.saveSalesOpportunityStop(o);
    await api.saveSalesOpportunityStop(o);
    expect(listener).toHaveBeenCalledTimes(1);
  } finally {
    window.removeEventListener("sicost:sales-committed", listener);
  }
});
