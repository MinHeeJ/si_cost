import { request } from "../../shared/api";
import { getPeopleCodes } from "../developers/peopleClient";

export type ProjectPlan = {
  jobCode: string;
  jobName: string;
  useYn: "Y" | "N";
  planMm: string | null;
  actualMm: string;
  differenceMm: string;
  source: "MANUAL" | "OPP" | null;
};
export type ProjectPlansData = {
  items: ProjectPlan[];
  planMmTotal: string;
  actualMmTotal: string;
  differenceMmTotal: string;
  revision: string;
};
export type SaveProjectPlansRequest = {
  year: number;
  expectedRevision: string;
  items: { jobCode: string; planMm: string }[];
};
export type SaveProjectPlansResult = {
  value: ProjectPlansData;
  revision: string;
  changed: boolean;
};
const plansPath = (id: string) =>
  `/api/projects/${encodeURIComponent(id)}/plans`;
export const getProjectPlans = (id: string) =>
  request<ProjectPlansData>(plansPath(id));
export const saveProjectPlans = (id: string, body: SaveProjectPlansRequest) =>
  request<SaveProjectPlansResult>(plansPath(id), {
    method: "PUT",
    body: JSON.stringify(body),
  });
export async function getPlanJobOptions() {
  const [active, all] = await Promise.all([
    getPeopleCodes("JOB"),
    request<{ items: { code: string; name: string }[] }>(
      "/api/codes/JOB?includeInactive=true",
    ),
  ]);
  return {
    items: active.items.filter((job) => job.code.length <= 10),
    order: all.items.map((job) => job.code),
  };
}

export function planMmInput(value: string): string | null {
  const trimmed = value.trim();
  if (!/^(?:|[0-9]{1,4}(?:\.[0-9]{1,2})?)$/.test(trimmed)) return null;
  return trimmed || "0";
}
export function planMmUnits(value: string): bigint {
  const negative = value.startsWith("-");
  const [whole, fraction = ""] = (negative ? value.slice(1) : value).split(".");
  const units = BigInt(whole || "0") * 100n + BigInt(fraction.padEnd(2, "0"));
  return negative ? -units : units;
}
export function planMmDisplay(units: bigint): string {
  const magnitude = units < 0n ? -units : units;
  return `${units < 0n ? "−" : ""}${magnitude / 100n}.${(magnitude % 100n).toString().padStart(2, "0")}`;
}
