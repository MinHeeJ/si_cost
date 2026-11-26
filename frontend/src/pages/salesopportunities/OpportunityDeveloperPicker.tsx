import { useEffect, useState } from "react";
import { BusinessName } from "../../shared/BusinessName";
import { Dialog } from "../../shared/Dialog";
import { Pagination } from "../../shared/Pagination";
import { ScreenLoading } from "../../shared/ScreenLoading";
import { SalesCommandError } from "../salesopportunitiesops/SalesQuotePanel";
import * as api from "./salesOpportunityApi";

const DISPLAY_SIZE = 10;
const SOURCE_SIZE = 20;
const PAGES_PER_RESULT = SOURCE_SIZE / DISPLAY_SIZE;
type Developer = { id: string; name: string };

export function OpportunityDeveloperPicker({
  selected,
  names,
  startMonth,
  opportunityId,
  scheduledReleases,
  onChangeSelection,
  onClose,
}: {
  selected: string[];
  names: Record<string, string>;
  startMonth: string;
  opportunityId?: string;
  scheduledReleases: string[];
  onChangeSelection(people: { id: string; name?: string }[], checked: boolean): string | undefined;
  onClose(): void;
}) {
  const [searchInput, setSearchInput] = useState("");
  const [search, setSearch] = useState({ term: "" });
  const [page, setPage] = useState(1);
  const [people, setPeople] = useState<Developer[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>();
  const [selectionError, setSelectionError] = useState<string>();
  // The developer-list API returns 20 at a time. Each result backs two UI pages.
  const sourcePage = Math.ceil(page / PAGES_PER_RESULT);
  const first = ((page - 1) % PAGES_PER_RESULT) * DISPLAY_SIZE;
  const visiblePeople = people.slice(first, first + DISPLAY_SIZE);
  const selectedOnPage = visiblePeople.filter((person) => selected.includes(person.id)).length;
  const pageSelected = visiblePeople.length > 0 && selectedOnPage === visiblePeople.length;
  const pagePartiallySelected = selectedOnPage > 0 && !pageSelected;

  function searchPeople() {
    setSelectionError(undefined);
    setSearch({ term: searchInput.trim() });
    setPage(1);
  }
  function updateSelection(items: { id: string; name?: string }[], checked: boolean) {
    setSelectionError(onChangeSelection(items, checked));
  }

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError(undefined);
    void api
      .getDevelopers(
        {
          q: search.term,
          directClass: "DRCT",
          includeStopped: false,
          page: sourcePage,
          size: SOURCE_SIZE,
        },
        controller.signal,
      )
      .then((result) => {
        if (controller.signal.aborted) return;
        setPeople(result.items);
        setTotal(result.total);
        const lastPage = Math.max(1, Math.ceil(result.total / DISPLAY_SIZE));
        setPage((current) => Math.min(current, lastPage));
      })
      .catch((reason: unknown) => {
        if (!controller.signal.aborted) setError(reason);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [search, sourcePage]);

  return (
    <Dialog
      open
      wide
      title="개발자 선택"
      className="sales-opportunity-developer-dialog"
      confirmLabel="선택 완료"
      onCancel={onClose}
      onConfirm={onClose}
    >
      <div
        className="sales-opportunity-developer-selector"
        data-testid="opportunity-developer-selector"
      >
        <section>
          <h3>선택된 개발자</h3>
          <div
            className={`sicost-table-viewport sales-opportunity-selected-viewport${selected.length > DISPLAY_SIZE ? " sales-opportunity-selected-scroll" : ""}`}
          >
            <table
              className="sicost-table sales-opportunity-selected-table"
              aria-label="선택된 개발자"
            >
              <thead>
                <tr>
                  <th scope="col">성명</th>
                  <th scope="col">아이디</th>
                  <th scope="col">현재 상태</th>
                  <th scope="col">해제</th>
                </tr>
              </thead>
              <tbody>
                {selected.length === 0 && (
                  <tr>
                    <td colSpan={4}>선택된 개발자가 없습니다.</td>
                  </tr>
                )}
                {selected.map((id) => (
                  <tr key={id}>
                    <td>{names[id] ?? <BusinessName kind="developer" id={id} />}</td>
                    <td>{id}</td>
                    <td>
                      <DeveloperStatus
                        id={id}
                        month={startMonth}
                        opportunityId={opportunityId}
                        scheduledRelease={scheduledReleases.includes(id)}
                      />
                    </td>
                    <td>
                      <button
                        type="button"
                        className="sales-opportunity-developer-alert"
                        aria-label={`${names[id] ?? id} 선택 해제`}
                        onClick={() => updateSelection([{ id }], false)}
                      >
                        해제
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
        <section className="sales-opportunity-developer-search">
          <div className="sales-opportunity-developer-search-row">
            <label>
              개발자 검색
              <input
                data-testid="opportunity-developer-search"
                value={searchInput}
                onChange={(event) => setSearchInput(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key !== "Enter" || event.nativeEvent.isComposing) return;
                  event.preventDefault();
                  searchPeople();
                }}
              />
            </label>
            <button type="button" onClick={searchPeople}>
              검색
            </button>
          </div>
          {selectionError && (
            <p role="alert" className="sicost-field-error">{selectionError}</p>
          )}
          {loading ? (
            <ScreenLoading message="개발자를 불러오는 중입니다." />
          ) : error != null ? (
            <SalesCommandError error={error} />
          ) : (
            <>
              <div className="sicost-table-viewport">
                <table
                  className="sicost-table sales-opportunity-search-table"
                  aria-label="개발자 검색 결과"
                >
                  <thead>
                    <tr>
                      <th scope="col">
                        <input
                          type="checkbox"
                          aria-label="이 페이지 전체 선택"
                          checked={pageSelected}
                          disabled={visiblePeople.length === 0}
                          ref={(input) => {
                            if (input) input.indeterminate = pagePartiallySelected;
                          }}
                          onChange={(event) => {
                            const { checked } = event.currentTarget;
                            event.currentTarget.indeterminate = pagePartiallySelected;
                            updateSelection(visiblePeople, checked);
                          }}
                        />
                      </th>
                      <th scope="col">사원명</th>
                      <th scope="col">아이디</th>
                    </tr>
                  </thead>
                  <tbody>
                    {visiblePeople.length === 0 && (
                      <tr>
                        <td colSpan={3}>선택할 개발자가 없습니다.</td>
                      </tr>
                    )}
                    {visiblePeople.map((person) => (
                      <tr key={person.id}>
                        <td>
                          <input
                            type="checkbox"
                            aria-label={person.name}
                            data-testid={`opportunity-developer-${person.id.toLowerCase()}`}
                            checked={selected.includes(person.id)}
                            onChange={(event) => updateSelection([person], event.target.checked)}
                          />
                        </td>
                        <td>{person.name}</td>
                        <td>{person.id}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <Pagination
                page={page}
                size={DISPLAY_SIZE}
                total={total}
                unit="명"
                onPageChange={(nextPage) => {
                  setSelectionError(undefined);
                  setPage(nextPage);
                }}
                label="인력 선택 페이지"
                testId="opportunity-people"
              />
            </>
          )}
        </section>
      </div>
    </Dialog>
  );
}

function DeveloperStatus({
  id,
  month,
  opportunityId,
  scheduledRelease,
}: {
  id: string;
  month: string;
  opportunityId?: string;
  scheduledRelease: boolean;
}) {
  const [messages, setMessages] = useState<string[]>();
  useEffect(() => {
    let live = true;
    setMessages(undefined);
    void api.getNeedWarnings(id, month, opportunityId).then(
      (result) => {
        if (live) setMessages(result);
      },
      () => {
        if (live) setMessages(["가배정·투입 상태를 불러오지 못했습니다."]);
      },
    );
    return () => {
      live = false;
    };
  }, [id, month, opportunityId]);

  return (
    <>
      {scheduledRelease && (
        <p className="sales-command-warning">기간 변경으로 해제 예정</p>
      )}
      {messages === undefined ? "상태 조회 중" : messages.length ? (
        <ul className="sales-opportunity-developer-status">
          {[...new Set(messages)].map((message) => <li key={message}>{message}</li>)}
        </ul>
      ) : !scheduledRelease && "별도 경고 없음"}
    </>
  );
}
