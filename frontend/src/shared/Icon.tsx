import type { SVGProps } from "react";

// Menu names come from MenuNode.icon; unknown DB icons use the folder fallback.
const paths: Record<string, string> = {
  grid: "M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z",
  db: "M4 6c0-4 16-4 16 0s-16 4-16 0 M4 6v12c0 4 16 4 16 0V6 M4 12c0 4 16 4 16 0",
  folder: "M3 6h7l2 3h9v11H3z M3 6V4h6l2 2",
  calendar: "M3 5h18v16H3z M7 2v6 M17 2v6 M3 10h18",
  receipt: "M5 3h14v19l-3-2-4 2-4-2-3 2z M8 7h8 M8 11h8 M8 15h5",
  chart: "M3 3v18h19 M7 16v-5 M12 16V6 M17 16V9",
  wallet: "M3 7h18v14H3z M3 7l14-4v4 M16 12h5v5h-5z",
  lock: "M7 10V6a5 5 0 0 1 10 0v4 M5 10h14v12H5z",
  sparkle: "m12 2 3 7 7 3-7 3-3 7-3-7-7-3 7-3z",
  gear: "m9 3 1-2h4l1 2 3 2 2 1v4l-2 1v3l2 1v4l-2 1-3 2-1 2h-4l-1-2-3-2-2-1v-4l2-1v-3l-2-1V6l2-1z M16 12a4 4 0 1 1-8 0 4 4 0 0 1 8 0",
  menu: "M4 6h16 M4 12h16 M4 18h16",
  bell: "M5 16V9a7 7 0 0 1 14 0v7l2 3H3z M10 22h4",
  user: "M16 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0 M4 22v-3a8 8 0 0 1 16 0v3",
  chevron: "m8 4 8 8-8 8",
  star: "m12 2 3 6 7 1-5 5 1 7-6-3-6 3 1-7-5-5 7-1z",
  clock: "M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0 M12 6v6l4 3",
  search: "M17 10a7 7 0 1 1-14 0 7 7 0 0 1 14 0 M15 15l6 6",
  plus: "M12 4v16 M4 12h16",
};
export function Icon({
  name,
  size = 18,
  ...props
}: Omit<SVGProps<SVGSVGElement>, "name"> & {
  name: string | null;
  size?: number;
}) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
      {...props}
    >
      <path d={paths[name ?? "folder"] ?? paths.folder} />
    </svg>
  );
}
