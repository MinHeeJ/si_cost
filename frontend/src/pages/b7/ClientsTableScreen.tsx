import { useEffect, useRef, useState } from "react";
import { request } from "../../shared/api";
import { DataTable, type Column } from "../../shared/DataTable";
import { ExcelUploadDialog } from "./ExcelUploadDialog";
import * as api from "./b7Api";
import "./b7.css";

export type B7ClientRow = {
  id: string;
  type: "CUST" | "VNDR" | "BOTH";
  name: string;
  businessNo: string;
  ceo: string | null;
  industry: string | null;
  contactName: string | null;
  phone: string | null;
  taxEmail: string | null;
  account: string | null;
  projectCount: number;
  personCount: number;
  useYn: "Y" | "N";
};
export const clientColumnOrder = [
  "type",
  "name",
  "businessNo",
  "ceo",
  "industry",
  "contactName",
  "phone",
  "taxEmail",
  "account",
  "projectCount",
  "personCount",
  "useYn",
];
const labels = [
  "구분",
  "상호",
  "사업자번호",
  "대표자",
  "업종",
  "담당자",
  "연락처",
  "세금계산서 이메일",
  "지급 계좌",
  "진행 프로젝트",
  "투입 인력",
  "사용 여부",
];
type Filters = { type: string; useYn: string; q: string };
const defaults: Filters = { type: "", useYn: "Y", q: "" };
// SCR-UI-15: table/action child; host supplies registration/detail content and retains its tab.
export function ClientsTableScreen({
  onRegister,
  onClientOpen,
  columnView,
  onColumnsChange,
  onDirtyChange,
}: {
  onRegister(): void;
  onClientOpen?(id: string): void;
  columnView?: { order: string[]; hidden: string[] };
  onColumnsChange?(): void;
  onDirtyChange?(dirty: boolean): void;
}) {
  const [draft, setDraft] = useState(defaults),
    [filters, setFilters] = useState(defaults);
  const [page, setPage] = useState(1),
    [size, setSize] = useState<20 | 50 | 100>(50);
  const [rows, setRows] = useState<B7ClientRow[]>([]),
    [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true),
    [error, setError] = useState("");
  const [upload, setUpload] = useState(false),
    [pending, setPending] = useState(false),
    [revision, setRevision] = useState(0);
  const [mobile, setMobile] = useState(
    () =>
      typeof matchMedia !== "undefined" &&
      matchMedia("(max-width: 767px)").matches,
  );
  const [sort, setSort] = useState("name"),
    [direction, setDirection] = useState<"ASC" | "DESC">("ASC");
  const requestVersion = useRef(0);
  const visible = (columnView?.order ?? clientColumnOrder).filter(
    (id) => !(columnView?.hidden ?? []).includes(id),
  );
  useEffect(() => {
    if (typeof matchMedia === "undefined") return;
    const media = matchMedia("(max-width: 767px)");
    const change = () => {
      setMobile(media.matches);
      setPage(1);
    };
    media.addEventListener("change", change);
    return () => media.removeEventListener("change", change);
  }, []);
  async function load() {
    const version = ++requestVersion.current;
    setLoading(true);
    setError("");
    const query = new URLSearchParams({
      page: String(page),
      size: String(mobile ? 20 : size),
      sort,
      direction,
    });
    for (const [key, value] of Object.entries(filters))
      if (value.trim()) query.set(key, value.trim());
    try {
      const data = await request<{ items: B7ClientRow[]; total: number }>(
        `/api/clients?${query}`,
      );
      if (version !== requestVersion.current) return;
      const lastPage = Math.max(
        1,
        Math.ceil(data.total / (mobile ? 20 : size)),
      );
      if (page > lastPage) {
        setPage(lastPage);
        return;
      }
      setRows((previous) =>
        mobile && page > 1 ? [...previous, ...data.items] : data.items,
      );
      setTotal(data.total);
    } catch (e) {
      if (version === requestVersion.current)
        setError(
          e instanceof Error ? e.message : "거래처를 불러오지 못했습니다.",
        );
      throw e;
    } finally {
      if (version === requestVersion.current) setLoading(false);
    }
  }
  useEffect(() => {
    void load().catch(() => undefined);
    return () => {
      requestVersion.current++;
    };
  }, [filters, page, size, revision, mobile, sort, direction]);
  async function download(action: () => Promise<api.Download>) {
    if (pending) return;
    setPending(true);
    setError("");
    try {
      api.saveDownload(await action());
    } catch (e) {
      setError(e instanceof Error ? e.message : "다운로드에 실패했습니다.");
    } finally {
      setPending(false);
    }
  }
  const reset = () => {
    setDraft(defaults);
    setFilters(defaults);
    setPage(1);
    setRevision((value) => value + 1);
  };
  const columns: Column<B7ClientRow>[] = visible.map((id) => ({
    id,
    label: labels[clientColumnOrder.indexOf(id)],
    render: (row) => {
      if (id === "type")
        return (
          <span className={row.type === "CUST" ? "b7-info" : "b7-neutral"}>
            {row.type === "CUST"
              ? "고객사"
              : row.type === "VNDR"
                ? "협력사"
                : "고객·협력"}
          </span>
        );
      if (id === "useYn")
        return (
          <span className={row.useYn === "Y" ? "b7-ok" : "b7-neutral"}>
            {row.useYn === "Y" ? "사용" : "중지"}
          </span>
        );
      return row[id as keyof B7ClientRow] ?? "–";
    },
  }));
  return (
    <section
      className="b7-clients"
      data-screen-id="UI-15"
      data-testid="clients-table-screen"
    >
      <div className="b7-actions">
        {!mobile && (
          <>
            <button
              type="button"
              data-testid="clients-template-button"
              disabled={pending}
              onClick={() => void download(api.getB7Template)}
            >
              양식 내려받기
            </button>
            <button
              type="button"
              data-testid="clients-upload-button"
              onClick={() => setUpload(true)}
            >
              엑셀 업로드
            </button>
            <button
              type="button"
              data-testid="clients-export-button"
              disabled={pending}
              onClick={() =>
                void download(() =>
                  api.downloadB7File("/api/exports/master-data", {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({
                      view: "CLIENTS",
                      filters: Object.fromEntries(
                        Object.entries(filters)
                          .filter(([, value]) => value.trim())
                          .map(([key, value]) => [key, value.trim()]),
                      ),
                      columns: visible,
                      sort,
                      direction,
                      amountUnit: "천원",
                    }),
                  }),
                )
              }
            >
              목록 다운로드
            </button>
          </>
        )}
        <button
          type="button"
          data-testid="clients-register-button"
          className="sicost-primary"
          onClick={onRegister}
        >
          등록
        </button>
      </div>
      <form
        data-testid="clients-table-filter-form"
        className="b7-filters"
        onSubmit={(event) => {
          event.preventDefault();
          setFilters({ ...draft });
          setPage(1);
        }}
      >
        <label className="sicost-field">
          구분
          <select
            data-testid="clients-type-filter"
            value={draft.type}
            onChange={(event) =>
              setDraft({ ...draft, type: event.target.value })
            }
          >
            <option value="">전체</option>
            <option value="CUST">고객사</option>
            <option value="VNDR">협력사</option>
            <option value="BOTH">고객·협력</option>
          </select>
        </label>
        <label className="sicost-field">
          사용 여부
          <select
            data-testid="clients-status-filter"
            value={draft.useYn}
            onChange={(event) =>
              setDraft({ ...draft, useYn: event.target.value })
            }
          >
            <option value="">전체</option>
            <option value="Y">사용</option>
            <option value="N">중지</option>
          </select>
        </label>
        <label className="sicost-field">
          상호
          <input
            data-testid="clients-search-input"
            placeholder="상호 또는 사업자번호"
            maxLength={100}
            value={draft.q}
            onChange={(event) => setDraft({ ...draft, q: event.target.value })}
          />
        </label>
        <button
          type="button"
          data-testid="clients-reset-button"
          onClick={reset}
        >
          초기화
        </button>
        <button data-testid="clients-search-button" type="submit">
          조회
        </button>
        <label className="sicost-field">
          정렬
          <select
            data-testid="clients-sort-select"
            value={sort}
            onChange={(event) => {
              setSort(event.target.value);
              setPage(1);
            }}
          >
            {clientColumnOrder
              .filter((id) => id !== "account")
              .map((id) => (
                <option
                  data-testid={`clients-table-sort-option-${id.replace(/[A-Z]/g, (c) => `-${c.toLowerCase()}`)}`}
                  key={id}
                  value={id}
                >
                  {labels[clientColumnOrder.indexOf(id)]}
                </option>
              ))}
          </select>
        </label>
        <button
          type="button"
          data-testid="clients-direction-button"
          onClick={() => {
            setDirection(direction === "ASC" ? "DESC" : "ASC");
            setPage(1);
          }}
        >
          {direction === "ASC" ? "오름차순" : "내림차순"}
        </button>
      </form>
      {mobile ? (
        <div aria-busy={loading}>
          {error && (
            <p role="alert">
              {error}
              <button
                type="button"
                data-testid="clients-mobile-retry-button"
                onClick={() => void load().catch(() => undefined)}
              >
                다시 시도
              </button>
            </p>
          )}
          {loading && <p role="status">불러오는 중입니다.</p>}
          {!loading && !rows.length && !error && <p>조회된 자료가 없습니다.</p>}
          {rows.map((row) => (
            <article
              className="b7-client-card"
              key={row.id}
              data-testid={`client-card-${row.id}`}
            >
              <button
                type="button"
                data-testid={`client-open-${row.id}`}
                onClick={() => onClientOpen?.(row.id)}
              >
                {row.name}
              </button>
              <p>{row.businessNo}</p>
              <p>{row.phone ?? "–"}</p>
            </article>
          ))}
          {rows.length < total && (
            <button
              type="button"
              data-testid="clients-more-button"
              disabled={loading}
              onClick={() => setPage(page + 1)}
            >
              더 보기
            </button>
          )}
        </div>
      ) : (
        <DataTable
          rows={rows}
          columns={columns}
          rowKey={(row) => row.id}
          loading={loading}
          error={error}
          onRetry={() => void load().catch(() => undefined)}
          page={page}
          size={size}
          total={total}
          onPageChange={setPage}
          onSizeChange={(value) => {
            setSize(value);
            setPage(1);
          }}
          onColumnsChange={onColumnsChange}
          onRowAction={(row) => onClientOpen?.(row.id)}
        />
      )}
      {!loading && !rows.length && !error && (
        <button
          type="button"
          data-testid="clients-empty-reset-button"
          onClick={reset}
        >
          조건 초기화
        </button>
      )}
      {upload && !mobile && (
        <ExcelUploadDialog
          onClose={() => setUpload(false)}
          onApplied={load}
          onDirtyChange={onDirtyChange}
        />
      )}
    </section>
  );
}
