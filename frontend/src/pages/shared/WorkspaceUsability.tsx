import { useContext, useEffect, useState } from "react";
import { PermissionContext } from "../../shared/permission/PermissionBoundary";
import { Dialog } from "../../shared/Dialog";
import { flattenMenus } from "./SharedScreenContext";
import type { MenuNode } from "./sharedApi";

const visible = (element: Element) => {
  for (let node: Element | null = element; node; node = node.parentElement) {
    const style = getComputedStyle(node);
    if (
      node.hasAttribute("hidden") ||
      style.display === "none" ||
      style.visibility === "hidden"
    )
      return false;
  }
  return true;
};
export function WorkspaceUsability({
  menus,
  active,
  onOpen,
  onClose,
  mode,
}: {
  menus: MenuNode[];
  active: string;
  onOpen(id: string): void;
  onClose(): void;
  mode: "search" | "help" | null;
}) {
  const [query, setQuery] = useState("");
  const permissions = useContext(PermissionContext);
  const [index, setIndex] = useState(0);
  const matches = flattenMenus(menus).filter(
    (m) =>
      m.screenId &&
      !(window.innerWidth < 768 && m.pcOnly) &&
      `${m.name} ${m.screenId}`.toLowerCase().includes(query.toLowerCase()),
  );
  useEffect(() => {
    const key = (event: KeyboardEvent) => {
      if (event.isComposing || event.repeat) return;
      const target = event.target as HTMLElement;
      if (
        event.key === "Enter" &&
        target.closest(".sicost-filterbar") &&
        (target.tagName === "TEXTAREA" || event.isComposing)
      )
        return;
      if (!event.ctrlKey || event.altKey || event.metaKey) return;
      if (event.key.toLowerCase() === "k") {
        event.preventDefault();
        window.dispatchEvent(new Event("sicost:menu-search"));
      }
      if (event.key.toLowerCase() === "s") {
        event.preventDefault();
        const dialogs = [
          ...document.querySelectorAll<HTMLElement>('[role="dialog"]'),
        ].filter(visible);
        const scope =
          dialogs.at(-1) ??
          [
            ...document.querySelectorAll<HTMLElement>(".sicost-screen-host"),
          ].find(visible);
        if (
          !scope ||
          scope.getAttribute("aria-busy") === "true" ||
          scope.querySelector('[aria-invalid="true"]')
        )
          return;
        const save = [
          ...scope.querySelectorAll<HTMLButtonElement>("button"),
        ].find(
          (b) =>
            visible(b) &&
            !b.disabled &&
            /^(저장|등록|수정|변경 저장|저장하기)$/.test(
              b.textContent?.trim() ?? "",
            ),
        );
        if (save) {
          const form = save.closest<HTMLElement>("[data-permission-screen]");
          const screen = form?.dataset.permissionScreen ?? active;
          const action =
            form?.dataset.permissionAction ??
            (save.textContent?.trim() === "등록" ? "REG" : "UPD");
          if (permissions?.screens[screen]?.includes(action)) save.click();
        }
      }
      if (["ArrowLeft", "ArrowRight"].includes(event.key)) {
        if (
          target.closest(
            'input,textarea,select,[contenteditable="true"],[role="dialog"]',
          ) ||
          document.querySelector('[role="dialog"]')
        )
          return;
        const host = [
          ...document.querySelectorAll<HTMLElement>(".sicost-screen-host"),
        ].find(visible);
        const label = event.key === "ArrowLeft" ? "이전 페이지" : "다음 페이지";
        const button = host?.querySelector<HTMLButtonElement>(
          `button[aria-label="${label}"]`,
        );
        if (button && !button.disabled) {
          event.preventDefault();
          button.click();
        }
      }
    };
    document.addEventListener("keydown", key);
    return () => document.removeEventListener("keydown", key);
  }, [active, permissions]);
  useEffect(() => {
    setQuery("");
    setIndex(0);
  }, [mode]);
  const open = () => {
    const id = matches[index]?.screenId;
    if (id) {
      onOpen(id);
      onClose();
    }
  };
  return (
    <>
      <Dialog
        open={mode === "search"}
        title="메뉴 찾기"
        onCancel={onClose}
        onConfirm={open}
        confirmLabel="이동"
        confirmDisabled={!matches.length}
      >
        <label className="sicost-field">
          화면명·번호
          <input
            aria-label="화면명·번호"
            value={query}
            onChange={(e) => {
              setQuery(e.target.value);
              setIndex(0);
            }}
            onKeyDown={(e) => {
              if (e.nativeEvent.isComposing) {
                if (e.key === "Enter") e.preventDefault();
                return;
              }
              if (e.key === "ArrowDown" || e.key === "ArrowUp") {
                e.preventDefault();
                setIndex((v) =>
                  Math.max(
                    0,
                    Math.min(
                      matches.length - 1,
                      v + (e.key === "ArrowDown" ? 1 : -1),
                    ),
                  ),
                );
              }
            }}
          />
        </label>
        <div key={query} role="listbox" aria-label="검색된 메뉴" className="sicost-command-results">
          {matches.map((m, i) => (
            <button
              type="button"
              style={{ animationDelay: `${Math.min(i, 5) * 20}ms` }}
              role="option"
              aria-selected={i === index}
              key={m.screenId}
              onClick={() => {
                onOpen(m.screenId!);
                onClose();
              }}
            >
              <strong>{m.name}</strong>
              <small>{menus.find(group => group.menuId === m.parentId)?.name} · {m.screenId}</small>
            </button>
          ))}
        </div>
        {!matches.length && <p>일치하는 메뉴가 없습니다.</p>}
      </Dialog>
      <Dialog
        open={mode === "help"}
        title="단축키 보기"
        onCancel={onClose}
        onConfirm={onClose}
        confirmLabel="닫기"
      >
        <p>Enter · 조회 조건에서 조회</p>
        <p>Ctrl+S · 활성 업무 저장</p>
        <p>Esc · 최상위 창 닫기</p>
        <p>Ctrl+K · 메뉴 찾기</p>
        <p>Ctrl+← / Ctrl+→ · 이전 / 다음 페이지</p>
      </Dialog>
    </>
  );
}
