import { request } from "./api";

export type AreaLevel = "SIDO" | "SGG" | "EMD";
export type PeopleArea = {
  code: string;
  level: AreaLevel;
  parentCode: string | null;
  name: string;
  useYn: "Y" | "N";
};
export type PeopleAreas = { items: PeopleArea[] };
export type PeopleAreaPath = {
  items: PeopleArea[];
  homeAddress: string | null;
};

export function getAreaSidos(signal?: AbortSignal): Promise<PeopleAreas> {
  return request("/api/areas/sidos", { signal });
}
export function getAreaChildren(
  parentCode: string,
  level: "SGG" | "EMD",
  signal?: AbortSignal,
): Promise<PeopleAreas> {
  const query = new URLSearchParams({ parentCode, level });
  return request(`/api/areas/children?${query}`, { signal });
}
export function getAreaPath(
  code: string,
  signal?: AbortSignal,
): Promise<PeopleAreaPath> {
  return request(`/api/areas/${encodeURIComponent(code)}/path`, { signal });
}
