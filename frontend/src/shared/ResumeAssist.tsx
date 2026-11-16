import { useRef } from "react";
import { ErrorBanner } from "./Feedback";
import { Icon } from "./Icon";
import { resumeAccept } from "./resumeClient";
import type { ResumeAssistState } from "./useResumeExtraction";
import "./resume.css";
export function ResumeUploadButton({
  state,
  disabled = false,
}: {
  state: ResumeAssistState;
  disabled?: boolean;
}) {
  const input = useRef<HTMLInputElement>(null);
  return (
    <div className="resume-upload">
      <input
        ref={input}
        type="file"
        hidden
        accept={resumeAccept}
        aria-label="이력서 파일 선택"
        onChange={(e) => {
          const file = e.target.files?.[0];
          e.target.value = "";
          if (file) void state.upload(file);
        }}
      />
      <button
        type="button"
        disabled={disabled || state.pending}
        onClick={() => input.current?.click()}
      >
        <Icon name="upload" size={16} />
        {state.pending ? "읽는 중…" : "이력서 등록"}
      </button>
    </div>
  );
}
const labels: Record<string, string> = {
  name: "이름",
  phone: "전화번호",
  email: "이메일",
  employeeType: "구분",
  companyId: "업체",
  jobCode: "주 직무",
  subJobCode: "보조 직무",
  skills: "기술",
  homeAreaCode: "거주지",
  desiredRate: "희망 단가",
  availableDate: "투입 가능일",
};
export function ResumeExtractionFeedback({
  state,
}: {
  state: ResumeAssistState;
}) {
  return (
    <section
      className="resume-feedback"
      aria-label="이력서 입력 보조"
      aria-live="polite"
    >
      {state.pending && (
        <p role="status">
          이력서 읽는 중… 입력과 취소는 가능합니다. 파일은 저장하지 않습니다.
        </p>
      )}
      {state.error && <ErrorBanner message={state.error} />}
      {state.notice && (
        <p className="sicost-banner sicost-tone-info">{state.notice}</p>
      )}
      {state.fields.length > 0 && (
        <ul className="resume-field-results">
          {state.fields.map((f) => (
            <li
              key={f.field}
              className={
                f.confidence === "INFERRED"
                  ? "sicost-tone-warn"
                  : "sicost-tone-ok"
              }
              data-resume-field={f.field}
            >
              <strong>{labels[f.field]}:</strong> {f.notice}
              {f.fragment && <small> · {f.fragment}</small>}
            </li>
          ))}
        </ul>
      )}
      {state.career && <p>경력 참고 (저장하지 않음): {state.career}</p>}
      {state.warnings.length > 0 && (
        <details open>
          <summary>확인할 항목</summary>
          <ul>
            {state.warnings.map((w, i) => (
              <li key={i}>{w}</li>
            ))}
          </ul>
        </details>
      )}
    </section>
  );
}
