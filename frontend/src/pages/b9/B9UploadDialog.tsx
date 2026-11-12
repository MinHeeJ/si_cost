import { useState } from "react";
import {
  ExcelUploadDialog,
  type ExcelUploadDialogProps,
  type UploadWorkflow,
} from "../b7/ExcelUploadDialog";
import { ApiError } from "../../shared/api";
import * as api from "./b9Api";
import "./b9.css";

type Props = Pick<
  ExcelUploadDialogProps,
  "onClose" | "onApplied" | "onDirtyChange"
> & { year: number; readOnly?: boolean };
async function template() {
  api.saveDownload(await api.getB9Template());
}
// SCR-UI-01: B9 is an embedded dialog in the retained project list, not a route.
export function B9UploadDialog({ year, readOnly = false, ...props }: Props) {
  const workflow: UploadWorkflow = {
    validate: (file) => api.createB9Validation(file, year),
    apply: (file, uploadId) => api.saveB9Apply(file, uploadId, year),
    result: api.getUploadResult,
  };
  if (window.matchMedia?.("(max-width: 767px)").matches)
    return (
      <section data-testid="b9-desktop-notice" data-screen-id="SCR-UI-01">
        <p>이 기능은 PC에서 사용해 주세요.</p>
        <button
          type="button"
          data-testid="b9-mobile-close"
          onClick={props.onClose}
        >
          닫기
        </button>
      </section>
    );
  return (
    <ExcelUploadDialog
      {...props}
      key={year}
      workflow={workflow}
      readOnly={readOnly}
      screenId="UI-01"
      title="프로젝트 신규 엑셀 업로드 B9"
      onTemplate={template}
      emptyRegistrationMessage="등록된 프로젝트는 없습니다."
      description={`선택연도 ${year} · 신규등록 전용 · 기존 프로젝트 수정은 화면에서 합니다. XLSX 10,485,760바이트 이하 · 첫 시트 15열 · 1~5,000행 · 입력안내 시트. 검사 후 같은 파일·uploadId·연도로 전량 반영합니다. 원본 파일은 서버에 보관하지 않습니다. 응답 유실 시 결과 재조회 또는 같은 요청으로 명시 확인하세요. 자동 재시도하지 않습니다.`}
    />
  );
}
export function B9UploadActions({
  year,
  readOnly = false,
  ...props
}: Omit<Props, "onClose">) {
  const [open, setOpen] = useState(false),
    [pending, setPending] = useState(false),
    [error, setError] = useState("");
  if (window.matchMedia?.("(max-width: 767px)").matches) return null;
  async function download() {
    if (pending) return;
    setPending(true);
    setError("");
    try {
      await template();
    } catch (e) {
      setError(
        e instanceof ApiError
          ? `${e.message} (요청 ID: ${e.body.requestId})`
          : e instanceof Error
            ? e.message
            : "다운로드하지 못했습니다.",
      );
    } finally {
      setPending(false);
    }
  }
  return (
    <section
      className="b9-upload-actions"
      data-testid="b9-upload-actions"
      data-screen-id="SCR-UI-01"
    >
      <button
        type="button"
        data-testid="b9-template-button"
        disabled={pending}
        onClick={() => void download()}
      >
        {pending ? "파일 생성 중…" : "양식 내려받기"}
      </button>
      <button
        type="button"
        data-testid="b9-upload-button"
        disabled={readOnly}
        onClick={() => setOpen(true)}
      >
        엑셀 업로드
      </button>
      {error && (
        <p role="alert">
          {error} 접근기록이 저장되지 않으면 파일은 전송되지 않습니다.
        </p>
      )}
      {open && (
        <B9UploadDialog
          {...props}
          year={year}
          readOnly={readOnly}
          onClose={() => setOpen(false)}
        />
      )}
    </section>
  );
}
