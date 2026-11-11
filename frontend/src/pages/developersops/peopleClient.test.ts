import { afterEach, expect, it, vi } from "vitest";
import {
  getDeveloperAssignments,
  getDeveloperSelection,
  saveDeveloperBulkStatus,
} from "./peopleClient";
afterEach(() => vi.unstubAllGlobals());
function server(data: unknown) {
  const fetch = vi.fn().mockResolvedValue(
    new Response(JSON.stringify({ success: true, meta: {}, data }), {
      status: 200,
    }),
  );
  vi.stubGlobal("fetch", fetch);
  return fetch;
}
it("selection has approved filters but no pagination", async () => {
  const fetch = server({ items: [] });
  await getDeveloperSelection({
    includeStopped: false,
    q: "이름",
    sort: "name",
    direction: "ASC",
  });
  const url = fetch.mock.calls[0][0] as string;
  expect(url).toContain("/api/developers/selection?");
  expect(url).toContain("includeStopped=false");
  expect(url).not.toMatch(/page=|size=/);
});
it("assignment path comes from selected developer and includes requested page", async () => {
  const fetch = server({ items: [], total: 0, page: 2, size: 20 });
  await getDeveloperAssignments("DEV-000901", 2, 20);
  expect(fetch.mock.calls[0][0]).toBe(
    "/api/developers/DEV-000901/assignments?page=2&size=20",
  );
});
it("bulk sends contract revision, not an invented expectedRevision item", async () => {
  const fetch = server({
    applied: [],
    noChange: [],
    rejected: [],
    appliedCount: 0,
    noChangeCount: 0,
    rejectedCount: 0,
  });
  await saveDeveloperBulkStatus("N", [
    { id: "DEV-000901", revision: "frozen" },
  ]);
  expect(JSON.parse(fetch.mock.calls[0][1].body)).toEqual({
    targetUseYn: "N",
    items: [{ id: "DEV-000901", revision: "frozen" }],
  });
});
