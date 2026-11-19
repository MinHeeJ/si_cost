import type { CSSProperties } from "react";
export function BrandMark({ size = 34 }: { size?: number }) {
  return (
    <svg
      className="sicost-brand-mark"
      width={Math.round(size * 1.1)}
      height={size}
      viewBox="0 0 34 31"
      aria-hidden="true"
    >
      <rect
        className="brand-bar"
        x="2"
        y="17"
        width="6"
        height="12"
        rx="2"
        fill="currentColor"
        opacity=".35"
      />
      <rect
        className="brand-bar"
        x="11"
        y="11"
        width="6"
        height="18"
        rx="2"
        fill="currentColor"
        opacity=".65"
        style={{ animationDelay: "80ms" }}
      />
      <rect
        className="brand-bar"
        x="20"
        y="5"
        width="6"
        height="24"
        rx="2"
        fill="currentColor"
        style={{ animationDelay: "160ms" }}
      />
      <path
        d="M1 12 9 6l6 3 13-7"
        fill="none"
        stroke="#ffc600"
        strokeWidth="2.6"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <circle cx="28" cy="2" r="2" fill="#ffc600" />
    </svg>
  );
}
export function Brand({ size = 34 }: { size?: number }) {
  return (
    <>
      <BrandMark size={size} />
      <span className="sicost-brand-word">
        <span>원가관리시스템</span>
        <small>SI PROJECT COST</small>
      </span>
    </>
  );
}
// Decorative illustration, not a dashboard or fabricated business data.
export function BrandIllustration() {
  return (
    <aside className="lhero" aria-hidden="true">
      <div className="lh-mesh" />
      <div className="lh-holo" />
      <div className="lh-grain" />
      <div className="lh-top">
        <BrandMark />
        <span>원가관리시스템</span>
      </div>
      <div className="lh-art">
        <div className="lh-card">
          <div className="lh-card-h">
            <span>
              <i style={{ background: "#93c5fd" }} />
              매출
            </span>
            <span>
              <i style={{ background: "#5eead4" }} />
              원가
            </span>
            <span>
              <i style={{ background: "#ffc600" }} />
              이익
            </span>
          </div>
          <svg viewBox="0 0 360 170" className="lh-svg">
            <defs>
              <linearGradient id="brand-hero-area" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0" stopColor="#ffc600" stopOpacity=".35" />
                <stop offset="1" stopColor="#ffc600" stopOpacity="0" />
              </linearGradient>
            </defs>
            <path
              d="M0 42H360M0 84H360M0 126H360"
              stroke="rgba(255,255,255,.1)"
            />
            {[38, 52, 46, 64, 58, 76, 72, 92, 88, 108, 104, 124].map((h, i) => (
              <g key={i}>
                <rect
                  x={10 + i * 29}
                  y={168 - h}
                  width="11"
                  height={h}
                  rx="3"
                  fill={`rgba(147,197,253,${0.28 + i * 0.03})`}
                  style={{ "--d": `${i * 45}ms` } as CSSProperties}
                />
                <rect
                  x={22 + i * 29}
                  y={168 - h * 0.62}
                  width="7"
                  height={h * 0.62}
                  rx="2.5"
                  fill="rgba(94,234,212,.45)"
                  style={{ "--d": `${i * 45 + 20}ms` } as CSSProperties}
                />
              </g>
            ))}
            <path
              d="M14 128 C60 120 80 112 110 104 S170 92 200 76 S270 52 346 26 L346 168 L14 168Z"
              fill="url(#brand-hero-area)"
              className="lh-area"
            />
            <path
              d="M14 128 C60 120 80 112 110 104 S170 92 200 76 S270 52 346 26"
              fill="none"
              stroke="#ffc600"
              strokeWidth="2.4"
              strokeLinecap="round"
              className="lh-line"
            />
            <circle cx="346" cy="26" r="5" fill="#ffc600" className="lh-dot" />
            <circle
              cx="346"
              cy="26"
              r="11"
              fill="none"
              stroke="#ffc600"
              strokeOpacity=".45"
              className="lh-ring"
            />
          </svg>
        </div>
        <div className="lh-chip lh-c1">
          <b>마감</b>
          <span>월별 확정</span>
        </div>
        <div className="lh-chip lh-c2">
          <b>배분</b>
          <span>투입 MM 기준</span>
        </div>
      </div>
      <div className="lh-copy">
        <h2>
          프로젝트 손익을
          <br />한 장부에서.
        </h2>
        <p>투입 · 원가 · 매출 · 마감을 하나의 흐름으로 관리합니다.</p>
      </div>
    </aside>
  );
}
