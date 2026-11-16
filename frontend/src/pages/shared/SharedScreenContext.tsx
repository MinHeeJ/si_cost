import { createContext, useContext, useEffect, type ReactNode } from "react";
import type { AnalysisContext } from "./analysisClient";
import type { MenuNode } from "./sharedApi";
export type SharedScreenState = {
  menus: MenuNode[];
  favorites: string[];
  star(screenId: string): void;
  pending: boolean;
  codes?: ReactNode;
  mappings?: ReactNode;
  standards?: ReactNode;
  pageSize?: 20 | 50 | 100;
  dense?: boolean;
  savePageSize?(size: 20 | 50 | 100): Promise<void>;
  saveDense?(dense: boolean): Promise<void>;
};
export const SharedScreenContext = createContext<SharedScreenState>({
  menus: [],
  favorites: [],
  star: () => {},
  pending: false,
});
export function flattenMenus(nodes: MenuNode[]): MenuNode[] {
  return nodes.flatMap((node) => [node, ...flattenMenus(node.children)]);
}
/** Local identifiers only; the question provider freezes this context at send time. */
export function useAnalysisScreenContext(
  screenId: string,
  context: AnalysisContext,
  active = true,
) {
  useEffect(() => {
    if (!active) return;
    // Kept-alive hidden tabs can finish a read after navigation. They own their
    // original screen context, never the currently visible screen's target.
    window.dispatchEvent(
      new CustomEvent("sicost:analysis-context", {
        detail: { ...context, screenId },
      }),
    );
    return () => {
      window.dispatchEvent(
        new CustomEvent("sicost:analysis-context", { detail: { screenId } }),
      );
    };
  }, [
    screenId,
    context.projectId,
    context.developerId,
    context.baseMonth,
    active,
  ]);
}
export function useSharedScreen(screenId: string) {
  const state = useContext(SharedScreenContext);
  return {
    ...state,
    menu: flattenMenus(state.menus).find((node) => node.screenId === screenId),
  };
}
