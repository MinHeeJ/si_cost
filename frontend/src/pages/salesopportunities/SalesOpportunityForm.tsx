import { FieldHelp } from "../../shared/FieldHelp";
import { useEffect, useRef, useState } from "react";
import { useFieldErrorFocus } from "../../shared/FieldErrors";
import { NumericInput, sanitizeNumber } from "../../shared/NumericInput";
import { useNavigate } from "react-router-dom";
import { FormPage } from "../../shared/FormPage";
import { Dialog } from "../../shared/Dialog";
import { ApiError } from "../../shared/api";
import { useCommitRefresh } from "../../shared/useCommitRefresh";
import {
  SalesQuotePanel,
  SalesCommandError,
} from "../salesopportunitiesops/SalesQuotePanel";
import { stageNames } from "../salesopportunitiesops/SalesOpportunityActions";
import type { SharedCodeLookup } from "../shared/sharedApi";
import * as api from "./salesOpportunityApi";
import "./salesOpportunity.css";

type Row = api.Need & { rowKey: string; selected: string[] };
type Props = {
  detail?: api.Opportunity;
  onClose(): void;
  onSaved(result: api.Result): Promise<void>;
  onDirtyChange?(dirty: boolean): void;
};
export function SalesOpportunityForm({
  detail,
  onClose,
  onSaved,
  onDirtyChange,
}: Props) {
  const navigate = useNavigate(),
    sequence = useRef(0),
    node = useRef<HTMLFormElement>(null);
  const [form, setForm] = useState<api.OpportunityInput>(() => ({
    name: detail?.name ?? "",
    customerId: detail?.customerId ?? "",
    partId: detail?.partId ?? "",
    stage: detail?.stage ?? "FIND",
    probability: detail?.probability ?? "",
    manualProbability: detail?.manualProbability ?? false,
    amount: detail?.amount ?? "",
    startMonth: detail?.startMonth ?? "",
    contractType: detail?.contractType ?? null,
    months: detail ? detail.months : 6,
    nextTask: detail?.nextTask ?? null,
    nextDue: detail?.nextDue ?? null,
    workplace: detail?.workplace ?? null,
    memo: detail?.memo ?? null,
    needs: [],
  }));
  const [rows, setRows] = useState<Row[]>(
    () =>
      detail?.needs.map((n) => ({
        ...n,
        rowKey: `need-${++sequence.current}`,
        selected: detail.tentativeAssignments
          .filter((t) => t.status === "ACT" && t.needSn === n.needSn)
          .map((t) => t.developerId),
      })) ?? [],
  );
  const [customers, setCustomers] = useState<{ id: string; name: string }[]>(
      [],
    ),
    [parts, setParts] = useState<{ id: string; name: string }[]>([]),
    [jobs, setJobs] = useState<SharedCodeLookup[]>([]),
    [grades, setGrades] = useState<SharedCodeLookup[]>([]),
    [defaults, setDefaults] = useState<Partial<Record<api.Stage, string>>>({});
  const [period, setPeriod] = useState<{
      currentDate: string;
      currentMonth: string;
      cashBaseMonth: string;
    }>(),
    [loading, setLoading] = useState(true),
    [lookupError, setLookupError] = useState<unknown>(),
    [error, setError] = useState<unknown>(),
    [fields, setFields] = useState<Record<string, string>>({}),
    [leaving, setLeaving] = useState(false),
    [dirty, setDirty] = useState(false),
    [generation, setGeneration] = useState(0),
    [warning, setWarning] = useState<string>();
  const [selector, setSelector] = useState<string>(),
    [search, setSearch] = useState(""),
    [people, setPeople] = useState<{ id: string; name: string }[]>([]),
    [peoplePage, setPeoplePage] = useState(1),
    [peopleTotal, setPeopleTotal] = useState(0),
    [peopleLoading, setPeopleLoading] = useState(false),
    [peopleError, setPeopleError] = useState<unknown>();
  const [committed, setCommitted] = useState<api.Result>();
  useFieldErrorFocus(node, fields);
  const destination = useRef<string>(),
    { pending, message, commit } = useCommitRefresh(
      "저장됐지만 다시 불러오지 못했습니다",
    );
  const set = <K extends keyof api.OpportunityInput>(
    key: K,
    value: api.OpportunityInput[K],
  ) => {
    setDirty(true);
    setForm((previous) => ({ ...previous, [key]: value }));
  };
  useEffect(() => {
    onDirtyChange?.(dirty);
    return () => onDirtyChange?.(false);
  }, [dirty, onDirtyChange]);
  useEffect(() => {
    let live = true;
    setLoading(true);
    setLookupError(undefined);
    void Promise.all([
      api.getClientOptions("CUSTOMER"),
      api.getSalesPartOptions(),
      api.getSharedCodes("JOB"),
      api.getSharedCodes("GRADE"),
      api.getPeriodContext(),
      Promise.all(
        (Object.keys(stageNames) as api.Stage[]).map(async (stage, index) => {
          try {
            const p = await api.getSharedParameter(`STEP_PRBL_${index + 1}`);
            return [stage, p.value] as const;
          } catch {
            return [stage, undefined] as const;
          }
        }),
      ),
    ])
      .then(([c, p, j, g, context, probabilities]) => {
        if (!live) return;
        setCustomers(c.items);
        setParts(p.items);
        setJobs(j.items);
        setGrades(g.items);
        setPeriod(context);
        const values = Object.fromEntries(probabilities);
        setDefaults(values);
        if (!detail) {
          setForm((f) => ({
            ...f,
            startMonth: f.startMonth || context.currentMonth,
            probability: f.probability || values.FIND || "",
          }));
          setRows((r) =>
            r.length
              ? r
              : [
                  {
                    rowKey: `need-${++sequence.current}`,
                    jobCode: "",
                    gradeCode:
                      g.items.find((v) => v.name === "중급")?.code ?? null,
                    count: 1,
                    startMonth: context.currentMonth,
                    months: 6,
                    monthlyMm: "1.00",
                    expectedRate: null,
                    selected: [],
                  },
                ],
          );
        }
      })
      .catch((e) => {
        if (live) setLookupError(e);
      })
      .finally(() => {
        if (live) setLoading(false);
      });
    return () => {
      live = false;
    };
  }, [detail, generation]);
  useEffect(() => {
    const unload = (e: BeforeUnloadEvent) => {
      if (dirty && !committed) {
        e.preventDefault();
        e.returnValue = "";
      }
    };
    const link = (e: MouseEvent) => {
      const a = (e.target as HTMLElement).closest<HTMLAnchorElement>("a[href]");
      if (
        !a ||
        !dirty ||
        committed ||
        pending ||
        !a.getAttribute("href")?.startsWith("/")
      )
        return;
      e.preventDefault();
      e.stopPropagation();
      destination.current = a.getAttribute("href")!;
      setLeaving(true);
    };
    window.addEventListener("beforeunload", unload);
    document.addEventListener("click", link, true);
    return () => {
      window.removeEventListener("beforeunload", unload);
      document.removeEventListener("click", link, true);
    };
  }, [dirty, committed, pending]);
  useEffect(() => {
    if (!selector) return;
    let live = true;
    setPeopleLoading(true);
    setPeopleError(undefined);
    void api
      .getDevelopers({
        q: search,
        directClass: "DRCT",
        includeStopped: false,
        page: peoplePage,
        size: 20,
      })
      .then((v) => {
        if (live) {
          setPeople(v.items);
          setPeopleTotal(v.total);
        }
      })
      .catch((e) => {
        if (live) setPeopleError(e);
      })
      .finally(() => {
        if (live) setPeopleLoading(false);
      });
    return () => {
      live = false;
    };
  }, [selector, search, peoplePage]);
  function close() {
    if (pending) return;
    if (dirty && !committed) setLeaving(true);
    else onClose();
  }
  function changeRow(index: number, patch: Partial<Row>) {
    setDirty(true);
    setRows((previous) =>
      previous.map((r, i) => (i === index ? { ...r, ...patch } : r)),
    );
  }
  function selectDeveloper(id: string) {
    const target = rows.find((r) => r.rowKey === selector);
    if (!target) return;
    if (target.selected.includes(id)) {
      changeRow(rows.indexOf(target), {
        selected: target.selected.filter((v) => v !== id),
      });
      return;
    }
    if (target.selected.length >= (target.count ?? 0)) {
      setPeopleError(
        new Error(
          "필요 인원수를 초과할 수 없습니다. 먼저 해제 대상을 골라 주세요.",
        ),
      );
      return;
    }
    if (rows.some((r) => r.rowKey !== selector && r.selected.includes(id)))
      setWarning("중복 선택한 개발자를 마지막 선택행으로 이동했습니다.");
    setDirty(true);
    setRows((previous) =>
      previous.map((r) => ({
        ...r,
        selected:
          r.rowKey === selector
            ? [...r.selected, id]
            : r.selected.filter((v) => v !== id),
      })),
    );
  }
  function field(key: string) {
    return fields[key] ? (
      <small className="sales-opportunity-field-error" role="alert">
        {fields[key]}
      </small>
    ) : null;
  }
  async function submit() {
    if (pending || committed) return;
    setError(undefined);
    setFields({});
    const errors: Record<string, string> = {};
    for (const key of [
      "name",
      "customerId",
      "partId",
      "probability",
      "amount",
      "startMonth",
    ] as const)
      if (!form[key]) errors[key] = "필수 입력입니다.";
    if (form.nextDue && !form.nextTask?.trim())
      errors.nextDue = "할 일을 입력한 뒤 기한을 지정해 주세요.";
    if (Object.keys(errors).length) {
      setFields(errors);
      const key = Object.keys(errors)[0];
      node.current?.querySelector<HTMLElement>(`[name="${key}"]`)?.focus();
      return;
    }
    const needs = rows.map(({ rowKey: _key, selected: _selected, ...n }) => ({
      ...n,
      expectedRate: n.expectedRate?.replace(/,/g, "") ?? null,
    }));
    const tentativeSelections = rows.flatMap((r, i) =>
      r.selected.map((developerId) => ({
        ...(r.needSn ? { needSn: r.needSn } : { needIndex: i + 1 }),
        developerId,
        startMonth: r.startMonth!,
        sourceScreenId: "UI-29" as const,
      })),
    );
    let result: api.Result | undefined;
    try {
      await commit(
        async () => {
          const payload = {
            ...form,
            amount: form.amount.replace(/,/g, ""),
            needs,
            tentativeSelections,
          };
          result = detail
            ? await api.putSalesOpportunity(detail.id, {
                ...payload,
                expectedRevision: detail.revision,
              })
            : await api.createSalesOpportunity(payload);
          setCommitted(result);
          setDirty(false);
        },
        async () => {
          if (result) await onSaved(result);
        },
      );
    } catch (e) {
      setError(e);
      if (e instanceof ApiError) {
        setFields(e.fieldMessages);
        const key = Object.keys(e.fieldMessages)[0];
        node.current?.querySelector<HTMLElement>(`[name="${key}"]`)?.focus();
      }
    }
  }

  const needInputs = rows.map(
    ({ rowKey: _key, selected: _selected, ...n }) => n,
  );
  return (
    <section
      data-testid="sales-opportunity-form"
      data-screen-id="SCR-UI-29-FORM"
      className="sales-opportunity-form"
    >
      <FormPage
        title={detail ? "영업 기회 수정" : "영업 기회 등록"}
        breadcrumb="인력 계획 › 영업 파이프라인"
        screenId="UI-29"
        actions={
          <button
            type="button"
            data-testid="opportunity-form-back"
            disabled={pending}
            onClick={close}
          >
            목록
          </button>
        }
      >
        {loading && <p role="status">입력 기준을 불러오는 중입니다.</p>}
        {lookupError != null && (
          <>
            <SalesCommandError error={lookupError} />
            <button
              data-testid="opportunity-lookup-retry"
              onClick={() => setGeneration((v) => v + 1)}
            >
              재조회
            </button>
          </>
        )}
        {error != null && <SalesCommandError error={error} />}
        {message && <p role="status">{message}</p>}
        {warning && (
          <p role="status" className="sales-command-warning">
            {warning}
          </p>
        )}
        {committed && !pending && (
          <div role="status">
            저장 결과 재조회
            <button
              data-testid="opportunity-refresh-retry"
              onClick={() => void onSaved(committed).catch(setError)}
            >
              재조회
            </button>
          </div>
        )}
        <form
          ref={node}
          noValidate
          onSubmit={(e) => {
            e.preventDefault();
            void submit();
          }}
        >
          <fieldset
            disabled={pending || loading || !!lookupError || !!committed}
          >
            <legend>기본 정보</legend>
            <div className="sales-opportunity-fields">
              <label>
                기회명 *
                <input
                  data-testid="opportunity-name"
                  name="name"
                  value={form.name}
                  onChange={(e) => set("name", e.target.value)}
                  aria-invalid={!!fields.name}
                />
                {field("name")}
              </label>
              <label>
                고객사 *
                <select
                  data-testid="opportunity-customer"
                  name="customerId"
                  value={form.customerId}
                  onChange={(e) => set("customerId", e.target.value)}
                >
                  <option value="">선택</option>
                  {detail &&
                    !customers.some((c) => c.id === detail.customerId) && (
                      <option value={detail.customerId}>
                        {detail.customerId} (기존 중지 참조)
                      </option>
                    )}
                  {customers.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.name}
                    </option>
                  ))}
                </select>
                {field("customerId")}
              </label>
              <label>
                영업 파트 *
                <select
                  data-testid="opportunity-part"
                  name="partId"
                  value={form.partId}
                  onChange={(e) => set("partId", e.target.value)}
                >
                  <option value="">선택</option>
                  {detail && !parts.some((p) => p.id === detail.partId) && (
                    <option value={detail.partId}>
                      {detail.partId} (기존 중지 참조)
                    </option>
                  )}
                  {parts.map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.name}
                    </option>
                  ))}
                </select>
                {field("partId")}
              </label>
              <label>
                단계 *
                <select
                  data-testid="opportunity-stage"
                  value={form.stage}
                  onChange={(e) => {
                    const stage = e.target.value as api.Stage;
                    set("stage", stage);
                    if (!form.manualProbability)
                      set("probability", defaults[stage] ?? "");
                  }}
                >
                  {Object.entries(stageNames).map(([value, name]) => (
                    <option key={value} value={value}>
                      {name}
                    </option>
                  ))}
                </select>
                {field("stage")}
              </label>
              <label>
                수주 확률 *
                <NumericInput
                  data-testid="opportunity-probability"
                  name="probability"
                  value={form.probability}
                  onChange={(e) => {
                    set("probability", e.target.value);
                    set("manualProbability", true);
                  }}
                />
                {field("probability")}
              </label>
              <div>
                <p>{form.manualProbability ? "수동 확률" : "기본 확률"}</p>
                <button
                  type="button"
                  data-testid="opportunity-default-probability"
                  disabled={!defaults[form.stage]}
                  onClick={() => {
                    set("probability", defaults[form.stage]!);
                    set("manualProbability", false);
                  }}
                >
                  기본 확률 사용
                </button>
              </div>
              <label>
                예상 금액 (원) *
                <NumericInput
                  kind="amount"
                  data-testid="opportunity-amount"
                  name="amount"
                  inputMode="numeric"
                  value={form.amount}
                  onChange={(e) => set("amount", e.target.value)}
                />
                {field("amount")}
              </label>
              <label>
                계약유형
                <select
                  data-testid="opportunity-contract-type"
                  value={form.contractType ?? ""}
                  onChange={(e) =>
                    set(
                      "contractType",
                      (e.target
                        .value as api.OpportunityInput["contractType"]) || null,
                    )
                  }
                >
                  <option value="">선택</option>
                  <option value="TK">턴키</option>
                  <option value="MO">월단위</option>
                  <option value="DP">파견</option>
                </select>
                {field("contractType")}
              </label>
              <label>
                시작 예정월 *
                <input
                  data-testid="opportunity-start-month"
                  name="startMonth"
                  type="month"
                  min={period?.cashBaseMonth}
                  value={form.startMonth}
                  onChange={(e) => set("startMonth", e.target.value)}
                />
                {field("startMonth")}
              </label>
              <label>
                예상 기간 (개월)
                <NumericInput
                  kind="integer"
                  data-testid="opportunity-months"
                  name="months"
                  value={form.months ?? ""}
                  onChange={(e) =>
                    set(
                      "months",
                      e.target.value === "" ? null : Number(e.target.value),
                    )
                  }
                />
                {field("months")}
                <small>
                  빈칸은 NULL로 저장하고 계산에는 6개월을 사용합니다.
                </small>
              </label>
              <label>
                근무지
                <FieldHelp label="근무지" /><input
                  data-testid="opportunity-workplace"
                  name="workplace"
                  value={form.workplace ?? ""}
                  onChange={(e) => set("workplace", e.target.value || null)}
                />
                {field("workplace")}
              </label>
              <label>
                할 일
                <input
                  data-testid="opportunity-next-task"
                  name="nextTask"
                  value={form.nextTask ?? ""}
                  onChange={(e) => set("nextTask", e.target.value || null)}
                />
                {field("nextTask")}
              </label>
              <label>
                기한
                <FieldHelp label="기한" /><input
                  data-testid="opportunity-next-due"
                  name="nextDue"
                  type="date"
                  className={
                    form.nextDue && period && form.nextDue < period.currentDate
                      ? "sales-opportunity-overdue"
                      : ""
                  }
                  value={form.nextDue ?? ""}
                  onChange={(e) => set("nextDue", e.target.value || null)}
                />
                {field("nextDue")}
              </label>
              <label>
                비고
                <textarea
                  data-testid="opportunity-memo"
                  name="memo"
                  value={form.memo ?? ""}
                  onChange={(e) => set("memo", e.target.value || null)}
                />
                {field("memo")}
              </label>
            </div>
          </fieldset>
          <fieldset disabled={pending || loading || !!committed}>
            <legend>필요 인력</legend>
            <button
              type="button"
              data-testid="opportunity-add-need"
              disabled={rows.length >= 999}
              onClick={() => {
                setDirty(true);
                setRows((previous) => [
                  ...previous,
                  {
                    rowKey: `need-${++sequence.current}`,
                    jobCode: "",
                    gradeCode:
                      grades.find((g) => g.name === "중급")?.code ?? null,
                    count: 1,
                    startMonth: form.startMonth,
                    months: form.months ?? 6,
                    monthlyMm: "1.00",
                    expectedRate: null,
                    selected: [],
                  },
                ]);
              }}
            >
              행 추가
            </button>
            {field("needs")}
            <div className="sicost-table-viewport">
              <table className="sicost-table">
                <thead>
                  <tr>
                    {[
                      "순번",
                      "직무 *",
                      "등급",
                      "인원 *",
                      "시작월 *",
                      "개월 *",
                      "월 MM *",
                      "예상 단가(원)",
                      "가배정",
                      "삭제",
                    ].map((h) => (
                      <th key={h}>{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {rows.map((r, index) => (
                    <tr
                      key={r.rowKey}
                      data-testid={`opportunity-need-${r.rowKey}`}
                    >
                      <td>{r.needSn ?? "신규"}</td>
                      <td>
                        <select
                          data-testid={`need-job-${r.rowKey}`}
                          aria-label={`${index + 1}번째 줄 직무`}
                          name={`needs[${index}].jobCode`}
                          value={r.jobCode ?? ""}
                          onChange={(e) =>
                            changeRow(index, { jobCode: e.target.value })
                          }
                        >
                          <option value="">선택</option>
                          {r.jobCode &&
                            !jobs.some((j) => j.code === r.jobCode) && (
                              <option value={r.jobCode}>
                                {r.jobCode} (기존)
                              </option>
                            )}
                          {jobs.map((j) => (
                            <option key={j.code} value={j.code}>
                              {j.name}
                            </option>
                          ))}
                        </select>
                        {field(`needs[${index}].jobCode`)}
                      </td>
                      <td>
                        <select
                          data-testid={`need-grade-${r.rowKey}`}
                          aria-label={`${index + 1}번째 줄 등급`}
                          value={r.gradeCode ?? ""}
                          onChange={(e) =>
                            changeRow(index, {
                              gradeCode: e.target.value || null,
                            })
                          }
                        >
                          <option value="">미지정 (중급 견적 기준)</option>
                          {r.gradeCode &&
                            !grades.some((g) => g.code === r.gradeCode) && (
                              <option value={r.gradeCode}>
                                {r.gradeCode} (기존)
                              </option>
                            )}
                          {grades.map((g) => (
                            <option key={g.code} value={g.code}>
                              {g.name}
                            </option>
                          ))}
                        </select>
                        {field(`needs[${index}].gradeCode`)}
                      </td>
                      {(
                        [
                          ["count", "인원", "number"],
                          ["startMonth", "시작월", "month"],
                          ["months", "개월", "number"],
                          ["monthlyMm", "월MM", "text"],
                          ["expectedRate", "예상단가", "text"],
                        ] as const
                      ).map(([key, label, type]) => (
                        <td key={key}>
                          <input
                            data-testid={`need-${key.toLowerCase()}-${r.rowKey}`}
                            name={`needs[${index}].${key}`}
                            aria-label={`${index + 1}번째 줄 ${label}`}
                            type={type === "month" ? "month" : "text"}
                            inputMode={type === "month" ? undefined : "decimal"}
                            value={r[key] ?? ""}
                            onChange={(e) =>
                              changeRow(index, {
                                [key]:
                                  key === "count" || key === "months"
                                    ? e.target.value === ""
                                      ? undefined
                                      : Number(
                                          sanitizeNumber(
                                            e.target.value,
                                            "integer",
                                          ),
                                        )
                                    : key === "expectedRate"
                                      ? sanitizeNumber(
                                          e.target.value,
                                          "amount",
                                        ) || null
                                      : key === "monthlyMm"
                                        ? sanitizeNumber(e.target.value)
                                        : e.target.value,
                              })
                            }
                          />
                          {field(`needs[${index}].${key}`)}
                        </td>
                      ))}
                      <td>
                        <button
                          type="button"
                          data-testid={`need-select-${r.rowKey}`}
                          onClick={() => {
                            setSelector(r.rowKey);
                            setPeoplePage(1);
                            setSearch("");
                          }}
                        >
                          개발자 선택
                        </button>
                        {r.selected.map((id) => (
                          <span key={id}>
                            {id}
                            <button
                              type="button"
                              data-testid={`need-release-${r.rowKey}-${id.toLowerCase()}`}
                              onClick={() =>
                                changeRow(index, {
                                  selected: r.selected.filter((v) => v !== id),
                                })
                              }
                            >
                              해제
                            </button>
                          </span>
                        ))}
                        {detail?.tentativeAssignments
                          .filter(
                            (t) =>
                              t.status === "ACT" &&
                              t.needSn === r.needSn &&
                              r.selected.includes(t.developerId) &&
                              r.startMonth &&
                              r.months &&
                              monthEnd(r.startMonth, r.months) < t.startMonth,
                          )
                          .map((t) => (
                            <p key={t.id} className="sales-command-warning">
                              {t.developerId} 해제 예정
                            </p>
                          ))}
                      </td>
                      <td>
                        <button
                          type="button"
                          data-testid={`need-delete-${r.rowKey}`}
                          disabled={rows.length <= 1}
                          onClick={() => {
                            setDirty(true);
                            setRows((previous) =>
                              previous.filter((v) => v.rowKey !== r.rowKey),
                            );
                          }}
                        >
                          행 삭제
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <p>
              빈 예상단가는 직무 기본단가를 사용합니다. 빈 등급은 견적에서 중급
              기준입니다. 행의 기간은 기회 기간과 독립입니다.
            </p>
          </fieldset>
          <p>
            기회 예상금액 {form.amount || "–"}원과 필요 인력 견적 매출은
            별개이며 자동으로 덮어쓰지 않습니다.
          </p>
          <SalesQuotePanel needs={needInputs} />
          <footer className="sales-opportunity-footer">
            <button
              type="button"
              data-testid="opportunity-form-cancel"
              disabled={pending}
              onClick={close}
            >
              취소
            </button>
            <button
              type="submit"
              className="sicost-primary"
              data-testid="opportunity-form-save"
              disabled={pending || loading || !!lookupError || !!committed}
            >
              저장
            </button>
          </footer>
        </form>
        <Dialog
          open={leaving}
          title="미저장 이동 확인"
          confirmLabel="나가기"
          cancelLabel="계속 작성"
          onCancel={() => {
            destination.current = undefined;
            setLeaving(false);
          }}
          onConfirm={() => {
            setDirty(false);
            if (destination.current) navigate(destination.current);
            else onClose();
          }}
        >
          <p>저장하지 않은 내용은 사라집니다.</p>
        </Dialog>
        <Dialog
          open={!!selector}
          title="개발자 선택"
          confirmLabel="선택 완료"
          onCancel={() => setSelector(undefined)}
          onConfirm={() => setSelector(undefined)}
        >
          <div data-testid="opportunity-developer-selector">
            <label>
              개발자 검색
              <input
                data-testid="opportunity-developer-search"
                value={search}
                onChange={(e) => {
                  setSearch(e.target.value);
                  setPeoplePage(1);
                }}
              />
            </label>
            {peopleLoading && <p role="status">개발자를 불러오는 중입니다.</p>}
            {peopleError != null && <SalesCommandError error={peopleError} />}
            {!peopleLoading && !people.length && (
              <p>선택할 개발자가 없습니다.</p>
            )}
            {people.map((p) => (
              <label key={p.id}>
                <input
                  type="checkbox"
                  data-testid={`opportunity-developer-${p.id.toLowerCase()}`}
                  checked={
                    rows
                      .find((r) => r.rowKey === selector)
                      ?.selected.includes(p.id) ?? false
                  }
                  onChange={() => selectDeveloper(p.id)}
                />
                {p.name}
              </label>
            ))}
            <button
              type="button"
              data-testid="opportunity-people-previous"
              disabled={peoplePage <= 1 || peopleLoading}
              onClick={() => setPeoplePage((v) => v - 1)}
            >
              이전
            </button>
            <button
              type="button"
              data-testid="opportunity-people-next"
              disabled={peoplePage * 20 >= peopleTotal || peopleLoading}
              onClick={() => setPeoplePage((v) => v + 1)}
            >
              다음
            </button>
          </div>
        </Dialog>
      </FormPage>
    </section>
  );
}
function monthEnd(month: string, months: number) {
  const [year, value] = month.split("-").map(Number);
  const date = new Date(Date.UTC(year, value - 1 + months - 1, 1));
  return `${date.getUTCFullYear()}-${String(date.getUTCMonth() + 1).padStart(2, "0")}`;
}
