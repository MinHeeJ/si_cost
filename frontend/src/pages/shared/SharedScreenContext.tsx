import { createContext, useContext, type ReactNode } from "react";
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
export function useSharedScreen(screenId: string) {
  const state = useContext(SharedScreenContext);
  return {
    ...state,
    menu: flattenMenus(state.menus).find((node) => node.screenId === screenId),
  };
}
