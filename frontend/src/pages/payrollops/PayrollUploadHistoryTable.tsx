import { DataTable, type Column } from "../../shared/DataTable";
import { StatusBadge } from "../../shared/Feedback";
import { formatDateTime } from "../../shared/format";
import type { PayrollUploadRecord } from "../apiops/payrollApi";

export function PayrollUploadHistoryTable({
  rows,
  loading,
  page,
  size,
  total,
  onPageChange,
  onSizeChange,
  onResult,
  pending,
}: {
  rows: PayrollUploadRecord[];
  loading: boolean;
  page: number;
  size: 20 | 50 | 100;
  total: number;
  onPageChange(page: number): void;
  onSizeChange(size: 20 | 50 | 100): void;
  onResult(uploadId: number): void;
  pending: boolean;
}) {
  const columns: Column<PayrollUploadRecord>[] = [
    {
      id: "file",
      label: "파일",
      render: (r) => (
        <span data-testid={`payroll-upload-${r.uploadId}`}>{r.fileName}</span>
      ),
    },
    { id: "actor", label: "처리자", render: (r) => r.actor },
    {
      id: "date",
      label: "등록일시",
      render: (r) => formatDateTime(r.registeredAt),
    },
    {
      id: "status",
      label: "상태",
      align: "center",
      render: (r) => (
        <StatusBadge tone={r.status === "APPLIED" ? "ok" : "info"}>
          {r.status === "APPLIED" ? "반영 완료" : "검사 완료"}
        </StatusBadge>
      ),
    },
    ...(
      [
        ["totalRows", "전체"],
        ["successRows", "성공"],
        ["warningRows", "경고"],
        ["errorRows", "오류"],
      ] as const
    ).map(
      ([field, label]): Column<PayrollUploadRecord> => ({
        id: field,
        label,
        align: "right",
        render: (r) => r.summary[field],
      }),
    ),
    {
      id: "result",
      label: "결과",
      render: (r) => (
        <button
          type="button"
          data-testid={`payroll-result-open-${r.uploadId}`}
          disabled={pending}
          onClick={() => onResult(r.uploadId)}
        >
          결과 보기
        </button>
      ),
    },
  ];
  return (
    <section data-testid="payroll-upload-history-table">
      <DataTable
        rows={rows}
        columns={columns}
        rowKey={(r) => r.uploadId}
        loading={loading}
        empty="선택월 기록 없음"
        page={page}
        size={size}
        total={total}
        onPageChange={onPageChange}
        onSizeChange={onSizeChange}
      />
    </section>
  );
}
