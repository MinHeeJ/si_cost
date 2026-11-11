import { useState } from "react";
import {
  ExcelUploadDialog,
  type ExcelUploadDialogProps,
  type UploadWorkflow,
} from "../b7/ExcelUploadDialog";
import { ApiError } from "../../shared/api";
import * as api from "./b8Api";
import "./b8.css";

const workflow: UploadWorkflow = {
  async validate(file) {
    const result = await api.createB8Validation(file);
    return { ...result, status: "CHECKED" };
  },
  async apply(file, uploadId) {
    const result = await api.saveB8Apply(file, uploadId);
    return {
      ...result,
      uploadId,
      status: "APPLIED",
      appliedCount: result.finalNumbers.length,
      appliedAt: "",
    };
  },
};
async function template() {
  api.saveDownload(await api.getB8Template());
}
// SCR-UI-02: retained developer pool dialog, no additional route.
export function B8UploadDialog(
  props: Pick<
    ExcelUploadDialogProps,
    "onClose" | "onApplied" | "onDirtyChange"
  >,
) {
  if (
    typeof window.matchMedia === "function" &&
    window.matchMedia("(max-width: 767px)").matches
  )
    return (
      <section data-testid="b8-desktop-notice">
        <p>엑셀 업로드는 PC에서 이용해 주세요.</p>
        <button
          type="button"
          data-testid="b8-mobile-close"
          onClick={props.onClose}
        >
          닫기
        </button>
      </section>
    );
  return (
    <ExcelUploadDialog
      {...props}
      workflow={workflow}
      screenId="UI-02"
      title="개발자 신규 엑셀 업로드 B8"
      onTemplate={template}
      description="B8 개발자 신규 등록 · XLSX 10MiB 이하 · 첫 시트 1~5,000행 · 파일명 1~200자. 기존 개발자는 수정·재개하지 않습니다. 검사 후 같은 파일로 전량 반영합니다. 응답 유실 시 같은 File/uploadId로 수동 재요청하여 확인하세요. 자동 재시도하지 않습니다."
    />
  );
}
export function B8UploadActions({
  onApplied,
  onDirtyChange,
}: Pick<ExcelUploadDialogProps, "onApplied" | "onDirtyChange">) {
  const [open, setOpen] = useState(false),
    [pending, setPending] = useState(false),
    [error, setError] = useState("");
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
      data-testid="b8-upload-actions"
      data-screen-id="UI-02"
      className="b8-upload-actions"
    >
      <button
        type="button"
        data-testid="b8-template-button"
        disabled={pending}
        onClick={() => void download()}
      >
        {pending ? "파일 생성 중…" : "양식 내려받기"}
      </button>
      <button
        type="button"
        data-testid="b8-upload-button"
        onClick={() => setOpen(true)}
      >
        엑셀 업로드
      </button>
      {error && (
        <p role="alert">
          {error} 접근기록이 저장되지 않으면 파일은 전송되지 않습니다. 다시
          시도해 주세요.
        </p>
      )}
      {open && (
        <B8UploadDialog
          onClose={() => setOpen(false)}
          onApplied={onApplied}
          onDirtyChange={onDirtyChange}
        />
      )}
    </section>
  );
}
