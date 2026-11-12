import { request } from "../../shared/api";

export type ProjectPart = {
  effectiveMonth: string;
  partId: string | null;
  partName: string | null;
  reason: string | null;
  actor: string;
  scheduled: boolean;
};
export type ProjectPartsData = {
  items: ProjectPart[];
  replacementTargets: ProjectPart[];
  retainedBasePart: ProjectPart | null;
  minimumMonth: string;
  maximumMonth: string;
  revision: string;
};
export type SaveProjectPartRequest = {
  year: number;
  expectedRevision: string;
  partId: string;
  effectiveMonth: string;
  reason?: string | null;
};
export type SaveProjectPartResult = {
  value: ProjectPartsData;
  revision: string;
  changed: boolean;
};
export function getProjectParts(id: string, signal?: AbortSignal) {
  return request<ProjectPartsData>(
    `/api/projects/${encodeURIComponent(id)}/parts`,
    { signal },
  );
}
export function saveProjectPart(id: string, body: SaveProjectPartRequest) {
  return request<SaveProjectPartResult>(
    `/api/projects/${encodeURIComponent(id)}/parts`,
    { method: "PUT", body: JSON.stringify(body) },
  );
}
