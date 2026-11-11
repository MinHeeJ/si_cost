import { useEffect, useRef, useState } from "react";
import { ApiError } from "../../shared/api";

import * as api from "./masterDataExportApi";
import "./masterDataExport.css";

export type MasterDataExportButtonProps = {
  view: "CLIENTS" | "PARTS";
  filters: api.MasterExportFilters;
  sort?: string;
  direction?: "ASC" | "DESC";
  columnView?: { order: string[]; hidden: string[] };
  amountUnit?: api.ExportAmountUnit;
  disabled?: boolean;
};
export function MasterDataExportButton({
  view,
  filters,
  sort,
  direction,
  columnView,
  amountUnit = "천원",
  disabled = false,
}: MasterDataExportButtonProps) {
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<unknown>();
  const [success, setSuccess] = useState(false);
  const [mobile, setMobile] = useState(
    () => window.matchMedia?.("(max-width: 767px)").matches ?? false,
  );
  const active = useRef<AbortController>();
  const mounted = useRef(true);
  useEffect(() => {
    mounted.current = true;
    const media = window.matchMedia?.("(max-width: 767px)");
    const change = () => setMobile(media?.matches ?? false);
    media?.addEventListener("change", change);
    return () => {
      mounted.current = false;
      active.current?.abort();
      media?.removeEventListener("change", change);
    };
  }, []);
  async function download() {
    if (active.current || mobile || disabled) return;
    const controller = new AbortController();
    active.current = controller;
    setPending(true);
    setError(undefined);
    setSuccess(false);
    const columns =
      view === "PARTS"
        ? api.partExportColumns
        : (columnView?.order ?? api.clientExportColumns).filter(
            (id): id is api.ClientExportColumn =>
              api.clientExportColumns.includes(id as api.ClientExportColumn) &&
              (id === "name" || !columnView?.hidden.includes(id)),
          );
    try {
      const result = await api.createMasterDataExport(
        { view, filters: { ...filters }, sort, direction, columns, amountUnit },
        controller.signal,
      );
      if (!mounted.current || controller.signal.aborted) return;
      api.saveMasterDataDownload(result);
      setSuccess(true);
    } catch (failure) {
      if (mounted.current && !controller.signal.aborted) setError(failure);
    } finally {
      active.current = undefined;
      if (mounted.current) setPending(false);
    }
  }
  if (mobile) return null;
  return (
    <div
      className="master-data-export"
      data-testid="master-data-export"
      aria-busy={pending}
    >
      <button
        type="button"
        data-testid="master-data-export-button"
        disabled={disabled || pending}
        onClick={() => void download()}
      >
        <svg
          width="16"
          height="16"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.6"
          aria-hidden="true"
        >
          <path d="M12 3v12m-4-4 4 4 4-4M4 17v4h16v-4" />
        </svg>
        {pending ? "파일 생성 중…" : "목록 다운로드"}
      </button>
      {pending && (
        <span role="status">
          조회 조건에 맞는 전체 결과를 생성하고 있습니다.
        </span>
      )}
      {success && <span role="status">파일을 내려받았습니다.</span>}
      {error != null && (
        <div role="alert" className="sicost-banner sicost-tone-bad">
          <span>
            {error instanceof Error
              ? error.message
              : "파일을 내려받지 못했습니다."}
            {error instanceof ApiError && (
              <small>
                {" "}
                {error.body.field && `항목: ${error.body.field} · `}문의 번호:{" "}
                {error.body.requestId}
              </small>
            )}
          </span>
          <button
            type="button"
            data-testid="master-data-export-retry"
            disabled={disabled || pending}
            onClick={() => void download()}
          >
            다시 시도
          </button>
        </div>
      )}
    </div>
  );
}
