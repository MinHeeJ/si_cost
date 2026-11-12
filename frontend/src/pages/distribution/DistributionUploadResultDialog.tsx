import { useEffect, useState } from "react";
import { Dialog } from "../../shared/Dialog";
import { ErrorBanner, StatusBadge } from "../../shared/Feedback";
import { ApiError } from "../../shared/api";
import { UploadIssuesPanel } from "../uploadsops/UploadIssuesPanel";
import {
  getUploadIssues,
  type UploadIssuesPage,
} from "../uploadsops/uploadIssuesApi";
import {
  getDistributionUploadResult,
  type DistributionUploadResult,
} from "./distributionApi";

export function DistributionUploadResultDialog({
  uploadId,
  onClose,
}: {
  uploadId: number;
  onClose(): void;
}) {
  const [data, setData] = useState<{
    result: DistributionUploadResult;
    issues: UploadIssuesPage;
  }>();
  const [error, setError] = useState<unknown>(),
    [loading, setLoading] = useState(true),
    [retry, setRetry] = useState(0);
  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(undefined);
    setData(undefined);
    Promise.all([
      getDistributionUploadResult(uploadId),
      getUploadIssues(uploadId, "", 1, 50),
    ])
      .then(([result, issues]) => {
        if (active) setData({ result, issues });
      })
      .catch((e) => {
        if (active) setError(e);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [uploadId, retry]);
  return (
    <Dialog
      open
      wide
      title="배분표 업로드 결과"
      confirmLabel="닫기"
      cancelLabel="닫기"
      onConfirm={onClose}
      onCancel={onClose}
    >
      <section
        data-testid="distribution-upload-result"
        data-screen-id="SCR-UI-01"
      >
        {loading && <p role="status">불러오는 중입니다.</p>}
        {Boolean(error) && (
          <ErrorBanner
            message={
              error instanceof Error
                ? error.message
                : "결과를 조회하지 못했습니다."
            }
            requestId={
              error instanceof ApiError ? error.body.requestId : undefined
            }
            onRetry={() => setRetry((value) => value + 1)}
          />
        )}
        {data && (
          <>
            <p>
              업로드 번호 {data.result.uploadId} · 당시 단계{" "}
              {data.result.step ?? "–"} ·{" "}
              <StatusBadge
                tone={data.result.status === "APPLIED" ? "ok" : "info"}
              >
                {data.result.status === "APPLIED" ? "반영 완료" : "검사 완료"}
              </StatusBadge>
            </p>
            <div className="distribution-summary" role="status">
              <span>전체 {data.result.summary.totalRows}건</span>
              <span>정상 {data.result.summary.successRows}건</span>
              <span className="sicost-tone-warn">
                경고 {data.result.summary.warningRows}건
              </span>
              <span className="sicost-tone-bad">
                오류 {data.result.summary.errorRows}건
              </span>
              <span>파일 경고 {data.result.summary.fileWarningCount}건</span>
              <span>계산표 경고 {data.result.summary.tableWarningCount}건</span>
            </div>
            <UploadIssuesPanel
              uploadId={uploadId}
              initialPage={data.issues}
              screenId="UI-01"
            />
          </>
        )}
      </section>
    </Dialog>
  );
}
