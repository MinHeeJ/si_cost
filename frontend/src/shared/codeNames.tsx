import { useCallback, useEffect, useState } from "react";
import { getSharedCodes } from "../pages/shared/sharedApi";

export type CodeNames = Record<
  string,
  readonly { code: string; name: string }[]
>;

export function codeName(
  codes: CodeNames,
  group: string,
  value: string | null | undefined,
): string {
  if (!value) return "–";
  const name = codes[group]?.find((item) => item.code === value)?.name;
  if (name) return name;
  if (codes[group])
    console.warn(`공통코드 이름을 찾지 못했습니다: ${group}/${value}`);
  return "–";
}

/** Include inactive entries so that historical values remain readable. Refresh after code edits. */
export function useCodeNames(...groups: string[]) {
  const key = [...new Set(groups)].sort().join(",");
  const [codes, setCodes] = useState<CodeNames>({});
  useEffect(() => {
    let live = true;
    const load = () => {
      for (const group of key.split(",").filter(Boolean)) {
        void Promise.resolve()
          .then(() => getSharedCodes(group, true))
          .then((result) => {
            const items = result.items;
            if (live) setCodes((previous) => ({ ...previous, [group]: items }));
          })
          .catch(() => {
            if (live) setCodes((previous) => ({ ...previous, [group]: [] }));
          });
      }
    };
    load();
    window.addEventListener("sicost:codegroups-committed", load);
    return () => {
      live = false;
      window.removeEventListener("sicost:codegroups-committed", load);
    };
  }, [key]);
  return useCallback(
    (group: string, value: string | null | undefined) =>
      codeName(codes, group, value),
    [codes],
  );
}

export function CodeName({
  group,
  value,
}: {
  group: string;
  value: string | null | undefined;
}) {
  const name = useCodeNames(group);
  return <>{name(group, value)}</>;
}
