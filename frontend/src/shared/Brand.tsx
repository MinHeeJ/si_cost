import type { CSSProperties } from "react";

export function Brand({
  size = 32,
  wordmark = true,
}: {
  size?: number;
  wordmark?: boolean;
}) {
  return (
    <span className="sicost-brand-mark">
      <svg
        className="logo-mk"
        width={size * 1.1}
        height={size}
        viewBox="0 0 34 31"
        aria-hidden="true"
      >
        <rect
          className="lb lb1"
          x="2"
          y="17"
          width="6"
          height="12"
          rx="2"
          fill="currentColor"
          opacity=".35"
        />
        <rect
          className="lb lb2"
          x="11"
          y="11"
          width="6"
          height="18"
          rx="2"
          fill="currentColor"
          opacity=".65"
        />
        <rect
          className="lb lb3"
          x="20"
          y="5"
          width="6"
          height="24"
          rx="2"
          fill="currentColor"
        />
        <path
          className="ll"
          d="M1 12 9 6l6 3 13-7"
          fill="none"
          stroke="#FFC600"
          strokeWidth="2.6"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
        <circle className="ld" cx="28" cy="2" r="2" fill="#FFC600" />
      </svg>
      {wordmark && (
        <span className="sicost-wordmark">
          <span className="sicost-sr-only">원가관리시스템</span>
          <span className="sicost-wordmark-characters" aria-hidden="true">
            {[..."원가관리시스템"].map((character, index) => (
              <i key={index} style={{ "--character": index } as CSSProperties}>
                {character}
              </i>
            ))}
          </span>
          <small aria-hidden="true">SI PROJECT COST</small>
        </span>
      )}
    </span>
  );
}
