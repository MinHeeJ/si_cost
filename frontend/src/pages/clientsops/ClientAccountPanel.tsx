import { useEffect, useRef, useState } from "react";
import * as api from "./clientActionsApi";
import "./clientActions.css";

// SCR-UI-15: mounted inside the existing basic-information drawer; never shared or persistent plaintext state.
export function ClientAccountPanel({
  clientId,
  maskedAccount,
  open = true,
  active = true,
}: {
  clientId: string;
  maskedAccount: string | null;
  open?: boolean;
  active?: boolean;
}) {
  return open && active ? (
    <AccountValue
      key={clientId}
      clientId={clientId}
      maskedAccount={maskedAccount}
    />
  ) : null;
}
function AccountValue({
  clientId,
  maskedAccount,
}: {
  clientId: string;
  maskedAccount: string | null;
}) {
  const [value, setValue] = useState<api.ClientAccount>();
  const [pending, setPending] = useState(false),
    [error, setError] = useState("");
  const controller = useRef<AbortController>(),
    generation = useRef(0),
    inFlight = useRef(false);
  function hide() {
    generation.current++;
    controller.current?.abort();
    inFlight.current = false;
    setValue(undefined);
    setError("");
    setPending(false);
  }
  useEffect(
    () => () => {
      generation.current++;
      controller.current?.abort();
    },
    [],
  );
  async function reveal() {
    if (inFlight.current) return;
    inFlight.current = true;
    const version = ++generation.current;
    controller.current = new AbortController();
    setPending(true);
    setError("");
    setValue(undefined);
    try {
      const result = await api.getClientAccount(
        clientId,
        controller.current.signal,
      );
      if (version === generation.current) setValue(result);
    } catch (reason) {
      if (version === generation.current)
        setError(
          reason instanceof Error
            ? reason.message
            : "접근기록을 저장하지 못해 계좌번호를 표시하지 않았습니다.",
        );
    } finally {
      if (version === generation.current) {
        setPending(false);
        inFlight.current = false;
      }
    }
  }
  return (
    <section
      className="client-account-panel"
      data-testid="client-account-panel"
      aria-label="지급 계좌"
    >
      <span>
        {value
          ? `${value.bankName ?? ""} ${value.accountNumber ?? "–"}`
          : (maskedAccount ?? "–")}
      </span>
      {value ? (
        <button type="button" data-testid="client-account-hide" onClick={hide}>
          숨기기
        </button>
      ) : (
        <button
          type="button"
          data-testid="client-account-reveal"
          disabled={pending || !maskedAccount}
          onClick={() => void reveal()}
        >
          {pending ? "확인 중…" : "전체 계좌번호 보기"}
        </button>
      )}
      {pending && (
        <button
          type="button"
          data-testid="client-account-cancel"
          onClick={hide}
        >
          숨기기
        </button>
      )}
      {error && (
        <p role="alert" className="sicost-field-error">
          {error}
        </p>
      )}
    </section>
  );
}
