import { useEffect, useState } from "react";
import { DataTable } from "../../shared/DataTable";
import { Dialog } from "../../shared/Dialog";
import { ErrorBanner, StatusBadge } from "../../shared/Feedback";
import { ApiError } from "../../shared/api";
import {
  getOperatingStandardHistory,
  type OperatingStandard,
  type StandardHistory,
} from "./operatingStandardsApi";

export function StandardHistoryDialog({
  standard,
  onClose,
}: {
  standard: OperatingStandard;
  onClose(): void;
}) {
  const [data, setData] = useState<StandardHistory>(),
    [error, setError] = useState<unknown>(),
    [loading, setLoading] = useState(true);
  async function load() {
    setLoading(true);
    setError(undefined);
    try {
      setData(await getOperatingStandardHistory(standard.key));
    } catch (e) {
      setError(e);
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => {
    void load();
  }, [standard.key]);
  return (
    <Dialog
      open
      wide
      title={`${standard.name} · 기준 적용 이력`}
      confirmLabel="닫기"
      cancelLabel="취소"
      onConfirm={onClose}
      onCancel={onClose}
    >
      <div data-screen-id="SCR-UI-27-M8">
        <p>
          {standard.key} · {standard.unitNm} · {standard.description}
        </p>
        {error != null ? (
          <ErrorBanner
            message={
              error instanceof Error
                ? error.message
                : "이력을 불러오지 못했습니다."
            }
            requestId={
              error instanceof ApiError ? error.body.requestId : undefined
            }
            onRetry={() => void load()}
          />
        ) : (
          <DataTable
            rows={data?.items ?? []}
            loading={loading}
            rowKey={(row) => row.effectiveMonth}
            empty="기준값 없음"
            columns={[
              {
                id: "effectiveMonth",
                label: "적용 시작월",
                render: (row) => row.effectiveMonth,
              },
              { id: "value", label: "값", render: (row) => row.value },
              { id: "unitNm", label: "단위", render: (row) => row.unitNm },
              {
                id: "status",
                label: "상태",
                render: (row) => (
                  <StatusBadge
                    tone={
                      row.status === "현재"
                        ? "ok"
                        : row.status === "예정"
                          ? "info"
                          : "lock"
                    }
                  >
                    {row.status}
                  </StatusBadge>
                ),
              },
            ]}
          />
        )}
      </div>
    </Dialog>
  );
}
