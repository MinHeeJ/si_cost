import { useEffect, useState } from "react";
import { getClient } from "../pages/clients/clientsApi";
import { getSalesPartOptions } from "../pages/salespartsops/partStandardsApi";
import { getProject } from "../pages/projects/projectsApi";
import { getDeveloper } from "../pages/developers/peopleClient";
import { getSalesOpportunity } from "../pages/salesopportunitiesops/salesCommandsApi";

export type BusinessNameKind = "client" | "part" | "project" | "opportunity" | "developer";
/** Business IDs are not common codes. Resolve through the existing domain read APIs. */
export function BusinessName({
  kind,
  id,
}: {
  kind: BusinessNameKind;
  id: string | null | undefined;
}) {
  const [name, setName] = useState<{
    id: string;
    kind: BusinessNameKind;
    value: string;
  }>();
  useEffect(() => {
    if (!id) return;
    let live = true;
    const load = async () => {
      switch (kind) {
        case "client":
          return (await getClient(id)).name;
        case "part":
          return (await getSalesPartOptions()).items.find(
            (part) => part.id === id,
          )?.name;
        case "project":
          return (await getProject(id)).project.name;
        case "opportunity":
          return (await getSalesOpportunity(id)).name;
        case "developer":
          return (await getDeveloper(id)).name;
      }
    };
    void load()
      .then((value) => {
        if (live && value) setName({ id, kind, value });
      })
      .catch(() => console.warn("업무 이름을 조회하지 못했습니다."));
    return () => {
      live = false;
    };
  }, [kind, id]);
  return <>{name && name.id === id && name.kind === kind ? name.value : "–"}</>;
}
