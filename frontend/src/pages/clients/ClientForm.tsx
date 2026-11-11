import { useEffect, useRef, useState } from "react";
import { Text } from "../../shared/Text";
import { CodeSelect } from "../../shared/CodeSelect";
import { Dialog } from "../../shared/Dialog";
import { ErrorBanner } from "../../shared/Feedback";
import { ApiError } from "../../shared/api";
import * as api from "./clientsApi";
import type {
  Client,
  ClientCode,
  ClientType,
  ClientSaveResult,
  PutClient,
} from "./clientsApi";

type Props = {
  client?: Client;
  onClose(): void;
  onSaved(result: ClientSaveResult): void | Promise<void>;
  onDirtyChange?(dirty: boolean): void;
};
type Form = {
  type: ClientType | "";
  name: string;
  businessNo: string;
  ceo: string;
  industryCode: string;
  contactName: string;
  phone: string;
  taxEmail: string;
  bankCode: string;
  accountNumber: string;
  depositor: string;
  accountAction: "KEEP" | "REPLACE";
};
export function ClientForm({ client, onClose, onSaved, onDirtyChange }: Props) {
  const [form, setForm] = useState<Form>(() => ({
    type: client?.type ?? "",
    name: client?.name ?? "",
    businessNo: client?.businessNo ?? "",
    ceo: client?.ceo ?? "",
    industryCode: client?.industryCode ?? "",
    contactName: client?.contactName ?? "",
    phone: client?.phone ?? "",
    taxEmail: client?.taxEmail ?? "",
    bankCode: "",
    accountNumber: "",
    depositor: "",
    accountAction: "KEEP",
  }));
  const initial = useRef(JSON.stringify(form));
  const dirty = JSON.stringify(form) !== initial.current;
  const [codes, setCodes] = useState<Record<string, ClientCode[]>>({});
  const [error, setError] = useState<unknown>();
  const [codeError, setCodeError] = useState<unknown>();
  const [fields, setFields] = useState<Record<string, string>>({});
  const [pending, setPending] = useState(false),
    [confirm, setConfirm] = useState(false),
    [leaving, setLeaving] = useState(false);
  const busy = useRef(false);
  const vendor = form.type === "VNDR" || form.type === "BOTH";
  const customer = form.type === "CUST" || form.type === "BOTH";
  async function loadCodes() {
    setCodeError(undefined);
    try {
      const results = await Promise.all([
        api.getClientCodes("CLNT_TYPE"),
        api.getClientCodes("INDUTY"),
        api.getClientCodes("BANK"),
      ]);
      setCodes({
        type: results[0].items,
        industryCode: results[1].items,
        bankCode: results[2].items,
      });
    } catch (e) {
      setCodeError(e);
    }
  }
  useEffect(() => {
    void loadCodes();
  }, []);
  useEffect(() => {
    onDirtyChange?.(dirty);
    return () => onDirtyChange?.(false);
  }, [dirty, onDirtyChange]);
  useEffect(() => {
    const prevent = (event: BeforeUnloadEvent) => {
      if (dirty) {
        event.preventDefault();
        event.returnValue = "";
      }
    };
    window.addEventListener("beforeunload", prevent);
    return () => window.removeEventListener("beforeunload", prevent);
  }, [dirty]);
  function change(key: keyof Form, value: string) {
    setForm((previous) => ({
      ...previous,
      [key]: value,
      ...(key === "type" && previous.type === "CUST" && value !== "CUST"
        ? { accountAction: "REPLACE" as const }
        : {}),
    }));
    setFields((previous) => ({ ...previous, [key]: "" }));
  }
  function options(key: "industryCode" | "bankCode") {
    const list = codes[key] ?? [];
    if (
      key === "industryCode" &&
      client?.industryCode &&
      !list.some((item) => item.code === client.industryCode)
    )
      return [
        {
          code: client.industryCode,
          name: `${client.industry ?? client.industryCode} (중지)`,
        },
        ...list,
      ];
    return list;
  }
  async function save() {
    if (busy.current || !form.type) return;
    busy.current = true;
    setPending(true);
    setError(undefined);
    setFields({});
    try {
      const base = {
        type: form.type,
        name: form.name,
        ceo: form.ceo,
        industryCode: form.industryCode,
        contactName: form.contactName,
        phone: form.phone,
        ...(customer ? { taxEmail: form.taxEmail } : {}),
      };
      const accountAction = !vendor
        ? "CLEAR"
        : client?.type === "CUST"
          ? "REPLACE"
          : form.accountAction;
      const account =
        vendor && (!client || accountAction === "REPLACE")
          ? {
              bankCode: form.bankCode,
              accountNumber: form.accountNumber,
              depositor: form.depositor,
            }
          : {};
      const result = client
        ? await api.putClient(client.id, {
            ...base,
            ...account,
            accountAction,
            expectedRevision: client.revision,
          } as PutClient)
        : await api.createClient({
            ...base,
            ...account,
            businessNo: form.businessNo,
          });
      setConfirm(false);
      onDirtyChange?.(false);
      await onSaved(result);
    } catch (e) {
      setConfirm(false);
      setError(e);
      if (e instanceof ApiError) setFields(e.fieldMessages);
    } finally {
      busy.current = false;
      setPending(false);
    }
  }
  const close = () => {
    if (pending) return;
    if (dirty) setLeaving(true);
    else onClose();
  };
  const text = (
    key: keyof Form,
    label: string,
    maxLength: number,
    required = false,
    readOnly = false,
  ) => (
    <Text
      key={key}
      label={label}
      data-testid={`client-${key.replace(/[A-Z]/g, (c) => `-${c.toLowerCase()}`)}-input`}
      value={form[key]}
      onChange={(value) => change(key, value)}
      error={fields[key]}
      maxLength={maxLength}
      required={required}
      readOnly={readOnly}
      disabled={pending}
    />
  );
  return (
    <section
      className={client ? "clients-edit" : "clients-form-page"}
      data-testid="client-form"
      data-screen-id="SCR-UI-15"
    >
      <header className="clients-toolbar">
        <h2>{client ? "기본 정보 수정" : "거래처 등록"}</h2>
        <button
          type="button"
          data-testid="client-form-back"
          disabled={pending}
          onClick={close}
        >
          {client ? "취소" : "목록"}
        </button>
      </header>
      {codeError != null && (
        <ErrorBanner
          message="선택 항목을 불러오지 못했습니다."
          onRetry={() => void loadCodes()}
        />
      )}
      {error != null && (
        <ErrorBanner
          message={
            error instanceof Error
              ? error.message
              : "저장하지 못했습니다. 입력한 내용은 유지됩니다."
          }
          requestId={
            error instanceof ApiError ? error.body.requestId : undefined
          }
        />
      )}
      <form
        data-testid="client-edit-form"
        onSubmit={(event) => {
          event.preventDefault();
          if (client?.account && (!vendor || form.accountAction === "REPLACE"))
            setConfirm(true);
          else void save();
        }}
      >
        <fieldset disabled={pending} className="clients-form-fields">
          <legend>기본 정보</legend>
          <CodeSelect
            label="구분"
            value={form.type}
            options={codes.type ?? []}
            required
            onChange={(value) => change("type", value)}
            error={fields.type}
          />
          {text("name", "상호", 50, true)}
          {text("businessNo", "사업자번호", 12, true, Boolean(client))}
          {text("ceo", "대표자", 20)}
          <CodeSelect
            label="업종"
            value={form.industryCode}
            options={options("industryCode")}
            onChange={(value) => change("industryCode", value)}
            error={fields.industryCode}
          />
          {text("contactName", "담당자", 20)}
          {text("phone", "연락처", 20)}
        </fieldset>
        {!form.type && (
          <p className="sicost-banner sicost-tone-info">
            구분을 선택하면 세금계산서 이메일 또는 지급 계좌를 입력할 수
            있습니다.
          </p>
        )}
        {customer && (
          <fieldset disabled={pending} className="clients-form-fields">
            <legend>세금계산서</legend>
            {text("taxEmail", "세금계산서 이메일", 100, true)}
          </fieldset>
        )}
        {vendor && (
          <fieldset disabled={pending} className="clients-form-fields">
            <legend>지급 계좌</legend>
            {client && (
              <>
                <label className="sicost-field">
                  계좌 작업
                  <select
                    data-testid="client-account-action"
                    value={
                      client.type === "CUST" ? "REPLACE" : form.accountAction
                    }
                    onChange={(event) =>
                      change("accountAction", event.target.value)
                    }
                  >
                    {client.type !== "CUST" && (
                      <option value="KEEP">등록 계좌 유지</option>
                    )}
                    <option value="REPLACE">새 계좌로 교체</option>
                  </select>
                </label>
                <p>
                  {client.account ?? "등록된 지급 계좌가 없습니다."}
                  {client.bankActive === false && " (중지)"}
                </p>
              </>
            )}
            {(!client ||
              client.type === "CUST" ||
              form.accountAction === "REPLACE") && (
              <>
                <CodeSelect
                  label="은행"
                  value={form.bankCode}
                  options={options("bankCode")}
                  required
                  onChange={(value) => change("bankCode", value)}
                  error={fields.bankCode}
                />
                {text("accountNumber", "계좌번호", 20, true)}
                {text("depositor", "예금주", 30, true)}
              </>
            )}
            {client &&
              client.type !== "CUST" &&
              form.accountAction === "KEEP" && (
                <Text
                  label="계좌번호"
                  data-testid="client-account-keep-input"
                  value=""
                  onChange={() => {}}
                  readOnly
                  placeholder="등록 계좌 유지 — 새 계좌로 교체할 때 입력"
                />
              )}
          </fieldset>
        )}
        <footer className="clients-actions">
          <button
            type="button"
            data-testid="client-form-cancel"
            disabled={pending}
            onClick={close}
          >
            취소
          </button>
          <button
            type="submit"
            data-testid="client-form-save"
            className="sicost-primary"
            disabled={pending || !form.type || Boolean(codeError)}
          >
            {pending ? "저장 중…" : "저장"}
          </button>
        </footer>
      </form>
      <Dialog
        open={confirm}
        title="지급 계좌 변경 확인"
        pending={pending}
        onCancel={() => setConfirm(false)}
        onConfirm={save}
        confirmLabel="확인 후 저장"
      >
        {!vendor ? (
          <p>고객사로 변경하면 지급 계좌 정보가 지워집니다</p>
        ) : (
          <>
            <p>다음 지급부터 새 계좌로 보냅니다</p>
            <p>
              {
                codes.bankCode?.find((item) => item.code === form.bankCode)
                  ?.name
              }{" "}
              ****{form.accountNumber.replace(/\D/g, "").slice(-4)}
            </p>
          </>
        )}
      </Dialog>
      <Dialog
        open={leaving}
        title="저장하지 않은 내용이 있습니다."
        onCancel={() => setLeaving(false)}
        onConfirm={onClose}
        confirmLabel="나가기"
        cancelLabel="계속 작성"
      >
        <p>저장하지 않은 내용이 있습니다.</p>
      </Dialog>
    </section>
  );
}
