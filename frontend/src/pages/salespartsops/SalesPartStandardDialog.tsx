import { useEffect, useRef, useState } from "react";
import { Dialog } from "../../shared/Dialog";
import { Text } from "../../shared/Text";
import { ErrorBanner } from "../../shared/Feedback";
import { ApiError } from "../../shared/api";
import { BusinessUserSelect } from "../businessusersops/BusinessUserSelect";
import {
  createSalesPartStandard,
  getPartStandardDetail,
  getSalesPartDefaults,
  type PartDetail,
  type PartStandardResult,
} from "./partStandardsApi";
import "./partStandards.css";

export function SalesPartStandardDialog({
  partId,
  onClose,
  onSaved,
  onDirtyChange,
}: {
  partId: string;
  onClose(): void;
  onSaved(result: PartStandardResult): void | Promise<void>;
  onDirtyChange?(dirty: boolean): void;
}) {
  const [detail, setDetail] = useState<PartDetail>();
  const [month, setMonth] = useState(""),
    [minimum, setMinimum] = useState("");
  const [leader, setLeader] = useState(""),
    [rate, setRate] = useState(""),
    [expense, setExpense] = useState("");
  const [loading, setLoading] = useState(true),
    [pending, setPending] = useState(false),
    [dirty, setDirty] = useState(false);
  const [confirm, setConfirm] = useState(false),
    [leaving, setLeaving] = useState(false);
  const [error, setError] = useState<unknown>(),
    [fields, setFields] = useState<Record<string, string>>({});
  const [saved, setSaved] = useState<PartStandardResult>(),
    [refreshFailed, setRefreshFailed] = useState(false);
  const submitting = useRef(false),
    generation = useRef(0);
  const effective = (data: PartDetail, selected: string) =>
    [...data.standards]
      .sort((a, b) => b.effectiveMonth.localeCompare(a.effectiveMonth))
      .find((row) => row.effectiveMonth <= selected);
  async function load() {
    const request = ++generation.current;
    setLoading(true);
    setError(undefined);
    try {
      const [data, defaults] = await Promise.all([
        getPartStandardDetail(partId),
        getSalesPartDefaults(),
      ]);
      if (generation.current !== request) return;
      setDetail(data);
      setMonth(defaults.effectiveMonth);
      setMinimum(defaults.minMonth);
      const base = effective(data, defaults.effectiveMonth);
      setLeader(base?.leaderUserId ?? "");
      setRate(base?.profitRate ?? defaults.profitRate ?? "");
      setExpense(base?.salesExpenseAmount ?? defaults.salesExpenseAmount ?? "");
      setDirty(false);
      setFields({});
    } catch (e) {
      if (generation.current === request) setError(e);
    } finally {
      if (generation.current === request) setLoading(false);
    }
  }
  useEffect(() => {
    void load();
    return () => {
      generation.current++;
    };
  }, [partId]);
  useEffect(() => {
    onDirtyChange?.(dirty);
    return () => onDirtyChange?.(false);
  }, [dirty, onDirtyChange]);
  function close() {
    if (dirty && !saved) setLeaving(true);
    else onClose();
  }
  async function refresh(result: PartStandardResult) {
    try {
      await onSaved(result);
      setRefreshFailed(false);
      onClose();
    } catch {
      setRefreshFailed(true);
    }
  }
  async function submit() {
    if (submitting.current || loading || !detail || saved) return;
    const invalid: Record<string, string> = {};
    if (!/^(?!0000)\d{4}-(0[1-9]|1[0-2])$/.test(month) || month < minimum)
      invalid.effectiveMonth = `적용 시작월은 ${minimum} 이후여야 합니다.`;
    if (detail.standards.some((row) => row.effectiveMonth === month))
      invalid.effectiveMonth = "이미 등록된 시작월입니다.";
    const first = [...detail.standards].sort((a, b) =>
      a.effectiveMonth.localeCompare(b.effectiveMonth),
    )[0];
    if (first && month < first.effectiveMonth)
      invalid.effectiveMonth = "최초 기준 시작월 이전에는 추가할 수 없습니다.";
    if (!leader) invalid.leaderUserId = "파트장을 선택해 주세요.";
    if (
      !/^\d+(\.\d{1,2})?$/.test(rate.trim()) ||
      !/^(?:0*\d{1,2}(?:\.\d{1,2})?|0*100(?:\.0{1,2})?)$/.test(rate.trim())
    )
      invalid.profitRate = "0~100, 소수 최대 2자리로 입력해 주세요.";
    const normalized = expense
      .replace(/[\s,]/g, "")
      .replace(/원$/, "")
      .replace(/^0+(?=\d)/, "");
    if (!/^\d{1,15}$/.test(normalized))
      invalid.salesExpenseAmount =
        "0~999,999,999,999,999원의 정수를 입력해 주세요.";
    setFields(invalid);
    if (Object.keys(invalid).length) return;
    if (!confirm) {
      setConfirm(true);
      return;
    }
    submitting.current = true;
    setPending(true);
    setError(undefined);
    try {
      const result = await createSalesPartStandard(partId, {
        effectiveMonth: month,
        leaderUserId: leader,
        profitRate: rate.trim(),
        salesExpenseAmount: normalized,
        expectedRevision: detail.revision,
      });
      setSaved(result);
      setDirty(false);
      await refresh(result);
    } catch (e) {
      setError(e);
      setConfirm(false);
      if (e instanceof ApiError) setFields(e.fieldMessages);
    } finally {
      submitting.current = false;
      setPending(false);
    }
  }
  return (
    <>
      <Dialog
        open
        wide
        title={confirm ? "영업 파트 기준 변경 확인" : "영업 파트 기준 변경"}
        pending={pending}
        confirmDisabled={loading || !detail || !!saved}
        confirmLabel={confirm ? "확인 후 저장" : "변경 확인"}
        onConfirm={submit}
        onCancel={close}
      >
        <section
          className="part-standard-editor"
          data-screen-id="UI-15"
          data-testid="part-standard-editor"
        >
          {loading ? (
            <p role="status">불러오는 중입니다.</p>
          ) : (
            detail && (
              <>
                <h2>{detail.part.name}</h2>
                <p className="sicost-banner sicost-tone-info">
                  새 시작월의 기준을 추가합니다. 기존 시작월은 덮어쓰지 않으며
                  예정된 변경은 보존됩니다.
                </p>
                {detail.part.useYn === "N" && (
                  <span className="sicost-badge sicost-tone-lock">
                    중지 · 기준 변경 가능
                  </span>
                )}
                <div className="part-standard-fields">
                  <Text
                    label="적용 시작월"
                    type="month"
                    required
                    min={minimum}
                    value={month}
                    readOnly={pending || confirm || !!saved}
                    error={fields.effectiveMonth}
                    onChange={(value) => {
                      setMonth(value);
                      setDirty(true);
                      if (!confirm) {
                        const base = effective(detail, value);
                        setLeader(base?.leaderUserId ?? "");
                        setRate(base?.profitRate ?? "");
                        setExpense(base?.salesExpenseAmount ?? "");
                      }
                    }}
                  />
                  <BusinessUserSelect
                    purpose="PART_LEADER"
                    value={leader}
                    selectedLabel={
                      detail.standards.find(
                        (row) => row.leaderUserId === leader,
                      )?.leaderName
                    }
                    required
                    disabled={pending || confirm || !!saved}
                    error={fields.leaderUserId}
                    onChange={(value) => {
                      setLeader(value);
                      setDirty(true);
                    }}
                  />
                  <Text
                    label="이익 배분율 (%)"
                    required
                    value={rate}
                    readOnly={pending || confirm || !!saved}
                    error={fields.profitRate}
                    onChange={(value) => {
                      setRate(value);
                      setDirty(true);
                    }}
                  />
                  <Text
                    label="공통관리비 (자사 인력 1인·월, 원)"
                    required
                    value={expense}
                    readOnly={pending || confirm || !!saved}
                    error={fields.salesExpenseAmount}
                    onChange={(value) => {
                      setExpense(value);
                      setDirty(true);
                    }}
                  />
                </div>
                <p>최소 적용월 {minimum} · 입력과 저장 단위는 원입니다.</p>
                {confirm && (
                  <div className="sicost-banner sicost-tone-warn">
                    <span>
                      적용 시작월 {month} · 파트장 {leader} · 이익 배분율 {rate}
                      % · 공통관리비 {expense}원으로 변경합니다.
                    </span>
                  </div>
                )}
              </>
            )
          )}
          {error != null && (
            <ErrorBanner
              message={
                error instanceof Error ? error.message : "처리하지 못했습니다."
              }
              requestId={
                error instanceof ApiError ? error.body.requestId : undefined
              }
              onRetry={!detail ? () => void load() : undefined}
            />
          )}
          {saved && <p role="status">{saved.message}</p>}
          {refreshFailed && (
            <div role="alert">
              저장됐지만 다시 불러오지 못했습니다.{" "}
              <button
                type="button"
                data-testid="part-standard-refresh"
                onClick={() => saved && void refresh(saved)}
              >
                다시 조회
              </button>
            </div>
          )}
        </section>
      </Dialog>
      {leaving && (
        <Dialog
          open
          title="저장하지 않은 내용이 있습니다."
          confirmLabel="나가기"
          cancelLabel="계속 작성"
          onConfirm={() => {
            setDirty(false);
            onClose();
          }}
          onCancel={() => setLeaving(false)}
        >
          <p>작성한 내용을 버리고 나가시겠습니까?</p>
        </Dialog>
      )}
    </>
  );
}
