import { type ReactNode, type RefObject, useEffect, useRef } from "react";
import "./editorFooter.css";

export function EditorFooter({
  editorRef,
  label,
  children,
}: {
  editorRef: RefObject<HTMLElement>;
  label: string;
  children: ReactNode;
}) {
  const footerRef = useRef<HTMLElement>(null);
  useEffect(() => {
    const editor = editorRef.current;
    const footer = footerRef.current;
    if (!editor || !footer) return;
    const main = editor.closest<HTMLElement>(".sicost-main") ?? editor;
    const position = () => {
      const bounds = main.getBoundingClientRect();
      if (!bounds.width || !footer.offsetHeight) return;
      editor.style.setProperty("--sicost-editor-footer-left", `${bounds.left}px`);
      editor.style.setProperty("--sicost-editor-footer-right", `${window.innerWidth - bounds.right}px`);
      editor.style.setProperty("--sicost-editor-footer-space", `${footer.offsetHeight}px`);
    };
    position();
    const observer = typeof ResizeObserver === "undefined"
      ? undefined
      : new ResizeObserver(position);
    observer?.observe(main);
    observer?.observe(footer);
    window.addEventListener("resize", position);
    return () => {
      observer?.disconnect();
      window.removeEventListener("resize", position);
      editor.style.removeProperty("--sicost-editor-footer-space");
    };
  }, [editorRef]);

  return (
    <footer ref={footerRef} className="sicost-editor-footer" aria-label={label}>
      {children}
    </footer>
  );
}
