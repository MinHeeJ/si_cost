import { useLocation } from "react-router-dom";
import { useEffect, useState } from "react";
import { DetailDrawer } from "../../shared/DetailDrawer";
import { ErrorBanner } from "../../shared/Feedback";
import type { AmountUnit } from "../../shared/format";
import { FinancialHistoryPanel } from "../apiops/FinancialHistoryPanel";
import type { SettlementQuery, SettlementResult } from "../salespartsettlements/salesPartSettlementsApi";
import { downloadSettlement } from "./settlementCommandsApi";

export function SettlementActions({data, query, trend, unit}: {
  data: SettlementResult; query: SettlementQuery; trend: boolean; unit: AmountUnit;
}) {
  const location = useLocation();
  const [history, setHistory] = useState(false);
  useEffect(() => { setHistory(false); }, [location.pathname]);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  return <>
    <div className="settlement-toolbar settlement-actions">
      <button type="button" data-testid="settlement-history" onClick={() => setHistory(true)}>변경 이력</button>
      <button type="button" className="settlement-download" data-testid="settlement-download" disabled={pending} onClick={async () => {
        if (pending) return;
        setPending(true); setError("");
        try { await downloadSettlement(data.partId, {...query, month: data.month}, trend ? "TREND" : "MONTH", unit); }
        catch (failure) { setError(failure instanceof Error ? failure.message : "다운로드에 실패했습니다."); }
        finally { setPending(false); }
      }}>{pending ? "엑셀 생성 중…" : "엑셀 다운로드"}</button>
    </div>
    {error && <ErrorBanner message={error} />}
    {history && <DetailDrawer title="영업 파트 정산 변경 이력" subtitle={`${data.partId} · ${data.month}`} onClose={() => setHistory(false)} testId="settlement-history-drawer">
      <FinancialHistoryPanel month={data.month} initialScope="PART_SETTLEMENT" partId={data.partId} refreshKey={data.revision} />
    </DetailDrawer>}
  </>;
}
