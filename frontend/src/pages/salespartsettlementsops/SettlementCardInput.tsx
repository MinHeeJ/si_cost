import { useLocation } from "react-router-dom";
import { useEffect, useRef, useState } from "react";
import { Dialog } from "../../shared/Dialog";
import { ApiError } from "../../shared/api";
import { useCommitRefresh } from "../../shared/useCommitRefresh";
import { formatAmount } from "../../shared/format";
import { useFinancialNavigationGuard } from "../financialitems/useFinancialNavigationGuard";
import {
  getSalesPartSettlement,
  type SettlementResult,
} from "../salespartsettlements/salesPartSettlementsApi";
import {
  SettlementConfirmationDialog,
  SettlementCancellationDialog,
} from "./SettlementDialogs";
import * as api from "./settlementCommandsApi";

export function SettlementCardInput({
  data,
  refresh,
  onGuard,
  onView,
  trend,
}: {
  data: SettlementResult;
  refresh(): void;
  onGuard(guard: (proceed: () => void) => void): void;
  onView(trend: boolean): void;
  trend: boolean;
}) {
  const location = useLocation();
  const [current, setCurrent] = useState(data),
    [draft, setDraft] = useState(data.cardAmount ?? "0");
  const [modal, setModal] = useState<"confirm" | "cancel">(),
    [error, setError] = useState<unknown>(),
    [refreshFailed, setRefreshFailed] = useState(false),
    [uncertain, setUncertain] = useState(false);
  const { pending, message, commit } = useCommitRefresh(
    "저장됐지만 다시 불러오지 못했습니다",
  );
  useEffect(() => { setModal(undefined); }, [location.pathname]);
  const active = useRef(false);
  useEffect(() => {
    setCurrent(data);
    setDraft(data.cardAmount ?? "0");
  }, [data]);
  let card: string | undefined,
    validation = "";
  try {
    card = api.parseSettlementCard(draft);
  } catch (e) {
    validation = (e as Error).message;
  }
  const dirty = card === undefined || card !== (current.cardAmount ?? "0");
  const draftProfit =
    card === undefined || current.summary.personProfitShareAmount == null
      ? null
      : (
          BigInt(current.summary.personProfitShareAmount) - BigInt(card)
        ).toString();
  const reload = async () => {
    const result = await getSalesPartSettlement(current.partId, {
      month: current.month,
      page: 1,
      size: 50,
      sort: "name",
      direction: "ASC",
    });
    setCurrent(result);
    setDraft(result.cardAmount ?? "0");
    setRefreshFailed(false);
    setUncertain(false);
    return result;
  };
  const run = async (
    operation: "card" | "confirm" | "cancel",
    reason?: string,
    refreshParent = true,
  ): Promise<boolean> => {
    if (active.current || pending || refreshFailed || uncertain) return false;
    if (operation !== "cancel" && !card) {
      setError(new Error(validation));
      return false;
    }
    active.current = true;
    setError(undefined);
    let refreshed = false;
    try {
      await commit(
        async () => {
          const payload = {
            month: current.month,
            cardAmount: card!,
            expectedRevision: current.revision,
          };
          if (operation === "card")
            await api.saveSalesPartSettlementCard(current.partId, payload);
          else if (operation === "confirm")
            await api.createSalesPartSettlementConfirmation(
              current.partId,
              payload,
            );
          else
            await api.createSalesPartSettlementCancellation(current.partId, {
              month: current.month,
              reason: reason!,
              expectedRevision: current.revision,
            });
          setModal(undefined);
        },
        async () => {
          try {
            await reload();
            refreshed = true;
          } catch (failure) {
            setRefreshFailed(true);
            throw failure;
          }
        },
      );
      if (refreshed && refreshParent) refresh();
      return refreshed;
    } catch (failure) {
      setError(failure);
      if (!(failure instanceof ApiError)) setUncertain(true);
      return false;
    } finally {
      active.current = false;
    }
  };
  const guard = useFinancialNavigationGuard(
    dirty,
    () => run("card", undefined, false),
    () => setDraft(current.cardAmount ?? "0"),
    "UI-19",
  );
  useEffect(() => {
    onGuard((proceed) => {
      void guard.ask("다른 정산으로 이동").then((allowed) => {
        if (allowed) proceed();
      });
    });
  }, [dirty, draft, pending, current]);
  useEffect(() => {
    const before = (event: BeforeUnloadEvent) => {
      if (dirty) {
        event.preventDefault();
        event.returnValue = "";
      }
    };
    window.addEventListener("beforeunload", before);
    return () => window.removeEventListener("beforeunload", before);
  }, [dirty]);
  const fields = error instanceof ApiError ? error.fieldMessages : {};
  const disabled = pending || refreshFailed || uncertain;
  return (
    <div data-testid="settlement-command-panel">
      <div className="settlement-card">
        {current.canSaveCard ? <label>
          법인카드 사용액{" "}
          <input
            data-testid="settlement-card-input"
            value={draft}
            readOnly={!current.canSaveCard}
            disabled={pending}
            onChange={(event) => setDraft(event.target.value)}
            aria-invalid={Boolean(validation || fields.cardAmount)}
          />
        </label> : <span>법인카드 사용액 {formatAmount(current.cardAmount, "원")}</span>}
        <span>원</span>
        {current.canSaveCard && (
          <button
            type="button"
            data-testid="settlement-card-save"
            disabled={disabled || Boolean(validation)}
            onClick={() => void run("card")}
          >
            카드 저장
          </button>
        )}
        {dirty && (
          <p role="status" data-testid="settlement-card-draft">
            저장 전 · 예상 파트 이익금 {formatAmount(draftProfit, "원")} 원
          </p>
        )}
        <p>
          파트 이익금에서 뺍니다(엑셀 ‘원가표’의 ‘법카 사용 별도’). 회사 카드와
          별도입니다.
        </p>
        {(validation || fields.cardAmount) && (
          <p role="alert">{validation || fields.cardAmount}</p>
        )}
      </div>
      <div className="settlement-toolbar">
        {current.canConfirm && (
          <button
            type="button"
            className="sicost-primary"
            data-testid="settlement-confirm-open"
            disabled={disabled || Boolean(validation)}
            onClick={() => setModal("confirm")}
          >
            정산 확정
          </button>
        )}
        {current.canCancel && (
          <button
            type="button"
            data-testid="settlement-cancel-open"
            disabled={disabled}
            onClick={() => setModal("cancel")}
          >
            확정 취소
          </button>
        )}
        <div role="tablist" aria-label="정산 보기" className="settlement-tabs">
          <button
            type="button"
            role="tab"
            data-testid="settlement-month-tab"
            aria-selected={!trend}
            onClick={() => {
              void guard.ask("이번 달 보기").then((ok) => {
                if (ok) onView(false);
              });
            }}
          >
            이번 달
          </button>
          <button
            type="button"
            role="tab"
            data-testid="settlement-trend-tab"
            aria-selected={trend}
            onClick={() => {
              void guard.ask("기간 추이 보기").then((ok) => {
                if (ok) onView(true);
              });
            }}
          >
            기간 추이 (1월~기준월)
          </button>
        </div>
      </div>
      {message && <p role="status">{message}</p>}
      {error != null && (
        <p role="alert">
          {error instanceof Error ? error.message : "저장에 실패했습니다."}
          {error instanceof ApiError ? ` (${error.body.requestId})` : ""}
        </p>
      )}
      {(refreshFailed || uncertain) && (
        <div className="settlement-banner warn">
          <p>
            {refreshFailed
              ? "저장됐지만 다시 불러오지 못했습니다"
              : "응답을 확인할 수 없습니다. 현재 상태를 조회한 후 수동으로 다시 시도해 주세요."}
          </p>
          <button
            type="button"
            data-testid="settlement-command-retry"
            onClick={() =>
              void reload()
                .then(() => refresh())
                .catch(setError)
            }
          >
            다시 조회
          </button>
        </div>
      )}
      {modal === "confirm" && card !== undefined && (
        <SettlementConfirmationDialog
          data={current}
          card={card}
          pending={pending}
          onCancel={() => setModal(undefined)}
          onConfirm={async () => {
            if (!(await run("confirm")))
              throw error instanceof Error
                ? error
                : new Error("확정 결과를 확인해 주세요. 초안은 유지됩니다.");
          }}
        />
      )}
      {modal === "cancel" && (
        <SettlementCancellationDialog
          data={current}
          pending={pending}
          onCancel={() => setModal(undefined)}
          onConfirm={async (reason) => {
            if (!(await run("cancel", reason)))
              throw new Error(
                "취소하지 못했습니다. 사유와 오류를 확인해 주세요.",
              );
          }}
        />
      )}
      {guard.request && (
        <Dialog
          open
          title="저장하지 않은 카드 사용액"
          pending={pending}
          onConfirm={guard.saveAndGo}
          onCancel={guard.stay}
          confirmLabel="저장 후 이동"
          cancelLabel="계속 편집"
        >
          <div
            data-testid="settlement-dirty-dialog"
            data-screen-id="SCR-UI-19-M3"
          >
            <p>
              {current.partId} / {current.month} · 저장 전 카드 {draft}
            </p>
            <p>저장한 뒤 이동하거나 변경을 버릴 수 있습니다</p>
            <button
              type="button"
              data-testid="settlement-dirty-discard"
              disabled={pending}
              onClick={guard.discardAndGo}
            >
              버리기
            </button>
          </div>
        </Dialog>
      )}
    </div>
  );
}
